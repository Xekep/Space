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

private enum class SandboxPanel { Tools }
private data class SandboxHudState(val name: String, val paused: Boolean, val timeScale: Double,
    val collisionsEnabled: Boolean, val preset: com.xekep.space.sim.SandboxPresetKind,val collisionMode: com.xekep.space.sim.SandboxCollisionMode)

/** Keep the universe visible: two small bars, with creation and editing on demand. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SandboxHud(game: SpaceGameState, candidate: CelestialBody?, options: com.xekep.space.storage.GameOptions, shakeAvailable: Boolean, motionAvailable: Boolean = true) {
    val context = LocalContext.current
    val metadata by remember(game) { derivedStateOf { game.sandbox?.let {
        SandboxHudState(it.name, it.paused, it.timeScale, it.collisionsEnabled, it.preset,it.collisionMode)
    } } }
    val scene = metadata ?: return
    val selection by remember(game) { derivedStateOf { game.selectedBody?.let { Triple(it.id, it.labelId(), it.kind == BodyKind.Ship || it.kind == BodyKind.Rocket) } } }
    var panel by remember { mutableStateOf<SandboxPanel?>(null) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    DisposableEffect(panel, editing, deleting, game) {
        game.sandboxOverlayOpen = panel != null || editing || deleting
        game.touchPreview = null
        game.resetFrameClock()
        onDispose { game.sandboxOverlayOpen = false; game.resetFrameClock() }
    }
    LaunchedEffect(game.selectedBodyId) { editing = false; deleting = false }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().blockWorldTouches(), shape = RoundedCornerShape(20.dp),
            color = Color(0xDB0B1425), contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudButton("menu", context.getString(R.string.menu), "open-menu", game::openMenu)
                Text(scene.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                ObjectCounter(game)
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
                    val message=game.feedback ?: R.string.place_satellite
                    Text(context.getString(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color=if (message == R.string.place_satellite) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    HudButton("close", context.getString(R.string.cancel_orbit), "cancel-orbit", game::clearSelection)
                }
            } else game.feedback?.let { Text(context.getString(it),modifier=Modifier.fillMaxWidth(),
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,style = MaterialTheme.typography.labelSmall, color = accent) }
            candidate?.let {
                BodyDetailsText(it, Modifier.align(Alignment.CenterHorizontally))
            }
            PilotHud(game)
            selection?.let { body ->
                Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(16.dp), color = Color(0xEF14223A), contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.padding(horizontal = 4.dp).testTag("body-toolbar")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(context.getString(body.second), Modifier.weight(1f).padding(start = 12.dp), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            HudButton("close", context.getString(R.string.close), "clear-selection", game::clearSelection)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            HudButton("focus", context.getString(R.string.focus_body), "focus-body", game::focusSelected)
                            HudButton("follow", context.getString(if (game.following) R.string.stop_following else R.string.follow),
                                "follow-body", game::followSelected, selected = game.following)
                            HudButton("orbit", context.getString(R.string.orbit_helper), "orbit-helper", game::prepareOrbit, enabled = !body.third)
                            HudButton("edit", context.getString(R.string.edit), "edit-body", { editing = true })
                            HudButton("delete", context.getString(R.string.delete), "delete-body", { deleting = true })
                        }
                    }
                }
            }
            Surface(Modifier.blockWorldTouches(), shape = RoundedCornerShape(22.dp), color = Color(0xEF0B1425), contentColor = MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudButton(if (scene.paused) "play" else "pause", context.getString(if (scene.paused) R.string.play else R.string.pause),
                        "sandbox-pause", game::toggleSandboxPause)
                    Text(speedLabel(scene.timeScale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SpawnCycleButton(game, Modifier.weight(1f), "sandbox-spawn")
                    FlightLoopButton(game)
                    MotionControlButton(options, motionAvailable, "sandbox-motion-control")
                    HudButton("tools", context.getString(R.string.sandbox_tools), "sandbox-tools", { panel = SandboxPanel.Tools })
                }
            }
        }
    }
    if (editing || deleting) game.selectedBody?.let { body ->
        if (editing) BodyEditDialog(body.mass, body.velocity, onDismiss = { editing = false },
            onApply = { mass, velocity -> game.editSelected(mass, velocity); editing = false }, body = body)
        if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(context.getString(R.string.delete_body_question)) },
            confirmButton = { TextButton(onClick = { game.deleteSelected(); deleting = false }, modifier = Modifier.testTag("confirm-delete")) { Text(context.getString(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(context.getString(R.string.cancel)) } })
    }
    if (panel != null) {
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.sandbox_tools), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    HudButton("close", context.getString(R.string.close), "close-sandbox-panel", { panel = null })
                }
                when (panel) {
                    SandboxPanel.Tools -> {
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
                        if (scene.collisionsEnabled) CollisionModePicker(scene.collisionMode,game::setCollisionMode)
                        Text(context.getString(R.string.shake_universe), style = MaterialTheme.typography.titleSmall)
                        FlowRow(Modifier.fillMaxWidth().testTag("sandbox-shake"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.xekep.space.sim.ShakeMode.entries.forEach { mode ->
                                val label=when (mode) {
                                    com.xekep.space.sim.ShakeMode.Off -> R.string.shake_off
                                    com.xekep.space.sim.ShakeMode.Classic -> R.string.shake_classic
                                    com.xekep.space.sim.ShakeMode.Inertial -> R.string.shake_inertial
                                }
                                FilterChip(options.shakeMode == mode,{ options.shakeMode=mode; options.save() },
                                    label={ Text(context.getString(label),maxLines=1) },enabled=shakeAvailable,
                                    modifier=Modifier.testTag("shake-${mode.name}"))
                            }
                        }
                        Text(context.getString(R.string.shake_intensity,(options.shakeIntensity*100).roundToInt()),style=MaterialTheme.typography.bodySmall)
                        Slider(options.shakeIntensity,{ options.shakeIntensity=it },valueRange=.25f..2.5f,steps=8,
                            enabled=shakeAvailable && options.shake, onValueChangeFinished=options::save,
                            modifier=Modifier.testTag("shake-intensity").semantics { contentDescription=context.getString(R.string.shake_intensity_label) })
                        if (!shakeAvailable) Text(context.getString(R.string.shake_unavailable), style = MaterialTheme.typography.bodySmall)
                        SandboxTools(game)
                        OutlinedButton(onClick={ game.generateRandomSystems(context.getString(R.string.random_systems_name)); panel=null },
                            modifier=Modifier.fillMaxWidth().testTag("generate-random-systems")) {
                            Text(context.getString(R.string.generate_random_systems))
                        }
                        Text(context.getString(R.string.body_count, game.bodies.size) + " · " + context.getString(R.string.zoom) + " " +
                            String.format(java.util.Locale.ROOT, "%.2fx", game.camera.zoom), style = MaterialTheme.typography.labelMedium)
                        if (game.bodies.size >= 30 && scene.timeScale > 1) Text(context.getString(R.string.large_system), style = MaterialTheme.typography.bodySmall)

                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun speedLabel(speed: Double) = if (speed == 0.25) "¼x" else "${speed.toInt()}x"

internal fun Modifier.blockWorldTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            // This panel wins hit testing over the sibling world canvas. Leave child gestures
            // untouched: consuming moves here cancels Slider's touch-slop detection.
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun HudButton(glyph: String, label: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, selected: Boolean = false) {
    val color = (if (selected) Color(0xFF80FFDF) else MaterialTheme.colorScheme.primary).copy(alpha = if (enabled) 1f else .3f)
    IconButton(onClick, Modifier.size(48.dp).testTag(tag).semantics { contentDescription = label }, enabled = enabled) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width; val h = size.height
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), 2.dp.toPx(), StrokeCap.Round)
            when (glyph) {
                "focus" -> { drawCircle(color, w * .28f, Offset(w*.4f,h*.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())); line(.6f,.6f,.9f,.9f) }
                "follow" -> { drawCircle(color,w*.23f,center,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())); line(.5f,.05f,.5f,.2f); line(.5f,.8f,.5f,.95f); line(.05f,.5f,.2f,.5f); line(.8f,.5f,.95f,.5f); if (selected) drawCircle(color,w*.08f,center) }
                "orbit" -> { drawOval(color,Offset(w*.08f,h*.28f),androidx.compose.ui.geometry.Size(w*.84f,h*.44f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())); drawCircle(color,w*.1f,center); drawCircle(color,w*.09f,Offset(w*.82f,h*.65f)) }
                "edit" -> { line(.18f,.8f,.78f,.2f); line(.32f,.86f,.86f,.32f); line(.18f,.8f,.15f,.95f); line(.15f,.95f,.32f,.86f); line(.78f,.2f,.86f,.32f) }
                "delete" -> { line(.12f,.24f,.88f,.24f); line(.38f,.1f,.62f,.1f); line(.23f,.34f,.28f,.9f); line(.77f,.34f,.72f,.9f); line(.28f,.9f,.72f,.9f); line(.42f,.42f,.42f,.76f); line(.58f,.42f,.58f,.76f) }
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
