package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.*
import kotlin.math.abs

/** Tactical routes are faint; no permanent text labels or waypoint handles. */
fun DrawScope.drawArcadeEncounterRoutes(game: SpaceGameState, camera: SpaceCamera) {
    val core=game.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return
    game.bodies.firstOrNull { it.kind == BodyKind.ArcadePlanet }?.let { planet ->
        drawCircle(Color(0xFF6FAAC9).copy(alpha=.12f),((planet.position-core.position).magnitude()*camera.zoom).toFloat(),
            worldToScreen(core.position,game.viewport,camera.center,camera.zoom),style=Stroke(1.dp.toPx()))
    }
    game.arcade?.convoy?.takeIf { it.status == ConvoyStatus.Approaching }?.let { convoy ->
        game.bodies.firstOrNull { it.id == convoy.bodyId }?.let { body ->
            drawLine(Color(0xFF82EAC8).copy(alpha=.12f),worldToScreen(body.position,game.viewport,camera.center,camera.zoom),
                worldToScreen(core.position,game.viewport,camera.center,camera.zoom),1.dp.toPx())
        }
    }
}

fun DrawScope.drawConvoyIndicator(game: SpaceGameState, camera: SpaceCamera) {
    val convoy=game.arcade?.convoy?.takeIf { it.status == ConvoyStatus.Approaching } ?: return
    val body=game.bodies.firstOrNull { it.id == convoy.bodyId } ?: return
    val point=worldToScreen(body.position,game.viewport,camera.center,camera.zoom,game.cameraRotation)
    val color=Color(0xFF82EAC8)
    val marginX=24.dp.toPx(); val marginY=minOf(160.dp.toPx(),size.height*.28f)
    if (point.x in marginX..(size.width-marginX) && point.y in marginY..(size.height-marginY)) {
        val radius=bodyScreenRadius(body,camera.zoom,density)+10.dp.toPx()
        repeat(3) { i -> drawCircle(color.copy(alpha=if (i < convoy.hull) .95f else .2f),2.dp.toPx(),
            point+Offset((i-1)*7.dp.toPx(),-radius)) }
    } else {
        val delta=point-center
        if (delta.getDistance() <= 1f) return
        val factor=minOf((size.width/2-marginX)/abs(delta.x).coerceAtLeast(.01f),
            (size.height/2-marginY)/abs(delta.y).coerceAtLeast(.01f)).coerceAtMost(1f)
        val edge=center+delta*factor
        drawCircle(color.copy(alpha=.15f),11.dp.toPx(),edge)
        drawArrow(edge-delta/delta.getDistance()*14.dp.toPx(),edge,color)
    }
}
