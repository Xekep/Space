package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.BodyKind
import com.xekep.space.storage.GameOptions

@Composable
fun ArcadeSpawnControls(game: SpaceGameState, options: GameOptions, tiltAvailable: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.width(80.dp).padding(end=4.dp), verticalArrangement=Arrangement.Center) {
            Text(stringResource(if (game.spawnKind == BodyKind.Ambient) R.string.spawn_body_short else game.spawnKind.labelId()),
                Modifier.testTag("arcade-selected-spawn"), style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.secondary, maxLines=2, overflow=TextOverflow.Ellipsis)
            FlightLoopButton(game)
        }
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.65f)) {
        Row {
        for (kind in listOf(BodyKind.Ambient,BodyKind.Rocket,BodyKind.Ship)) {
            val count by remember(game,kind) { derivedStateOf { game.spawnCountFor(kind) } }
            val maximum=game.spawnLimitFor(kind)
            val active=game.spawnKind == kind
            val name=stringResource(if (kind == BodyKind.Ambient) R.string.spawn_body_short else kind.labelId())
            val countLabel=stringResource(R.string.object_count,count,maximum)
            Surface(onClick={ game.chooseSpawnKind(kind) },
                modifier=Modifier.width(48.dp).height(64.dp).testTag("arcade-spawn-${kind.name}")
                    .semantics { role=Role.RadioButton; selected=active; contentDescription="$name. $countLabel" },
                shape=RoundedCornerShape(14.dp),
                color=if (active) MaterialTheme.colorScheme.secondary.copy(alpha=.16f) else Color.Transparent) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    SpawnKindIcon(kind,Modifier.size(26.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("$count/$maximum",Modifier.testTag("arcade-count-${kind.name}"),style=MaterialTheme.typography.labelSmall,
                        color=if (count >= maximum) MaterialTheme.colorScheme.error else
                            if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
        }
        Spacer(Modifier.weight(1f))
        MotionControlButton(options,tiltAvailable,"arcade-motion-control")
    }
}
