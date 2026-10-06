package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import kotlin.math.roundToInt

private enum class SandboxPanel { Spawn, Tools, Inspector }
private data class SandboxHudState(val name: String, val paused: Boolean, val timeScale: Double,
    val collisionsEnabled: Boolean, val preset: com.xekep.space.sim.SandboxPresetKind)

/** Keep the universe visible: two small bars, with creation and editing on demand. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SandboxHud(game: SpaceGameState, candidate: CelestialBody?, options: com.xekep.space.storage.GameOptions, shakeAvailable: Boolean) {
    val context = LocalContext.current
    val metadata by remember(game) { derivedStateOf { game.sandbox?.let {
        SandboxHudState(it.name, it.paused, it.timeScale, it.collisionsEnabled, it.preset)
    } } }
    val scene = metadata ?: return
    val selection by remember(game) { derivedStateOf { game.selectedBody?.let { it.id to it.kind } } }
    var panel by remember { mutableStateOf<SandboxPanel?>(null) }
    DisposableEffect(panel, game) {
        game.sandboxOverlayOpen = panel != null
        game.touchPreview = null
        game.resetFrameClock()
        onDispose { game.sandboxOverlayOpen = false; game.resetFrameClock() }
    }
    LaunchedEffect(game.selectedBodyId) {
        if (panel == SandboxPanel.Inspector && game.selectedBody == null) panel = null
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().blockWorldTouches(), shape = RoundedCornerShape(20.dp),
            color = Color(0xDB0B1425), contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudButton("menu", context.getString(R.string.menu), "open-menu", game::openMenu)
                Text(scene.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (game.dirty) Canvas(Modifier.padding(8.dp).size(6.dp).semantics { contentDescription = context.getString(R.string.unsaved) }) {
                    drawCircle(accent)
                }
                HudButton("fit", context.getString(R.string.fit_system), "fit-system", game::fitCamera)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            game.tutorialText?.let { message ->
                Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(context.getString(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = game::skipTutorial) { Text(context.getString(R.string.skip)) }
                    }
                }
            }
            if (game.orbitSource != null) Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.place_satellite), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    HudButton("close", context.getString(R.string.cancel_orbit), "cancel-orbit", game::clearSelection)
                }
            } else game.feedback?.let { Text(context.getString(it), style = MaterialTheme.typography.labelSmall, color = accent) }
            if (game.behind) Text(context.getString(R.string.catching_up), style = MaterialTheme.typography.labelSmall, color = accent)
            candidate?.let {
                Text(context.getString(R.string.body_details, it.mass.roundToInt(), it.velocity.magnitude().roundToInt()),
                    Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium, color = accent)
            }
            selection?.let { body ->
                Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(16.dp), color = Color(0xEF14223A), contentColor = MaterialTheme.colorScheme.onSurface) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { panel = SandboxPanel.Inspector }, modifier = Modifier.weight(1f).testTag("open-body-tools")) {
                            Text(context.getString(body.second.labelId()) + " · " + context.getString(R.string.edit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HudButton("close", context.getString(R.string.close), "clear-selection", game::clearSelection)
                    }
                }
            }
            Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(22.dp), color = Color(0xEF0B1425), contentColor = MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudButton(if (scene.paused) "play" else "pause", context.getString(if (scene.paused) R.string.play else R.string.pause),
                        "sandbox-pause", game::toggleSandboxPause)
                    Text(speedLabel(scene.timeScale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { panel = SandboxPanel.Spawn }, modifier = Modifier.weight(1f).testTag("sandbox-spawn")) {
                        Text("+ " + context.getString(game.spawnKind.labelId()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HudButton("tools", context.getString(R.string.sandbox_tools), "sandbox-tools", { panel = SandboxPanel.Tools })
                }
            }
        }
    }
    if (panel != null) {
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(when (panel) {
                        SandboxPanel.Spawn -> R.string.spawn_object
                        SandboxPanel.Inspector -> R.string.selected_body
                        else -> R.string.sandbox_tools
                    }), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    HudButton("close", context.getString(R.string.close), "close-sandbox-panel", { panel = null })
                }
                when (panel) {
                    SandboxPanel.Spawn -> {
                        Text(context.getString(R.string.spawn_gesture), style = MaterialTheme.typography.bodySmall)
                        listOf(BodyKind.Ambient, BodyKind.Ship, BodyKind.Rocket).forEach { kind ->
                            Surface(Modifier.fillMaxWidth().testTag("spawn-${kind.name}").clickable { game.chooseSpawnKind(kind); panel = null },
                                shape = RoundedCornerShape(16.dp), color = if (game.spawnKind == kind) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Canvas(Modifier.size(48.dp)) {
                                        val color = if (kind == BodyKind.Rocket) Color(0xFFFFB36B) else Color(0xFF8BD3FF)
                                        drawBody(CelestialBody(-1, Vec2.Zero, Vec2(0.0, -1.0), 30.0, 11f, color, kind,
                                            burnRemaining = if (kind == BodyKind.Rocket) 3.0 else 0.0), IntSize(size.width.toInt(), size.height.toInt()), Vec2.Zero, 1f)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(context.getString(kind.labelId()), style = MaterialTheme.typography.titleMedium)
                                        Text(context.getString(kind.spawnDescriptionId()), style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (game.spawnKind == kind) Text("✓", color = accent)
                                }
                            }
                        }
                    }
                    SandboxPanel.Inspector -> SandboxTools(game, showHistory = false)
                    SandboxPanel.Tools -> {
                        Text(context.getString(R.string.sandbox_panel_paused), style = MaterialTheme.typography.bodySmall)
                        Text(context.getString(R.string.time_speed), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.25, 1.0, 3.0, 6.0).forEach { speed ->
                                FilterChip(selected = scene.timeScale == speed, onClick = { game.setTimeScale(speed) },
                                    label = { Text(speedLabel(speed)) }, modifier = Modifier.testTag("speed-$speed"))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(context.getString(R.string.merge_impact), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Switch(scene.collisionsEnabled, game::setCollisions, Modifier.testTag("sandbox-collisions"))
                        }
                        Text(context.getString(R.string.vehicle_collision_help), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(context.getString(R.string.shake_universe), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Switch(options.shake, { options.shake = it; options.save() }, Modifier.testTag("sandbox-shake"), enabled = shakeAvailable)
                        }
                        Text(context.getString(if (shakeAvailable) R.string.shake_help else R.string.shake_unavailable), style = MaterialTheme.typography.bodySmall)
                        SandboxTools(game, showInspector = false)
                        Text(context.getString(R.string.body_count, game.bodies.size) + " · " + context.getString(R.string.zoom) + " " +
                            String.format(java.util.Locale.ROOT, "%.2fx", game.camera.zoom), style = MaterialTheme.typography.labelMedium)
                        if (game.bodies.size >= 30 && scene.timeScale > 1) Text(context.getString(R.string.large_system), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text(context.getString(R.string.gesture_hint), style = MaterialTheme.typography.bodySmall)
                        Text(context.getString(when (scene.preset) {
                            com.xekep.space.sim.SandboxPresetKind.SolarSystem -> R.string.experiment_solar
                            com.xekep.space.sim.SandboxPresetKind.BinaryStars -> R.string.experiment_binary
                            else -> R.string.experiment_empty
                        }), style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun speedLabel(speed: Double) = if (speed == 0.25) "¼x" else "${speed.toInt()}x"

private fun Modifier.blockWorldTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun HudButton(glyph: String, label: String, tag: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    IconButton(onClick, Modifier.size(48.dp).testTag(tag).semantics { contentDescription = label }) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width; val h = size.height
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), 2.dp.toPx(), StrokeCap.Round)
            when (glyph) {
                "menu" -> listOf(.25f, .5f, .75f).forEach { line(.1f, it, .9f, it) }
                "pause" -> { line(.32f, .15f, .32f, .85f); line(.68f, .15f, .68f, .85f) }
                "play" -> drawPath(Path().apply { moveTo(w * .25f, h * .1f); lineTo(w * .9f, h * .5f); lineTo(w * .25f, h * .9f); close() }, color)
                "close" -> { line(.2f, .2f, .8f, .8f); line(.8f, .2f, .2f, .8f) }
                "fit" -> { listOf(.1f, .9f).forEach { x ->
                    val end = if (x < .5f) .35f else .65f
                    line(x, .1f, end, .1f); line(x, .1f, x, .35f)
                    line(x, .9f, end, .9f); line(x, .9f, x, .65f)
                } }
                else -> listOf(.2f, .5f, .8f).forEachIndexed { i, y ->
                    line(.1f, y, .9f, y); drawCircle(color, 3.dp.toPx(), Offset(w * if (i == 1) .7f else .35f, h * y))
                }
            }
        }
    }
}
