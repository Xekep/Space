package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.fuelFraction
import kotlin.math.roundToInt

@Composable
fun PilotHud(game: SpaceGameState) {
    val craft=game.bodies.firstOrNull { it.id == game.controlledVehicleId } ?: return
    val context=LocalContext.current
    val speed=craft.velocity.magnitude()
    val fuel=craft.fuelFraction
    val fuelColor=if (fuel <= .1f) Color(0xFFFF7A6B) else if (fuel <= .25f) Color(0xFFFFD166) else Color(0xFF80FFDF)
    Surface(Modifier.fillMaxWidth().testTag("pilot-hud"),shape=RoundedCornerShape(16.dp),color=Color(0xEF0B1425),contentColor=MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_speed,speed.roundToInt()),style=MaterialTheme.typography.labelSmall,color=Color(0xFF8BD3FF))
                LinearProgressIndicator(progress={ (speed/900).coerceIn(0.0,1.0).toFloat() },
                    modifier=Modifier.fillMaxWidth().height(4.dp).testTag("pilot-speed"),color=Color(0xFF8BD3FF))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.pilot_fuel,(fuel*100).roundToInt()),style=MaterialTheme.typography.labelSmall,color=fuelColor)
                LinearProgressIndicator(progress={ fuel },modifier=Modifier.fillMaxWidth().height(4.dp).testTag("pilot-fuel"),color=fuelColor)
            }
        }
    }
}
