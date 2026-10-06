package com.xekep.space.ui.space

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.SandboxPreset
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.toOffset
import com.xekep.space.storage.SandboxSnapshot
import com.xekep.space.storage.SandboxStorage
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun SpaceSceneRoot() {
    val context = LocalContext.current
    val sandboxStorage = remember(context) { SandboxStorage(context) }

    var mode by remember { mutableStateOf(AppMode.Arcade) }
    var menuOpen by remember { mutableStateOf(false) }
    var bodies by remember { mutableStateOf<List<CelestialBody>>(emptyList()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var cameraCenter by remember { mutableStateOf(Vec2.Zero) }
    var cameraZoom by remember { mutableFloatStateOf(1f) }
    var touchPreview by remember { mutableStateOf<TouchPreview?>(null) }
    var frameNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var saveSummaries by remember { mutableStateOf(sandboxStorage.summaries()) }
    var sandboxReferenceEnergy by remember { mutableDoubleStateOf(0.0) }

    var energy by remember { mutableDoubleStateOf(MaxEnergy) }
    var score by remember { mutableDoubleStateOf(0.0) }
    var bestScore by remember { mutableDoubleStateOf(0.0) }
    var coreLives by remember { mutableIntStateOf(StartingCoreLives) }
    var meteorsDestroyed by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var spawnTimer by remember { mutableDoubleStateOf(1.35) }
    var hitFlash by remember { mutableDoubleStateOf(0.0) }
    var combo by remember { mutableDoubleStateOf(1.0) }

    val inputBodies by rememberUpdatedState(bodies)
    val inputEnergy by rememberUpdatedState(energy)
    val inputMode by rememberUpdatedState(mode)
    val inputMenuOpen by rememberUpdatedState(menuOpen)
    val inputCameraCenter by rememberUpdatedState(cameraCenter)
    val inputCameraZoom by rememberUpdatedState(cameraZoom)
    val inputViewport by rememberUpdatedState(viewport)
    val inputCoreAlive by rememberUpdatedState(coreLives > 0)

    val arcadeRandom = remember { Random(SystemClock.elapsedRealtime().toInt()) }
    val backgroundStars = remember(viewport) {
        if (viewport == IntSize.Zero) {
            emptyList()
        } else {
            val random = Random(73)
            List(160) {
                BackgroundStar(
                    position = androidx.compose.ui.geometry.Offset(
                        x = random.nextFloat() * viewport.width,
                        y = random.nextFloat() * viewport.height,
                    ),
                    radius = 0.8f + (random.nextFloat() * 2.2f),
                    alpha = 0.15f + (random.nextFloat() * 0.8f),
                )
            }
        }
    }

    fun viewportVector(): Vec2 = Vec2(viewport.width.toDouble(), viewport.height.toDouble())

    fun resetArcadeState() {
        energy = MaxEnergy
        score = 0.0
        coreLives = StartingCoreLives
        meteorsDestroyed = 0
        elapsed = 0.0
        spawnTimer = 1.35
        hitFlash = 0.0
        combo = 1.0
    }

    fun loadSandboxSnapshot(snapshot: SandboxSnapshot) {
        mode = AppMode.Sandbox
        bodies = snapshot.bodies
        cameraCenter = snapshot.cameraCenter
        cameraZoom = snapshot.zoom.coerceIn(SandboxMinZoom, SandboxMaxZoom)
        sandboxReferenceEnergy = snapshot.referenceEnergy
        touchPreview = null
        hitFlash = 0.0
        menuOpen = false
    }

    fun resetSandboxFromPreset(preset: SandboxPreset = SimulationEngine.sandboxPreset()) {
        mode = AppMode.Sandbox
        bodies = preset.bodies
        cameraCenter = preset.cameraCenter
        cameraZoom = preset.zoom.coerceIn(SandboxMinZoom, SandboxMaxZoom)
        sandboxReferenceEnergy = preset.referenceEnergy
        touchPreview = null
        hitFlash = 0.0
        menuOpen = false
    }

    fun resetArcadeScene() {
        mode = AppMode.Arcade
        val scene = if (viewport != IntSize.Zero) SimulationEngine.arcadeBodies(viewportVector()) else emptyList()
        bodies = scene
        cameraCenter = scene.firstOrNull { it.kind == BodyKind.Core }?.position
            ?: Vec2(viewport.width.toDouble() / 2.0, viewport.height.toDouble() / 2.0)
        cameraZoom = 1f
        sandboxReferenceEnergy = 0.0
        touchPreview = null
        menuOpen = false
        resetArcadeState()
    }

    fun refreshSaveSummaries() {
        saveSummaries = sandboxStorage.summaries()
    }

    LaunchedEffect(viewport) {
        if (viewport == IntSize.Zero || bodies.isNotEmpty()) return@LaunchedEffect
        resetArcadeScene()
    }

    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frame ->
                frameNanos = frame
                if (previousFrame != 0L && viewport != IntSize.Zero && !menuOpen) {
                    val dt = ((frame - previousFrame) / 1_000_000_000.0).coerceIn(0.0, 1.0 / 20.0)
                    hitFlash = (hitFlash - (dt * 1.8)).coerceAtLeast(0.0)

                    when (mode) {
                        AppMode.Arcade -> updateArcadeState(
                            dt = dt,
                            bodies = bodies,
                            viewport = viewport,
                            energy = energy,
                            score = score,
                            bestScore = bestScore,
                            coreLives = coreLives,
                            meteorsDestroyed = meteorsDestroyed,
                            elapsed = elapsed,
                            spawnTimer = spawnTimer,
                            combo = combo,
                            random = arcadeRandom,
                            onBodies = { bodies = it },
                            onEnergy = { energy = it },
                            onScore = { score = it },
                            onBestScore = { bestScore = it },
                            onCoreLives = { coreLives = it },
                            onMeteorsDestroyed = { meteorsDestroyed = it },
                            onElapsed = { elapsed = it },
                            onSpawnTimer = { spawnTimer = it },
                            onCombo = { combo = it },
                            onHitFlash = { hitFlash = it },
                            onTouchPreview = { touchPreview = it },
                        )

                        AppMode.Sandbox -> {
                            bodies = SimulationEngine.stepSandbox(
                                bodies = bodies,
                                dt = dt * SandboxTimeScale,
                                referenceEnergy = sandboxReferenceEnergy,
                            ).bodies
                        }
                    }
                }
                previousFrame = frame
            }
        }
    }

    val previewMass = touchPreview?.let {
        SimulationEngine.massFromHold(((frameNanos - it.startedAtNanos).coerceAtLeast(0L)) / 1_000_000_000.0)
    }
    val previewSpeed = touchPreview?.let {
        SimulationEngine.launchVelocityFromDrag(it.startWorld.toOffset(), it.currentWorld.toOffset()).magnitude()
    }
    val previewCost = previewMass?.let(SimulationEngine::energyCostForMass)
    val canLaunchPreview = when (mode) {
        AppMode.Arcade -> previewCost != null && energy >= previewCost && coreLives > 0
        AppMode.Sandbox -> true
    }
    val energyRatio = (energy / MaxEnergy).toFloat().coerceIn(0f, 1f)
    val wave = 1 + (elapsed / 14.0).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF02040B), Color(0xFF081125), Color(0xFF0D1834)),
                ),
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(mode, menuOpen, viewport) {
                    detectTransformGestures(panZoomLock = true) { centroid, pan, zoomChange, _ ->
                        if (menuOpen || viewport == IntSize.Zero) return@detectTransformGestures
                        val before = screenToWorld(centroid, viewport, cameraCenter, cameraZoom)
                        cameraZoom = (cameraZoom * zoomChange).coerceIn(minimumZoom(mode), maximumZoom(mode))
                        val after = screenToWorld(centroid, viewport, cameraCenter, cameraZoom)
                        cameraCenter += before - after
                        cameraCenter -= Vec2(pan.x.toDouble(), pan.y.toDouble()) / cameraZoom.toDouble()
                    }
                }
                .pointerInput(mode, menuOpen, viewport) {
                    if (menuOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startWorld = screenToWorld(down.position, inputViewport, inputCameraCenter, inputCameraZoom)
                        touchPreview = TouchPreview(startWorld, startWorld, SystemClock.elapsedRealtimeNanos())

                        var cancelledByMultiTouch = false
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            if (event.changes.count { it.pressed } > 1) {
                                cancelledByMultiTouch = true
                                touchPreview = null
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            touchPreview = touchPreview?.copy(
                                currentWorld = screenToWorld(
                                    change.position,
                                    inputViewport,
                                    inputCameraCenter,
                                    inputCameraZoom,
                                ),
                            )
                            if (!change.pressed) break
                            change.consume()
                        }

                        val preview = touchPreview
                        if (!cancelledByMultiTouch && preview != null && !inputMenuOpen) {
                            val holdSeconds = ((SystemClock.elapsedRealtimeNanos() - preview.startedAtNanos)
                                .coerceAtLeast(0L)) / 1_000_000_000.0
                            val body = SimulationEngine.createBody(
                                position = preview.startWorld,
                                dragEnd = preview.currentWorld,
                                holdSeconds = holdSeconds,
                                kind = if (inputMode == AppMode.Arcade) BodyKind.Player else BodyKind.Ambient,
                            )
                            when (inputMode) {
                                AppMode.Arcade -> {
                                    val cost = SimulationEngine.energyCostForMass(body.mass)
                                    if (inputEnergy >= cost && inputCoreAlive) {
                                        energy = (energy - cost).coerceAtLeast(0.0)
                                        bodies = inputBodies + body
                                    }
                                }
                                AppMode.Sandbox -> {
                                    val nextBodies = inputBodies + body
                                    bodies = nextBodies
                                    sandboxReferenceEnergy = SimulationEngine.totalEnergy(nextBodies)
                                }
                            }
                        }
                        touchPreview = null
                    }
                },
        ) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x221E3A8A), Color.Transparent),
                    center = center,
                    radius = max(size.width, size.height) * 0.75f,
                ),
            )
            backgroundStars.forEach { star ->
                drawCircle(Color.White.copy(alpha = star.alpha), star.radius, star.position)
            }
            bodies.forEach { drawTrail(it, viewport, cameraCenter, cameraZoom) }
            bodies.forEach { drawBody(it, viewport, cameraCenter, cameraZoom) }

            touchPreview?.let { preview ->
                val holdSeconds = ((frameNanos - preview.startedAtNanos).coerceAtLeast(0L)) / 1_000_000_000.0
                val mass = SimulationEngine.massFromHold(holdSeconds)
                val radius = (SimulationEngine.radiusForMass(mass) * cameraZoom).coerceIn(6f, 42f)
                val vectorColor = if (canLaunchPreview) Color(0xFF9BE7FF) else Color(0xFFFF7A6B)
                val start = worldToScreen(preview.startWorld, viewport, cameraCenter, cameraZoom)
                val end = worldToScreen(preview.currentWorld, viewport, cameraCenter, cameraZoom)
                drawCircle(vectorColor.copy(alpha = 0.12f), radius * 2.6f, start)
                drawCircle(vectorColor.copy(alpha = 0.88f), radius, start, style = Stroke(width = 2.5f))
                if (distance(start, end) > 6f) {
                    drawArrow(start, end, vectorColor)
                    drawFingerDirection(end, end - start, vectorColor)
                }
            }
            if (hitFlash > 0.0) {
                drawRect(Color(0xFFFF6B6B).copy(alpha = (hitFlash * 0.16).toFloat()))
            }
        }

        FilledTonalButton(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            onClick = { touchPreview = null; menuOpen = true },
        ) { Text("Menu") }

        ModeHud(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            mode = mode,
            score = score,
            coreLives = coreLives,
            wave = wave,
            combo = combo,
            bodyCount = bodies.size,
            cameraZoom = cameraZoom,
        )
        if (mode == AppMode.Arcade) {
            ArcadeEnergyHud(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                energyRatio = energyRatio,
                energy = energy,
            )
        }
        PreviewHud(
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp),
            mode = mode,
            previewMass = previewMass,
            previewSpeed = previewSpeed,
            previewCost = previewCost,
        )
        if (mode == AppMode.Arcade && coreLives <= 0) {
            GameOverOverlay(
                modifier = Modifier.align(Alignment.Center),
                score = score,
                onRetry = ::resetArcadeScene,
                onMenu = { menuOpen = true },
            )
        }
        if (menuOpen) {
            MenuOverlay(
                modifier = Modifier,
                mode = mode,
                saveSummaries = saveSummaries,
                onArcade = ::resetArcadeScene,
                onSandbox = ::resetSandboxFromPreset,
                onResetCurrent = { if (mode == AppMode.Arcade) resetArcadeScene() else resetSandboxFromPreset() },
                onClose = { menuOpen = false },
                onSaveSlot = { slot ->
                    sandboxStorage.save(
                        slot,
                        SandboxSnapshot(
                            bodies = bodies,
                            cameraCenter = cameraCenter,
                            zoom = cameraZoom,
                            referenceEnergy = sandboxReferenceEnergy,
                            timestampUtcMillis = System.currentTimeMillis(),
                        ),
                    )
                    refreshSaveSummaries()
                },
                onLoadSlot = { slot ->
                    sandboxStorage.load(slot)?.let(::loadSandboxSnapshot)
                    refreshSaveSummaries()
                },
            )
        }
    }
}

