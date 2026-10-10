package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.fuelFraction
import com.xekep.space.sim.flightSpeed
import com.xekep.space.sim.enginePowered
import com.xekep.space.sim.pilotSpeedLimit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotHud(game: SpaceGameState, joystick: Boolean = false) {
    val craft=game.bodies.firstOrNull { it.id == game.controlledVehicleId } ?: return
    val context=LocalContext.current
    val speed=craft.flightSpeed()
    val limit=craft.pilotSpeedLimit()
    val fuel=craft.fuelFraction
    val retro=LocalRetroUi.current
    val fuelColor=if (fuel <= .1f) Color(0xFFFF7A6B) else if (fuel <= .25f) Color(0xFFFFD166) else Color(0xFF80FFDF)
    Surface(Modifier.retroFrame().fillMaxWidth().blockWorldTouches().testTag("pilot-hud"),shape=spaceShape(16.dp),color=Color(0xEF0B1425),contentColor=MaterialTheme.colorScheme.onSurface) {
        // A paused world allows planning a speed setpoint without enabling manoeuvres.
        val speedEnabled=craft.fuelRemaining > 1e-9 && !game.sandboxOverlayOpen && !game.arcadeUpgradePending
        val enabled=speedEnabled && (game.mode != AppMode.Sandbox || game.sandbox?.paused == false)
        BoxWithConstraints {
        val stickSize=if (maxWidth < 340.dp) 80.dp else 96.dp
        Row(Modifier.padding(horizontal=if (joystick) 4.dp else 12.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if (joystick) 4.dp else 16.dp)) {
            if (joystick) VirtualJoystick(game,enabled,left=true,diameter=stickSize)
            if (joystick) PilotMeters(game,speed,craft.pilotTargetSpeed ?: speed,fuel,speedEnabled,limit,Modifier.weight(1f))
            else Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_speed,speed.roundToInt()),maxLines=1,style=MaterialTheme.typography.labelSmall,color=Color(0xFF8BD3FF))
                val target=(craft.pilotTargetSpeed ?: speed).coerceIn(0.0,limit)
                PixelCanvas(Modifier.fillMaxWidth().height(48.dp).testTag("pilot-speed").semantics {
                        contentDescription=context.getString(R.string.pilot_target_speed)
                        stateDescription=context.getString(R.string.pilot_speed,speed.roundToInt())
                        progressBarRangeInfo=ProgressBarRangeInfo(target.toFloat(),0f..limit.toFloat())
                        if (!speedEnabled) disabled()
                        setProgress { if (speedEnabled && it.isFinite()) { game.setPilotTargetSpeed(it.toDouble()); true } else false }
                    }.pilotSpeedInput(game,speedEnabled,limit,vertical=false)) {
                    val thickness=4.dp.toPx()
                    val top=center.y-thickness/2
                    if (retro) {
                        val segments=24
                        repeat(segments) { index ->
                            drawRect(Color(0xFF8BD3FF).copy(alpha=if ((index+.5f)/segments <= speed/limit) .9f else .12f),
                                Offset(index*size.width/segments,top),androidx.compose.ui.geometry.Size((size.width/segments-1.dp.toPx()).coerceAtLeast(1f),thickness))
                        }
                    } else {
                        drawRoundRect(Color(0xFF253249),Offset(0f,top),androidx.compose.ui.geometry.Size(size.width,thickness))
                        drawRoundRect(Color(0xFF8BD3FF),Offset(0f,top),androidx.compose.ui.geometry.Size(size.width*(speed/limit).coerceIn(0.0,1.0).toFloat(),thickness))
                    }
                    val x=(size.width*target/limit).toFloat().coerceIn(1.dp.toPx(),size.width-1.dp.toPx())
                    drawLine(Color(0xFF8BD3FF),Offset(x,center.y-9.dp.toPx()),Offset(x,center.y+9.dp.toPx()),3.dp.toPx())
                }

            }
            if (joystick) VirtualJoystick(game,enabled && craft.enginePowered,left=false,diameter=stickSize) else Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_fuel,(fuel*100).roundToInt()),maxLines=1,style=MaterialTheme.typography.labelSmall,color=fuelColor)
                if (retro) PixelMeter(fuel,fuelColor,Modifier.fillMaxWidth().padding(top=22.dp).height(4.dp).testTag("pilot-fuel"))
                else LinearProgressIndicator(progress={ fuel },modifier=Modifier.fillMaxWidth().padding(top=22.dp).height(4.dp).testTag("pilot-fuel"),color=fuelColor)
            }
        }
        }
    }
}
