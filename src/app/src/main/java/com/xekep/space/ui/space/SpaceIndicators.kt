package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.toOffset
import kotlin.math.abs

fun DrawScope.drawSpaceIndicators(game: SpaceGameState,camera: SpaceCamera=game.camera,
    renderedPosition: (com.xekep.space.sim.CelestialBody) -> Vec2 = { it.position }) {
    game.bodies.firstOrNull { it.id == game.controlledVehicleId }?.let { body ->
        val point = worldToScreen(renderedPosition(body), game.viewport, camera.center, camera.zoom, game.cameraRotation)
        val radius = bodyScreenRadius(body, camera.zoom, density, game.largeVehicleIcons) + 5.dp.toPx()
        // Four small brackets identify the pilot's craft without a text banner.
        repeat(4) { index ->
            drawArc(Color(0xFF80FFDF), index * 90f + 28f, 34f, false,
                point - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
    }
    (game.selectedBody ?: game.orbitSource)?.let {
            drawCircle(Color(0xFF8BD3FF), bodyScreenRadius(it, camera.zoom, density, game.largeVehicleIcons).coerceAtLeast(12f) + 8f,
                worldToScreen(renderedPosition(it), game.viewport, camera.center, camera.zoom, game.cameraRotation), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
    if (game.mode == AppMode.Sandbox) {
        return
    }
    val marginX = 24.dp.toPx(); val marginY = 160.dp.toPx().coerceAtMost(size.height * 0.28f)
    val center = Offset(size.width / 2, size.height / 2)
    (game.bodies.filter { it.kind == BodyKind.Meteor } + game.arcade?.pending.orEmpty().map { it.body }).forEach { body ->
        val point = worldToScreen(renderedPosition(body), game.viewport, camera.center, camera.zoom, game.cameraRotation)
        val warning = game.arcade?.pending?.any { it.body.id == body.id } == true
        if (body.kind == BodyKind.Meteor) {
            val velocity=rotateVector(body.velocity*camera.zoom.toDouble(),game.cameraRotation)
            val entry=incomingScreenEntry(Vec2(point.x.toDouble(),point.y.toDouble()),velocity,size.width.toDouble(),size.height.toDouble())
            if (entry != null) {
                val edge=Offset(entry.x.toFloat().coerceIn(marginX,size.width-marginX),entry.y.toFloat().coerceIn(marginY,size.height-marginY))
                val direction=velocity.normalized().toOffset()
                val color=Color(0xFFFF8A5B)
                drawCircle(color.copy(alpha=.2f),12.dp.toPx(),edge)
                drawArrow(edge-direction*12.dp.toPx(),edge+direction*8.dp.toPx(),color)
                return@forEach
            }
        }
        if (warning || point.x !in marginX..(size.width - marginX) || point.y !in marginY..(size.height - marginY)) {
            val delta = point - center
            if (delta.getDistance() < 1f) return@forEach
            val factor = minOf((size.width / 2 - marginX) / abs(delta.x).coerceAtLeast(0.01f),
                (size.height / 2 - marginY) / abs(delta.y).coerceAtLeast(0.01f)).coerceAtMost(1f)
            val edge = center + delta * factor
            val color = Color(0xFFFF8A5B)
            drawCircle(color.copy(alpha = 0.2f), 12.dp.toPx(), edge)
            drawArrow(edge - delta / delta.getDistance() * 16.dp.toPx(), edge, color)
        }
    }
}
