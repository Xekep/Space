package com.xekep.space.ui.space

import com.xekep.space.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.Vec2
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SandboxTools(game: SpaceGameState) {
    val context = LocalContext.current
    var restoring by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = game::undo, enabled = game.undoCount > 0, modifier = Modifier.testTag("sandbox-undo")) { Text(context.getString(R.string.undo)) }
        TextButton(onClick = game::saveCheckpoint) { Text(context.getString(R.string.checkpoint)) }
        TextButton(onClick = { restoring = true }, enabled = game.checkpoint != null) { Text(context.getString(R.string.restore)) }
    }
    if (restoring) AlertDialog(onDismissRequest = { restoring = false }, title = { Text(context.getString(R.string.restore_question)) },
        text = { Text(context.getString(R.string.restore_confirmation)) },
        confirmButton = { TextButton(onClick = { game.restoreCheckpoint(); restoring = false }) { Text(context.getString(R.string.restore)) } },
        dismissButton = { TextButton(onClick = { restoring = false }) { Text(context.getString(R.string.cancel)) } })
}

@Composable
internal fun BodyEditDialog(initialMass: Double, initialVelocity: Vec2, onDismiss: () -> Unit, onApply: (Double, Vec2) -> Unit, body: com.xekep.space.sim.CelestialBody? = null) {
    val context = LocalContext.current
    var mass by remember { mutableStateOf(initialMass.toString()) }
    var x by remember { mutableStateOf(initialVelocity.x.toString()) }
    var y by remember { mutableStateOf(initialVelocity.y.toString()) }
    val m = mass.toDoubleOrNull(); val vx = x.toDoubleOrNull(); val vy = y.toDoubleOrNull()
    val valid = m != null && m.isFinite() && m in 1e-8..100000000.0 && vx != null && vx.isFinite() && vy != null && vy.isFinite() && Vec2(vx, vy).magnitude() <= 5000.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(R.string.edit_body)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            body?.solar?.let {
                Text(context.getString(body.labelId()), style = MaterialTheme.typography.titleSmall)
                Text(context.getString(R.string.solar_catalog_details,
                    String.format(java.util.Locale.ROOT,"%.0f",body.radius * com.xekep.space.sim.AU_KM / com.xekep.space.sim.AU_WORLD),
                    String.format(java.util.Locale.ROOT,"%.2e",body.mass / 100000.0 * com.xekep.space.sim.SolarBody.Sun.massKg)), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(mass, { mass = it }, label = { Text(context.getString(R.string.mass)) }, singleLine = true, modifier = Modifier.testTag("body-mass"))
            OutlinedTextField(x, { x = it }, label = { Text(context.getString(R.string.velocity_x)) }, singleLine = true)
            OutlinedTextField(y, { y = it }, label = { Text(context.getString(R.string.velocity_y)) }, singleLine = true)
            if (!valid) Text(context.getString(R.string.numeric_limits), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onApply(m!!, Vec2(vx!!, vy!!)) }, enabled = valid, modifier = Modifier.testTag("apply-body-edit")) { Text(context.getString(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } })
}
