package com.xekep.space.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ModeHud(
    modifier: Modifier = Modifier,
    mode: AppMode,
    score: Double,
    coreLives: Int,
    wave: Int,
    combo: Double,
    bodyCount: Int,
    cameraZoom: Float,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
    ) {
        when (mode) {
            AppMode.Arcade -> Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CompactHudValue("Score", score.roundToInt().toString())
                CompactHudValue("Hull", coreLives.toString())
                CompactHudValue("Wave", wave.toString())
                CompactHudValue("Combo", "x${"%.1f".format(combo)}")
            }

            AppMode.Sandbox -> Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CompactHudValue("Mode", "Sandbox")
                CompactHudValue("Bodies", bodyCount.toString())
                CompactHudValue("Zoom", "x${"%.2f".format(cameraZoom)}")
            }
        }
    }
}

@Composable
fun ArcadeEnergyHud(modifier: Modifier = Modifier, energyRatio: Float, energy: Double) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Energy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(
                    text = energy.roundToInt().toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(10.dp).background(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(999.dp),
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(energyRatio).height(10.dp).background(
                        brush = Brush.horizontalGradient(colors = listOf(Color(0xFF56E39F), Color(0xFF8BD3FF))),
                        shape = RoundedCornerShape(999.dp),
                    ),
                )
            }
        }
    }
}

@Composable
fun PreviewHud(
    modifier: Modifier = Modifier,
    mode: AppMode,
    previewMass: Double?,
    previewSpeed: Double?,
    previewCost: Double?,
) {
    if (previewMass == null || previewSpeed == null) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompactHudValue("Mass", previewMass.roundToInt().toString())
            CompactHudValue("Speed", previewSpeed.roundToInt().toString())
            if (mode == AppMode.Arcade && previewCost != null) {
                CompactHudValue("Cost", previewCost.roundToInt().toString())
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    modifier: Modifier = Modifier,
    score: Double,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Core Breached", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Final score ${score.roundToInt()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text(
                text = "Try to chain meteor kills and keep the combo alive longer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onRetry) { Text("Retry") }
                FilledTonalButton(onClick = onMenu) { Text("Menu") }
            }
        }
    }
}

@Composable
fun MenuOverlay(
    modifier: Modifier = Modifier,
    mode: AppMode,
    saveSummaries: List<com.xekep.space.storage.SandboxSlotSummary?>,
    onArcade: () -> Unit,
    onSandbox: () -> Unit,
    onResetCurrent: () -> Unit,
    onClose: () -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0x8802040B))) {
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onArcade) { Text("Arcade") }
                    FilledTonalButton(onClick = onSandbox) { Text("Sandbox Mode") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onResetCurrent) { Text("Reset Current") }
                    FilledTonalButton(onClick = onClose) { Text("Close") }
                }
                if (mode == AppMode.Sandbox) {
                    Text("Save Slots", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    (1..3).forEach { slot ->
                        SandboxSlotRow(
                            slot = slot,
                            summary = saveSummaries.getOrNull(slot - 1),
                            onSave = { onSaveSlot(slot) },
                            onLoad = { onLoadSlot(slot) },
                        )
                    }
                }
            }
        }
    }
}
