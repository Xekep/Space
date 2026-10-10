package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.isVehicle
import com.xekep.space.sim.fuelFraction

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
    if ((game.arcade?.lives ?: 0) <= 0) return
    drawConvoyIndicator(game,camera)
    game.bodies.filter { it.isVehicle && it.fuelFraction <= .15f }.forEach { body ->
        val point=worldToScreen(flightRenderPosition(body,renderedPosition(body),game.viewport,camera.zoom,game.cameraRotation),
            game.viewport,camera.center,camera.zoom,game.cameraRotation)
        val radius=vehicleRenderRadius(body,camera.zoom,density,game.largeVehicleIcons,game.pilotVisualZoom)+7.dp.toPx()
        drawArc(Color(0xFFFFB76B),-90f,360f*body.fuelFraction/.15f,false,point-Offset(radius,radius),
            androidx.compose.ui.geometry.Size(radius*2,radius*2),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
    game.selectedBody?.takeIf { it.isVehicle }?.let { craft ->
        game.bodies.firstOrNull { it.id == game.arcade?.combat?.craft?.get(craft.id)?.targetId }?.let { target ->
            val from=worldToScreen(flightRenderPosition(craft,renderedPosition(craft),game.viewport,camera.zoom,game.cameraRotation),
                game.viewport,camera.center,camera.zoom,game.cameraRotation)
            val to=worldToScreen(renderedPosition(target),game.viewport,camera.center,camera.zoom,game.cameraRotation)
            drawLine(Color(0xFF80FFDF).copy(alpha=.4f),from,to,1.dp.toPx(),
                pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),8.dp.toPx())))
            drawCircle(Color(0xFF80FFDF),bodyScreenRadius(target,camera.zoom,density)+5.dp.toPx(),to,
                style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
    val marginX = 24.dp.toPx(); val marginY = 160.dp.toPx().coerceAtMost(size.height * 0.28f)
    val bounds = game.indicatorBounds(Rect(marginX, marginY, size.width - marginX, size.height - marginY),14.dp.toPx())
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val markerScale=minOf(1f,bounds.height/(40.dp.toPx()))
    (game.bodies.filter { it.kind == BodyKind.Meteor } + game.arcade?.pending.orEmpty().map { it.body }).forEach { body ->
        val point = worldToScreen(renderedPosition(body), game.viewport, camera.center, camera.zoom, game.cameraRotation)
        val velocity = rotateVector(body.velocity * camera.zoom.toDouble(), game.cameraRotation)
        val marker = threatDirectionMarker(point, velocity, bodyScreenRadius(body, camera.zoom, density), game.viewport, bounds)
        val heavy=body.mass >= 400.0
        val core=game.bodies.firstOrNull { it.kind == BodyKind.Core }
        val imminent=core?.let { (body.position-it.position).magnitude() <= 500.0 } == true
        val color=(if (heavy) Color(0xFFFFC46B) else Color(0xFFFF8A5B)).copy(alpha=if (imminent) 1f else .7f)
        if (marker == null) {
            // A wide view may include a world-space arrival. Keep the warning visible
            // while the announced body has not yet joined the simulation.
            game.arcade?.pending?.firstOrNull { it.body.id == body.id }?.let { warning ->
                val radius=(if (heavy) 15.dp.toPx() else 12.dp.toPx())
                val ratio=(warning.seconds/(game.arcade?.difficulty?.warningSeconds ?: 1.2)).coerceIn(0.0,1.0)
                drawArc(color,-90f,(90+270*ratio).toFloat(),false,point-Offset(radius,radius),
                    androidx.compose.ui.geometry.Size(radius*2,radius*2),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
            return@forEach
        }
        if (heavy) drawCircle(color,9.dp.toPx()*markerScale,marker.position,
            style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        drawCircle(color.copy(alpha = if (imminent) .28f else .12f),12.dp.toPx()*markerScale,marker.position)
        drawArrow(marker.position - marker.direction * (if (heavy) 20.dp.toPx() else 14.dp.toPx())*markerScale,marker.position,color)
    }
}
