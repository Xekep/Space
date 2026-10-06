package com.xekep.space.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xekep.space.R
import com.xekep.space.sim.BodyKind

@Composable
fun FlightLoopButton(game: SpaceGameState) {
    if (game.spawnKind != BodyKind.Ship && game.spawnKind != BodyKind.Rocket) return
    val active=game.loopFlightRoutes
    val color=if (active) Color(0xFF80FFDF) else MaterialTheme.colorScheme.primary
    val label=stringResource(R.string.loop_flight_route)
    IconToggleButton(checked=active,onCheckedChange={ game.loopFlightRoutes=it },
        modifier=Modifier.size(48.dp).testTag("route-loop").background(
            if (active) color.copy(alpha=.16f) else Color.Transparent,CircleShape).semantics { contentDescription=label }) {
        Text("∞",color=color,fontSize=24.sp)
    }
}
