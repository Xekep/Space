package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        val enabled=craft.fuelRemaining > 1e-9 && !game.sandboxOverlayOpen && !game.arcadeUpgradePending &&
            (game.mode != AppMode.Sandbox || game.sandbox?.paused == false)
        Row(Modifier.padding(horizontal=if (joystick) 4.dp else 12.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if (joystick) 4.dp else 16.dp)) {
            if (joystick) VirtualJoystick(game,enabled,left=true)
            if (joystick) PilotMeters(game,speed,craft.pilotTargetSpeed ?: speed,fuel,enabled,limit,Modifier.weight(1f))
            else Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_speed,speed.roundToInt()),maxLines=1,style=MaterialTheme.typography.labelSmall,color=Color(0xFF8BD3FF))
                Slider(value=(craft.pilotTargetSpeed ?: speed).coerceIn(0.0,limit).toFloat(),
                    onValueChange={ game.setPilotTargetSpeed(it.toDouble()) },valueRange=0f..limit.toFloat(),enabled=enabled,
                    modifier=Modifier.fillMaxWidth().testTag("pilot-speed").semantics {
                        contentDescription=context.getString(R.string.pilot_target_speed)
                    },thumb={
                        PixelCanvas(Modifier.size(width=14.dp,height=18.dp)) {
                            drawLine(Color(0xFF8BD3FF),Offset(center.x,0f),Offset(center.x,size.height),3.dp.toPx())
                        }
                    },track={
                        PixelCanvas(Modifier.fillMaxWidth().height(4.dp)) {
                            if (retro) drawPixelMeter((speed/limit).toFloat(),Color(0xFF8BD3FF)) else {
                            drawRoundRect(Color(0xFF253249))
                            drawRoundRect(Color(0xFF8BD3FF),size=androidx.compose.ui.geometry.Size(size.width*(speed/limit).coerceIn(0.0,1.0).toFloat(),size.height))
                            }
                        }
                    })

            }
            if (joystick) VirtualJoystick(game,enabled && craft.enginePowered,left=false) else Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_fuel,(fuel*100).roundToInt()),maxLines=1,style=MaterialTheme.typography.labelSmall,color=fuelColor)
                if (retro) PixelMeter(fuel,fuelColor,Modifier.fillMaxWidth().padding(top=22.dp).height(4.dp).testTag("pilot-fuel"))
                else LinearProgressIndicator(progress={ fuel },modifier=Modifier.fillMaxWidth().padding(top=22.dp).height(4.dp).testTag("pilot-fuel"),color=fuelColor)
            }
        }
    }
}
