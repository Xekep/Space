package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.BodyKind
import kotlin.math.abs

fun DrawScope.drawSpaceIndicators(game: SpaceGameState) {
    val camera = game.camera
    if (game.mode == AppMode.Sandbox) {
        (game.selectedBody ?: game.orbitSource)?.let {
            drawCircle(Color(0xFF8BD3FF), (it.radius * camera.zoom).coerceAtLeast(12f) + 8f,
                worldToScreen(it.position, game.viewport, camera.center, camera.zoom), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        return
    }
    val marginX = 24.dp.toPx(); val marginY = 160.dp.toPx().coerceAtMost(size.height * 0.28f)
    val center = Offset(size.width / 2, size.height / 2)
    (game.bodies.filter { it.kind == BodyKind.Core || it.kind == BodyKind.Meteor } + game.arcade?.pending.orEmpty().map { it.body }).forEach { body ->
        val point = worldToScreen(body.position, game.viewport, camera.center, camera.zoom)
        val warning = game.arcade?.pending?.any { it.body.id == body.id } == true
        if (warning || point.x !in marginX..(size.width - marginX) || point.y !in marginY..(size.height - marginY)) {
            val delta = point - center
            if (delta.getDistance() < 1f) return@forEach
            val factor = minOf((size.width / 2 - marginX) / abs(delta.x).coerceAtLeast(0.01f),
                (size.height / 2 - marginY) / abs(delta.y).coerceAtLeast(0.01f)).coerceAtMost(1f)
            val edge = center + delta * factor
            val color = if (body.kind == BodyKind.Core) Color(0xFFFFD166) else Color(0xFFFF8A5B)
            drawCircle(color.copy(alpha = 0.2f), 12.dp.toPx(), edge)
            drawArrow(edge - delta / delta.getDistance() * 16.dp.toPx(), edge, color)
        }
    }
}
