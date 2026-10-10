package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ArcadeCompletionDialog(game: SpaceGameState) {
    val run=game.arcade ?: return
    AlertDialog(onDismissRequest=game::openMenu,modifier=Modifier.testTag("arcade-completion"),
        title={ Text(stringResource(com.xekep.space.R.string.arcade_complete)) },
        text={ Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (run.carrier?.defeated == true) com.xekep.space.R.string.carrier_victory else com.xekep.space.R.string.arcade_complete_help))
            Text(stringResource(com.xekep.space.R.string.run_sorties,run.salvageCollected,
                if (run.convoy?.status == ConvoyStatus.Delivered) 1 else 0))
            Text(stringResource(com.xekep.space.R.string.score)+" · "+run.score.toInt())
            Text(stringResource(com.xekep.space.R.string.hull)+" · "+run.lives)
        } },
        confirmButton={ TextButton(onClick=game::continueArcadeEndless,modifier=Modifier.testTag("continue-endless")) { Text(stringResource(com.xekep.space.R.string.continue_endless)) } },
        dismissButton={ TextButton(onClick=game::openMenu) { Text(stringResource(com.xekep.space.R.string.choose_mode)) } })
}

@Composable
internal fun ArcadeGoalsDialog(game: SpaceGameState,onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("arcade-goals"),title={ Text(stringResource(com.xekep.space.R.string.arcade_goals)) },
        text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            for (goal in ArcadeGoal.entries) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(goal.label),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(goal.description),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(if (goal in game.completedGoals) com.xekep.space.R.string.goal_completed else com.xekep.space.R.string.goal_open),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            }
        } },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(com.xekep.space.R.string.close)) } })
}

@Composable
internal fun FleetRolesDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(com.xekep.space.R.string.fleet_roles)) },
        text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            for (label in listOf(com.xekep.space.R.string.salvage_help,com.xekep.space.R.string.carrier_help,com.xekep.space.R.string.role_interceptor,com.xekep.space.R.string.role_guardian,com.xekep.space.R.string.role_rocket,com.xekep.space.R.string.role_heavy)) Text(stringResource(label))
        } },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(com.xekep.space.R.string.close)) } })
}

@Composable
internal fun PrivacyDialog(onDismiss: () -> Unit) {
    val context=LocalContext.current
    val language=context.resources.configuration.locales[0].language
    val policy=remember(language) { context.assets.open("privacy/"+(language.takeIf { it in listOf("ru","fr","de","zh") } ?: "en")+".txt").bufferedReader().use { it.readText() } }
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("privacy-dialog"),title={ Text(stringResource(com.xekep.space.R.string.privacy_policy)) },
        text={ Text(policy,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) },
        confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(com.xekep.space.R.string.close)) } })
}

@Composable
internal fun GraphicsQualitySetting(options: com.xekep.space.storage.GameOptions) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(stringResource(com.xekep.space.R.string.graphics_quality),Modifier.weight(1f).padding(top=12.dp),style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={
            val all=com.xekep.space.storage.GraphicsQuality.entries
            options.graphicsQuality=all[(options.graphicsQuality.ordinal+1)%all.size]; options.save()
        },modifier=Modifier.testTag("graphics-quality")) { Text(stringResource(when (options.graphicsQuality) {
            com.xekep.space.storage.GraphicsQuality.Auto -> com.xekep.space.R.string.graphics_auto
            com.xekep.space.storage.GraphicsQuality.Full -> com.xekep.space.R.string.graphics_high
            com.xekep.space.storage.GraphicsQuality.Economy -> com.xekep.space.R.string.graphics_low
        })) }
    }
}
