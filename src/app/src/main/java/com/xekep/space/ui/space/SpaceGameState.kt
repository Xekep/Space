package com.xekep.space.ui.space

import androidx.annotation.StringRes
import com.xekep.space.R
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class ArcadeDifficulty(val title: String, val lives: Int, val spawnDelay: Double, val scoreFactor: Double) {
    Easy("Easy", 6, 1.3, 0.8), Normal("Normal", 4, 1.0, 1.0), Hard("Hard", 3, 0.75, 1.5);
    val warningSeconds: Double get() = when (this) { Easy -> 1.6; Normal -> 1.2; Hard -> 0.9 }
}
data class SpaceCamera(val center: Vec2 = Vec2.Zero, val zoom: Float = 1f)
data class PendingThreat(val body: CelestialBody, val seconds: Double)
data class ArcadeSession(
    val bodies: List<CelestialBody>, val camera: SpaceCamera, val arena: IntSize, val difficulty: ArcadeDifficulty,
    val energy: Double = MaxEnergy, val score: Double = 0.0, val lives: Int = difficulty.lives,
    val destroyed: Int = 0, val elapsed: Double = 0.0, val spawnTimer: Double = 3.0, val combo: Double = 1.0,
    val hitFlash: Double = 0.0, val immunity: Double = 0.0, val lastIntercept: Double = 0.0,
    val pending: List<PendingThreat> = emptyList(), val launches: Int = 0, val successfulLaunches: Set<Long> = emptySet(),
    val practice: Boolean = false,
) {
    val wave: Int get() = 1 + (elapsed / 28.0 + 1e-9).toInt()
    val resting: Boolean get() = elapsed % 28.0 >= 24.0
    val accuracy: Int get() = if (launches == 0) 0 else successfulLaunches.size * 100 / launches
}
data class SandboxSession(
    val bodies: List<CelestialBody>, val camera: SpaceCamera, val referenceEnergy: Double, val preset: SandboxPresetKind,
    val timeScale: Double = 1.0, val paused: Boolean = false, val collisionsEnabled: Boolean = false, val name: String = preset.title,
)

