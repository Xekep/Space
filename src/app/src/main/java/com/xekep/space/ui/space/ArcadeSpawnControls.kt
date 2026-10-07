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
import com.xekep.space.sim.ShipClass
import com.xekep.space.storage.GameOptions

@Composable
fun ArcadeSpawnControls(game: SpaceGameState, options: GameOptions, tiltAvailable: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val guardian=game.arcade?.guardianUnlocked == true
        val entries=listOf(BodyKind.Ambient to ShipClass.Interceptor,BodyKind.Rocket to ShipClass.Interceptor,
            BodyKind.Ship to ShipClass.Interceptor)+if (guardian) listOf(BodyKind.Ship to ShipClass.Guardian) else emptyList()
        val labelWidth=(maxWidth-((entries.size+1)*48).dp).coerceIn(48.dp,80.dp)
        val compact=labelWidth < 72.dp
        val selectedLabel=stringResource(game.spawnLabelId())
        Column {
            if (compact) Text(selectedLabel,Modifier.testTag("arcade-selected-spawn"),
                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp), verticalAlignment=Alignment.CenterVertically) {
                if (compact) Box(Modifier.width(48.dp)) { FlightLoopButton(game) }
                else Column(Modifier.width(labelWidth).padding(end=4.dp), verticalArrangement=Arrangement.Center) {
                    Text(selectedLabel,Modifier.testTag("arcade-selected-spawn"),
                        style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    FlightLoopButton(game)
                }
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.65f)) {
                Row {
                    for ((kind,shipClass) in entries) {
                        val count by remember(game,kind) { derivedStateOf { game.spawnCountFor(kind) } }
                        val maximum=game.spawnLimitFor(kind)
                        val isGuardian=shipClass == ShipClass.Guardian
                        val tag=if (isGuardian) "Guardian" else kind.name
                        val active=game.spawnKind == kind && (kind != BodyKind.Ship || game.arcadeShipClass == shipClass)
                        val name=stringResource(if (isGuardian) R.string.spawn_guardian else
                            if (kind == BodyKind.Ambient) R.string.spawn_body_short else kind.labelId())
                        val countLabel=stringResource(R.string.object_count,count,maximum)
                        Surface(onClick={ if (kind == BodyKind.Ship) game.chooseArcadeShipClass(shipClass) else game.chooseSpawnKind(kind) },
                            modifier=Modifier.width(48.dp).height(64.dp).testTag("arcade-spawn-$tag")
                                .semantics { role=Role.RadioButton; selected=active; contentDescription="$name. $countLabel" },
                            shape=RoundedCornerShape(14.dp),
                            color=if (active) MaterialTheme.colorScheme.secondary.copy(alpha=.16f) else Color.Transparent) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                SpawnKindIcon(kind,Modifier.size(26.dp),shipClass)
                                Spacer(Modifier.height(2.dp))
                                Text("$count/$maximum",Modifier.testTag("arcade-count-$tag"),style=MaterialTheme.typography.labelSmall,
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
    }
}
