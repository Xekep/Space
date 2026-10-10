package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xekep.space.R

@Composable
internal fun ArcadeUpgradeDialog(game: SpaceGameState) {
    val offer=game.arcade?.upgradeOffer ?: return
    Dialog(onDismissRequest=game::openMenu) {
        Surface(shape=spaceShape(24.dp),color=MaterialTheme.colorScheme.surface,
            modifier=Modifier.fillMaxWidth().heightIn(max=560.dp).retroFrame().testTag("arcade-upgrade-dialog")) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if (offer.salvageBonus) stringResource(R.string.salvage_upgrade_title) else if (offer.convoyBonus) stringResource(R.string.convoy_upgrade_title) else stringResource(R.string.upgrade_title,offer.wave),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(if (offer.salvageBonus) R.string.salvage_upgrade_help else if (offer.convoyBonus) R.string.convoy_upgrade_description else if (offer.wave == 3) R.string.guardian_unlocked else R.string.upgrade_description),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                for (upgrade in offer.choices) {
                    Surface(onClick={ game.chooseArcadeUpgrade(upgrade) },shape=spaceShape(14.dp),
                        color=MaterialTheme.colorScheme.secondary.copy(alpha=.10f),
                        modifier=Modifier.fillMaxWidth().retroFrame(MaterialTheme.colorScheme.secondary).testTag("arcade-upgrade-${upgrade.name}")) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(upgrade.labelId()),style=MaterialTheme.typography.titleSmall,
                                color=MaterialTheme.colorScheme.secondary)
                            Text(stringResource(upgrade.descriptionId()),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
