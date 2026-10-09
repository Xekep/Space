package com.xekep.space.ui.space

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.pilotSpeedLimit

internal fun thrustFraction(y: Float, center: Float, radius: Float): Float =
    if (!y.isFinite() || !center.isFinite() || !radius.isFinite() || radius <= 0) 0f
    else ((center+radius-y)/(2*radius)).coerceIn(0f,1f)

/** A latched vertical throttle. Release retains thrust; the right stick stays independent. */
@Composable
fun ThrustJoystick(game: SpaceGameState, value: Float, enabled: Boolean) {
    val limit=game.bodies.firstOrNull { it.id == game.controlledVehicleId }?.pilotSpeedLimit() ?: 900.0
    val label=stringResource(R.string.thrust_joystick)
    PixelCanvas(Modifier.size(96.dp).testTag("thrust-joystick").semantics {
        contentDescription=label
        progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
        if (!enabled) disabled()
        setProgress { if (enabled && it.isFinite()) { game.setPilotTargetSpeed(it.coerceIn(0f,1f)*limit); true } else false }
    }.pointerInput(game,enabled,game.controlledVehicleId) {
        if (!enabled) return@pointerInput
        val radius=32.dp.toPx()
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false); down.consume()
            fun move(point: Offset) { game.setPilotTargetSpeed(thrustFraction(point.y,size.height/2f,radius)*limit) }
            move(down.position)
            do {
                val change=awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                move(change.position)
            } while (true)
        }
    }) {
        val color=Color(0xFF80FFDF).copy(alpha=if (enabled) 1f else .3f)
        val radius=32.dp.toPx()
        drawCircle(color.copy(alpha=.06f),38.dp.toPx(),center)
        drawCircle(color.copy(alpha=.4f),38.dp.toPx(),center,style=Stroke(1.5.dp.toPx()))
        drawLine(color.copy(alpha=.22f),center-Offset(0f,radius),center+Offset(0f,radius),3.dp.toPx())
        drawLine(color.copy(alpha=.65f),center+Offset(0f,radius),center+Offset(0f,radius*(1-2*value)),3.dp.toPx())
        val thumb=center+Offset(0f,radius*(1-2*value))
        drawCircle(color.copy(alpha=.8f),14.dp.toPx(),thumb)
        drawCircle(Color(0xFF091625),10.dp.toPx(),thumb)
        drawLine(color,thumb+Offset(-4.dp.toPx(),2.dp.toPx()),thumb-Offset(0f,2.dp.toPx()),1.5.dp.toPx())
        drawLine(color,thumb-Offset(0f,2.dp.toPx()),thumb+Offset(4.dp.toPx(),2.dp.toPx()),1.5.dp.toPx())
    }
}
