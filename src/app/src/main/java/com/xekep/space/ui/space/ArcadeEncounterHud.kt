package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xekep.space.R

@Composable
internal fun ArcadeEncounterHud(game: SpaceGameState) {
    val run=game.arcade ?: return
    if (run.practice) return
    if (!run.resting && run.character != WaveCharacter.Giant) {
        val label=if (run.planetId != null && run.wave == 11 && run.wavePhase.seconds < 3) R.string.planet_arrived else when (run.character) {
            WaveCharacter.Approach -> R.string.wave_approach
            WaveCharacter.Swarm -> R.string.wave_swarm
            WaveCharacter.Siege -> R.string.wave_siege
            WaveCharacter.Pincer -> R.string.wave_pincer
            WaveCharacter.Recovery -> R.string.wave_recovery
            WaveCharacter.Escort -> R.string.wave_escort
            WaveCharacter.Giant -> R.string.challenge_giant
        }
        Text(stringResource(label),Modifier.fillMaxWidth().testTag("wave-character"),textAlign=TextAlign.Center,
            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    run.convoy?.let { convoy ->
        if (convoy.status == ConvoyStatus.Approaching) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            val focus=stringResource(R.string.find_convoy)
            TextButton(onClick=game::focusConvoy,modifier=Modifier.testTag("find-convoy").semantics { contentDescription=focus },
                contentPadding=PaddingValues(horizontal=12.dp,vertical=2.dp)) {
                Text(stringResource(R.string.convoy_hull,convoy.hull),style=MaterialTheme.typography.labelMedium,color=Color(0xFF82EAC8))
                Spacer(Modifier.width(8.dp))
                PixelCanvas(Modifier.size(16.dp)) {
                    val color=Color(0xFF82EAC8)
                    drawCircle(color,size.minDimension*.30f,style=Stroke(1.dp.toPx()))
                    drawCircle(color,size.minDimension*.08f)
                    drawLine(color,Offset(0f,size.height/2),Offset(size.width*.15f,size.height/2),1.dp.toPx())
                    drawLine(color,Offset(size.width*.85f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx())
                }
            }
        } else if (convoy.elapsed < 4.0) Text(stringResource(if (convoy.status == ConvoyStatus.Delivered) R.string.convoy_delivered else R.string.convoy_lost),
            Modifier.fillMaxWidth().testTag("convoy-result"),textAlign=TextAlign.Center,
            style=MaterialTheme.typography.labelMedium,color=Color(0xFF82EAC8))
    }
}
