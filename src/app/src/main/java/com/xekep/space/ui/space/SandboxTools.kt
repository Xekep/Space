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
fun SandboxTools(game: SpaceGameState, showHistory: Boolean = true, showInspector: Boolean = true) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    if (showHistory) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = game::undo, enabled = game.undoCount > 0, modifier = Modifier.testTag("sandbox-undo")) { Text(context.getString(R.string.undo)) }
        TextButton(onClick = game::saveCheckpoint) { Text(context.getString(R.string.checkpoint)) }
        TextButton(onClick = { restoring = true }, enabled = game.checkpoint != null) { Text(context.getString(R.string.restore)) }
    }
    if (showInspector) game.selectedBody?.let { body ->
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(context.getString(body.kind.labelId()), style = MaterialTheme.typography.titleSmall)
                Text(context.getString(R.string.body_details, body.mass.roundToInt(), body.velocity.magnitude().roundToInt()), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { editing = true }, modifier = Modifier.testTag("edit-body")) { Text(context.getString(R.string.edit)) }
                    TextButton(onClick = { deleting = true }, modifier = Modifier.testTag("delete-body")) { Text(context.getString(R.string.delete)) }
                    TextButton(onClick = game::prepareOrbit, modifier = Modifier.testTag("orbit-helper")) { Text(context.getString(R.string.orbit_helper)) }
                    TextButton(onClick = game::followSelected) { Text(if (game.following) context.getString(R.string.stop_following) else context.getString(R.string.follow)) }
                    TextButton(onClick = game::clearSelection) { Text(context.getString(R.string.close)) }
                }
            }
        }
        if (editing) BodyEditDialog(body.mass, body.velocity, onDismiss = { editing = false }, onApply = { mass, velocity -> game.editSelected(mass, velocity); editing = false })
        if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(context.getString(R.string.delete_body_question)) },
            text = { Text(context.getString(R.string.delete_body_confirmation)) },
            confirmButton = { TextButton(onClick = { game.deleteSelected(); deleting = false }, modifier = Modifier.testTag("confirm-delete")) { Text(context.getString(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(context.getString(R.string.cancel)) } })
    }
    if (restoring) AlertDialog(onDismissRequest = { restoring = false }, title = { Text(context.getString(R.string.restore_question)) },
        text = { Text(context.getString(R.string.restore_confirmation)) },
        confirmButton = { TextButton(onClick = { game.restoreCheckpoint(); restoring = false }) { Text(context.getString(R.string.restore)) } },
        dismissButton = { TextButton(onClick = { restoring = false }) { Text(context.getString(R.string.cancel)) } })
}

@Composable
private fun BodyEditDialog(initialMass: Double, initialVelocity: Vec2, onDismiss: () -> Unit, onApply: (Double, Vec2) -> Unit) {
    val context = LocalContext.current
    var mass by remember { mutableStateOf(initialMass.toString()) }
    var x by remember { mutableStateOf(initialVelocity.x.toString()) }
    var y by remember { mutableStateOf(initialVelocity.y.toString()) }
    val m = mass.toDoubleOrNull(); val vx = x.toDoubleOrNull(); val vy = y.toDoubleOrNull()
    val valid = m != null && m.isFinite() && m in 1.0..100000.0 && vx != null && vx.isFinite() && vy != null && vy.isFinite() && Vec2(vx, vy).magnitude() <= 5000.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(R.string.edit_body)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(mass, { mass = it }, label = { Text(context.getString(R.string.mass)) }, singleLine = true, modifier = Modifier.testTag("body-mass"))
            OutlinedTextField(x, { x = it }, label = { Text(context.getString(R.string.velocity_x)) }, singleLine = true)
            OutlinedTextField(y, { y = it }, label = { Text(context.getString(R.string.velocity_y)) }, singleLine = true)
            if (!valid) Text(context.getString(R.string.numeric_limits), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onApply(m!!, Vec2(vx!!, vy!!)) }, enabled = valid, modifier = Modifier.testTag("apply-body-edit")) { Text(context.getString(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } })
}
