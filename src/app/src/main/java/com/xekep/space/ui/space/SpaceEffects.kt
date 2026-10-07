package com.xekep.space.ui.space

import android.content.Context
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.*
import kotlin.math.*

fun DrawScope.drawExplosions(effects: List<Explosion>, viewport: IntSize, camera: Vec2, zoom: Float, reducedFlashes: Boolean) {
    effects.forEach { burst ->
        val center = worldToScreen(burst.position + burst.drift * burst.age, viewport, camera, zoom)
        if (burst.collapseRadius > 0f) {
            drawCollapse(burst,center,zoom,reducedFlashes)
            return@forEach
        }
        val progress = (burst.age / burst.duration).toFloat()
        val alpha = (1f - progress).pow(2)
        if (!reducedFlashes && progress < .32f) {
            drawCircle(Color(0xFFFFCB79).copy(alpha = alpha * .35f), (12 + progress * 65).dp.toPx(), center)
            drawCircle(Color(0xFFFFEDD0).copy(alpha = alpha), (5 + progress * 50).dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
        burst.particles.forEach { particle ->
            val displacement = particle.velocity.toOffset() * (burst.age.toFloat() * zoom.coerceIn(.45f, 2f))
            val point = center + displacement
            val color = (if (particle.ember) Color(0xFFFFAE5B) else Color(0xFFB6CBDC)).copy(alpha = alpha)
            if (particle.ember) drawLine(color, point - displacement * .075f, point, particle.size * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            else rotate(particle.spin * burst.age.toFloat(), point) {
                drawRect(color, point - Offset(particle.size, particle.size), androidx.compose.ui.geometry.Size(particle.size * density, particle.size * .6f * density))
            }
        }
    }
}

private val labelPaint = ThreadLocal<Paint>()

fun DrawScope.drawFlightRoute(start: Vec2, points: List<Vec2>, viewport: IntSize, camera: Vec2, zoom: Float, color: Color,
    route: FlightPath? = null, distance: Double = 0.0, showMarkers: Boolean = false) {
    val curve = route ?: FlightPath.through(start, points) ?: return
    val path = Path()
    curve.drawingPoints(distance).forEachIndexed { index, point ->
        val screen=worldToScreen(point,viewport,camera,zoom)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    val routeColor=if (curve.isLoop) Color(0xFF80FFDF) else color
    drawPath(path,routeColor.copy(alpha=.45f),style=Stroke(1.5.dp.toPx(),
        pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),5.dp.toPx()))))
    if (!showMarkers) return
    points.forEach { point ->
        val screen=worldToScreen(point,viewport,camera,zoom)
        drawCircle(color.copy(alpha=.85f),5.dp.toPx(),screen,style=Stroke(1.5.dp.toPx()))
        drawCircle(color,1.5.dp.toPx(),screen)
    }
    curve.loopStartIndex?.let { index ->
        val center=worldToScreen(curve.knots[index],viewport,camera,zoom)-Offset(0f,22.dp.toPx())
        val infinity=Path()
        val radius=8.dp.toPx()
        repeat(49) { step ->
            val angle=step*2*PI/48
            val x=center.x+radius*sin(angle).toFloat()
            val y=center.y+radius*.45f*sin(2*angle).toFloat()
            if (step == 0) infinity.moveTo(x,y) else infinity.lineTo(x,y)
        }
        drawPath(infinity,routeColor,style=Stroke(1.5.dp.toPx()))
    }
}

/** Reference ellipses translate with their parent until their orbit is disturbed. */
fun DrawScope.drawSolarOrbits(bodies: List<CelestialBody>, viewport: IntSize, camera: Vec2, zoom: Float, hidden: Set<Long> = emptySet()) {
    bodies.forEach { body ->
        if (body.id in hidden) return@forEach
        val entry = body.solar?.takeUnless { it == SolarBody.Sun } ?: return@forEach
        val parent = bodies.firstOrNull { it.solar?.name == entry.parent } ?: return@forEach
        if (entry.axisAu * AU_WORLD * zoom < 12.dp.toPx()) return@forEach
        val a = entry.axisAu * AU_WORLD
        val b = a * sqrt(1 - entry.eccentricity.pow(2))
        val rotation = entry.periapsis * PI / 180
        val path = Path()
        repeat(97) { index ->
            val angle = index * 2 * PI / 96
            val x = a * (cos(angle) - entry.eccentricity); val y = b * sin(angle)
            val point = worldToScreen(parent.position + Vec2(x * cos(rotation) - y * sin(rotation), x * sin(rotation) + y * cos(rotation)), viewport, camera, zoom)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, body.color.copy(alpha = if (entry.isMoon) .22f else .16f), style = Stroke(1.dp.toPx()))
    }
}

fun visibleSolarBodies(bodies: List<CelestialBody>, zoom: Float, density: Float): List<CelestialBody> = bodies.filter { body ->
    val entry = body.solar
    if (entry?.isMoon != true) true else {
        val parent = bodies.firstOrNull { it.solar?.name == entry.parent }
        parent == null || (body.position - parent.position).magnitude() * zoom >= 22 * density
    }
}

class SolarLabelLayout {
    private data class Anchor(var position: Offset, var slot: Int)
    private val anchors = mutableMapOf<Long, Anchor>()
    private var previousNanos = 0L
    private var camera: SpaceCamera? = null
    fun beginFrame(currentCamera: SpaceCamera): Float {
        val old = camera
        if (old != null && (abs(currentCamera.zoom / old.zoom - 1) > .15 ||
                (currentCamera.center - old.center).magnitude() * currentCamera.zoom > 150)) anchors.clear()
        camera = currentCamera
        val now = android.os.SystemClock.elapsedRealtimeNanos()
        val dt = if (previousNanos == 0L) .016 else ((now - previousNanos) / 1e9).coerceIn(0.0, .1)
        previousNanos = now
        return (1 - exp(-dt / .12)).toFloat()
    }
    fun slot(id: Long): Int = anchors[id]?.slot ?: 0
    fun place(id: Long, target: Offset, slot: Int, blend: Float): Offset {
        val anchor = anchors.getOrPut(id) { Anchor(target, slot) }
        anchor.position += (target - anchor.position) * blend
        anchor.slot = slot
        return anchor.position
    }
}

fun solarLabelAlpha(age: Double): Float = ((60 - age) / 5).coerceIn(0.0, 1.0).toFloat()

fun DrawScope.drawSolarLabels(bodies: List<CelestialBody>, viewport: IntSize, camera: Vec2, zoom: Float, context: Context,
                             layout: SolarLabelLayout, age: Double, rotation: Double = 0.0) {
    val alpha = solarLabelAlpha(age)
    if (alpha <= 0) return
    val blend = layout.beginFrame(SpaceCamera(camera, zoom))
    val paint = labelPaint.get() ?: Paint(Paint.ANTI_ALIAS_FLAG).also { labelPaint.set(it) }
    paint.textSize = 11.dp.toPx(); paint.color = android.graphics.Color.rgb(215, 227, 243)
    paint.alpha = (255 * alpha).toInt()
    val occupied = mutableListOf<android.graphics.RectF>()
    val visible = visibleSolarBodies(bodies, zoom, density).filter { it.solar != null }
    val symbols = visible.map { body ->
        val center = worldToScreen(body.position, viewport, camera, zoom, rotation)
        val radius = bodyScreenRadius(body, zoom, density) * 1.05f
        android.graphics.RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    }
    visible.forEach { body ->
        val center = worldToScreen(body.position, viewport, camera, zoom, rotation)
        if (center.x !in 0f..size.width || center.y !in 110.dp.toPx()..(size.height - 150.dp.toPx())) return@forEach
        val text = context.getString(body.labelId()); val width = paint.measureText(text)
        val x = (center.x + bodyScreenRadius(body, zoom, density) + 5.dp.toPx()).coerceIn(8.dp.toPx(), (size.width - width - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
        var slot = layout.slot(body.id)
        var y = center.y - 7.dp.toPx() + slot * 17.dp.toPx()
        val box = android.graphics.RectF(x, y - paint.textSize, x + width, y + 4.dp.toPx())
        fun overlaps() = occupied.any { android.graphics.RectF.intersects(it, box) } || symbols.any { android.graphics.RectF.intersects(it, box) }
        // Retain the chosen lane instead of bouncing back to lane zero every frame.
        if (overlaps()) { slot = 0; y = center.y - 7.dp.toPx(); box.set(x, y - paint.textSize, x + width, y + 4.dp.toPx()) }
        while (slot < 16 && overlaps()) { slot++; y += 17.dp.toPx(); box.offset(0f, 17.dp.toPx()) }
        if (overlaps()) return@forEach
        if (y > size.height - 150.dp.toPx()) return@forEach
        val point = layout.place(body.id, Offset(x, y), slot, blend)
        box.set(point.x, point.y - paint.textSize, point.x + width, point.y + 4.dp.toPx())
        occupied += box
        drawLine(body.color.copy(alpha = .45f * alpha), center, Offset(point.x - 3.dp.toPx(), point.y - paint.textSize * .3f), 1.dp.toPx())
        drawContext.canvas.nativeCanvas.drawText(text, point.x, point.y, paint)
    }
}
