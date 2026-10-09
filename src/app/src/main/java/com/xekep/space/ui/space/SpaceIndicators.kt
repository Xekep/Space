package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.Vec2

fun DrawScope.drawSpaceIndicators(game: SpaceGameState,camera: SpaceCamera=game.camera,
    renderedPosition: (com.xekep.space.sim.CelestialBody) -> Vec2 = { it.position }) {
    game.bodies.firstOrNull { it.id == game.controlledVehicleId }?.let { body ->
        val point = worldToScreen(flightRenderPosition(body,renderedPosition(body),game.viewport,camera.zoom,game.cameraRotation), game.viewport, camera.center, camera.zoom, game.cameraRotation)
        val radius = vehicleRenderRadius(body,camera.zoom,density,game.largeVehicleIcons,game.pilotVisualZoom)*1.4f + 5.dp.toPx()
        // Four small brackets identify the pilot's craft without a text banner.
        repeat(4) { index ->
            drawArc(Color(0xFF80FFDF), index * 90f + 28f, 34f, false,
                point - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
    }
    (game.selectedBody ?: game.orbitSource)?.let {
            drawCircle(Color(0xFF8BD3FF), bodyScreenRadius(it, camera.zoom, density, game.largeVehicleIcons).coerceAtLeast(12f) + 8f,
                worldToScreen(flightRenderPosition(it,renderedPosition(it),game.viewport,camera.zoom,game.cameraRotation), game.viewport, camera.center, camera.zoom, game.cameraRotation), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
    if (game.mode == AppMode.Sandbox) {
        return
    }
    drawConvoyIndicator(game,camera)
    val marginX = 24.dp.toPx(); val marginY = 160.dp.toPx().coerceAtMost(size.height * 0.28f)
    val bounds = Rect(marginX, marginY, size.width - marginX, size.height - marginY)
    (game.bodies.filter { it.kind == BodyKind.Meteor } + game.arcade?.pending.orEmpty().map { it.body }).forEach { body ->
        val point = worldToScreen(renderedPosition(body), game.viewport, camera.center, camera.zoom, game.cameraRotation)
        val velocity = rotateVector(body.velocity * camera.zoom.toDouble(), game.cameraRotation)
        val marker = threatDirectionMarker(point, velocity, bodyScreenRadius(body, camera.zoom, density), game.viewport, bounds)
            ?: return@forEach
        val color = Color(0xFFFF8A5B)
        drawCircle(color.copy(alpha = 0.2f), 12.dp.toPx(), marker.position)
        drawArrow(marker.position - marker.direction * 16.dp.toPx(), marker.position, color)
    }
}
