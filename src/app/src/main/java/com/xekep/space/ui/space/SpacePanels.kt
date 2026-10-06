package com.xekep.space.ui.space

import com.xekep.space.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ModeHud(modifier: Modifier = Modifier, mode: AppMode, score: Double, coreLives: Int, wave: Int,
    combo: Double, bodyCount: Int, cameraZoom: Float) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (mode == AppMode.Arcade) {
                CompactHudValue(context.getString(R.string.score), score.roundToInt().toString())
                CompactHudValue(context.getString(R.string.hull), coreLives.toString())
                CompactHudValue(context.getString(R.string.wave), wave.toString())
                CompactHudValue(context.getString(R.string.combo), "x${"%.1f".format(combo)}")
            } else {
                CompactHudValue(context.getString(R.string.bodies), bodyCount.toString())
                CompactHudValue(context.getString(R.string.zoom), "x${"%.2f".format(cameraZoom)}")
                CompactHudValue(context.getString(R.string.space), context.getString(R.string.unlimited))
            }
        }
    }
}

@Composable
fun ArcadeEnergyHud(modifier: Modifier = Modifier, energyRatio: Float, energy: Double) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(context.getString(R.string.launch_energy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text("${energy.roundToInt()} / ${MaxEnergy.toInt()}", style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(99.dp))) {
                Box(Modifier.fillMaxWidth(energyRatio.coerceIn(0f, 1f)).height(6.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF56E39F), Color(0xFF8BD3FF))), RoundedCornerShape(99.dp)))
            }
        }
    }
}

@Composable
fun PreviewHud(modifier: Modifier = Modifier, mode: AppMode, previewMass: Double?, previewSpeed: Double?, previewCost: Double?) {
    val context = LocalContext.current
    if (previewMass == null || previewSpeed == null) return
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CompactHudValue(context.getString(R.string.mass), previewMass.roundToInt().toString())
            CompactHudValue(context.getString(R.string.speed), previewSpeed.roundToInt().toString())
            if (mode == AppMode.Arcade && previewCost != null) CompactHudValue(context.getString(R.string.energy_cost), previewCost.roundToInt().toString())
        }
    }
}

@Composable
fun GameOverOverlay(modifier: Modifier = Modifier, score: Double, bestScore: Double, destroyed: Int,
    elapsed: Double = 0.0, wave: Int = 1, accuracy: Int = 0, onRetry: () -> Unit, onMenu: () -> Unit) {
    val context = LocalContext.current
    Surface(modifier.widthIn(max = 340.dp).padding(20.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF0B1425),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(context.getString(R.string.core_breached), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(score.roundToInt().toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
            Text(context.getString(R.string.game_over_intercepts, destroyed, bestScore.roundToInt()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(context.getString(R.string.game_over_details, elapsed.toInt(), wave, accuracy), style = MaterialTheme.typography.bodySmall)
            Text(context.getString(R.string.cause_meteor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.try_again)) }
            TextButton(onClick = onMenu) { Text(context.getString(R.string.choose_mode)) }
        }
    }
}
