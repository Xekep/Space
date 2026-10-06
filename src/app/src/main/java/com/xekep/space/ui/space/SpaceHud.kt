package com.xekep.space.ui.space

import com.xekep.space.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.xekep.space.storage.SandboxSlotSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun CompactHudValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SandboxSlotRow(
    slot: Int,
    summary: SandboxSlotSummary?,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(Modifier.size(48.dp)) {
                    val points = summary?.preview.orEmpty()
                    if (points.isNotEmpty()) {
                        val minX = points.minOf { it.position.x }; val maxX = points.maxOf { it.position.x }
                        val minY = points.minOf { it.position.y }; val maxY = points.maxOf { it.position.y }
                        val scale = minOf(size.width / (maxX - minX).coerceAtLeast(100.0), size.height / (maxY - minY).coerceAtLeast(100.0)) * 0.8
                        points.forEach {
                            val point = androidx.compose.ui.geometry.Offset((size.width / 2 + (it.position.x - (minX + maxX) / 2) * scale).toFloat(),
                                (size.height / 2 + (it.position.y - (minY + maxY) / 2) * scale).toFloat())
                            drawCircle(it.color, (it.radius * scale).toFloat().coerceIn(2f, 7f), point)
                        }
                    } else drawCircle(Color.White.copy(alpha = 0.12f), size.minDimension * 0.3f)
                }
                Column(Modifier.weight(1f)) {
                    Text(summary?.let { context.getString(R.string.slot_name, slot, it.name) } ?: context.getString(R.string.slot_empty, slot), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary?.let { context.getString(R.string.body_count, it.bodyCount) } ?: context.getString(R.string.empty), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    summary?.let { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timestampUtcMillis)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onSave) { Text(context.getString(R.string.save)) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = onLoad, enabled = summary != null) { Text(context.getString(R.string.load)) }
            }
        }
    }
}
