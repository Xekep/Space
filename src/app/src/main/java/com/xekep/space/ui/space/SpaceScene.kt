package com.xekep.space.ui.space

import com.xekep.space.R
import com.xekep.space.audio.AmbientMusic
import com.xekep.space.input.SpaceShake
import com.xekep.space.input.SpaceTilt
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.SatellitePlacement
import com.xekep.space.sim.satellitePlacement
import com.xekep.space.sim.toOffset
import com.xekep.space.storage.SandboxStorage
import com.xekep.space.storage.GameOptions
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SpaceSceneRoot(state: SpaceGameState? = null) {
    val context = LocalContext.current
    val game = state ?: viewModel<SpaceViewModel>().game
    val hasSession by remember(game) { derivedStateOf { game.hasSession } }
    val sandboxPaused by remember(game) { derivedStateOf { game.sandbox?.paused } }
    val view = LocalView.current
    val keepScreenOn = hasSession && !game.menuOpen &&
        (game.mode != AppMode.Arcade || (game.arcade?.lives ?: 0) > 0)
    DisposableEffect(view, keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    val density = LocalDensity.current.density
    SideEffect { game.updateDensity(density) }
    val storage = remember(context) { SandboxStorage(context) }
    val options = remember(context) { GameOptions(context) }
    val largeVehicleIcons = options.largeVehicleIcons
    SideEffect { game.largeVehicleIcons = largeVehicleIcons }
    val music = remember(context) { AmbientMusic(context.applicationContext) }
    val musicEnabled = options.music
    SideEffect { music.setEnabled(musicEnabled) }
    val haptic = remember(context) { com.xekep.space.input.GameHaptics(context) }
    SideEffect { haptic.enabled=options.vibration && !game.menuOpen }
    val shakeCallback by rememberUpdatedState<(com.xekep.space.sim.Vec2) -> Unit> { impulse ->
        if (game.shakeSandbox(impulse,options.shakeMode,options.shakeIntensity.toDouble())) haptic.impact()
    }
    val shake = remember(context, game) { SpaceShake(context) { shakeCallback(it) } }
    val controlledId by remember(game) { derivedStateOf { game.controlledVehicleId } }
    val tiltCallback by rememberUpdatedState<(com.xekep.space.sim.Vec2) -> Unit> { game.setSteeringInput(it) }
    val tilt = remember(context, game) { SpaceTilt(context) { tiltCallback(it) } }
    val controlsActive = !game.menuOpen && !game.sandboxOverlayOpen && hasSession &&
        (if (game.mode == AppMode.Sandbox) sandboxPaused == false && game.orbitSourceId == null else (game.arcade?.lives ?: 0) > 0)
    val motionEnabled = options.motionControl && tilt.available
    SideEffect { game.setMotionControlEnabled(motionEnabled); tilt.setEnabled(motionEnabled && controlledId != null && controlsActive) }
    LaunchedEffect(controlledId, game.mode) { tilt.recalibrate() }
    val shakeMode = options.shakeMode
    val shakeEnabled = controlledId == null && shakeMode != com.xekep.space.sim.ShakeMode.Off && game.mode == AppMode.Sandbox && !game.menuOpen && !game.sandboxOverlayOpen && sandboxPaused == false && game.orbitSourceId == null
    SideEffect { shake.setMode(shakeMode); shake.setEnabled(shakeEnabled) }
    val tone = remember(context) { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    var summaries by remember { mutableStateOf(storage.summaries()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val exportScene = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            notice = runCatching {
                val snapshot = game.pendingExport ?: error("Scene unavailable")
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(storage.encode(snapshot)) } ?: error("Cannot write file")
                context.getString(R.string.scene_exported)
            }.getOrElse { context.getString(R.string.export_failed) }
        }
        game.pendingExport = null
    }
    val importScene = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) notice = runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytesLimited(2_000_000)
                String(bytes, Charsets.UTF_8)
            } ?: error("Cannot read file")
            game.loadSandbox(storage.decode(raw))
            context.getString(R.string.scene_imported)
        }.getOrElse { context.getString(R.string.import_failed) }
    }
    var frameNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var drawNanos by remember { mutableLongStateOf(0L) }
    val interpolation=remember(game) { SandboxInterpolation() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shake, tilt) {
        shake.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        tilt.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { shake.setForeground(true); tilt.setForeground(true) }
            if (event == Lifecycle.Event.ON_PAUSE) { shake.setForeground(false); tilt.setForeground(false) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); shake.close(); tilt.close() }
    }

    DisposableEffect(lifecycleOwner, music) {
        music.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        haptic.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { music.setForeground(true); haptic.setForeground(true) }
            if (event == Lifecycle.Event.ON_PAUSE) { music.setForeground(false); haptic.setForeground(false) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music.close()
            haptic.close()
        }
    }

    DisposableEffect(lifecycleOwner, game) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) game.openMenu()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = hasSession) {
        if (game.menuOpen) game.closeMenu() else game.openMenu()
    }
    LaunchedEffect(game.menuOpen) {
        notice = null
        if (game.menuOpen) summaries = storage.summaries()
    }
    LaunchedEffect(game.feedback) {
        if (game.feedback != null && game.orbitSourceId == null) { delay(4000); game.dismissFeedback() }
    }
    LaunchedEffect(game, game.mode, game.sceneGeneration, game.menuOpen, sandboxPaused, game.sandboxOverlayOpen) {
        var previousFrame=0L
        var previousPhysics=0L
        var calculation: Job?=null
        game.resetFrameClock()
        try {
            while (true) {
                val frame=withInfiniteAnimationFrameNanos { it }
                if (game.touchPreview != null) frameNanos=SystemClock.elapsedRealtimeNanos()
                if (previousFrame != 0L) {
                    val dt=((frame-previousFrame)/1_000_000_000.0).coerceAtLeast(0.0)
                    if (game.mode == AppMode.Sandbox && (game.bodies.size >= 40 || game.sandbox?.collisionsEnabled == true)) {
                        game.updateSandboxPresentation(dt)
                        if (!game.menuOpen && !game.sandboxOverlayOpen && game.sandbox?.paused == false && game.orbitSourceId == null) {
                            if (game.bodies.size >= 160) drawNanos=SystemClock.elapsedRealtimeNanos()
                            if (calculation?.isActive != true) {
                                val elapsed=if (previousPhysics == 0L) dt else ((frame-previousPhysics)/1e9).coerceAtLeast(0.0)
                                previousPhysics=frame
                                calculation=launch { game.updateSandboxAsync(elapsed,budgeted=true) }
                            }
                        } else previousPhysics=0L
                    } else game.update(dt)
                }
                previousFrame=frame
            }
        } finally { calculation?.cancel() }
    }

    val solarLabels = remember(game, game.sceneGeneration) { SolarLabelLayout() }
    val viewport = game.viewport
    val camera by remember(game) { derivedStateOf { game.camera } }
    val arcade = game.arcade
    val coreId = arcade?.bodies?.firstOrNull { it.kind == com.xekep.space.sim.BodyKind.Core }?.id
    var lastLives by remember(coreId) { mutableIntStateOf(arcade?.lives ?: 0) }
    var lastDestroyed by remember(coreId) { mutableIntStateOf(arcade?.destroyed ?: 0) }
    LaunchedEffect(coreId, arcade?.lives, arcade?.destroyed) {
        if (arcade != null) {
            val hit = arcade.lives < lastLives
            val intercept = arcade.destroyed > lastDestroyed
            if (hit || intercept) {
                if (options.sound) tone?.startTone(if (hit) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK, 90)
                haptic.impact(strong=hit)
            }
            lastLives = arcade.lives; lastDestroyed = arcade.destroyed
        }
    }

    val sandboxBurst=game.explosions.lastOrNull()
    LaunchedEffect(sandboxBurst?.position,sandboxBurst?.seed) {
        if (sandboxBurst != null && game.mode == AppMode.Sandbox) haptic.impact()
    }
    val accent = if (game.mode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val preview = game.touchPreview
    val candidate = preview?.let { game.previewBody(it, (frameNanos - it.startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0) }
    val previewMass = candidate?.mass
    val prediction = remember(frameNanos / 80_000_000L, preview?.currentWorld, game.orbitSourceId, game.spawnKind) {
        candidate?.takeIf { it.waypoints.isEmpty() }?.let { body ->
            game.orbitSource?.let { parent ->
                val offset=body.position-parent.position
                List(24) { index -> parent.position+rotateVector(offset,index*2*PI/24) }
            } ?: SimulationEngine.predictPath(body, game.bodies.sortedByDescending { it.mass }.take(24))
        }.orEmpty()
    }
    val previewCost = candidate?.let(::launchCost)
    val orbitPlacement=candidate?.let { body -> game.orbitSource?.let { satellitePlacement(it,body,game.bodies) } }
    val canLaunch = candidate != null && orbitPlacement != SatellitePlacement.Overlap && (game.mode == AppMode.Sandbox ||
        (arcade != null && arcade.lives > 0 && (previewCost ?: 0.0) <= arcade.energy))
    val stars = remember(viewport) {
        val random = Random(73)
        List(160) {
            BackgroundStar(Offset(random.nextFloat() * viewport.width, random.nextFloat() * viewport.height),
                0.8f + random.nextFloat() * 2.2f, 0.15f + random.nextFloat() * 0.8f)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF02040B), Color(0xFF081125), Color(0xFF0D1834))))) {
        Canvas(Modifier.fillMaxSize().testTag("space-scene").semantics { contentDescription = context.getString(R.string.space_scene) }
            .onSizeChanged(game::resize).spaceGestures(game, hasSession)) {
            if (game.menuOpen) return@Canvas
            drawRect(Brush.radialGradient(listOf(Color(0x221E3A8A), Color.Transparent), center, max(size.width, size.height) * 0.75f))
            val bodies = game.bodies
            interpolation.begin(bodies,game.sandboxEditRevision,drawNanos,
                game.mode == AppMode.Sandbox && bodies.size >= 160 && !game.menuOpen && !game.sandboxOverlayOpen &&
                    game.sandbox?.paused == false && game.orbitSourceId == null && game.touchPreview == null)
            val tracked=game.cameraTarget
            val renderCamera=if (tracked != null) camera.copy(center=interpolation.position(tracked)) else camera
            rotate((game.cameraRotation*180/PI).toFloat()) {
            stars.forEach {
                // A little parallax makes camera travel visible even far from a planet.
                val x = ((it.position.x - renderCamera.center.x * renderCamera.zoom * 0.06) % size.width + size.width) % size.width
                val y = ((it.position.y - renderCamera.center.y * renderCamera.zoom * 0.06) % size.height + size.height) % size.height
                val tiles=if (abs(game.cameraRotation) > .001) -1..1 else 0..0
                for (tileX in tiles) for (tileY in tiles) {
                    drawCircle(Color.White.copy(alpha = it.alpha), it.radius, Offset(x.toFloat()+tileX*size.width, y.toFloat()+tileY*size.height))
                }
            }
            if (game.mode == AppMode.Sandbox) drawSolarOrbits(bodies, viewport, renderCamera.center, renderCamera.zoom, game.hiddenSolarOrbits)
            bodies.filter { it.waypoints.isNotEmpty() }.forEach { drawFlightRoute(it.position,it.waypoints,viewport,renderCamera.center,renderCamera.zoom,it.color,it.routePath,it.routeDistance) }
            candidate?.takeIf { it.waypoints.isNotEmpty() }?.let { drawFlightRoute(it.position,it.waypoints,viewport,renderCamera.center,renderCamera.zoom,accent,it.routePath,showMarkers=true) }
            bodies.forEach {
                if (bodies.size < 160 || it.mass >= 2 || renderCamera.zoom >= .1f)
                    drawTrail(it, viewport, renderCamera.center, renderCamera.zoom, detailed = bodies.size < 60,
                        cameraRotation=game.cameraRotation,renderPosition=interpolation.position(it),stride=if (bodies.size >= 160) 6 else if (bodies.size < 60) 1 else 2)
            }
            drawWorldBodies(visibleSolarBodies(bodies,renderCamera.zoom,density),viewport,renderCamera,game.cameraRotation,
                controlledId,largeVehicleIcons,interpolation,bodies.size >= 160)
            drawExplosions(if (game.mode == AppMode.Sandbox) game.explosions else arcade?.explosions.orEmpty(), viewport, renderCamera.center, renderCamera.zoom, options.reducedFlashes)
            arcade?.combat?.projectiles?.forEach { shot ->
                val point = worldToScreen(shot.position, viewport, renderCamera.center, renderCamera.zoom)
                drawLine(Color(0xFF9EF8FF), point - shot.velocity.normalized().toOffset() * 10.dp.toPx(), point, 2.dp.toPx())
            }
            prediction.forEachIndexed { index, point ->
                drawCircle((if (canLaunch) accent else Color(0xFFFF7A6B)).copy(alpha = 0.8f - index * 0.02f), 2.dp.toPx(), worldToScreen(point, viewport, renderCamera.center, renderCamera.zoom))
            }
            preview?.let {
                val radius = (SimulationEngine.radiusForMass(previewMass ?: 70.0) * renderCamera.zoom).coerceIn(6f, 42f)
                val color = if (canLaunch) Color(0xFF9BE7FF) else Color(0xFFFF7A6B)
                val start = worldToScreen(it.startWorld, viewport, renderCamera.center, renderCamera.zoom)
                val end = worldToScreen(it.currentWorld, viewport, renderCamera.center, renderCamera.zoom)
                drawCircle(color.copy(alpha = 0.12f), radius * 2.6f, start)
                if (candidate != null && candidate.kind in listOf(com.xekep.space.sim.BodyKind.Ship,com.xekep.space.sim.BodyKind.Rocket,com.xekep.space.sim.BodyKind.Star,com.xekep.space.sim.BodyKind.BlackHole))
                    drawBody(candidate, viewport, renderCamera.center, renderCamera.zoom, game.cameraRotation, largeVehicleIcons=largeVehicleIcons)
                else drawCircle(color.copy(alpha = 0.88f), radius, start, style = Stroke(width = 2.5f))
                if (distance(start, end) > 6f) { drawArrow(start, end, color); drawFingerDirection(end, end - start, color) }
            }
            }
            drawSolarLabels(bodies, viewport, renderCamera.center, renderCamera.zoom, context, solarLabels, game.presentationAge, game.cameraRotation)
            drawSpaceIndicators(game,renderCamera) { interpolation.position(it) }
            if (!options.reducedFlashes && game.mode == AppMode.Arcade && (arcade?.hitFlash ?: 0.0) > 0.0) {
                drawRect(Color(0xFFFF6B6B).copy(alpha = (arcade!!.hitFlash * 0.16).toFloat()))
            }
        }

        if (hasSession && !game.menuOpen && game.mode == AppMode.Sandbox) SandboxHud(game, candidate, options, shake.available, tilt.available)
        if (hasSession && !game.menuOpen && game.mode == AppMode.Arcade) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val toolsHeight = maxHeight * 0.60f
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ArcadeTopHud(game)
                        if (arcade?.resting == true) Text(
                            context.getString(R.string.rest),
                            modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = toolsHeight).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.tutorialText?.let { message ->
                            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(context.getString(message), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = game::skipTutorial) { Text(context.getString(R.string.skip)) }
                                }
                            }
                        }
                        game.feedback?.let { Text(context.getString(it),modifier=Modifier.fillMaxWidth(),
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center,style = MaterialTheme.typography.bodySmall, color = accent) }
                        PilotHud(game)
                        if (candidate == null) ArcadeSelectionHud(game)
                        else BodyDetailsText(candidate, Modifier.align(Alignment.CenterHorizontally))
                        ArcadeSpawnControls(game, options, tilt.available)
                        if (game.orbitSource != null) TextButton(onClick = game::clearSelection) { Text(context.getString(R.string.cancel_orbit)) }
                        if (game.mode == AppMode.Arcade && arcade != null) {
                            ArcadeEnergyHud(Modifier.fillMaxWidth(), (arcade.energy / MaxEnergy).toFloat(), arcade.energy)
                        }

                    }
                }
            }
        }

        CoreDirectionIndicator(game)

        if (!game.menuOpen && game.mode == AppMode.Arcade && arcade != null && arcade.lives <= 0) {
            GameOverOverlay(Modifier.align(Alignment.Center), arcade.score, game.bestScore, arcade.destroyed,
                elapsed = arcade.elapsed, wave = arcade.wave, accuracy = arcade.accuracy,
                onRetry = { game.startArcade(arcade.difficulty) }, onMenu = game::openMenu)
        }
        if (game.menuOpen) {
            val menuPhase=rememberMenuPhase()
            MenuCosmos(Modifier.fillMaxSize(),menuPhase)
            SpaceMenu(game, summaries, notice, options = options, orbitPhase=menuPhase,
                onExport = { game.pendingExport = game.snapshot(System.currentTimeMillis()); exportScene.launch("${game.sandbox?.name?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: context.getString(R.string.space)}.space.json") },
                onImport = { importScene.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onSaveSlot = { slot ->
                    game.snapshot(System.currentTimeMillis())?.let {
                        storage.save(slot, it)
                        game.markSaved()
                        summaries = storage.summaries()
                        notice = context.getString(R.string.saved_slot, slot)
                    }
                },
                onLoadSlot = { slot ->
                    val snapshot = storage.load(slot)
                    if (snapshot != null) game.loadSandbox(snapshot) else notice = context.getString(R.string.load_failed)
                })
        }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Scene file is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
