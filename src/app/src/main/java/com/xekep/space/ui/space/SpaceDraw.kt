package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import kotlin.math.hypot
import kotlin.random.Random

fun DrawScope.drawBody(
    body: CelestialBody,
    viewport: IntSize,
    cameraCenter: com.xekep.space.sim.Vec2,
    zoom: Float,
) {
    val center = worldToScreen(body.position, viewport, cameraCenter, zoom)
    val margin = 120.dp.toPx()
    if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) {
        drawVehicle(body, center, zoom)
        return
    }
    val screenRadius = (body.radius * zoom).coerceIn(4f, 38f)
    val glowScale = when (body.kind) {
        BodyKind.Core -> 3.4f
        BodyKind.Meteor -> 2.4f
        else -> 2.8f
    }
    val glowAlpha = when (body.kind) {
        BodyKind.Core -> 0.2f
        BodyKind.Meteor -> 0.12f
        else -> 0.14f
    }

    drawCircle(
        color = body.color.copy(alpha = glowAlpha),
        radius = screenRadius * glowScale,
        center = center,
    )

    if (body.kind == BodyKind.Core) {
        drawCircle(
            color = Color.White.copy(alpha = 0.16f),
            radius = screenRadius * 1.45f,
            center = center,
            style = Stroke(width = 2.2f),
        )
    }

    if (body.kind == BodyKind.Meteor) {
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = screenRadius * 1.18f,
            center = center,
            style = Stroke(width = 1.8f),
        )
    }

    drawCircle(
        color = body.color,
        radius = screenRadius,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = screenRadius * 0.32f,
        center = center - Offset(screenRadius * 0.2f, screenRadius * 0.2f),
    )
}

private fun DrawScope.drawVehicle(body: CelestialBody, center: Offset, zoom: Float) {
    val r = (body.radius * zoom).coerceIn(9.dp.toPx(), 18.dp.toPx())
    val heading = if (body.kind == BodyKind.Rocket || body.velocity.magnitude() < 1e-6) body.heading else body.velocity.normalized()
    val angle = (kotlin.math.atan2(heading.y, heading.x) * 180.0 / Math.PI + 90.0).toFloat()
    drawCircle(body.color.copy(alpha = .10f), r * 1.8f, center)
    rotate(angle, center) {
        fun hull(points: List<Offset>, color: Color) {
            drawPath(Path().apply {
                moveTo(center.x + points[0].x * r, center.y + points[0].y * r)
                points.drop(1).forEach { lineTo(center.x + it.x * r, center.y + it.y * r) }
                close()
            }, color)
        }
        if (body.kind == BodyKind.Ship) {
            hull(listOf(Offset(0f, -1.2f), Offset(.95f, .7f), Offset(.35f, .45f), Offset(0f, .7f), Offset(-.35f, .45f), Offset(-.95f, .7f)), body.color)
            hull(listOf(Offset(0f, -.8f), Offset(.22f, .25f), Offset(-.22f, .25f)), Color(0xFF14334F))
        } else {
            hull(listOf(Offset(0f, -1.3f), Offset(.35f, -.55f), Offset(.35f, .8f), Offset(-.35f, .8f), Offset(-.35f, -.55f)), Color(0xFFEAF3FF))
            hull(listOf(Offset(-.35f, .15f), Offset(-.75f, .9f), Offset(-.35f, .75f)), body.color)
            hull(listOf(Offset(.35f, .15f), Offset(.75f, .9f), Offset(.35f, .75f)), body.color)
            drawCircle(Color(0xFF276B95), r * .19f, center + Offset(0f, -r * .3f))
            if (body.burnRemaining > 0.0) {
                hull(listOf(Offset(-.25f, .8f), Offset(0f, 1.9f), Offset(.25f, .8f)), Color(0xFFFF9851))
                hull(listOf(Offset(-.13f, .8f), Offset(0f, 1.45f), Offset(.13f, .8f)), Color(0xFFFFE6A3))
            }
        }
    }
}

fun DrawScope.drawTrail(
    body: CelestialBody,
    viewport: IntSize,
    cameraCenter: com.xekep.space.sim.Vec2,
    zoom: Float,
    detailed: Boolean = true,
) {
    if (body.trail.size < 2) {
        return
    }
    var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
    for (point in body.trail) {
        minX = minOf(minX, point.x); maxX = maxOf(maxX, point.x)
        minY = minOf(minY, point.y); maxY = maxOf(maxY, point.y)
    }
    val left = cameraCenter.x - viewport.width / (2.0 * zoom); val top = cameraCenter.y - viewport.height / (2.0 * zoom)
    if (maxX < left || maxY < top || minX > left + viewport.width / zoom || minY > top + viewport.height / zoom) return

    val trailBoost = when (body.kind) {
        BodyKind.Meteor -> 0.08f
        BodyKind.Player -> 0.05f
        else -> 0f
    }

    val first = worldToScreen(body.trail.first(), viewport, cameraCenter, zoom)
    val last = worldToScreen(body.trail.last(), viewport, cameraCenter, zoom)
    val path = Path().apply {
        moveTo(first.x, first.y)
        for (index in 1 until body.trail.size step if (detailed) 1 else 2) {
            val point = worldToScreen(body.trail[index], viewport, cameraCenter, zoom)
            lineTo(point.x, point.y)
        }
        lineTo(last.x, last.y)
    }
    if (!detailed) {
        drawPath(path, body.color.copy(alpha = 0.24f + trailBoost), style = Stroke(2f, cap = StrokeCap.Round))
        return
    }
    drawPath(path, Brush.linearGradient(listOf(body.color.copy(alpha = 0.04f), body.color.copy(alpha = 0.32f + trailBoost)),
        first, if ((last - first).getDistance() > 0.01f) last else first + Offset(1f, 0f)),
        style = Stroke(if (body.kind == BodyKind.Meteor) 2.6f else 2.0f, cap = StrokeCap.Round))
}

fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )

    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (length <= 0f) {
        return
    }

    val direction = Offset(dx / length, dy / length)
    val normal = Offset(-direction.y, direction.x)
    val headLength = 22f
    val headWidth = 10f
    val base = end - (direction * headLength)

    drawLine(
        color = color,
        start = end,
        end = base + (normal * headWidth),
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = end,
        end = base - (normal * headWidth),
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )
}

fun DrawScope.drawFingerDirection(
    anchor: Offset,
    directionVector: Offset,
    color: Color,
) {
    val length = hypot(directionVector.x.toDouble(), directionVector.y.toDouble()).toFloat()
    if (length <= 0f) {
        return
    }

    val direction = Offset(directionVector.x / length, directionVector.y / length)
    val start = anchor + (direction * 12f)
    val end = start + (direction * 30f)
    drawArrow(start, end, color.copy(alpha = 0.75f))
}

fun nextMeteorDelay(elapsed: Double, random: Random): Double {
    val baseDelay = (1.6 - (elapsed * 0.018)).coerceAtLeast(0.45)
    return baseDelay * random.nextDouble(0.82, 1.14)
}
