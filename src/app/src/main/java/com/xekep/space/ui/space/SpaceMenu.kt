package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.storage.GameOptions
import com.xekep.space.storage.SandboxSlotSummary
import com.xekep.space.ui.LanguageMenuButton
import kotlin.math.*

private data class MenuConfirmation(val title: String, val message: String, val action: () -> Unit)
private enum class MenuPanel { Settings, Worlds }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpaceMenu(
    game: SpaceGameState,
    saveSummaries: List<SandboxSlotSummary?>,
    notice: String?,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    options: GameOptions? = null,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    orbitPhase: State<Float>? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val compactPresets = configuration.screenWidthDp < 360 || configuration.fontScale > 1.15f
    var selectedMode by rememberSaveable(game.mode) { mutableStateOf(game.mode) }
    var difficulty by rememberSaveable { mutableStateOf(game.arcade?.difficulty ?: ArcadeDifficulty.Normal) }
    var preset by rememberSaveable { mutableStateOf(game.sandbox?.preset?.takeUnless { it == SandboxPresetKind.Empty } ?: SandboxPresetKind.SolarSystem) }
    var panel by rememberSaveable { mutableStateOf<MenuPanel?>(null) }
    var info by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<MenuConfirmation?>(null) }
    var naming by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf(game.sandbox?.name.orEmpty()) }
    val hasSelectedSession = if (selectedMode == AppMode.Arcade) game.arcade?.let { it.lives > 0 } == true else game.sandbox != null
    val accent = if (selectedMode == AppMode.Arcade) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val startNew: () -> Unit = {
        if (selectedMode == AppMode.Arcade) game.startArcade(difficulty)
        else {
            val collisions=game.sandbox?.collisionsEnabled ?: false
            val mode=game.sandbox?.collisionMode ?: com.xekep.space.sim.SandboxCollisionMode.Merge
            game.startSandbox(preset,context.getString(preset.labelId()))
            game.setCollisions(collisions); game.setCollisionMode(mode)
        }
    }
    val newSession: () -> Unit = {
        confirmation=if (selectedMode == AppMode.Arcade)
            MenuConfirmation(context.getString(R.string.new_run_question),context.getString(R.string.new_run_confirmation),startNew)
        else MenuConfirmation(context.getString(R.string.new_universe_question),context.getString(R.string.new_universe_confirmation),startNew)
    }
    val practice: () -> Unit = {
        panel=null
        val action={ game.beginTutorial(selectedMode,context.getString(R.string.empty_space)) }
        if (hasSelectedSession) confirmation=MenuConfirmation(context.getString(R.string.practice_question),context.getString(R.string.practice_confirmation),action)
        else action()
    }

    val header: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    OrbitGlyph(Modifier.size(32.dp),accent,orbitPhase)
                    Text("SPACE",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleMedium,
                        letterSpacing=3.sp,color=accent)
                    IconButton(onClick={ panel=MenuPanel.Settings },modifier=Modifier.testTag("open-settings")
                        .semantics { contentDescription=context.getString(R.string.settings) }) {
                        SettingsGlyph(Modifier.size(22.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
    }
    val worldsActions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ panel=MenuPanel.Worlds },modifier=Modifier.weight(1f).testTag("open-worlds")) { Text(context.getString(R.string.worlds)) }
            InfoButton { info=true }
        }
    }
    val choices: @Composable (Boolean) -> Unit = { wide ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppMode.entries.forEach { mode ->
                        ModeTab(mode,selectedMode == mode,accent,Modifier.weight(1f)) { selectedMode=mode }
                    }
                }
                if (selectedMode == AppMode.Arcade) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        ArcadeDifficulty.entries.forEach { level ->
                            FilterChip(difficulty == level,{ difficulty=level },modifier=Modifier.weight(1f).testTag("difficulty-${level.name}").retroFrame(if (difficulty == level) accent else MaterialTheme.colorScheme.outline),
                                shape=spaceShape(8.dp),colors=FilterChipDefaults.filterChipColors(
                                    selectedContainerColor=accent.copy(alpha=.14f),selectedLabelColor=accent),
                                label={ Text(context.getString(level.labelId()),maxLines=1,overflow=TextOverflow.Ellipsis) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(context.getString(R.string.menu_record,game.recordFor(difficulty).roundToInt()),Modifier.weight(1f),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        InfoButton { info=true }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("preset-row"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(SandboxPresetKind.SolarSystem,SandboxPresetKind.BinaryStars,SandboxPresetKind.ClassicOrbits).forEach { choice ->
                            val label=when (choice) {
                                SandboxPresetKind.RandomSystems -> R.string.random_systems_short
                                SandboxPresetKind.SystemGalaxy -> R.string.system_galaxy_short
                                SandboxPresetKind.SolarSystem -> R.string.solar
                                SandboxPresetKind.BinaryStars -> R.string.binary
                                SandboxPresetKind.ClassicOrbits -> R.string.classic_orbits
                                SandboxPresetKind.Empty -> R.string.empty
                            }
                            val active=preset == choice
                            Surface(onClick={ preset=choice },modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=48.dp)
                                .testTag("preset-${choice.name}").semantics { selected=active; role=Role.RadioButton }.retroFrame(if (active) accent else MaterialTheme.colorScheme.outline),
                                shape=spaceShape(8.dp),color=if (active) accent.copy(alpha=.14f) else Color(0xFF141E30),
                                contentColor=if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant) {
                                Box(Modifier.padding(horizontal=4.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                                    Text(context.getString(label),maxLines=2,textAlign=TextAlign.Center,
                                        style=if (compactPresets) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(SandboxPresetKind.RandomSystems,SandboxPresetKind.SystemGalaxy).forEach { choice ->
                            val active=preset == choice
                            val label=if (choice == SandboxPresetKind.SystemGalaxy) R.string.system_galaxy_short else R.string.random_systems_short
                            Surface(onClick={ preset=choice },modifier=Modifier.weight(1f).heightIn(min=48.dp)
                                .testTag("preset-${choice.name}").semantics { selected=active; role=Role.RadioButton }.retroFrame(if (active) accent else MaterialTheme.colorScheme.outline),
                                shape=spaceShape(8.dp),color=if (active) accent.copy(alpha=.14f) else Color(0xFF141E30),
                                contentColor=if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant) {
                                Box(Modifier.padding(horizontal=6.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                                    Text(context.getString(label),maxLines=2,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    if (!wide) worldsActions()
                }
    }
    val actions: @Composable (Boolean) -> Unit = { wide ->
                if (wide && selectedMode == AppMode.Sandbox) worldsActions()
                if (notice != null) Text(notice,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
                    style=MaterialTheme.typography.bodySmall,color=accent)
                Button(onClick={ if (hasSelectedSession) game.enterMode(selectedMode) else startNew() },
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("menu-primary").retroFrame(accent),shape=spaceShape(10.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color(0xFF041018))) {
                    Text(context.getString(if (hasSelectedSession) R.string.resume_game else if (selectedMode == AppMode.Arcade)
                        R.string.start_game else R.string.create_universe),fontWeight=FontWeight.SemiBold)
                }
                if (hasSelectedSession) TextButton(onClick=newSession,modifier=Modifier.fillMaxWidth().testTag("new-session")) {
                    Text(context.getString(if (selectedMode == AppMode.Arcade) R.string.new_game else R.string.new_world))
                }
                LanguageMenuButton(Modifier.fillMaxWidth().padding(bottom=8.dp))
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(if (orbitPhase != null) Color(0x2802040B) else Color(0xEA02040B)).safeDrawingPadding().padding(16.dp),contentAlignment=Alignment.Center) {
        val wide=maxHeight < 380.dp && maxWidth >= 500.dp
        Surface(Modifier.widthIn(max=if (wide) 680.dp else 420.dp).fillMaxWidth().retroFrame(),shape=spaceShape(16.dp),color=Color(0xF20B1425)) {
            if (wide) Row(Modifier.padding(horizontal=20.dp).verticalScroll(rememberScrollState()).testTag("menu-content"),
                horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1.3f).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { header(); choices(true) }
                Column(Modifier.weight(1f).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { actions(true) }
            } else Column(Modifier.padding(horizontal=20.dp).verticalScroll(rememberScrollState()).testTag("menu-content"),
                verticalArrangement=Arrangement.spacedBy(12.dp)) { header(); choices(false); actions(false) }
        }
    }

    panel?.let { openPanel ->
        ModalBottomSheet(onDismissRequest={ panel=null },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=Color(0xFF0B1425),contentColor=MaterialTheme.colorScheme.onSurface,shape=spaceShape(topStart=16.dp,topEnd=16.dp)) {
            Column(Modifier.widthIn(max=520.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal=20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(context.getString(if (openPanel == MenuPanel.Settings) R.string.settings else R.string.worlds),
                        Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={ panel=null },modifier=Modifier.testTag("close-menu-panel")) { Text(context.getString(R.string.close)) }
                }
                Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=24.dp)
                    .testTag(if (openPanel == MenuPanel.Settings) "settings-panel" else "worlds-panel"),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if (openPanel == MenuPanel.Settings) {
                    options?.let { settings ->
                        FlightControlSettings(settings)
                        OptionSwitch(context.getString(R.string.music),settings.music,"ambient-music-switch") { settings.music=it; settings.save() }
                        OptionSwitch(context.getString(R.string.sound),settings.sound,"sound-switch") { settings.sound=it; settings.save() }
                        OptionSwitch(context.getString(R.string.vibration),settings.vibration,"vibration-switch") { settings.vibration=it; settings.save() }
                        OptionSwitch(context.getString(R.string.retro_console),settings.retroConsole,"retro-console-switch") {
                            settings.retroConsole=it; settings.save()
                        }
                        OptionSwitch(context.getString(R.string.reduced_flashes),settings.reducedFlashes,"reduced-flashes-switch") { settings.reducedFlashes=it; settings.save() }
                        OptionSwitch(context.getString(R.string.large_vehicle_icons),settings.largeVehicleIcons,"large-vehicle-icons",
                            context.getString(R.string.large_vehicle_icons_description)) { settings.largeVehicleIcons=it; settings.save() }
                    }
                    HorizontalDivider(color=Color.White.copy(alpha=.08f))
                    TextButton(onClick=practice,modifier=Modifier.fillMaxWidth().testTag("practice-controls")) {
                        Text(context.getString(R.string.how_to_play))
                    }
                } else {
                    game.sandbox?.let { scene ->
                        Text(scene.name + if (game.dirty) context.getString(R.string.unsaved_changes) else "",
                            style=MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={ sceneName=scene.name; naming=true },modifier=Modifier.testTag("rename-world")) { Text(context.getString(R.string.rename)) }
                            TextButton(onClick={ panel=null; onExport() },modifier=Modifier.testTag("export-world")) { Text(context.getString(R.string.export_scene)) }
                        }
                    }
                    TextButton(onClick={
                        if (game.sandbox == null) { panel=null; onImport() }
                        else confirmation=MenuConfirmation(context.getString(R.string.import_question),context.getString(R.string.import_confirmation)) { panel=null; onImport() }
                    },modifier=Modifier.testTag("import-world")) { Text(context.getString(R.string.import_scene)) }
                    if (notice != null) Text(notice,style=MaterialTheme.typography.bodySmall,color=accent)
                    (1..3).forEach { slot ->
                        SandboxSlotRow(slot,saveSummaries.getOrNull(slot-1),
                            onSave={
                                if (saveSummaries.getOrNull(slot-1) == null) onSaveSlot(slot)
                                else confirmation=MenuConfirmation(context.getString(R.string.replace_slot_question,slot),context.getString(R.string.replace_slot_confirmation)) { onSaveSlot(slot) }
                            },onLoad={
                                if (game.sandbox == null) onLoadSlot(slot)
                                else confirmation=MenuConfirmation(context.getString(R.string.load_slot_question,slot),context.getString(R.string.load_slot_confirmation)) { onLoadSlot(slot) }
                            },canSave=game.sandbox != null)
                    }
                }
            }
            }
        }
    }
    if (info) AlertDialog(onDismissRequest={ info=false },
        title={ Text(context.getString(if (selectedMode == AppMode.Arcade) difficulty.labelId() else preset.labelId())) },
        text={ Text(if (selectedMode == AppMode.Arcade) context.getString(R.string.difficulty_details,difficulty.lives,difficulty.scoreFactor.toString())
            else context.getString(preset.descriptionId())) },confirmButton={ TextButton(onClick={ info=false }) { Text(context.getString(R.string.close)) } })
    confirmation?.let { pending ->
        AlertDialog(onDismissRequest={ confirmation=null },title={ Text(pending.title) },text={ Text(pending.message) },
            confirmButton={ TextButton(onClick={ confirmation=null; pending.action() },modifier=Modifier.testTag("confirm-action")) { Text(context.getString(R.string.confirm)) } },
            dismissButton={ TextButton(onClick={ confirmation=null }) { Text(context.getString(R.string.keep_current)) } })
    }
    if (naming) AlertDialog(onDismissRequest={ naming=false },title={ Text(context.getString(R.string.name_universe)) },
        text={ OutlinedTextField(sceneName,{ sceneName=it.take(40) },singleLine=true,label={ Text(context.getString(R.string.name)) },modifier=Modifier.testTag("world-name")) },
        confirmButton={ TextButton(onClick={ game.renameSandbox(sceneName); naming=false },enabled=sceneName.isNotBlank(),modifier=Modifier.testTag("save-world-name")) { Text(context.getString(R.string.save)) } },
        dismissButton={ TextButton(onClick={ naming=false }) { Text(context.getString(R.string.cancel)) } })
}

@Composable
private fun OptionSwitch(label: String,checked: Boolean,tag: String,description: String=label,onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f).padding(end=12.dp),style=MaterialTheme.typography.bodyMedium)
        SpaceSwitch(checked,onChange,Modifier.testTag(tag).semantics { contentDescription=description })
    }
}

@Composable
private fun ModeTab(mode: AppMode,active: Boolean,accent: Color,modifier: Modifier,onClick: () -> Unit) {
    val context=LocalContext.current
    Surface(onClick=onClick,modifier=modifier.testTag("mode-${mode.name}").semantics { selected=active; role=Role.Tab },color=Color.Transparent) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                Text(context.getString(mode.labelId()),style=MaterialTheme.typography.titleSmall,
                    color=if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness=2.dp,color=if (active) accent else Color.White.copy(alpha=.06f))
        }
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    val context=LocalContext.current
    IconButton(onClick,Modifier.testTag("menu-info").semantics { contentDescription=context.getString(R.string.details) }) {
        Text("ⓘ",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OrbitGlyph(modifier: Modifier,color: Color,phase: State<Float>?) {
    PixelCanvas(modifier) {
        val angle=phase?.value ?: .4f
        drawOval(color.copy(alpha=.4f),Offset(size.width*.06f,size.height*.28f),Size(size.width*.88f,size.height*.44f),style=Stroke(1.dp.toPx()))
        drawCircle(color,size.minDimension*.12f)
        drawCircle(color.copy(alpha=.8f),size.minDimension*.06f,Offset(center.x+cos(angle)*size.width*.44f,center.y+sin(angle)*size.height*.22f))
    }
}

@Composable
private fun SettingsGlyph(modifier: Modifier,color: Color) {
    PixelCanvas(modifier) {
        val r=size.minDimension*.32f
        drawCircle(color,r,style=Stroke(1.5.dp.toPx()))
        drawCircle(color,r*.38f,style=Stroke(1.5.dp.toPx()))
        repeat(8) { n ->
            val angle=n*PI/4
            val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
            drawLine(color,center+direction*r,center+direction*(r*1.35f),2.dp.toPx())
        }
    }
}
