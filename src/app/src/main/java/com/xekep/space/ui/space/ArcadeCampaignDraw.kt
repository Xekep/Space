package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.*
import kotlin.math.*

/** Vector artwork follows world scale and works with the existing retro canvas. */
internal fun DrawScope.drawCampaignObjects(game: SpaceGameState, camera: SpaceCamera) {
    val run=game.arcade ?: return
    run.salvage?.takeIf { it.status == SalvageStatus.Available }?.let { salvage ->
        val point=worldToScreen(salvage.position,game.viewport,camera.center,camera.zoom)
        val radius=(55*camera.zoom).coerceAtLeast(12.dp.toPx())
        drawCircle(Color(0xFF9EF8FF).copy(alpha=.12f),radius,point)
        drawCircle(Color(0xFF9EF8FF).copy(alpha=.55f),radius,point,style=Stroke(1.dp.toPx()))
        drawDiamond(point,8.dp.toPx(),Color(0xFF9EF8FF))
        drawArc(Color(0xFF9EF8FF),-90f,(salvage.remaining/32*360).toFloat(),false,
            point-Offset(radius,radius),androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(2.dp.toPx()))
    }
    run.carrier?.takeUnless { it.defeated }?.let { carrier ->
        val center=worldToScreen(run.carrierPosition,game.viewport,camera.center,camera.zoom)
        val phase=carrier.angle+carrier.elapsed*.055
        val color=Color(0xFFFF8B70)
        val radius=82f*camera.zoom
        val hull=Path()
        for (i in 0..5) {
            val angle=phase+i*PI/3
            val distance=radius*(if (i%2 == 0) 1f else .48f)
            val point=center+Offset((cos(angle)*distance).toFloat(),(sin(angle)*distance).toFloat())
            if (i == 0) hull.moveTo(point.x,point.y) else hull.lineTo(point.x,point.y)
        }
        hull.close()
        drawPath(hull,Color(0xFF402C40));drawPath(hull,color.copy(alpha=.55f),style=Stroke(1.5.dp.toPx()))
        drawCircle(color.copy(alpha=.20f),radius*.25f,center)
        drawCircle(color,radius*.12f,center,style=Stroke(1.5.dp.toPx()))
        for (body in run.bodies.filter { it.id in carrier.nodeIds }) {
            val point=worldToScreen(body.position,game.viewport,camera.center,camera.zoom)
            val nodeRadius=(body.radius*camera.zoom).coerceAtLeast(4.dp.toPx())
            drawLine(color.copy(alpha=.4f),center,point,2.dp.toPx())
            drawCircle(Color(0xFF251A2C),nodeRadius,point)
            drawCircle(color.copy(alpha=.25f),nodeRadius,point,style=Stroke(2.dp.toPx()))
            drawArc(color,-90f,(360*body.mass/run.carrierNodeHull).toFloat().coerceIn(0f,360f),false,
                point-Offset(nodeRadius,nodeRadius),androidx.compose.ui.geometry.Size(nodeRadius*2,nodeRadius*2),style=Stroke(2.dp.toPx()))
            drawCircle(Color(0xFFFFDBB0),nodeRadius*.28f,point)
        }
    }
}

private fun DrawScope.drawDiamond(point: Offset, radius: Float, color: Color) {
    val path=Path().apply { moveTo(point.x,point.y-radius);lineTo(point.x+radius,point.y)
        lineTo(point.x,point.y+radius);lineTo(point.x-radius,point.y);close() }
    drawPath(path,color,style=Stroke(2.dp.toPx()))
}

/** Cyan diamond is distinct from yellow core and red threat triangles. */
internal fun DrawScope.drawSalvageIndicator(game: SpaceGameState, camera: SpaceCamera) {
    val salvage=game.arcade?.salvage?.takeIf { it.status == SalvageStatus.Available } ?: return
    val point=worldToScreen(salvage.position,game.viewport,camera.center,camera.zoom,game.cameraRotation)
    val margin=24.dp.toPx()
    val bounds=game.indicatorBounds(Rect(margin,margin,size.width-margin,size.height-margin),14.dp.toPx())
    if (bounds.width <= 0 || bounds.height <= 0 || bounds.contains(point)) return
    val anchor=indicatorAnchor(game.viewport,bounds); val delta=point-anchor
    if (delta.getDistance() < 1f) return
    val factor=minOf(if (delta.x == 0f) Float.POSITIVE_INFINITY else
        ((if (delta.x > 0) bounds.right else bounds.left)-anchor.x)/delta.x,
        if (delta.y == 0f) Float.POSITIVE_INFINITY else
        ((if (delta.y > 0) bounds.bottom else bounds.top)-anchor.y)/delta.y).coerceAtMost(1f)
    drawDiamond(anchor+delta*factor,6.dp.toPx(),Color(0xFF9EF8FF))
}
