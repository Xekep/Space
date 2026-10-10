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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.xekep.space.sim.hullClass
import com.xekep.space.sim.VehicleHullClass
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.isVehicle
import com.xekep.space.sim.fuelFraction
import com.xekep.space.sim.ShipClass

@Composable
fun BodyDetailsText(body: CelestialBody, modifier: Modifier = Modifier, showHullClass: Boolean = false) {
    val context=LocalContext.current
    val prefix=if (showHullClass && body.hullClass == VehicleHullClass.Heavy) context.getString(body.labelId())+" · " else ""
    Text(prefix+context.getString(R.string.body_details, body.mass.roundToInt(), body.velocity.magnitude().roundToInt()),
        modifier.testTag("body-details"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ArcadeSelectionHud(game: SpaceGameState) {
    val body=game.selectedBody ?: return
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth().blockWorldTouches(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(context.getString(body.labelId()), Modifier.testTag("arcade-object-details"),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            BodyDetailsText(body)
            if (body.isVehicle) {
                val task=when {
                    body.fuelRemaining <= 1e-9 -> R.string.craft_coast
                    body.id == game.controlledVehicleId -> R.string.craft_manual
                    body.waypoints.isNotEmpty() -> R.string.craft_route
                    body.shipClass == ShipClass.Guardian -> R.string.craft_patrol
                    game.arcade?.combat?.craft?.get(body.id)?.targetId != null -> R.string.craft_intercept
                    else -> R.string.craft_reserve
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(task),Modifier.testTag("arcade-craft-task"),style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    PixelMeter(body.fuelFraction,if (body.fuelFraction <= .15f) Color(0xFFFFB76B) else Color(0xFF80FFDF),
                        Modifier.width(48.dp).height(5.dp).testTag("arcade-craft-fuel").semantics {
                            contentDescription=context.getString(R.string.pilot_fuel,(body.fuelFraction*100).roundToInt())
                        })
                }
            }
        }
        TextButton(onClick=game::clearSelection,modifier=Modifier.testTag("clear-arcade-selection").semantics {
            contentDescription=context.getString(R.string.close)
        }) { Text("×") }
    }
}

@Composable
fun ModeHud(modifier: Modifier = Modifier, mode: AppMode, score: Double, coreLives: Int, wave: Int,
    combo: Double, bodyCount: Int, cameraZoom: Float) {
    val context = LocalContext.current
    Surface(modifier.retroFrame(), shape = spaceShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
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
fun ArcadeEnergyHud(modifier: Modifier = Modifier, energyRatio: Float, energy: Double, maximum: Double = MaxEnergy, previewCost: Double? = null, compact: Boolean = false) {
    val context = LocalContext.current
    Surface(modifier.retroFrame(), shape = spaceShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (!compact) Text(context.getString(R.string.launch_energy),Modifier.weight(1f),maxLines=1,style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                previewCost?.let { cost -> Text("−${cost.roundToInt()}",Modifier.padding(horizontal=8.dp).testTag("launch-cost"),style=MaterialTheme.typography.labelMedium,
                    color=if (cost > energy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary) }
                Text("${energy.roundToInt()} / ${maximum.toInt()}", style = MaterialTheme.typography.labelMedium)
            }
            if (LocalRetroUi.current) PixelMeter(energyRatio,Color(0xFF80FFDF),Modifier.fillMaxWidth().height(6.dp))
            else {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.08f), spaceShape(99.dp))) {
                    Box(Modifier.fillMaxWidth(energyRatio.coerceIn(0f, 1f)).height(6.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF56E39F), Color(0xFF8BD3FF))), spaceShape(99.dp)))
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(modifier: Modifier = Modifier, score: Double, bestScore: Double, destroyed: Int,
    elapsed: Double = 0.0, wave: Int = 1, accuracy: Int = 0, carrierOverrun: Boolean = false, onRetry: () -> Unit, onMenu: () -> Unit) {
    val context = LocalContext.current
    Surface(modifier.widthIn(max = 340.dp).padding(20.dp).retroFrame(), shape = spaceShape(28.dp), color = Color(0xFF0B1425),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(context.getString(if (carrierOverrun) R.string.carrier_overrun else R.string.core_breached), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(score.roundToInt().toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
            Text(context.getString(R.string.game_over_intercepts, destroyed, bestScore.roundToInt()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(context.getString(R.string.game_over_details, elapsed.toInt(), wave, accuracy), style = MaterialTheme.typography.bodySmall)
            Text(context.getString(if (carrierOverrun) R.string.carrier_overrun_help else R.string.cause_meteor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.try_again)) }
            TextButton(onClick = onMenu) { Text(context.getString(R.string.choose_mode)) }
        }
    }
}
