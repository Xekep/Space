package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
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
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.pilotSpeedLimit
import com.xekep.space.sim.flightSpeed
import kotlin.math.abs
import kotlin.math.max

internal fun joystickDirection(offset: Offset, radius: Float): Vec2 {
    if (!offset.x.isFinite() || !offset.y.isFinite() || !radius.isFinite() || radius <= 0) return Vec2.Zero
    val unit=offset/(max(radius,offset.getDistance()))
    fun axis(value: Float)=if (abs(value) <= .12f) 0.0 else
        ((abs(value)-.12f)/.88f).toDouble().coerceIn(0.0,1.0)*(if (value < 0) -1 else 1)
    return Vec2(axis(unit.x),axis(unit.y))
}

internal fun joystickThrottle(current: Double, previousY: Float, nextY: Float, radius: Float): Double =
    if (!current.isFinite()) 0.0 else if (!previousY.isFinite() || !nextY.isFinite() || !radius.isFinite() || radius <= 0) current.coerceIn(0.0,1.0)
    else (current+(previousY-nextY)/(2*radius)).coerceIn(0.0,1.0)

@Composable
fun VirtualJoystick(game: SpaceGameState, enabled: Boolean, left: Boolean, diameter: androidx.compose.ui.unit.Dp = 96.dp) {
    var stick by remember(game.controlledVehicleId) { mutableStateOf(Offset.Zero) }
    var pressed by remember(game.controlledVehicleId) { mutableStateOf(false) }
    val label=stringResource(if (left) R.string.virtual_joystick else R.string.pitch_joystick)
    val craft=game.bodies.firstOrNull { it.id == game.controlledVehicleId }
    val throttle=craft?.let { ((it.pilotTargetSpeed ?: it.flightSpeed())/it.pilotSpeedLimit()).coerceIn(0.0,1.0) } ?: .5
    fun input(value: Vec2) { if (left) game.setJoystickInput(value) else game.setAttitudeJoystickInput(value) }
    DisposableEffect(game,enabled,game.controlledVehicleId,left) {
        onDispose { input(Vec2.Zero) }
    }
    PixelCanvas(Modifier.size(diameter).testTag(if (left) "flight-joystick" else "pitch-joystick").semantics {
        contentDescription=label; if (!enabled) disabled()
    }.pointerInput(game,enabled,game.controlledVehicleId,diameter) {
        if (!enabled) return@pointerInput
        val radius=(minOf(size.width,size.height)/2f-10.dp.toPx()).coerceAtLeast(1f)
        try {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false); down.consume()
                val initialCraft=game.bodies.firstOrNull { it.id == game.controlledVehicleId }
                val initialThrottle=initialCraft?.let { ((it.pilotTargetSpeed ?: it.flightSpeed())/it.pilotSpeedLimit()).coerceIn(0.0,1.0) } ?: .5
                var verticalAnchor=down.position.y
                var latchedThrottle=initialThrottle
                fun move(point: Offset) {
                    val delta=point-Offset(size.width/2f,size.height/2f)
                    stick=if (left) Offset(delta.x.coerceIn(-radius,radius),delta.y.coerceIn(-radius,radius))
                        else delta*(radius/max(radius,delta.getDistance()))
                    pressed=true
                    if (left) {
                        // Relative throttle: yaw or a fresh touch never overwrites the setpoint.
                        // Re-anchor at the end stops so reversing the finger responds immediately.
                        latchedThrottle=joystickThrottle(latchedThrottle,verticalAnchor,point.y,radius)
                        verticalAnchor=point.y
                        val craft=game.bodies.firstOrNull { it.id == game.controlledVehicleId }
                        if (craft != null) game.setPilotTargetSpeed(latchedThrottle*craft.pilotSpeedLimit())
                        input(joystickDirection(Offset(stick.x,0f),radius))
                    } else input(joystickDirection(stick,radius))
                }
                move(down.position)
                do {
                    val change=awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    move(change.position)
                } while (true)
                pressed=false; stick=Offset.Zero; input(Vec2.Zero)
            }
        } finally { pressed=false; stick=Offset.Zero; input(Vec2.Zero) }
    }) {
        val color=(if (left) Color(0xFF80FFDF) else Color(0xFF8BD3FF)).copy(alpha=if (enabled) 1f else .3f)
        val radius=(size.minDimension/2f-10.dp.toPx()).coerceAtLeast(1f)
        drawCircle(color.copy(alpha=.06f),radius,center)
        drawCircle(color.copy(alpha=.4f),radius,center,style=Stroke(1.5.dp.toPx()))
        drawLine(color.copy(alpha=.18f),center-Offset(radius*.7f,0f),center+Offset(radius*.7f,0f),1.dp.toPx())
        drawLine(color.copy(alpha=.18f),center-Offset(0f,radius*.7f),center+Offset(0f,radius*.7f),1.dp.toPx())
        val knob=if (left) Offset(if (pressed) stick.x else 0f,((1-2*throttle)*radius).toFloat()) else stick
        drawCircle(color.copy(alpha=if (pressed) .9f else .5f),14.dp.toPx(),center+knob)
        drawCircle(Color(0xFF091625),10.dp.toPx(),center+knob)
    }
}
