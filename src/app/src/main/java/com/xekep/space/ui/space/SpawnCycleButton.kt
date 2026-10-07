package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.*

@Composable
fun SpawnCycleButton(game: SpaceGameState, modifier: Modifier = Modifier, tag: String) {
    val context = LocalContext.current
    val label=context.getString(R.string.cycle_spawn,context.getString(game.spawnKind.labelId()))
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
    val compact=maxWidth < (140*fontScale).dp
    TextButton(onClick = game::cycleSpawnKind, modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription=label }) {
        SpawnKindIcon(game.spawnKind, Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (compact) "↻" else label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
}

@Composable
internal fun SpawnKindIcon(kind: BodyKind, modifier: Modifier = Modifier, shipClass: ShipClass = ShipClass.Interceptor) {
        Canvas(modifier) {
            clipRect { scale(if (kind == BodyKind.Star || kind == BodyKind.BlackHole) .6f else 1f) {
            drawBody(CelestialBody(-1, Vec2.Zero, Vec2.Zero, 20.0, 8f,
                when (kind) { BodyKind.Rocket -> Color(0xFFFFB36B); BodyKind.Star -> Color(0xFFFFD166); BodyKind.BlackHole -> Color(0xFFCB9BFF); else -> if (shipClass == ShipClass.Guardian) Color(0xFF81E5C4) else Color(0xFF8BD3FF) }, kind,shipClass=shipClass),
                IntSize(size.width.toInt(), size.height.toInt()), Vec2.Zero,
                if (kind == BodyKind.Ship || kind == BodyKind.Rocket) 9.dp.toPx()/8f else 1f)
            } }
        }
}
