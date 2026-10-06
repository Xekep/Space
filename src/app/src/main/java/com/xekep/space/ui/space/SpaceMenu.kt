package com.xekep.space.ui.space

import com.xekep.space.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.storage.SandboxSlotSummary
import com.xekep.space.storage.GameOptions
import kotlin.math.roundToInt

private data class MenuConfirmation(val title: String, val message: String, val action: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceMenu(
    game: SpaceGameState,
    saveSummaries: List<SandboxSlotSummary?>,
    notice: String?,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    options: GameOptions? = null,
    motionAvailable: Boolean = true,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val compactMenu = configuration.screenHeightDp < 420 || (configuration.screenWidthDp < 360 && configuration.fontScale > 1.15f)
    val compactPresets = configuration.screenWidthDp < 360 || configuration.fontScale > 1.15f
    var selectedMode by rememberSaveable(game.mode) { mutableStateOf(game.mode) }
    var difficulty by rememberSaveable { mutableStateOf(game.arcade?.difficulty ?: ArcadeDifficulty.Normal) }
    var preset by rememberSaveable { mutableStateOf(game.sandbox?.preset?.takeUnless { it == SandboxPresetKind.Empty } ?: SandboxPresetKind.SolarSystem) }
    var collisions by rememberSaveable { mutableStateOf(game.sandbox?.collisionsEnabled ?: false) }
    var confirmation by remember { mutableStateOf<MenuConfirmation?>(null) }
    var naming by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf(game.sandbox?.name.orEmpty()) }
    val hasSelectedSession = if (selectedMode == AppMode.Arcade) game.arcade != null && game.arcade!!.lives > 0 else game.sandbox != null
    val accent = if (selectedMode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val startNew: () -> Unit = {
        if (selectedMode == AppMode.Arcade) game.startArcade(difficulty)
        else { game.startSandbox(preset, context.getString(preset.labelId())); game.setCollisions(collisions) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xF202040B)).safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0B1425), contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SPACE", style = MaterialTheme.typography.labelLarge, letterSpacing = 5.sp, color = accent)
                        if (!compactMenu) Text(if (game.hasSession) context.getString(R.string.take_breath) else context.getString(R.string.universe_awaits),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (!compactMenu) Text(if (game.hasSession) context.getString(R.string.simulation_paused) else context.getString(R.string.two_modes),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!compactMenu) OrbitGlyph(Modifier.size(58.dp), accent, selectedMode == AppMode.Arcade)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("menu-content")
                    .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(AppMode.Arcade, selectedMode == AppMode.Arcade) { selectedMode = AppMode.Arcade }
                    ModeCard(AppMode.Sandbox, selectedMode == AppMode.Sandbox) { selectedMode = AppMode.Sandbox }
                    if (selectedMode == AppMode.Arcade) {
                        MenuLabel(context.getString(R.string.next_run))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArcadeDifficulty.entries.forEach {
                                FilterChip(selected = difficulty == it, onClick = { difficulty = it },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.16f), selectedLabelColor = accent),
                                    label = { Text(context.getString(it.labelId()), maxLines = 1) }, modifier = Modifier.widthIn(min = 72.dp))
                            }
                        }
                        Text(context.getString(R.string.difficulty_details, difficulty.lives, difficulty.scoreFactor.toString()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF17223A),
                            contentColor = MaterialTheme.colorScheme.onSurface) {
                            FlowRow(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactHudValue(context.getString(R.string.personal_best, context.getString(difficulty.labelId())), game.recordFor(difficulty).roundToInt().toString())
                                game.arcade?.let { CompactHudValue(context.getString(R.string.last_run), it.score.roundToInt().toString()) }
                            }
                        }
                        options?.let { LargeVehicleSwitch(it) }
                    } else {
                        MenuLabel(if (game.sandbox == null) context.getString(R.string.starting_scene) else context.getString(R.string.new_universe))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("preset-row"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(SandboxPresetKind.SolarSystem, SandboxPresetKind.BinaryStars, SandboxPresetKind.ClassicOrbits).forEach {
                                val label = when (it) { SandboxPresetKind.SolarSystem -> context.getString(R.string.solar); SandboxPresetKind.BinaryStars -> context.getString(R.string.binary); SandboxPresetKind.ClassicOrbits -> context.getString(R.string.classic_orbits); SandboxPresetKind.Empty -> context.getString(R.string.empty) }
                                val active = preset == it
                                Surface(onClick = { preset = it }, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                                    .testTag("preset-${it.name}").semantics { selected = active; role = Role.RadioButton },
                                    shape = RoundedCornerShape(12.dp), color = if (active) accent.copy(alpha = .16f) else Color.Transparent,
                                    contentColor = if (active) accent else MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(1.dp, if (active) accent else Color.White.copy(alpha = .16f))) {
                                    Box(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(label, maxLines = 2, textAlign = TextAlign.Center,
                                            style = if (compactPresets) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        if (hasSelectedSession) {
                            Button(onClick = {
                                confirmation = MenuConfirmation(context.getString(R.string.new_universe_question), context.getString(R.string.new_universe_confirmation), startNew)
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("new-session"),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF041018))) {
                                Text(context.getString(R.string.create_preset, context.getString(preset.labelId())), fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(onClick = startNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu-primary"),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF041018))) {
                                Text(context.getString(R.string.create_universe), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(context.getString(preset.descriptionId()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(context.getString(R.string.merge_impact), style = MaterialTheme.typography.titleSmall)
                            }
                            Switch(checked = collisions, onCheckedChange = {
                                collisions = it
                                game.setCollisions(it)
                            }, modifier = Modifier.testTag("sandbox-collisions"))
                        }
                        options?.let { LargeVehicleSwitch(it) }
                        if (game.sandbox != null) {
                            MenuLabel(context.getString(R.string.current_universe))
                            Text("${game.sandbox!!.name}${if (game.dirty) context.getString(R.string.unsaved_changes) else ""}", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { sceneName = game.sandbox!!.name; naming = true }) { Text(context.getString(R.string.rename)) }
                                TextButton(onClick = onExport) { Text(context.getString(R.string.export_scene)) }
                                TextButton(onClick = {
                                    confirmation = MenuConfirmation(context.getString(R.string.import_question), context.getString(R.string.import_confirmation), onImport)
                                }) { Text(context.getString(R.string.import_scene)) }
                            }
                            MenuLabel(context.getString(R.string.saved_universes))
                            (1..3).forEach { slot ->
                                val summary = saveSummaries.getOrNull(slot - 1)
                                SandboxSlotRow(slot, summary,
                                    onSave = {
                                        if (summary == null) onSaveSlot(slot)
                                        else confirmation = MenuConfirmation(context.getString(R.string.replace_slot_question, slot), context.getString(R.string.replace_slot_confirmation)) { onSaveSlot(slot) }
                                    },
                                    onLoad = {
                                        confirmation = MenuConfirmation(context.getString(R.string.load_slot_question, slot), context.getString(R.string.load_slot_confirmation)) { onLoadSlot(slot) }
                                    })
                            }
                        } else if (saveSummaries.any { it != null }) {
                            MenuLabel(context.getString(R.string.saved_universes))
                            saveSummaries.filterNotNull().forEach { summary ->
                                OutlinedButton(onClick = { onLoadSlot(summary.slot) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(context.getString(R.string.load_slot_bodies, summary.slot, summary.bodyCount))
                                }
                            }
                        }
                        if (game.sandbox == null) TextButton(onClick = onImport) { Text(context.getString(R.string.import_scene)) }
                    }
                    if (hasSelectedSession && selectedMode == AppMode.Arcade) {
                        OutlinedButton(onClick = {
                            confirmation = MenuConfirmation(context.getString(R.string.new_run_question), context.getString(R.string.new_run_confirmation), startNew)
                        }, modifier = Modifier.fillMaxWidth().testTag("new-session")) {
                            Text(context.getString(R.string.new_arcade_run))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    if (notice != null) Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    game.feedback?.let { Text(context.getString(it), style = MaterialTheme.typography.bodySmall, color = accent) }
                    options?.let {
                        MenuLabel(context.getString(R.string.feedback_options))
                        OptionSwitch(context.getString(R.string.motion_control), it.motionControl, Modifier.testTag("motion-control-switch"), enabled = motionAvailable) { enabled -> it.motionControl = enabled; it.save() }
                        if (!motionAvailable) Text(context.getString(R.string.shake_unavailable), style = MaterialTheme.typography.bodySmall)
                        OptionSwitch(context.getString(R.string.music), it.music, Modifier.testTag("ambient-music-switch")) { enabled -> it.music = enabled; it.save() }
                        OptionSwitch(context.getString(R.string.sound), it.sound) { enabled -> it.sound = enabled; it.save() }
                        OptionSwitch(context.getString(R.string.vibration), it.vibration) { enabled -> it.vibration = enabled; it.save() }
                        OptionSwitch(context.getString(R.string.reduced_flashes), it.reducedFlashes) { enabled -> it.reducedFlashes = enabled; it.save() }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hasSelectedSession || selectedMode == AppMode.Arcade) {
                        Button(onClick = { if (hasSelectedSession) game.enterMode(selectedMode) else startNew() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu-primary"),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF041018))) {
                            Text(if (hasSelectedSession) context.getString(R.string.continue_mode, context.getString(selectedMode.labelId())) else context.getString(R.string.launch_arcade),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = {
                        val action = { game.beginTutorial(selectedMode, context.getString(R.string.empty_space)) }
                        if (hasSelectedSession) confirmation = MenuConfirmation(context.getString(R.string.practice_question), context.getString(R.string.practice_confirmation), action)
                        else action()
                    }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("practice-controls")) { Text(context.getString(R.string.practice_controls)) }
                }
            }
        }
    }
    confirmation?.let { pending ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(pending.title) }, text = { Text(pending.message) },
            confirmButton = { TextButton(onClick = { confirmation = null; pending.action() }, modifier = Modifier.testTag("confirm-action")) { Text(context.getString(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(context.getString(R.string.keep_current)) } })
    }
    if (naming) AlertDialog(onDismissRequest = { naming = false }, title = { Text(context.getString(R.string.name_universe)) },
        text = { OutlinedTextField(sceneName, { sceneName = it.take(40) }, singleLine = true, label = { Text(context.getString(R.string.name)) }) },
        confirmButton = { TextButton(onClick = { game.renameSandbox(sceneName); naming = false }, enabled = sceneName.isNotBlank()) { Text(context.getString(R.string.save)) } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text(context.getString(R.string.cancel)) } })
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange, modifier = modifier, enabled = enabled)
    }
}

@Composable
private fun LargeVehicleSwitch(settings: GameOptions) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.large_vehicle_icons), Modifier.weight(1f), maxLines=1, style=MaterialTheme.typography.titleSmall)
        Switch(checked=settings.largeVehicleIcons,onCheckedChange={ settings.largeVehicleIcons=it; settings.save() },
            modifier=Modifier.testTag("large-vehicle-icons").semantics {
                contentDescription=context.getString(R.string.large_vehicle_icons_description)
            })
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ModeCard(mode: AppMode, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val accent = if (mode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("mode-${mode.name}")
        .semantics { selected = isSelected; role = Role.RadioButton }, shape = RoundedCornerShape(20.dp),
        color = if (isSelected) accent.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.025f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (isSelected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f))) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbitGlyph(Modifier.size(46.dp), accent, mode == AppMode.Arcade)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(context.getString(mode.labelId()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (mode == AppMode.Arcade) context.getString(R.string.arcade_description) else context.getString(R.string.sandbox_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (isSelected) "●" else "○", color = accent, fontSize = 18.sp)
        }
    }
}

@Composable
private fun OrbitGlyph(modifier: Modifier, color: Color, arcade: Boolean) {
    Canvas(modifier) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)), size.minDimension / 2)
        drawOval(color.copy(alpha = 0.5f), topLeft = Offset(size.width * 0.06f, size.height * 0.28f),
            size = Size(size.width * 0.88f, size.height * 0.44f), style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color, size.minDimension * 0.12f)
        drawCircle(if (arcade) Color(0xFFFF8A5B) else Color(0xFFB8F2B3), size.minDimension * 0.06f,
            center = Offset(size.width * 0.86f, size.height * 0.37f))
    }
}
