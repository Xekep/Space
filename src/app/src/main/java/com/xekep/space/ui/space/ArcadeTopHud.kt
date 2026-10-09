package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import kotlin.math.roundToInt

@Composable
fun ArcadeTopHud(game: SpaceGameState, compactPilot: Boolean = false) {
    val session=game.arcade ?: return
    val menu=stringResource(R.string.menu)
    val core=stringResource(R.string.find_core)
    val accent=MaterialTheme.colorScheme.secondary
    Surface(Modifier.retroFrame().fillMaxWidth().testTag("arcade-top-hud").blockWorldTouches(),shape=spaceShape(20.dp),
        color=MaterialTheme.colorScheme.surface.copy(alpha=.88f)) {
        Row(Modifier.padding(horizontal=4.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=game::openMenu,modifier=Modifier.size(48.dp).testTag("open-menu")
                .semantics { contentDescription=menu }) {
                PixelCanvas(Modifier.size(22.dp)) {
                    for (y in listOf(.25f,.5f,.75f)) drawLine(accent,Offset(size.width*.1f,size.height*y),
                        Offset(size.width*.9f,size.height*y),2.dp.toPx(),StrokeCap.Round)
                }
            }
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                val stats=listOf(R.string.score to session.score.roundToInt().toString(), R.string.hull to session.lives.toString(),
                    R.string.wave to session.wave.toString(), R.string.combo to "x${"%.1f".format(session.combo)}")
                for ((label,value) in stats) Column(Modifier.weight(1f).padding(horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(stringResource(label),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,
                        color=MaterialTheme.colorScheme.onSurface)
                }
            }
            if (compactPilot) PilotExitButton(game) else IconButton(onClick=game::fitCamera,modifier=Modifier.size(48.dp).testTag("find-core")
                .semantics { contentDescription=core }) {
                PixelCanvas(Modifier.size(22.dp)) {
                    drawCircle(accent,size.minDimension*.27f,style=Stroke(1.5.dp.toPx()))
                    drawCircle(accent,size.minDimension*.08f)
                    val w=size.width; val h=size.height
                    for (range in listOf(.05f to .18f,.82f to .95f)) {
                        drawLine(accent,Offset(w*range.first,h*.5f),Offset(w*range.second,h*.5f),1.5.dp.toPx(),StrokeCap.Round)
                        drawLine(accent,Offset(w*.5f,h*range.first),Offset(w*.5f,h*range.second),1.5.dp.toPx(),StrokeCap.Round)
                    }
                }
            }
        }
    }
}
