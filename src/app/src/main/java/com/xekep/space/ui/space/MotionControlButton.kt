package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.xekep.space.storage.GameOptions
import com.xekep.space.input.FlightControlMode
import com.xekep.space.R

@Composable
fun MotionControlButton(game: SpaceGameState, options: GameOptions, tiltAvailable: Boolean, tag: String) {
    val joystick=options.flightControl == FlightControlMode.Joystick
    val available=joystick || tiltAvailable
    val active=game.motionSteeringEnabled && available
    val accent=if (active) Color(0xFF80FFDF) else MaterialTheme.colorScheme.primary
    val label=stringResource(if (joystick) R.string.virtual_joystick else R.string.motion_control)
    IconToggleButton(checked=active,onCheckedChange=game::setMotionControlEnabled,enabled=available,
        modifier=Modifier.size(48.dp).testTag(tag).background(if (active) accent.copy(alpha=.16f) else Color.Transparent,spaceShape(99.dp))
            .semantics { contentDescription=label }) {
        PixelCanvas(Modifier.size(24.dp)) {
            val color=accent.copy(alpha=if (available) 1f else .3f)
            val stroke=Stroke(1.7.dp.toPx())
            if (joystick) {
                drawCircle(color,size.width*.38f,center,style=stroke)
                val knob=center+Offset(size.width*.14f,-size.height*.14f)
                drawLine(color,center,knob,2.dp.toPx())
                drawCircle(color,size.width*.12f,knob)
            } else {
            drawRoundRect(color,Offset(size.width*.32f,size.height*.14f),Size(size.width*.36f,size.height*.72f),CornerRadius(2.dp.toPx()),style=stroke)
            drawCircle(color,1.dp.toPx(),Offset(size.width*.5f,size.height*.72f))
            drawArc(color,120f,120f,false,Offset.Zero,size,style=stroke)
            drawArc(color,-60f,120f,false,Offset.Zero,size,style=stroke)
            drawLine(color,Offset(size.width*.09f,size.height*.23f),Offset(size.width*.05f,size.height*.43f),1.7.dp.toPx())
            drawLine(color,Offset(size.width*.95f,size.height*.57f),Offset(size.width*.91f,size.height*.77f),1.7.dp.toPx())
            }
        }
    }
}