private fun updateArcadeState(
    dt: Double,
    bodies: List<CelestialBody>,
    viewport: IntSize,
    energy: Double,
    score: Double,
    bestScore: Double,
    coreLives: Int,
    meteorsDestroyed: Int,
    elapsed: Double,
    spawnTimer: Double,
    combo: Double,
    random: Random,
    onBodies: (List<CelestialBody>) -> Unit,
    onEnergy: (Double) -> Unit,
    onScore: (Double) -> Unit,
    onBestScore: (Double) -> Unit,
    onCoreLives: (Int) -> Unit,
    onMeteorsDestroyed: (Int) -> Unit,
    onElapsed: (Double) -> Unit,
    onSpawnTimer: (Double) -> Unit,
    onCombo: (Double) -> Unit,
    onHitFlash: (Double) -> Unit,
    onTouchPreview: (TouchPreview?) -> Unit,
) {
    if (coreLives <= 0) return
    var nextElapsed = elapsed + dt
    var nextCombo = (combo - (dt * 0.35)).coerceAtLeast(1.0)
    var nextEnergy = (energy + (dt * EnergyRegenPerSecond)).coerceAtMost(MaxEnergy)
    val stepResult = SimulationEngine.step(bodies, dt)
    var nextBodies = stepResult.bodies.filter { shouldKeepBody(it, viewport) }
    var hits = 0
    var destroyed = 0
    var scoreBonus = dt * ScorePerSecond * nextCombo

    stepResult.collisions.forEach { collision ->
        val kinds = setOf(collision.firstKind, collision.secondKind)
        if (BodyKind.Meteor !in kinds) return@forEach
        when {
            BodyKind.Core in kinds -> { hits += 1; nextCombo = 1.0 }
            kinds.size == 1 && BodyKind.Meteor in kinds -> scoreBonus += 10.0 * nextCombo
            else -> { destroyed += 1; scoreBonus += 24.0 * nextCombo }
        }
    }

    if (nextBodies.none { it.kind == BodyKind.Core }) hits = StartingCoreLives
    if (hits > 0) onHitFlash(1.0)
    if (destroyed > 0) {
        nextCombo = (nextCombo + (destroyed * 0.45)).coerceAtMost(4.5)
        nextEnergy = (nextEnergy + (destroyed * 7.0)).coerceAtMost(MaxEnergy)
    }

    val nextCoreLives = (coreLives - hits).coerceAtLeast(0)
    val nextScore = score + scoreBonus
    val nextBest = max(bestScore, nextScore)
    val wave = 1 + (nextElapsed / 14.0).toInt()
    var nextSpawnTimer = spawnTimer

    if (nextCoreLives > 0) {
        nextSpawnTimer -= dt
        val difficulty = 1.0 + (nextElapsed / 18.0)
        while (nextSpawnTimer <= 0.0) {
            nextBodies = nextBodies + SimulationEngine.spawnMeteor(
                viewport = Vec2(viewport.width.toDouble(), viewport.height.toDouble()),
                difficulty = difficulty + (wave * 0.2),
                random = random,
            )
            if (wave >= 3 && random.nextDouble() < 0.24) {
                nextBodies = nextBodies + SimulationEngine.spawnMeteor(
                    viewport = Vec2(viewport.width.toDouble(), viewport.height.toDouble()),
                    difficulty = difficulty + 1.8,
                    random = random,
                )
            }
            nextSpawnTimer += nextMeteorDelay(nextElapsed, random)
        }
    } else {
        onTouchPreview(null)
    }

    onBodies(nextBodies)
    onEnergy(nextEnergy)
    onScore(nextScore)
    onBestScore(nextBest)
    onCoreLives(nextCoreLives)
    onMeteorsDestroyed(meteorsDestroyed + destroyed)
    onElapsed(nextElapsed)
    onSpawnTimer(nextSpawnTimer)
    onCombo(nextCombo)
}
