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
import kotlin.math.abs
import kotlin.math.max

internal fun joystickDirection(offset: Offset, radius: Float): Vec2 {
    if (!offset.x.isFinite() || !offset.y.isFinite() || !radius.isFinite() || radius <= 0) return Vec2.Zero
    val unit=offset/(max(radius,offset.getDistance()))
    fun axis(value: Float)=if (abs(value) <= .12f) 0.0 else
        ((abs(value)-.12f)/.88f).toDouble().coerceIn(0.0,1.0)*(if (value < 0) -1 else 1)
    return Vec2(axis(unit.x),axis(unit.y))
}

@Composable
fun VirtualJoystick(game: SpaceGameState, enabled: Boolean) {
    var stick by remember(game.controlledVehicleId) { mutableStateOf(Offset.Zero) }
    var pressed by remember(game.controlledVehicleId) { mutableStateOf(false) }
    val label=stringResource(R.string.virtual_joystick)
    LaunchedEffect(pressed,enabled,game.controlledVehicleId) {
        try {
            if (pressed && enabled) {
                var previous=withFrameNanos { it }
                while (true) withFrameNanos { time ->
                    val seconds=((time-previous)/1e9).coerceIn(0.0,.05); previous=time
                    val radius=38*game.density
                    val input=joystickDirection(stick,radius)
                    // Up adds forward thrust progressively; horizontal motion turns relative to the craft.
                    game.setSteeringInput(Vec2(input.x,max(0.0,-input.y)*seconds*.8))
                }
            }
        } finally { game.setSteeringInput(Vec2.Zero) }
    }
    DisposableEffect(game) { onDispose { game.setSteeringInput(Vec2.Zero) } }
    Canvas(Modifier.size(96.dp).testTag("flight-joystick").semantics {
        contentDescription=label; if (!enabled) disabled()
    }.pointerInput(game,enabled,game.controlledVehicleId) {
        if (!enabled) return@pointerInput
        val radius=38.dp.toPx()
        try {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false); down.consume()
                fun move(point: Offset) {
                    val delta=point-Offset(size.width/2f,size.height/2f)
                    stick=delta*(radius/max(radius,delta.getDistance()))
                    pressed=true
                    game.setSteeringInput(Vec2(joystickDirection(stick,radius).x,0.0))
                }
                move(down.position)
                do {
                    val change=awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    move(change.position)
                } while (true)
                pressed=false; stick=Offset.Zero; game.setSteeringInput(Vec2.Zero)
            }
        } finally { pressed=false; stick=Offset.Zero; game.setSteeringInput(Vec2.Zero) }
    }) {
        val color=Color(0xFF8BD3FF).copy(alpha=if (enabled) 1f else .3f)
        val radius=38.dp.toPx()
        drawCircle(color.copy(alpha=.06f),radius,center)
        drawCircle(color.copy(alpha=.4f),radius,center,style=Stroke(1.5.dp.toPx()))
        drawLine(color.copy(alpha=.18f),center-Offset(radius*.7f,0f),center+Offset(radius*.7f,0f),1.dp.toPx())
        drawLine(color.copy(alpha=.18f),center-Offset(0f,radius*.7f),center+Offset(0f,radius*.7f),1.dp.toPx())
        drawCircle(color.copy(alpha=if (pressed) .9f else .5f),14.dp.toPx(),center+stick)
        drawCircle(Color(0xFF091625),10.dp.toPx(),center+stick)
    }
}
