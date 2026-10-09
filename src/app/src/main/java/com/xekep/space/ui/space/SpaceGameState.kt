package com.xekep.space.ui.space

import androidx.annotation.StringRes
import com.xekep.space.R
import com.xekep.space.input.ShakeImpulseDetector
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import kotlin.random.Random
import kotlin.math.*
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
    val combat: ArcadeCombat = ArcadeCombat(), val explosions: List<Explosion> = emptyList(),
    val upgrades: Map<ArcadeUpgrade,Int> = emptyMap(),
    val offeredUpgradeWaves: Set<Int> = emptySet(), val chosenUpgradeWaves: Set<Int> = emptySet(),
    val upgradeOffer: ArcadeUpgradeOffer? = null, val challenge: ArcadeChallenge? = null,
    val waveDelay: Double = 0.0,
    val planetId: Long? = null, val convoy: ArcadeConvoy? = null,
) {
    val waveTime: Double get() = (elapsed-waveDelay).coerceAtLeast(0.0)
    val wave: Int get() = 1 + (waveTime / 28.0 + 1e-9).toInt()
    val resting: Boolean get() = waveTime % 28.0 >= 24.0 && !(wave == 10 && challenge?.ids?.isNotEmpty() == true)
    val accuracy: Int get() = if (launches == 0) 0 else successfulLaunches.size * 100 / launches
}
data class SandboxSession(
    val bodies: List<CelestialBody>, val camera: SpaceCamera, val referenceEnergy: Double, val preset: SandboxPresetKind,
    val timeScale: Double = 1.0, val paused: Boolean = false, val collisionsEnabled: Boolean = false, val name: String = preset.title,
    val collisionMode: SandboxCollisionMode = SandboxCollisionMode.Merge,
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
    var arcadeShipClass by mutableStateOf(ShipClass.Interceptor); private set
    val arcadeUpgradePending: Boolean get() = mode == AppMode.Arcade && arcade?.upgradeOffer != null
    var explosions by mutableStateOf<List<Explosion>>(emptyList()); private set
    var simulationLoad by mutableFloatStateOf(0f); private set
    var sandboxOverlayOpen by mutableStateOf(false)
    var pendingExport: SandboxSnapshot? = null
    var motionSteeringEnabled by mutableStateOf(false); private set
    var largeVehicleIcons by mutableStateOf(true)
    var loopFlightRoutes by mutableStateOf(false)
    private var lastArcadeVehicleId by mutableStateOf<Long?>(null)
    private var lastSandboxVehicleId by mutableStateOf<Long?>(null)
    private var steeringInput = Vec2.Zero
    private var pendingBoost = 0.0
    private var pitchInput = 0.0
    private var rollInput = 0.0
    private var fpvControl = false
    private var pilotCameraFollowing = true
    private var pilotZoomReference = 1f
    val pilotVisualZoom: Float get() = camera.zoom/pilotZoomReference
    var cameraRotation by mutableDoubleStateOf(0.0); private set
    var presentationAge by mutableDoubleStateOf(0.0); private set
    var sceneGeneration by mutableIntStateOf(0); private set
    var hiddenSolarOrbits by mutableStateOf<Set<Long>>(emptySet()); private set
    val controlledVehicleId: Long? get() {
        if (!motionSteeringEnabled) return null
        val id = if (mode == AppMode.Sandbox) lastSandboxVehicleId else lastArcadeVehicleId
        return id?.takeIf { value -> bodies.any { it.id == value && it.isVehicle } }
    }
    val spawnLimit: Int get() = spawnLimitFor(spawnKind)
    val spawnCount: Int get() = spawnCountFor(spawnKind)
    fun spawnLimitFor(kind: BodyKind): Int = if (mode == AppMode.Sandbox) MAX_SANDBOX_BODIES else arcade?.launchLimit(kind) ?: when (kind) { BodyKind.Ship -> 3; BodyKind.Rocket -> 10; else -> 30 }
    fun spawnCountFor(kind: BodyKind): Int = if (mode == AppMode.Sandbox) bodies.size else bodies.count {
        it.kind == if (kind == BodyKind.Ambient) BodyKind.Player else kind
    }
    fun chooseArcadeShipClass(value: ShipClass) {
        if (mode != AppMode.Arcade || (value == ShipClass.Guardian && arcade?.guardianUnlocked != true)) return
        chooseSpawnKind(BodyKind.Ship)
        arcadeShipClass=value
    }
    fun chooseArcadeUpgrade(upgrade: ArcadeUpgrade) {
        if (mode != AppMode.Arcade) return
        val current=arcade ?: return
        arcade=selectUpgrade(current,upgrade)
        touchPreview=null; steeringInput=Vec2.Zero; pendingBoost=0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; resetFrameClock()
    }
    fun setMotionControlEnabled(value: Boolean) {
        if (motionSteeringEnabled != value) { if (value) pilotZoomReference=camera.zoom; sandboxRevision++; steeringInput = Vec2.Zero; pendingBoost = 0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; motionSteeringEnabled = value; pilotCameraFollowing = value }
    }
    private fun resetMotionControl() {
        setMotionControlEnabled(false)
        cameraRotation=0.0; pilotCameraFollowing=false; steeringInput=Vec2.Zero; pendingBoost=0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false
    }
    fun clearFlightInput() { steeringInput=Vec2.Zero; pendingBoost=0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false }
    fun setSteeringInput(value: Vec2) {
        val valid = !menuOpen && !sandboxOverlayOpen && !arcadeUpgradePending && controlledVehicleId != null &&
            (mode != AppMode.Sandbox || sandbox?.paused == false) && value.x.isFinite() && value.y.isFinite()
        steeringInput = if (valid) Vec2(value.x.coerceIn(-1.0,1.0),0.0) else Vec2.Zero
        pendingBoost = if (valid) (pendingBoost + value.y.coerceIn(0.0,1.0)).coerceAtMost(1.0) else 0.0
    }
    /** Left FPV stick: yaw rate. Throttle is latched separately, including at zero thrust. */
    fun setJoystickInput(value: Vec2) { fpvControl=true; setSteeringInput(Vec2(value.x,0.0)) }
    /** Right FPV stick: pull back to raise the nose; sideways rotates the hull's bank. */
    fun setAttitudeJoystickInput(value: Vec2) {
        fpvControl=true
        val valid=!menuOpen && !sandboxOverlayOpen && !arcadeUpgradePending && controlledVehicleId != null &&
            (mode != AppMode.Sandbox || sandbox?.paused == false) && value.x.isFinite() && value.y.isFinite()
        pitchInput=if (valid) value.y.coerceIn(-1.0,1.0) else 0.0
        rollInput=if (valid) value.x.coerceIn(-1.0,1.0) else 0.0
    }
    private fun flightControl(): ManualFlightControl? {
        val control = controlledVehicleId?.let { ManualFlightControl(it, steeringInput.x, pendingBoost, pitchInput, rollInput, fpvControl) }
        pendingBoost = 0.0
        return control
    }
    private fun resetPresentation() { simulationLoad=0f; sceneGeneration++; presentationAge = 0.0; hiddenSolarOrbits = emptySet(); steeringInput = Vec2.Zero; pendingBoost = 0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false }
    private fun updateCameraRotation(dt: Double) {
        if (!dt.isFinite() || dt <= 0) return
        if (touchPreview != null && !menuOpen) return
        val pilot = if (!menuOpen && !sandboxOverlayOpen && pilotCameraFollowing &&
            (mode != AppMode.Arcade || (arcade?.lives ?: 0) > 0)) bodies.firstOrNull { it.id == controlledVehicleId } else null
        val target = pilot?.let { -PI/2-atan2(it.heading.y,it.heading.x)-it.roll*.22 } ?: 0.0
        val difference = atan2(sin(target-cameraRotation),cos(target-cameraRotation))
        cameraRotation += difference * (1-exp(-dt.coerceAtMost(.1)/.18))
        cameraRotation = atan2(sin(cameraRotation),cos(cameraRotation))
        if (pilot == null && abs(cameraRotation) < .001) cameraRotation = 0.0
    }
    private fun updatePresentation(dt: Double) {
        if (mode != AppMode.Sandbox) return
        presentationAge = (presentationAge + dt).coerceAtMost(60.0)
        val hidden = bodies.mapNotNull { body ->
            val entry = body.solar?.takeUnless { it == SolarBody.Sun } ?: return@mapNotNull null
            val parent = bodies.firstOrNull { it.solar?.name == entry.parent }
            if (parent == null || !retainsSolarOrbit(body, parent)) body.id else null
        }
        if (hidden.any { it !in hiddenSolarOrbits }) hiddenSolarOrbits = hiddenSolarOrbits + hidden
    }
    private var accumulator = 0.0
    private var sandboxRevision = 0L
    val sandboxEditRevision: Long get() = sandboxRevision
    private val history = ArrayDeque<SandboxSession>()
    private var movedCameraInTutorial = false
    val hasSession: Boolean get() = if (mode == AppMode.Arcade) arcade != null else sandbox != null
    val bodies: List<CelestialBody> get() = if (mode == AppMode.Arcade) arcade?.bodies.orEmpty() else sandbox?.bodies.orEmpty()
    val cameraTarget: CelestialBody? get() = orbitSource ?: if (following) selectedBody else if (pilotCameraFollowing && touchPreview == null)
        bodies.firstOrNull { it.id == controlledVehicleId } else null
    val camera: SpaceCamera get() = if (mode == AppMode.Arcade) arcade?.camera ?: SpaceCamera() else sandbox?.camera ?: SpaceCamera()
    val selectedBody: CelestialBody? get() {
        val id = selectedBodyId ?: return null
        return bodies.firstOrNull { it.id == id }
    }
    val orbitSource: CelestialBody? get() {
        val id = orbitSourceId ?: return null
        val source = if (mode == AppMode.Sandbox) sandbox?.bodies?.firstOrNull { it.id == id } else null
        // Resolve a coarse galaxy particle as an individual body when attaching a moon.
        // Preview uses the same gravity that launch commits, without changing the scene yet.
        return source?.let { if (it.galaxyParticle) it.copy(galaxyParticle=false) else it }
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
    fun openMenu() { steeringInput = Vec2.Zero; pendingBoost = 0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; touchPreview = null; menuOpen = true; resetFrameClock(); persistRecord() }
    fun closeMenu() { if (hasSession) { menuOpen = false; resetFrameClock() } }
    fun enterMode(nextMode: AppMode) {
        persistRecord()
        if (mode != nextMode) pilotCameraFollowing = false
        if (nextMode == AppMode.Arcade && arcade == null) startArcade()
        if (nextMode == AppMode.Sandbox && sandbox == null) startSandbox()
        mode = nextMode; touchPreview = null; tutorialStep = -1; menuOpen = !hasSession
        if (spawnKind !in spawnKinds()) spawnKind = BodyKind.Ambient
        bestScore = arcade?.let { recordFor(it.difficulty) } ?: bestScore; resetFrameClock()
    }
    fun startArcade(difficulty: ArcadeDifficulty = ArcadeDifficulty.Normal) {
        if (viewport == IntSize.Zero) return
        persistRecord()
        resetMotionControl()
        arcadeShipClass=ShipClass.Interceptor
        lastArcadeVehicleId = null; steeringInput = Vec2.Zero; pendingBoost = 0.0
        val scene = SimulationEngine.arcadeBodies(Vec2(900.0, 1400.0))
        arcade = ArcadeSession(scene, SpaceCamera(scene.first().position, arcadeFitZoom()), IntSize(900, 1400), difficulty)
        if (spawnKind !in listOf(BodyKind.Ambient, BodyKind.Ship, BodyKind.Rocket)) spawnKind = BodyKind.Ambient
        bestScore = recordFor(difficulty); mode = AppMode.Arcade; touchPreview = null
        menuOpen = false; feedback = null; tutorialStep = -1; resetFrameClock()
    }
    fun startSandbox(preset: SandboxPresetKind = SandboxPresetKind.SolarSystem, name: String = preset.title) {
        sandboxRevision++; resetMotionControl()
        lastSandboxVehicleId = null; resetPresentation()
        val scene = SimulationEngine.sandboxPreset(preset)
        sandbox = SandboxSession(scene.bodies, SpaceCamera(scene.cameraCenter, scene.zoom), scene.referenceEnergy, preset, name = name)
        history.clear(); undoCount = 0; checkpoint = sandbox; dirty = false; clearSelection()
        mode = AppMode.Sandbox; touchPreview = null; menuOpen = false; feedback = null; tutorialStep = -1; resetFrameClock()
        spawnKind = BodyKind.Ambient
        if (preset in listOf(SandboxPresetKind.RandomSystems,SandboxPresetKind.SystemGalaxy) && viewport != IntSize.Zero) fitCamera()
        explosions = emptyList()
        if (preset == SandboxPresetKind.SolarSystem && viewport != IntSize.Zero) { focusSolar(SolarBody.Sun); clearSelection() }
        checkpoint = sandbox
    }
    private fun pauseAfterStall() {
        if (mode == AppMode.Sandbox) {
            sandboxRevision++; sandbox=sandbox?.copy(paused=true)
            steeringInput=Vec2.Zero; pendingBoost=0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; touchPreview=null; resetFrameClock()
        } else openMenu()
        feedback=R.string.lag_paused
    }
    fun setPilotTargetSpeed(value: Double) {
        if (!value.isFinite()) return
        val id=controlledVehicleId ?: return
        val limit=bodies.firstOrNull { it.id == id }?.pilotSpeedLimit() ?: return
        val target=value.coerceIn(0.0,limit)
        if (mode == AppMode.Sandbox) {
            sandboxRevision++
            sandbox=sandbox?.let { scene -> scene.copy(bodies=scene.bodies.map { if (it.id == id) it.copy(pilotTargetSpeed=target,pilotThrottle=target/limit) else it }) }
            dirty=true
        } else arcade=arcade?.let { scene -> scene.copy(bodies=scene.bodies.map { if (it.id == id) it.copy(pilotTargetSpeed=target,pilotThrottle=target/limit) else it }) }
    }
    fun update(dt: Double) {
        updateCameraRotation(dt)
        if (menuOpen || !hasSession || !dt.isFinite() || dt <= 0.0) return
        updatePresentation(dt)
        if (mode == AppMode.Sandbox && (sandbox?.paused == true || sandboxOverlayOpen || orbitSourceId != null)) { resetFrameClock(); return }
        if (mode == AppMode.Arcade && ((arcade?.lives ?: 0) <= 0 || arcadeUpgradePending)) { resetFrameClock(); return }
        accumulator += dt
        if (accumulator > 2.0) { pauseAfterStall(); return }
        var steps = 0
        val seconds = 1.0 / 60.0
        while (accumulator + 1e-9 >= seconds && steps < 15) {
            if (mode == AppMode.Sandbox) {
                val current = sandbox ?: break
                val control = flightControl()
                val steered = applyFlightControls(current.bodies, control, seconds * current.timeScale)
                val next = SimulationEngine.stepSandbox(steered, seconds * current.timeScale, current.referenceEnergy, current.collisionsEnabled, control?.bodyId,current.collisionMode)
                explosions = advanceExplosions(explosions, next.collisions, seconds)
                if (next.collisions.any { it.vehicleExplosion }) sandbox = current.copy(referenceEnergy = SimulationEngine.totalEnergy(next.bodies))
                publishSandboxBodies(advanceWaypoints(steered,next.bodies))
            } else {
                val current = arcade ?: break
                if (current.lives <= 0) { accumulator = 0.0; break }
                val stepped = advanceArcade(current, seconds, random, flightControl(),ThreatView(viewport,cameraRotation))
                val next = stepped.copy(camera = trackedCamera(stepped.bodies,stepped.camera)); arcade = next
                val best = maxOf(recordFor(next.difficulty), next.score)
                if (!next.practice) { records = records + (next.difficulty to best); bestScore = best }
                if (next.practice && next.hitFlash > current.hitFlash) feedback = R.string.practice_hit
                if (tutorialStep == 1 && next.destroyed > current.destroyed) tutorialStep = 2
                if (next.lives <= 0) { touchPreview = null; persistRecord() }
                if (next.upgradeOffer != null) { touchPreview=null; steeringInput=Vec2.Zero; pendingBoost=0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; resetFrameClock(); break }
            }
            accumulator -= seconds; steps++
        }
        behind = accumulator >= seconds
        if (selectedBodyId != null && bodies.none { it.id == selectedBodyId }) { selectedBodyId = null; following = false }
    }

    /** Called from the main thread. Only immutable body snapshots leave it; UI edits never wait
     * for the solver, and an obsolete result cannot overwrite a newer scene. */
    fun updateSandboxPresentation(dt: Double) {
        updateCameraRotation(dt)
        if (!menuOpen && hasSession && dt.isFinite() && dt > 0) updatePresentation(dt)
    }
    suspend fun updateSandboxAsync(dt: Double, budgeted: Boolean = false) {
        if (!budgeted) updateCameraRotation(dt)
        if (mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen || !dt.isFinite() || dt <= 0.0) return
        if (!budgeted) updatePresentation(dt)
        val current = sandbox ?: return
        if (current.paused || orbitSourceId != null) { resetFrameClock(); return }
        accumulator += dt
        if (!budgeted && accumulator > 2.0) { pauseAfterStall(); return }
        val large=budgeted && current.bodies.size >= BARNES_HUT_THRESHOLD
        val seconds = if (large) 1.0/30.0 else 1.0/60.0
        // Do not build an ever growing catch-up batch when a dense scene is slower than real time.
        // Keep fixed physics steps; shed old wall-clock debt instead of skipping through contacts.
        if (budgeted) accumulator=accumulator.coerceAtMost(4*seconds)
        val steps = ((accumulator + 1e-9) / seconds).toInt().coerceAtMost(if (large) 1 else if (budgeted) 2 else 15)
        if (steps == 0) return
        val revision = sandboxRevision
        val initialEffects = explosions
        val control = flightControl()
        var solverSeconds: Double
        val next = withContext(Dispatchers.Default) {
            val solverStarted=System.nanoTime()
            var bodies = current.bodies
            var effects = initialEffects
            var energy = current.referenceEnergy
            repeat(steps) { step ->
                currentCoroutineContext().ensureActive()
                val steered=applyFlightControls(bodies,if (step == 0) control else control?.copy(boost=0.0),seconds*current.timeScale)
                val result = SimulationEngine.stepSandbox(steered, seconds * current.timeScale, energy, current.collisionsEnabled, control?.bodyId,current.collisionMode)
                bodies = advanceWaypoints(steered,result.bodies)
                if (result.collisions.any { it.vehicleExplosion }) energy = SimulationEngine.totalEnergy(bodies)
                effects = advanceExplosions(effects, result.collisions, seconds)
            }
            solverSeconds=(System.nanoTime()-solverStarted)/1e9
            Triple(bodies, effects, energy)
        }
        if (revision != sandboxRevision || mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen || orbitSourceId != null ||
            sandbox?.paused != false || sandbox?.bodies !== current.bodies) { resetFrameClock(); return }
        val load=(solverSeconds/(steps*seconds)).toFloat().coerceIn(0f,8f)
        simulationLoad+=(load-simulationLoad)*.15f
        sandbox = sandbox?.copy(referenceEnergy = next.third)
        publishSandboxBodies(next.first)
        explosions = next.second
        accumulator = (accumulator - steps * seconds).coerceAtLeast(0.0)
        behind = accumulator >= seconds
    }

    private fun trackedCamera(next: List<CelestialBody>, current: SpaceCamera): SpaceCamera {
        val id = if (following) selectedBodyId ?: orbitSourceId
            else if (pilotCameraFollowing && touchPreview == null) controlledVehicleId else null
        if (id == null) return current
        val target=next.firstOrNull { it.id == id } ?: return current
        if (following || id != controlledVehicleId) return current.copy(center=target.position)
        val shortSide=minOf(viewport.width,viewport.height).coerceAtLeast(1)
        // Visible attitude response in both tiny catalogue units and ordinary arcade space.
        val screenOffset=Vec2(((if (target.enginePowered) steeringInput.x else 0.0)*.08+sin(target.roll)*.06)*shortSide,-sin(target.pitch)*shortSide*.07)
        val visualOffset=rotateVector(screenOffset/current.zoom.toDouble(),-cameraRotation)
        return current.copy(center=pilotCameraCenter(current.center,target.position,current.zoom,viewport,visualOffset))
    }
    private fun publishSandboxBodies(next: List<CelestialBody>) {
        val current = sandbox ?: return
        if (next != current.bodies) dirty = true
        val selected = next.firstOrNull { it.id == selectedBodyId }
        sandbox = current.copy(bodies = next, camera = if (following && selected != null)
            current.camera.copy(center = selected.position) else trackedCamera(next,current.camera))
        if (selectedBodyId != null && selected == null) { selectedBodyId = null; following = false }
    }
    fun worldAt(position: Offset): Vec2 = screenToWorld(position, viewport, camera.center, camera.zoom, cameraRotation)
    fun previewAt(start: Offset, end: Offset, startedAt: Long) = TouchPreview(worldAt(start), worldAt(end), startedAt, (end - start) / density)
    internal fun bodyAt(point: Vec2): CelestialBody? = visibleSolarBodies(bodies,camera.zoom,density)
        .filter { body ->
            val radius=if (body.isVehicle) maxOf(20.0*density,(if (body.id == controlledVehicleId) pilotScreenRadius(body,pilotVisualZoom,density,largeVehicleIcons) else bodyScreenRadius(body,camera.zoom,density,largeVehicleIcons))*1.35)/camera.zoom
                else maxOf(body.radius.toDouble(),20.0*density/camera.zoom)
            (body.position-point).magnitude() <= radius
        }.minByOrNull { (it.position-point).magnitude() }
    fun launchVelocity(preview: TouchPreview): Vec2 = preview.dragDp?.let { drag ->
        rotateVector(SimulationEngine.velocityFromGesture(drag), -cameraRotation)
    }
        ?: SimulationEngine.launchVelocityFromDrag(Offset.Zero, (preview.currentWorld - preview.startWorld).toOffset())
    fun previewBody(preview: TouchPreview, holdSeconds: Double): CelestialBody? {
        val kind = if (mode == AppMode.Arcade && spawnKind == BodyKind.Ambient) BodyKind.Player else spawnKind
        val hold = holdSeconds.coerceIn(0.0, 4.0)
        val physicalScale = mode == AppMode.Sandbox && bodies.any { it.physicalScale }
        val requested = when (kind) {
            BodyKind.Ship -> 24.0 + 18.0 * hold
            BodyKind.Rocket -> 12.0 + 6.0 * hold
            BodyKind.Star -> 6000.0 + 4000.0 * hold
            BodyKind.BlackHole -> if (physicalScale) SolarBody.Sun.worldMass * (3.0 + 2.0 * hold)
                else 12000.0 + 8000.0 * hold
            else -> SimulationEngine.massFromHold(hold)
        }
        val baseCost = when (kind) { BodyKind.Ship -> if (mode == AppMode.Arcade && arcadeShipClass == ShipClass.Guardian) 48.0 else 38.0; BodyKind.Rocket -> 22.0; else -> 10.0 }
        val massCost = if (kind == BodyKind.Ship || kind == BodyKind.Rocket) .1 else .028
        val affordable = if (mode != AppMode.Arcade) requested
            else if (kind == BodyKind.Ship || kind == BodyKind.Rocket)
                affordableVehicleMass(kind,requested,arcade?.energy ?: 0.0,arcadeShipClass) ?: return null
            else ((arcade?.energy ?: 0.0) - baseCost) / massCost
        val minimumMass = when (kind) { BodyKind.Ship -> 24.0; BodyKind.Rocket -> 12.0; else -> 70.0 }
        if (mode == AppMode.Arcade && affordable + 1e-8 < minimumMass) return null
        // Keep an assisted satellite small enough that its parent remains dominant.
        val mass = orbitSource?.let { minOf(requested, it.mass * 0.02) }
            ?: minOf(requested, affordable)
        val velocity = orbitSource?.let { SimulationEngine.orbitVelocity(it, preview.startWorld, mass, physicalScale) } ?: launchVelocity(preview)
        val color = when (kind) { BodyKind.Ship -> if (mode == AppMode.Arcade && arcadeShipClass == ShipClass.Guardian) Color(0xFF81E5C4) else Color(0xFF8BD3FF); BodyKind.Rocket -> Color(0xFFFFB36B)
            BodyKind.Star -> Color(0xFFFFD166); BodyKind.BlackHole -> Color(0xFFCB9BFF); else -> Color.Cyan }
        val radius = orbitSource?.let { parent ->
            // Preserve the parent density instead of using the playground minimum radius.
            (parent.radius * cbrt(mass / parent.mass)).toFloat()
        } ?: if (physicalScale) when (kind) {
            BodyKind.Ship -> .00001f
            BodyKind.Rocket -> .000002f
            BodyKind.Star -> SolarBody.Sun.worldRadius * cbrt(mass/SolarBody.Sun.worldMass).toFloat()
            BodyKind.BlackHole -> (.03 * cbrt(mass/12000)).toFloat()
            else -> SolarBody.Earth.worldRadius * kotlin.math.cbrt(mass / SolarBody.Earth.worldMass).toFloat()
        } else when (kind) { BodyKind.Ship -> 8f; BodyKind.Rocket -> 6f
            BodyKind.BlackHole -> (10*cbrt(mass/12000)).toFloat(); else -> SimulationEngine.radiusForMass(mass) }
        val route = if (kind == BodyKind.Ship || kind == BodyKind.Rocket)
            FlightPath.through(preview.startWorld,preview.waypoints,loopFlightRoutes,18.0*density/camera.zoom) else null
        val candidate = CelestialBody(-1, preview.startWorld, velocity, mass,
            (radius * vehicleSizeScale(kind, mass)).toFloat(), color, kind, physicalScale = physicalScale,
            burnRemaining = if (kind == BodyKind.Rocket) 3.0 else 0.0,
            heading = if (velocity.magnitude() > 1e-6) velocity.normalized() else rotateVector(Vec2(0.0, -1.0),-cameraRotation),
            waypoints = route?.remainingPoints(0.0).orEmpty(), routePath = route,
            routeSpeed = if (preview.waypoints.isEmpty()) 0.0 else routeCruiseSpeed(velocity.magnitude()),
            routeTolerance = if (preview.waypoints.isEmpty()) 0.0 else 3.0*density/camera.zoom,
            shipClass=if (mode == AppMode.Arcade && kind == BodyKind.Ship) arcadeShipClass else ShipClass.Interceptor,
            fuelConsumptionScale=if (mode == AppMode.Arcade) arcade?.fuelScale ?: 1.0 else 1.0)
        return orbitSource?.let { assistedSatellite(it, candidate, bodies) } ?: candidate
    }
    fun transformCamera(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (menuOpen || !hasSession || viewport == IntSize.Zero) return
        val previous = camera
        val zoom = (previous.zoom * zoomChange).coerceIn(minimumZoom(mode), maximumZoom(mode))
        val followed = orbitSource ?: selectedBody?.takeIf { following }
        if (followed != null) {
            setCamera(previous.copy(center = followed.position, zoom = zoom))
            return
        }
        val pilot = controlledVehicleId?.let { id -> bodies.firstOrNull { it.id == id } }
        if (pilot != null) {
            following = false; pilotCameraFollowing = true
            setCamera(SpaceCamera(pilot.position, zoom))
            return
        }
        val anchor = screenToWorld(centroid, viewport, previous.center, previous.zoom, cameraRotation)
        val after = screenToWorld(centroid + pan, viewport, previous.center, zoom, cameraRotation)
        following = false; pilotCameraFollowing = false; setCamera(SpaceCamera(previous.center + anchor - after, zoom))
        if (tutorialStep == 2 && mode == AppMode.Sandbox) movedCameraInTutorial = true
    }
    private fun setCamera(camera: SpaceCamera) {
        if (mode == AppMode.Arcade) arcade = arcade?.copy(camera = camera) else sandbox = sandbox?.copy(camera = camera)
    }
    private fun arcadeFitZoom() = minOf(viewport.width * 0.88f / 900f, viewport.height * 0.60f / 1400f).coerceAtLeast(ArcadeMinZoom)
    fun fitCamera() {
        touchPreview = null; following = false; pilotCameraFollowing = false
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
        if (holdSeconds < 0.3 && (preview.dragDp?.getDistance() ?: 0f) < 12f && orbitSourceId == null && preview.waypoints.isEmpty()) {
            // Resolve the object touched on pointer down, even if it moved away before release.
            if (preview.tapBodyId != null) {
                if (bodies.any { it.id == preview.tapBodyId }) { selectedBodyId=preview.tapBodyId; feedback=null }
                return
            }
            bodyAt(preview.startWorld)?.let { selectedBodyId=it.id; feedback=null; return }
        }
        launch(preview, holdSeconds)
    }
    fun launch(preview: TouchPreview, holdSeconds: Double) {
        if (menuOpen || !hasSession || sandboxOverlayOpen || arcadeUpgradePending || (mode == AppMode.Arcade && (arcade?.lives ?: 0) <= 0)) return
        if (orbitSourceId != null && orbitSource == null) {
            orbitSourceId=null; touchPreview=null; feedback=R.string.satellite_parent_lost; return
        }
        if (spawnCount >= spawnLimit) { feedback = null; return }
        val candidate = previewBody(preview, holdSeconds)
        if (candidate == null || (mode == AppMode.Arcade && launchCost(candidate) > (arcade?.energy ?: 0.0)+1e-8)) {
            feedback = R.string.not_enough_energy; return
        }
        val followedId = if (following) selectedBodyId ?: orbitSourceId else null
        orbitSource?.let { parent ->
            when (satellitePlacement(parent,candidate,bodies)) {
                SatellitePlacement.Overlap -> { feedback = R.string.satellite_farther; return }
                SatellitePlacement.StrongTides -> { feedback = R.string.satellite_unstable; return }
                SatellitePlacement.Clear -> Unit
            }
        }
        val generated = SimulationEngine.createBody(candidate.position, candidate.position, 0.0, candidate.kind)
        val body = candidate.copy(id = generated.id, color = if (candidate.kind == BodyKind.Ambient || candidate.kind == BodyKind.Player) generated.color else candidate.color)
        if (body.isVehicle) {
            if (motionSteeringEnabled) pilotZoomReference=camera.zoom
            if (mode == AppMode.Arcade) lastArcadeVehicleId = body.id else lastSandboxVehicleId = body.id
            steeringInput = Vec2.Zero; pendingBoost = 0.0; pitchInput=0.0; rollInput=0.0; fpvControl=false; pilotCameraFollowing = true; following = false
        }
        if (mode == AppMode.Arcade) {
            val current = arcade ?: return
            selectedBodyId = followedId.takeIf { following }
            arcade = current.copy(bodies = current.bodies + body, energy = (current.energy - launchCost(body)).coerceAtLeast(0.0), launches = current.launches + 1)
            if (tutorialStep == 0) tutorialStep = 1 else if (tutorialStep == 2) {
                startArcade(ArcadeDifficulty.Easy)
                feedback = R.string.practice_complete
                return
            }
        } else {
            sandboxRevision++
            rememberEdit(); val current = sandbox ?: return
            val next = current.bodies.map { if (it.id == orbitSourceId && it.galaxyParticle) it.copy(galaxyParticle=false) else it } + body
            sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next))
            selectedBodyId = followedId.takeIf { following }; orbitSourceId = null; dirty = true
            if (tutorialStep == 0) tutorialStep = 1
        }
        if (body.isVehicle && motionSteeringEnabled) setCamera(camera.copy(center=body.position))
        feedback = null
    }
    private fun rememberEdit() { sandbox?.let { if (history.size >= 20) history.removeFirst(); history.addLast(it); undoCount = history.size } }
    fun undo() {
        if (history.isEmpty()) return
        sandboxRevision++
        explosions = emptyList(); sandbox = history.removeLast(); undoCount = history.size; clearSelection(); dirty = true; resetFrameClock()
    }
    fun saveCheckpoint() { checkpoint = sandbox; feedback = R.string.checkpoint_stored }
    fun restoreCheckpoint() { checkpoint?.let { sandboxRevision++; rememberEdit(); explosions = emptyList(); sandbox = it; dirty = true; clearSelection(); resetFrameClock() } }
    fun markSaved() { dirty = false }
    fun inform(@StringRes message: Int) { feedback = message }
    fun dismissFeedback() { feedback = null }
    fun shakeSandbox(impulse: Vec2, shakeMode: ShakeMode = ShakeMode.Inertial, intensity: Double = 1.0): Boolean {
        val current = sandbox ?: return false
        if (shakeMode == ShakeMode.Off || !intensity.isFinite() || intensity !in .25..2.5) return false
        if (mode != AppMode.Sandbox || menuOpen || sandboxOverlayOpen || orbitSourceId != null || controlledVehicleId != null || current.paused || current.bodies.isEmpty() ||
            !impulse.x.isFinite() || !impulse.y.isFinite() || impulse.magnitude() !in 1.0..(ShakeImpulseDetector.MAX_IMPULSE + 1e-6)) return false
        sandboxRevision++; rememberEdit()
        val next = current.bodies.map { body ->
            val screenImpulse=rotateVector(impulse,-cameraRotation)
            val delta=if (shakeMode == ShakeMode.Classic) screenImpulse else shakeVelocityDelta(body.id, screenImpulse, current.camera.zoom, density)
            val velocity = body.velocity + delta*intensity
            body.copy(velocity = if (velocity.magnitude() > 5000.0) velocity.normalized() * 5000.0 else velocity)
        }
        sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next))
        following = false; dirty = true; feedback = null; resetFrameClock()
        return true
    }
    fun selectBody(id: Long) { selectedBodyId = id; orbitSourceId = null }
    fun focusConvoy() {
        val body=arcade?.convoy?.takeIf { it.status == ConvoyStatus.Approaching }?.let { convoy -> bodies.firstOrNull { it.id == convoy.bodyId } } ?: return
        selectedBodyId=body.id; following=true; pilotCameraFollowing=false
        setCamera(SpaceCamera(body.position,arcadeFitZoom()))
    }
    fun clearSelection() {
        if (orbitSourceId != null) { feedback = null; resetFrameClock() }
        selectedBodyId = null; following = false; orbitSourceId = null
    }
    fun cycleSpawnKind() {
        val kinds = spawnKinds()
        chooseSpawnKind(kinds[(kinds.indexOf(spawnKind) + 1) % kinds.size])
    }
    fun chooseSpawnKind(kind: BodyKind) {
        if (kind == BodyKind.Ship) arcadeShipClass=ShipClass.Interceptor
        if (kind !in spawnKinds()) return
        spawnKind = kind; touchPreview = null; clearSelection(); feedback = null
    }
    private fun spawnKinds() = if (mode == AppMode.Sandbox)
        listOf(BodyKind.Ambient,BodyKind.Ship,BodyKind.Rocket,BodyKind.Star,BodyKind.BlackHole)
        else listOf(BodyKind.Ambient,BodyKind.Ship,BodyKind.Rocket)
    fun focusSolar(entry: SolarBody) {
        val body = bodies.firstOrNull { it.solar == entry } ?: return
        val details = bodies.filter { it.orbitalDetail != null && it.orbitParentId == body.id }
        val extent = if (entry == SolarBody.Sun) 3400.0 * body.solarOrbitScale else if (details.isNotEmpty()) {
            details.maxOf { (it.position - body.position).magnitude() } * 2.8
        } else {
            val moons = bodies.filter { it.solar?.parent == entry.name }
            if (moons.isEmpty()) 60.0 else moons.maxOf { (it.position - body.position).magnitude() } * 2.8
        }
        selectedBodyId = body.id; orbitSourceId = null; following = true
        setCamera(SpaceCamera(body.position, (viewport.width * .70 / extent).toFloat().coerceIn(SandboxMinZoom, SandboxMaxZoom)))
    }
    fun followSelected() { if (selectedBody != null) { pilotCameraFollowing = false; following = !following; if (following) setCamera(camera.copy(center = selectedBody!!.position)) } }
    fun focusSelected() {
        val body=selectedBody ?: return
        if (viewport == IntSize.Zero) return
        pilotCameraFollowing = false
        val wasFollowing=following
        if (body.solar != null) focusSolar(body.solar)
        else setCamera(SpaceCamera(body.position,(minOf(viewport.width,viewport.height)*.65/body.radius.coerceAtLeast(1f)/12).toFloat().coerceIn(SandboxMinZoom,SandboxMaxZoom)))
        following=wasFollowing
    }
    fun prepareOrbit() {
        val parent=selectedBody?.takeUnless { it.isVehicle } ?: return
        sandboxRevision++; resetFrameClock()
        spawnKind = BodyKind.Ambient; orbitSourceId = parent.id; selectedBodyId = null
        pilotCameraFollowing = false; feedback = R.string.place_satellite
        if (viewport != IntSize.Zero) {
            val radius=maxOf(parent.radius*4.0,if (parent.physicalScale) .4 else 1.0)
            val zoom=(minOf(viewport.width,viewport.height)*.20/radius).toFloat()
            setCamera(SpaceCamera(parent.position,maxOf(camera.zoom,zoom).coerceIn(SandboxMinZoom,SandboxMaxZoom)))
        }
    }
    fun deleteSelected() { selectedBodyId?.let { id -> editBodies { it.filterNot { body -> body.id == id } }; clearSelection() } }
    fun editSelected(mass: Double, velocity: Vec2) {
        if (!mass.isFinite() || mass !in 1e-8..100000000.0 || !velocity.x.isFinite() || !velocity.y.isFinite() || velocity.magnitude() > 5000.0) return
        val id = selectedBodyId ?: return
        editBodies { it.map { body -> if (body.id == id) body.copy(mass = mass,
            radius = if (body.isVehicle) (body.radius * vehicleSizeScale(body.kind, mass) / vehicleSizeScale(body.kind, body.mass)).toFloat()
                else if (body.physicalScale || body.kind == BodyKind.BlackHole) body.radius * cbrt(mass / body.mass).toFloat() else SimulationEngine.radiusForMass(mass), velocity = velocity,
            orbitalDetail=body.orbitalDetail.takeIf { mass <= 1e-15 }) else body } }
    }
    private fun editBodies(change: (List<CelestialBody>) -> List<CelestialBody>) {
        sandboxRevision++
        val current = sandbox ?: return; rememberEdit(); val next = change(current.bodies)
        sandbox = current.copy(bodies = next, referenceEnergy = SimulationEngine.totalEnergy(next)); dirty = true
    }
    fun renameSandbox(name: String) { val clean = name.trim().take(40); if (clean.isNotEmpty()) { rememberEdit(); sandbox = sandbox?.copy(name = clean); dirty = true } }
    fun toggleSandboxPause() {
        sandboxRevision++; steeringInput = Vec2.Zero; pendingBoost = 0.0
        rememberEdit(); sandbox = sandbox?.let { it.copy(paused = !it.paused) }; resetFrameClock(); dirty = true
        if (tutorialStep == 1 && sandbox?.paused == false) tutorialStep = 2
    }
    fun setTimeScale(value: Double) { if (value in sandboxTimeScalesFor(sandbox?.preset ?: SandboxPresetKind.Empty) && sandbox?.timeScale != value) { sandboxRevision++; rememberEdit(); sandbox = sandbox?.copy(timeScale = value); dirty = true } }
    fun setCollisions(enabled: Boolean) { sandbox?.let { if (it.collisionsEnabled != enabled) { sandboxRevision++; rememberEdit(); sandbox = it.copy(collisionsEnabled = enabled, referenceEnergy = SimulationEngine.totalEnergy(it.bodies)); dirty = true } } }
    fun setCollisionMode(value: SandboxCollisionMode) {
        sandbox?.let { if (it.collisionMode != value) { sandboxRevision++; rememberEdit(); sandbox=it.copy(collisionMode=value); dirty=true } }
    }
    fun generateRandomSystems(name: String, resolved: Boolean = false) {
        if (mode != AppMode.Sandbox || sandbox == null) return
        sandboxRevision++; resetMotionControl(); rememberEdit(); clearSelection(); resetFrameClock(); resetPresentation()
        val scene=if (resolved) SystemGalaxy.create(random,SimulationEngine::newBodyId) else RandomSystems.create(random,SimulationEngine::newBodyId)
        lastSandboxVehicleId=null; explosions=emptyList(); touchPreview=null
        sandbox=sandbox?.copy(bodies=scene,referenceEnergy=SimulationEngine.totalEnergy(scene),name=name,preset=if (resolved) SandboxPresetKind.SystemGalaxy else SandboxPresetKind.RandomSystems,
            timeScale=1.0)
        dirty=true; feedback=null; fitCamera()
    }
    fun beginTutorial(nextMode: AppMode = mode, sandboxName: String = SandboxPresetKind.Empty.title) {
        if (nextMode == AppMode.Arcade) startArcade(ArcadeDifficulty.Easy) else { startSandbox(SandboxPresetKind.Empty, sandboxName); sandbox = sandbox?.copy(paused = true) }
        if (nextMode == AppMode.Arcade) arcade = arcade?.let { it.copy(bodies = listOf(it.bodies.first()), practice = true) }
        tutorialStep = 0; movedCameraInTutorial = false
    }
    fun skipTutorial() { if (mode == AppMode.Arcade && arcade?.practice == true) startArcade(ArcadeDifficulty.Easy); tutorialStep = -1 }
    fun snapshot(timestamp: Long): SandboxSnapshot? = sandbox?.let { SandboxSnapshot(it.bodies, it.camera.center, it.camera.zoom,
        it.referenceEnergy, timestamp, it.timeScale, it.paused, it.collisionsEnabled, it.preset, it.name,it.collisionMode) }
    fun loadSandbox(snapshot: SandboxSnapshot) {
        sandboxRevision++; resetMotionControl()
        lastSandboxVehicleId = null; resetPresentation()
        SimulationEngine.reserveBodyIds(snapshot.bodies)
        sandbox = SandboxSession(snapshot.bodies, SpaceCamera(snapshot.cameraCenter, snapshot.zoom.coerceIn(SandboxMinZoom, SandboxMaxZoom)),
            snapshot.referenceEnergy, snapshot.preset, snapshot.timeScale.coerceAtLeast(if (snapshot.preset == SandboxPresetKind.SolarSystem) .0001 else .25), snapshot.paused, snapshot.collisionsEnabled, snapshot.name,snapshot.collisionMode)
        history.clear(); undoCount = 0; checkpoint = sandbox; dirty = false; clearSelection()
        mode = AppMode.Sandbox; touchPreview = null; menuOpen = false; tutorialStep = -1; feedback = null; resetFrameClock()
        spawnKind = BodyKind.Ambient; explosions = emptyList()
    }
}
