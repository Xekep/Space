package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.*

enum class ExperimentKind(val title: Int,val help: Int) {
    Orbit(R.string.experiment_orbit,R.string.experiment_orbit_help),
    Collision(R.string.experiment_collision,R.string.experiment_collision_help),
    Capture(R.string.experiment_black_hole,R.string.experiment_black_hole_help),
}

internal fun experimentBodies(kind: ExperimentKind): List<CelestialBody> {
    fun body(point: Vec2,velocity: Vec2,mass: Double,color: Color,type: BodyKind=BodyKind.Ambient)=
        CelestialBody(SimulationEngine.newBodyId(),point,velocity,mass,SimulationEngine.radiusForMass(mass),color,type)
    if (kind == ExperimentKind.Collision) return listOf(
        body(Vec2(-170.0,0.0),Vec2(25.0,0.0),600.0,Color(0xFF8CD6FF)),
        body(Vec2(170.0,0.0),Vec2(-40.0,0.0),250.0,Color(0xFFFFA46B)))
    val center=body(Vec2.Zero,Vec2.Zero,if (kind == ExperimentKind.Capture) 4000.0 else 3000.0,
        Color(0xFFFFCE69),if (kind == ExperimentKind.Capture) BodyKind.BlackHole else BodyKind.Star)
    val point=Vec2(350.0,0.0)
    val planet=body(point,SimulationEngine.orbitVelocity(center,point,30.0),30.0,Color(0xFF8CD6FF))
    return if (kind == ExperimentKind.Orbit) listOf(center,planet) else listOf(center,planet,
        body(Vec2(-300.0,0.0),Vec2(25.0,3.0),4.0,Color(0xFFFFA46B)))
}

@Composable
internal fun ExperimentDialog(onDismiss: () -> Unit,onStart: (ExperimentKind) -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("experiments-dialog"),title={ Text(stringResource(R.string.experiments)) },
        text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            for (kind in ExperimentKind.entries) OutlinedButton(onClick={ onStart(kind) },modifier=Modifier.fillMaxWidth().testTag("experiment-${kind.name}")) {
                Column { Text(stringResource(kind.title)); Text(stringResource(kind.help),style=MaterialTheme.typography.bodySmall) }
            }
        } },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.close)) } })
}
