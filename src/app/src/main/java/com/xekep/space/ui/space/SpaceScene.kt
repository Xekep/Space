package com.xekep.space.ui.space

import com.xekep.space.R
import com.xekep.space.audio.AmbientMusic
import com.xekep.space.input.SpaceShake
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.toOffset
import com.xekep.space.storage.SandboxStorage
import com.xekep.space.storage.GameOptions
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun SpaceSceneRoot(state: SpaceGameState? = null) {
    val context = LocalContext.current
    val game = state ?: viewModel<SpaceViewModel>().game
    val hasSession by remember(game) { derivedStateOf { game.hasSession } }
    val sandboxPaused by remember(game) { derivedStateOf { game.sandbox?.paused } }
    val density = LocalDensity.current.density
    SideEffect { game.updateDensity(density) }
    val storage = remember(context) { SandboxStorage(context) }
    val options = remember(context) { GameOptions(context) }
    val music = remember(context) { AmbientMusic(context.applicationContext) }
    val musicEnabled = options.music
    SideEffect { music.setEnabled(musicEnabled) }
    val haptic = LocalHapticFeedback.current
    val shakeCallback by rememberUpdatedState<(com.xekep.space.sim.Vec2) -> Unit> { impulse ->
        if (game.shakeSandbox(impulse) && options.vibration) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val shake = remember(context, game) { SpaceShake(context) { shakeCallback(it) } }
    val shakeEnabled = options.shake && game.mode == AppMode.Sandbox && !game.menuOpen && !game.sandboxOverlayOpen && sandboxPaused == false
    SideEffect { shake.setEnabled(shakeEnabled) }
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
            game.inform(R.string.scene_imported)
            context.getString(R.string.scene_imported)
        }.getOrElse { context.getString(R.string.import_failed) }
    }
    var frameNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shake) {
        shake.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) shake.setForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) shake.setForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); shake.close() }
    }

    DisposableEffect(lifecycleOwner, music) {
        music.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) music.setForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) music.setForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music.close()
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
    LaunchedEffect(game, game.menuOpen, sandboxPaused, game.sandboxOverlayOpen) {
        var previousFrame = 0L
        game.resetFrameClock()
        while (true) {
            val frame = withInfiniteAnimationFrameNanos { it }
            if (game.touchPreview != null) frameNanos = SystemClock.elapsedRealtimeNanos()
            if (previousFrame != 0L) {
                val dt = ((frame - previousFrame) / 1_000_000_000.0).coerceAtLeast(0.0)
                if (game.mode == AppMode.Sandbox && game.bodies.size >= 40) game.updateSandboxAsync(dt) else game.update(dt)
            }
            previousFrame = frame
        }
    }

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
                if (options.vibration) haptic.performHapticFeedback(if (hit) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
            lastLives = arcade.lives; lastDestroyed = arcade.destroyed
        }
    }
    val accent = if (game.mode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val preview = game.touchPreview
    val candidate = preview?.let { game.previewBody(it, (frameNanos - it.startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0) }
    val previewMass = candidate?.mass
    val previewSpeed = candidate?.velocity?.magnitude()
    val prediction = remember(frameNanos / 80_000_000L, preview?.currentWorld, game.orbitSourceId, game.spawnKind) {
        candidate?.let { SimulationEngine.predictPath(it, game.bodies.sortedByDescending { body -> body.mass }.take(24)) }.orEmpty()
    }
    val previewCost = previewMass?.let(SimulationEngine::energyCostForMass)
    val canLaunch = candidate != null && (game.mode == AppMode.Sandbox ||
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
            drawRect(Brush.radialGradient(listOf(Color(0x221E3A8A), Color.Transparent), center, max(size.width, size.height) * 0.75f))
            stars.forEach {
                // A little parallax makes camera travel visible even far from a planet.
                val x = ((it.position.x - camera.center.x * camera.zoom * 0.06) % size.width + size.width) % size.width
                val y = ((it.position.y - camera.center.y * camera.zoom * 0.06) % size.height + size.height) % size.height
                drawCircle(Color.White.copy(alpha = it.alpha), it.radius, Offset(x.toFloat(), y.toFloat()))
            }
            val bodies = game.bodies
            bodies.forEach { drawTrail(it, viewport, camera.center, camera.zoom, detailed = bodies.size < 60) }
            bodies.forEach { drawBody(it, viewport, camera.center, camera.zoom) }
            drawSpaceIndicators(game)
            prediction.forEachIndexed { index, point ->
                drawCircle(accent.copy(alpha = 0.8f - index * 0.02f), 2.dp.toPx(), worldToScreen(point, viewport, camera.center, camera.zoom))
            }
            preview?.let {
                val radius = (SimulationEngine.radiusForMass(previewMass ?: 70.0) * camera.zoom).coerceIn(6f, 42f)
                val color = if (canLaunch) Color(0xFF9BE7FF) else Color(0xFFFF7A6B)
                val start = worldToScreen(it.startWorld, viewport, camera.center, camera.zoom)
                val end = worldToScreen(it.currentWorld, viewport, camera.center, camera.zoom)
                drawCircle(color.copy(alpha = 0.12f), radius * 2.6f, start)
                if (candidate?.kind == com.xekep.space.sim.BodyKind.Ship || candidate?.kind == com.xekep.space.sim.BodyKind.Rocket)
                    drawBody(candidate, viewport, camera.center, camera.zoom)
                else drawCircle(color.copy(alpha = 0.88f), radius, start, style = Stroke(width = 2.5f))
                if (distance(start, end) > 6f) { drawArrow(start, end, color); drawFingerDirection(end, end - start, color) }
            }
            if (!options.reducedFlashes && game.mode == AppMode.Arcade && (arcade?.hitFlash ?: 0.0) > 0.0) {
                drawRect(Color(0xFFFF6B6B).copy(alpha = (arcade!!.hitFlash * 0.16).toFloat()))
            }
        }

        if (hasSession && !game.menuOpen && game.mode == AppMode.Sandbox) SandboxHud(game, candidate, options, shake.available)
        if (hasSession && !game.menuOpen && game.mode == AppMode.Arcade) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compactHud = maxHeight < 420.dp
                val toolsHeight = maxHeight * 0.60f
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = game::openMenu, modifier = Modifier.testTag("open-menu"),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.16f), contentColor = accent)) { Text(context.getString(R.string.menu)) }
                            Text(context.getString(game.mode.labelId()).uppercase(), style = MaterialTheme.typography.labelMedium,
                                color = if (game.mode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                            TextButton(onClick = game::fitCamera) { Text(if (game.mode == AppMode.Arcade) context.getString(R.string.find_core) else context.getString(R.string.fit_system)) }
                        }
                        if (!compactHud) ModeHud(Modifier.fillMaxWidth(), game.mode, arcade?.score ?: 0.0, arcade?.lives ?: 0,
                            arcade?.wave ?: 1, arcade?.combo ?: 1.0, game.bodies.size, camera.zoom)
                        if (compactHud && game.mode == AppMode.Arcade && arcade != null) Text(
                            context.getString(R.string.arcade_compact, arcade.lives, arcade.wave, arcade.score.toInt()),
                            style = MaterialTheme.typography.labelMedium, color = accent)
                        if (game.mode == AppMode.Arcade && arcade != null) Text(
                            if (arcade.resting) context.getString(R.string.rest) else context.getString(R.string.run_status, arcade.elapsed.toInt(), arcade.destroyed),
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
                        game.feedback?.let { Text(context.getString(it), style = MaterialTheme.typography.bodySmall, color = accent) }
                        if (game.orbitSource != null) TextButton(onClick = game::clearSelection) { Text(context.getString(R.string.cancel_orbit)) }
                        if (game.behind) Text(context.getString(R.string.catching_up), style = MaterialTheme.typography.labelSmall, color = accent)
                        PreviewHud(mode = game.mode, previewMass = previewMass, previewSpeed = previewSpeed, previewCost = previewCost)
                        if (game.mode == AppMode.Arcade && arcade != null) {
                            ArcadeEnergyHud(Modifier.fillMaxWidth(), (arcade.energy / MaxEnergy).toFloat(), arcade.energy)
                        }
                        Text(context.getString(R.string.gesture_hint), modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                }
            }
        }

        if (!game.menuOpen && game.mode == AppMode.Arcade && arcade != null && arcade.lives <= 0) {
            GameOverOverlay(Modifier.align(Alignment.Center), arcade.score, game.bestScore, arcade.destroyed,
                elapsed = arcade.elapsed, wave = arcade.wave, accuracy = arcade.accuracy,
                onRetry = { game.startArcade(arcade.difficulty) }, onMenu = game::openMenu)
        }
        if (game.menuOpen) {
            SpaceMenu(game, summaries, notice, options = options,
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
