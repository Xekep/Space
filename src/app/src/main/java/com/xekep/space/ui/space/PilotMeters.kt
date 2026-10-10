package com.xekep.space.ui.space

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import kotlin.math.roundToInt

@Composable
internal fun PilotMeters(game: SpaceGameState,speed: Double,target: Double,fuel: Float,enabled: Boolean,limit: Double,modifier: Modifier) {
    val context=LocalContext.current
    val fuelColor=if (fuel <= .1f) Color(0xFFFF7A6B) else if (fuel <= .25f) Color(0xFFFFD166) else Color(0xFF80FFDF)
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            PixelCanvas(Modifier.width(48.dp).height(72.dp).testTag("pilot-speed").semantics {
                contentDescription=context.getString(R.string.pilot_target_speed)
                stateDescription=context.getString(R.string.pilot_speed,speed.roundToInt())
                progressBarRangeInfo=ProgressBarRangeInfo(target.toFloat(),0f..limit.toFloat())
                if (!enabled) disabled()
                setProgress { if (enabled && it.isFinite()) { game.setPilotTargetSpeed(it.coerceIn(0f,limit.toFloat()).toDouble()); true } else false }
            }.pilotSpeedInput(game,enabled,limit,vertical=true)) {
                val width=size.width*.48f; val x=size.width*.22f; val count=12
                for (segment in 0 until count) {
                    val fraction=(segment+.5f)/count
                    val color=if (fraction > .90f) Color(0xFFFF7A6B) else if (fraction > .74f) Color(0xFFFFD166) else Color(0xFF8BD3FF)
                    val y=size.height-(segment+1)*size.height/count
                    drawRect(color.copy(alpha=if (speed/limit >= fraction) .92f else .12f),Offset(x,y),Size(width,size.height/count-2.dp.toPx()))
                    if (segment%3 == 0) drawLine(Color(0xFF91A5BF).copy(alpha=.45f),Offset(x-4.dp.toPx(),y),Offset(x-1.dp.toPx(),y),1.dp.toPx())
                }
                val y=(size.height*(1-target/limit)).toFloat().coerceIn(1.dp.toPx(),size.height-1.dp.toPx())
                drawLine(Color(0xFFD2F2FF),Offset(x-1.dp.toPx(),y),Offset(x+width+1.dp.toPx(),y),2.dp.toPx())
                drawPath(Path().apply { moveTo(size.width*.93f,y); lineTo(size.width*.77f,y-4.dp.toPx()); lineTo(size.width*.77f,y+4.dp.toPx()); close() },Color(0xFF8BD3FF))
            }
            Text(speed.roundToInt().toString(),style=MaterialTheme.typography.labelMedium,color=Color(0xFF8BD3FF),maxLines=1)
        }
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            PixelCanvas(Modifier.width(48.dp).height(72.dp).testTag("pilot-fuel").semantics {
                contentDescription=context.getString(R.string.pilot_fuel,(fuel*100).roundToInt())
                progressBarRangeInfo=ProgressBarRangeInfo(fuel,0f..1f)
            }) {
                val width=size.width*.48f; val x=size.width*.22f; val count=12
                for (segment in 0 until count) {
                    val y=size.height-(segment+1)*size.height/count
                    drawRect(fuelColor.copy(alpha=if (fuel >= (segment+.5f)/count) .90f else .12f),Offset(x,y),Size(width,size.height/count-2.dp.toPx()))
                    if (segment%3 == 0) drawLine(Color(0xFF91A5BF).copy(alpha=.45f),Offset(x-4.dp.toPx(),y),Offset(x-1.dp.toPx(),y),1.dp.toPx())
                }
            }
            Text("${(fuel*100).roundToInt()}%",style=MaterialTheme.typography.labelMedium,color=fuelColor,maxLines=1)
        }
    }
}

@Composable
internal fun PilotExitButton(game: SpaceGameState) {
    val label=androidx.compose.ui.res.stringResource(R.string.leave_pilot)
    IconButton(onClick={ game.setMotionControlEnabled(false) },modifier=Modifier.size(48.dp).testTag("pilot-exit").semantics { contentDescription=label }) {
        PixelCanvas(Modifier.size(22.dp)) {
            val color=Color(0xFF80FFDF)
            drawLine(color,Offset(size.width*.2f,size.height*.2f),Offset(size.width*.8f,size.height*.8f),2.dp.toPx())
            drawLine(color,Offset(size.width*.8f,size.height*.2f),Offset(size.width*.2f,size.height*.8f),2.dp.toPx())
        }
    }
}