@Stable
class SpaceGameState(
    initialBestScore: Double = 0.0, private val saveBestScore: (Double) -> Unit = {}, private val random: Random = Random.Default,
    initialRecords: Map<ArcadeDifficulty, Double> = emptyMap(), private val saveRecord: (ArcadeDifficulty, Double) -> Unit = { _, _ -> },
) {
    var mode by mutableStateOf(AppMode.Arcade); private set
    var menuOpen by mutableStateOf(true); private set
    var viewport by mutableStateOf(IntSize.Zero); private set
    var density by mutableFloatStateOf(1f); private set
    var arcade by mutableStateOf<ArcadeSession?>(null); private set
    var sandbox by mutableStateOf<SandboxSession?>(null); private set
    var records by mutableStateOf(initialRecords.ifEmpty { mapOf(ArcadeDifficulty.Normal to initialBestScore) }); private set
    var bestScore by mutableDoubleStateOf(records[ArcadeDifficulty.Normal] ?: 0.0); private set
    var touchPreview by mutableStateOf<TouchPreview?>(null)
    var feedback by mutableStateOf<Int?>(null); private set
    var selectedBodyId by mutableStateOf<Long?>(null); private set
    var following by mutableStateOf(false); private set
    var orbitSourceId by mutableStateOf<Long?>(null); private set
    var undoCount by mutableIntStateOf(0); private set
    var checkpoint by mutableStateOf<SandboxSession?>(null); private set
    var dirty by mutableStateOf(false); private set
    var tutorialStep by mutableIntStateOf(-1); private set
    var behind by mutableStateOf(false); private set
    var spawnKind by mutableStateOf(BodyKind.Ambient); private set
    var sandboxOverlayOpen by mutableStateOf(false)
    var pendingExport: SandboxSnapshot? = null
    private var accumulator = 0.0
    private var sandboxRevision = 0L
    private val history = ArrayDeque<SandboxSession>()
    private var movedCameraInTutorial = false
    val hasSession: Boolean get() = if (mode == AppMode.Arcade) arcade != null else sandbox != null
    val bodies: List<CelestialBody> get() = if (mode == AppMode.Arcade) arcade?.bodies.orEmpty() else sandbox?.bodies.orEmpty()
    val camera: SpaceCamera get() = if (mode == AppMode.Arcade) arcade?.camera ?: SpaceCamera() else sandbox?.camera ?: SpaceCamera()
    val selectedBody: CelestialBody? get() {
        val id = selectedBodyId ?: return null
        return if (mode == AppMode.Sandbox) bodies.firstOrNull { it.id == id } else null
    }
    val orbitSource: CelestialBody? get() {
        val id = orbitSourceId ?: return null
        return if (mode == AppMode.Sandbox) sandbox?.bodies?.firstOrNull { it.id == id } else null
    }
    val tutorialText: Int? get() = when {
        tutorialStep < 0 -> null
        mode == AppMode.Arcade && tutorialStep == 0 -> R.string.tutorial_arcade_launch
        mode == AppMode.Arcade && tutorialStep == 1 -> R.string.tutorial_arcade_intercept
        mode == AppMode.Arcade -> R.string.tutorial_arcade_prepare
        tutorialStep == 0 -> R.string.tutorial_sandbox_create
        tutorialStep == 1 -> R.string.tutorial_sandbox_play
        else -> R.string.tutorial_sandbox_camera
    }
    fun resize(size: IntSize) { viewport = size }
    fun updateDensity(value: Float) { density = value.coerceAtLeast(0.5f) }
    fun resetFrameClock() { accumulator = 0.0; behind = false }
    private fun persistRecord() { arcade?.let { saveRecord(it.difficulty, recordFor(it.difficulty)) }; saveBestScore(bestScore) }
    fun recordFor(difficulty: ArcadeDifficulty) = records[difficulty] ?: 0.0
    fun openMenu() { touchPreview = null; menuOpen = true; resetFrameClock(); persistRecord() }
    fun closeMenu() { if (hasSession) { menuOpen = false; resetFrameClock() } }
    fun enterMode(nextMode: AppMode) {
        persistRecord()
        if (nextMode == AppMode.Arcade && arcade == null) startArcade()
        if (nextMode == AppMode.Sandbox && sandbox == null) startSandbox()
        mode = nextMode; touchPreview = null; tutorialStep = -1; menuOpen = !hasSession
        bestScore = arcade?.let { recordFor(it.difficulty) } ?: bestScore; resetFrameClock()
    }
    fun startArcade(difficulty: ArcadeDifficulty = ArcadeDifficulty.Normal) {
        if (viewport == IntSize.Zero) return
        persistRecord()
        val scene = SimulationEngine.arcadeBodies(Vec2(900.0, 1400.0))
        arcade = ArcadeSession(scene, SpaceCamera(scene.first().position, arcadeFitZoom()), IntSize(900, 1400), difficulty)
        bestScore = recordFor(difficulty); mode = AppMode.Arcade; touchPreview = null
        menuOpen = false; feedback = null; tutorialStep = -1; resetFrameClock()
    }
    fun startSandbox(preset: SandboxPresetKind = SandboxPresetKind.SolarSystem, name: String = preset.title) {
        sandboxRevision++
        val scene = SimulationEngine.sandboxPreset(preset)
        sandbox = SandboxSession(scene.bodies, SpaceCamera(scene.cameraCenter, scene.zoom), scene.referenceEnergy, preset, name = name)
        history.clear(); undoCount = 0; checkpoint = sandbox; dirty = false; clearSelection()
        mode = AppMode.Sandbox; touchPreview = null; menuOpen = false; feedback = null; tutorialStep = -1; resetFrameClock()
        spawnKind = BodyKind.Ambient
    }
    fun update(dt: Double) {
        if (menuOpen || !hasSession || !dt.isFinite() || dt <= 0.0) return
        if (mode == AppMode.Sandbox && (sandbox?.paused == true || sandboxOverlayOpen)) { resetFrameClock(); return }
        if (mode == AppMode.Arcade && (arcade?.lives ?: 0) <= 0) { resetFrameClock(); return }
        accumulator += dt
        if (accumulator > 2.0) { openMenu(); feedback = R.string.lag_paused; return }
        var steps = 0
        val seconds = 1.0 / 60.0
        while (accumulator + 1e-9 >= seconds && steps < 15) {
            if (mode == AppMode.Sandbox) {
                val current = sandbox ?: break
                val next = SimulationEngine.stepSandbox(current.bodies, seconds * current.timeScale, current.referenceEnergy, current.collisionsEnabled).bodies
                publishSandboxBodies(next)
            } else {
                val current = arcade ?: break
                if (current.lives <= 0) { accumulator = 0.0; break }
                val next = advanceArcade(current, seconds, random); arcade = next
                val best = maxOf(recordFor(next.difficulty), next.score)
                if (!next.practice) { records = records + (next.difficulty to best); bestScore = best }
                if (next.practice && next.hitFlash > current.hitFlash) feedback = R.string.practice_hit
                if (tutorialStep == 1 && next.destroyed > current.destroyed) tutorialStep = 2
                if (next.lives <= 0) { touchPreview = null; persistRecord() }
            }
            accumulator -= seconds; steps++
        }
        behind = accumulator >= seconds
        if (selectedBodyId != null && sandbox?.bodies?.none { it.id == selectedBodyId } == true) { selectedBodyId = null; following = false }
    }

    /** Called from the main thread. Only immutable body snapshots leave it; UI edits never wait
     * for the solver, and an obsolete result cannot overwrite a newer scene. */
    suspend fun updateSandboxAsync(dt: Double) {
        if (mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen || !dt.isFinite() || dt <= 0.0) return
        val current = sandbox ?: return
        if (current.paused) { resetFrameClock(); return }
        accumulator += dt
        if (accumulator > 2.0) { openMenu(); feedback = R.string.lag_paused; return }
        val seconds = 1.0 / 60.0
        val steps = ((accumulator + 1e-9) / seconds).toInt().coerceAtMost(15)
        if (steps == 0) return
        val revision = sandboxRevision
        val next = withContext(Dispatchers.Default) {
            var bodies = current.bodies
            repeat(steps) {
                currentCoroutineContext().ensureActive()
                bodies = SimulationEngine.stepSandbox(bodies, seconds * current.timeScale, current.referenceEnergy, current.collisionsEnabled).bodies
            }
            bodies
        }
        if (revision != sandboxRevision || mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen ||
            sandbox?.paused != false || sandbox?.bodies !== current.bodies) { resetFrameClock(); return }
        publishSandboxBodies(next)
        accumulator = (accumulator - steps * seconds).coerceAtLeast(0.0)
        behind = accumulator >= seconds
    }

    private fun publishSandboxBodies(next: List<CelestialBody>) {
        val current = sandbox ?: return
        if (next != current.bodies) dirty = true
        val selected = next.firstOrNull { it.id == selectedBodyId }
        sandbox = current.copy(bodies = next, camera = if (following && selected != null)
            current.camera.copy(center = selected.position) else current.camera)
        if (selectedBodyId != null && selected == null) { selectedBodyId = null; following = false }
    }
    fun worldAt(position: Offset): Vec2 = screenToWorld(position, viewport, camera.center, camera.zoom)
    fun previewAt(start: Offset, end: Offset, startedAt: Long) = TouchPreview(worldAt(start), worldAt(end), startedAt, (end - start) / density)
    fun launchVelocity(preview: TouchPreview): Vec2 = preview.dragDp?.let(SimulationEngine::velocityFromGesture)
        ?: SimulationEngine.launchVelocityFromDrag(Offset.Zero, (preview.currentWorld - preview.startWorld).toOffset())
    fun previewBody(preview: TouchPreview, holdSeconds: Double): CelestialBody? {
        val kind = if (mode == AppMode.Arcade) BodyKind.Player else spawnKind
        val hold = holdSeconds.coerceIn(0.0, 4.0)
        val requested = when (kind) {
            BodyKind.Ship -> 24.0 + 18.0 * hold
            BodyKind.Rocket -> 12.0 + 6.0 * hold
            else -> SimulationEngine.massFromHold(hold)
        }
        val affordable = if (mode == AppMode.Arcade) ((arcade?.energy ?: 0.0) - 10.0) / 0.028 else requested
        if (mode == AppMode.Arcade && affordable + 1e-8 < 70.0) return null
        // Keep an assisted satellite small enough that its parent remains dominant.
        val mass = orbitSource?.let { minOf(requested, it.mass * 0.02).coerceAtLeast(1.0) }
            ?: minOf(requested, affordable)
        val velocity = orbitSource?.let { SimulationEngine.orbitVelocity(it, preview.startWorld) } ?: launchVelocity(preview)
        val color = when (kind) { BodyKind.Ship -> Color(0xFF8BD3FF); BodyKind.Rocket -> Color(0xFFFFB36B); else -> Color.Cyan }
        return CelestialBody(-1, preview.startWorld, velocity, mass,
            when (kind) { BodyKind.Ship -> 8f; BodyKind.Rocket -> 6f; else -> SimulationEngine.radiusForMass(mass) }, color, kind,
            burnRemaining = if (kind == BodyKind.Rocket) 3.0 else 0.0,
            heading = if (velocity.magnitude() > 1e-6) velocity.normalized() else Vec2(0.0, -1.0))
    }
    fun transformCamera(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (menuOpen || !hasSession || viewport == IntSize.Zero) return
        val previous = camera; val anchor = screenToWorld(centroid, viewport, previous.center, previous.zoom)
        val zoom = (previous.zoom * zoomChange).coerceIn(minimumZoom(mode), maximumZoom(mode))
        val after = screenToWorld(centroid + pan, viewport, previous.center, zoom)
        following = false; setCamera(SpaceCamera(previous.center + anchor - after, zoom))
        if (tutorialStep == 2 && mode == AppMode.Sandbox) movedCameraInTutorial = true
    }
    private fun setCamera(camera: SpaceCamera) {
        if (mode == AppMode.Arcade) arcade = arcade?.copy(camera = camera) else sandbox = sandbox?.copy(camera = camera)
    }
    private fun arcadeFitZoom() = minOf(viewport.width * 0.88f / 900f, viewport.height * 0.60f / 1400f).coerceAtLeast(ArcadeMinZoom)
    fun fitCamera() {
        touchPreview = null; following = false
        if (viewport == IntSize.Zero) return
        if (mode == AppMode.Arcade) { val core = bodies.firstOrNull { it.kind == BodyKind.Core } ?: return; setCamera(SpaceCamera(core.position, arcadeFitZoom())) }
        else {
            if (bodies.isEmpty()) setCamera(SpaceCamera()) else {
                val minX = bodies.minOf { it.position.x - it.radius }; val maxX = bodies.maxOf { it.position.x + it.radius }
                val minY = bodies.minOf { it.position.y - it.radius }; val maxY = bodies.maxOf { it.position.y + it.radius }
                val zoom = minOf(viewport.width * 0.8 / (maxX - minX).coerceAtLeast(100.0), viewport.height * 0.6 / (maxY - minY).coerceAtLeast(100.0)).toFloat().coerceIn(SandboxMinZoom, 1f)
                setCamera(SpaceCamera(Vec2((minX + maxX) / 2, (minY + maxY) / 2), zoom))
            }
            if (tutorialStep == 2 && movedCameraInTutorial) tutorialStep = -1
        }
    }
    fun finishGesture(preview: TouchPreview, holdSeconds: Double) {
        if (mode == AppMode.Sandbox && holdSeconds < 0.3 && (preview.dragDp?.getDistance() ?: 0f) < 12f && orbitSourceId == null) {
            val body = bodies.minByOrNull { (it.position - preview.startWorld).magnitude() }
            if (body != null && (body.position - preview.startWorld).magnitude() <= maxOf(body.radius.toDouble(), 20.0 * density / camera.zoom)) {
                selectedBodyId = body.id; feedback = null; return
            }
        }
        launch(preview, holdSeconds)
    }
    fun launch(preview: TouchPreview, holdSeconds: Double) {
        if (menuOpen || !hasSession || sandboxOverlayOpen || (mode == AppMode.Arcade && (arcade?.lives ?: 0) <= 0)) return
        if ((mode == AppMode.Arcade && bodies.count { it.kind == BodyKind.Player } >= 30) ||
            (mode == AppMode.Sandbox && bodies.size >= 1000)) { feedback = R.string.scene_full; return }
        val candidate = previewBody(preview, holdSeconds)
        if (candidate == null) { feedback = R.string.not_enough_energy; return }
        if (orbitSource != null && (candidate.position - orbitSource!!.position).magnitude() <= candidate.radius + orbitSource!!.radius + 8) {
            feedback = R.string.satellite_farther; return
        }
        val generated = SimulationEngine.createBody(candidate.position, candidate.position, 0.0, candidate.kind)
        val body = candidate.copy(id = generated.id, color = if (candidate.kind == BodyKind.Ship || candidate.kind == BodyKind.Rocket) candidate.color else generated.color)
        if (mode == AppMode.Arcade) {
            val current = arcade ?: return
            arcade = current.copy(bodies = current.bodies + body, energy = (current.energy - SimulationEngine.energyCostForMass(body.mass)).coerceAtLeast(0.0), launches = current.launches + 1)
            if (tutorialStep == 0) tutorialStep = 1 else if (tutorialStep == 2) {
                startArcade(ArcadeDifficulty.Easy)
                feedback = R.string.practice_complete
                return
            }
        } else {
            sandboxRevision++
            rememberEdit(); val current = sandbox ?: return; val next = current.bodies + body
            sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next)); selectedBodyId = null; orbitSourceId = null; dirty = true
            if (tutorialStep == 0) tutorialStep = 1
        }
        feedback = null
    }
    private fun rememberEdit() { sandbox?.let { if (history.size >= 20) history.removeFirst(); history.addLast(it); undoCount = history.size } }
    fun undo() {
        if (history.isEmpty()) return
        sandboxRevision++
        sandbox = history.removeLast(); undoCount = history.size; clearSelection(); dirty = true; resetFrameClock()
    }
    fun saveCheckpoint() { checkpoint = sandbox; feedback = R.string.checkpoint_stored }
    fun restoreCheckpoint() { checkpoint?.let { sandboxRevision++; rememberEdit(); sandbox = it; dirty = true; clearSelection(); resetFrameClock() } }
    fun markSaved() { dirty = false }
    fun inform(@StringRes message: Int) { feedback = message }
    fun dismissFeedback() { feedback = null }
    fun shakeSandbox(impulse: Vec2): Boolean {
        val current = sandbox ?: return false
        if (mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen || current.paused || current.bodies.isEmpty() ||
            !impulse.x.isFinite() || !impulse.y.isFinite() || impulse.magnitude() !in 1.0..80.000001) return false
        sandboxRevision++; rememberEdit()
        val next = current.bodies.map { body ->
            val velocity = body.velocity + impulse
            body.copy(velocity = if (velocity.magnitude() > 5000.0) velocity.normalized() * 5000.0 else velocity)
        }
        sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next))
        following = false; dirty = true; feedback = R.string.shake_applied; resetFrameClock()
        return true
    }
    fun selectBody(id: Long) { selectedBodyId = id; orbitSourceId = null }
    fun clearSelection() { selectedBodyId = null; following = false; orbitSourceId = null }
    fun chooseSpawnKind(kind: BodyKind) {
        if (kind !in listOf(BodyKind.Ambient, BodyKind.Ship, BodyKind.Rocket)) return
        spawnKind = kind; touchPreview = null; clearSelection(); feedback = null
    }
    fun followSelected() { if (selectedBody != null) { following = !following; if (following) setCamera(camera.copy(center = selectedBody!!.position)) } }
    fun prepareOrbit() { selectedBody?.let { spawnKind = BodyKind.Ambient; orbitSourceId = it.id; selectedBodyId = null; following = false; feedback = R.string.place_satellite } }
    fun deleteSelected() { selectedBodyId?.let { id -> editBodies { it.filterNot { body -> body.id == id } }; clearSelection() } }
    fun editSelected(mass: Double, velocity: Vec2) {
        if (!mass.isFinite() || mass !in 1.0..100000.0 || !velocity.x.isFinite() || !velocity.y.isFinite() || velocity.magnitude() > 5000.0) return
        val id = selectedBodyId ?: return
        editBodies { it.map { body -> if (body.id == id) body.copy(mass = mass,
            radius = if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) body.radius else SimulationEngine.radiusForMass(mass), velocity = velocity) else body } }
    }
    private fun editBodies(change: (List<CelestialBody>) -> List<CelestialBody>) {
        sandboxRevision++
        val current = sandbox ?: return; rememberEdit(); val next = change(current.bodies)
        sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next)); dirty = true
    }
    fun renameSandbox(name: String) { val clean = name.trim().take(40); if (clean.isNotEmpty()) { rememberEdit(); sandbox = sandbox?.copy(name = clean); dirty = true } }
    fun toggleSandboxPause() {
        sandboxRevision++
        rememberEdit(); sandbox = sandbox?.let { it.copy(paused = !it.paused) }; resetFrameClock(); dirty = true
        if (tutorialStep == 1 && sandbox?.paused == false) tutorialStep = 2
    }
    fun setTimeScale(value: Double) { if (value in listOf(0.25, 1.0, 3.0, 6.0) && sandbox?.timeScale != value) { sandboxRevision++; rememberEdit(); sandbox = sandbox?.copy(timeScale = value); dirty = true } }
    fun setCollisions(enabled: Boolean) { sandbox?.let { if (it.collisionsEnabled != enabled) { sandboxRevision++; rememberEdit(); sandbox = it.copy(collisionsEnabled = enabled, referenceEnergy = SimulationEngine.totalEnergy(it.bodies)); dirty = true } } }
    fun beginTutorial(nextMode: AppMode = mode, sandboxName: String = SandboxPresetKind.Empty.title) {
        if (nextMode == AppMode.Arcade) startArcade(ArcadeDifficulty.Easy) else { startSandbox(SandboxPresetKind.Empty, sandboxName); sandbox = sandbox?.copy(paused = true) }
        if (nextMode == AppMode.Arcade) arcade = arcade?.let { it.copy(bodies = listOf(it.bodies.first()), practice = true) }
        tutorialStep = 0; movedCameraInTutorial = false
    }
    fun skipTutorial() { if (mode == AppMode.Arcade && arcade?.practice == true) startArcade(ArcadeDifficulty.Easy); tutorialStep = -1 }
    fun snapshot(timestamp: Long): SandboxSnapshot? = sandbox?.let { SandboxSnapshot(it.bodies, it.camera.center, it.camera.zoom,
        it.referenceEnergy, timestamp, it.timeScale, it.paused, it.collisionsEnabled, it.preset, it.name) }
    fun loadSandbox(snapshot: SandboxSnapshot) {
        sandboxRevision++
        SimulationEngine.reserveBodyIds(snapshot.bodies)
        sandbox = SandboxSession(snapshot.bodies, SpaceCamera(snapshot.cameraCenter, snapshot.zoom.coerceIn(SandboxMinZoom, SandboxMaxZoom)),
            snapshot.referenceEnergy, snapshot.preset, snapshot.timeScale, snapshot.paused, snapshot.collisionsEnabled, snapshot.name)
        history.clear(); undoCount = 0; checkpoint = sandbox; dirty = false; clearSelection()
        mode = AppMode.Sandbox; touchPreview = null; menuOpen = false; tutorialStep = -1; feedback = null; resetFrameClock()
        spawnKind = BodyKind.Ambient
    }
}
