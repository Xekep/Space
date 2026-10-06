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
import com.xekep.space.sim.SolarBody
import kotlin.math.hypot
import kotlin.math.pow
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.random.Random

fun DrawScope.drawBody(
    body: CelestialBody,
    viewport: IntSize,
    cameraCenter: com.xekep.space.sim.Vec2,
    zoom: Float,
    cameraRotation: Double = 0.0,
    piloted: Boolean = false,
    largeVehicleIcons: Boolean = false,
) {
    val center = worldToScreen(body.position, viewport, cameraCenter, zoom)
    val margin = maxOf(120.dp.toPx(),bodyScreenRadius(body,zoom,density,largeVehicleIcons)*2f)
    val extent = hypot(size.width,size.height) / 2 + margin
    if (cameraRotation == 0.0) {
        if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return
    } else if ((center-this.center).getDistance() > extent) return
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) {
        drawVehicle(body, center, zoom, piloted, largeVehicleIcons)
        return
    }
    val screenRadius = bodyScreenRadius(body, zoom, density)
    if (body.kind == BodyKind.BlackHole) {
        drawCircle(Color(0xFFAB78E8).copy(alpha=.14f),screenRadius*2.8f,center)
        rotate(-22f,center) {
            drawOval(Color(0xFFF2BB7C).copy(alpha=.75f),center-Offset(screenRadius*2.2f,screenRadius*.65f),
                androidx.compose.ui.geometry.Size(screenRadius*4.4f,screenRadius*1.3f),style=Stroke(screenRadius*.23f))
        }
        drawCircle(Color(0xFF020309),screenRadius,center)
        drawCircle(body.color,screenRadius*1.12f,center,style=Stroke(1.5.dp.toPx()))
        drawArc(Color(0xFFFFE9C6),205f,105f,false,center-Offset(screenRadius*1.14f,screenRadius*1.14f),
            androidx.compose.ui.geometry.Size(screenRadius*2.28f,screenRadius*2.28f),style=Stroke(2.dp.toPx()))
        return
    }
    if (body.solar == SolarBody.Saturn) {
        rotate(-24f, center) {
            drawOval(Color(0xFFCCB77F).copy(alpha = .65f), center - Offset(screenRadius * 2.0f, screenRadius * .65f),
                androidx.compose.ui.geometry.Size(screenRadius * 4f, screenRadius * 1.3f), style = Stroke(screenRadius * .28f))
            drawOval(Color(0xFFFFE8B3).copy(alpha = .45f), center - Offset(screenRadius * 2.3f, screenRadius * .76f),
                androidx.compose.ui.geometry.Size(screenRadius * 4.6f, screenRadius * 1.52f), style = Stroke(screenRadius * .09f))
        }
    }
    val glowScale = if (body.solar != null) 1.65f else when (body.kind) {
        BodyKind.Core, BodyKind.Star -> 3.4f
        BodyKind.Meteor -> 2.4f
        else -> 2.8f
    }
    val glowAlpha = if (body.solar != null) .07f else when (body.kind) {
        BodyKind.Core, BodyKind.Star -> 0.2f
        BodyKind.Meteor -> 0.12f
        else -> 0.14f
    }

    drawCircle(
        color = body.color.copy(alpha = glowAlpha),
        radius = screenRadius * glowScale,
        center = center,
    )

    if (body.kind == BodyKind.Core || body.kind == BodyKind.Star) {
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
    if (body.solar == SolarBody.Jupiter || body.solar == SolarBody.Saturn) {
        clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(screenRadius, screenRadius), center + Offset(screenRadius, screenRadius))) }) {
            listOf(-.5f, -.12f, .32f, .58f).forEach { y ->
                drawLine(Color(0xFF957453).copy(alpha = .35f), center + Offset(-screenRadius, y * screenRadius),
                    center + Offset(screenRadius, y * screenRadius), screenRadius * .14f)
            }
        }
    }
    if (body.solar == SolarBody.Earth) {
        drawOval(Color(0xFF75BB83), center + Offset(-screenRadius * .50f, -screenRadius * .65f), androidx.compose.ui.geometry.Size(screenRadius * .6f, screenRadius * .9f))
        drawOval(Color(0xFF75BB83), center + Offset(screenRadius * .04f, screenRadius * .05f), androidx.compose.ui.geometry.Size(screenRadius * .42f, screenRadius * .66f))
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = screenRadius * 0.32f,
        center = center - Offset(screenRadius * 0.2f, screenRadius * 0.2f),
    )
}

private fun DrawScope.drawVehicle(body: CelestialBody, center: Offset, zoom: Float, piloted: Boolean, largeVehicleIcons: Boolean) {
    val r = bodyScreenRadius(body, zoom, density, largeVehicleIcons)
    val heading = body.heading
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
            // Twin nacelles, swept wings, central fuselage and luminous cockpit.
            hull(listOf(Offset(-.22f, -.45f), Offset(-1.15f, .35f), Offset(-1.05f, .85f), Offset(-.28f, .45f)), Color(0xFF537A9A))
            hull(listOf(Offset(.22f, -.45f), Offset(1.15f, .35f), Offset(1.05f, .85f), Offset(.28f, .45f)), Color(0xFF537A9A))
            listOf(-.78f, .78f).forEach { x ->
                hull(listOf(Offset(x - .14f, -.4f), Offset(x, -.65f), Offset(x + .14f, -.4f), Offset(x + .14f, .85f), Offset(x - .14f, .85f)), body.color)
                drawLine(Color(0xFF72E9FF), center + Offset(x * r, .8f * r), center + Offset(x * r, 1.18f * r), r * .12f, StrokeCap.Round)
            }
            hull(listOf(Offset(0f, -1.35f), Offset(.32f, -.48f), Offset(.29f, .67f), Offset(0f, .9f), Offset(-.29f, .67f), Offset(-.32f, -.48f)), Color(0xFFD6EAF5))
            hull(listOf(Offset(0f, -.92f), Offset(.18f, -.4f), Offset(.15f, .05f), Offset(-.15f, .05f), Offset(-.18f, -.4f)), Color(0xFF1D5E87))
            drawLine(Color(0xFFB0FBFF), center + Offset(0f, -.72f * r), center + Offset(0f, -.25f * r), r * .07f, StrokeCap.Round)
            if (piloted && body.fuelRemaining > 1e-9) listOf(-.78f,.78f).forEach { x ->
                hull(listOf(Offset(x-.1f,.85f),Offset(x,1.45f+body.pilotThrottle.toFloat()*.7f),Offset(x+.1f,.85f)),Color(0xFF9EF8FF))
            }
        } else {
            hull(listOf(Offset(0f, -1.3f), Offset(.35f, -.55f), Offset(.35f, .8f), Offset(-.35f, .8f), Offset(-.35f, -.55f)), Color(0xFFEAF3FF))
            hull(listOf(Offset(-.35f, .15f), Offset(-.75f, .9f), Offset(-.35f, .75f)), body.color)
            hull(listOf(Offset(.35f, .15f), Offset(.75f, .9f), Offset(.35f, .75f)), body.color)
            drawCircle(Color(0xFF276B95), r * .19f, center + Offset(0f, -r * .3f))
            if (body.fuelRemaining > 1e-9) {
                hull(listOf(Offset(-.25f, .8f), Offset(0f, if (piloted) 1.6f+body.pilotThrottle.toFloat()*.8f else 1.9f), Offset(.25f, .8f)), Color(0xFFFF9851))
                hull(listOf(Offset(-.13f, .8f), Offset(0f, 1.45f), Offset(.13f, .8f)), Color(0xFFFFE6A3))
            }
        }
    }
}

fun bodyScreenRadius(body: CelestialBody, zoom: Float, density: Float, largeVehicleIcons: Boolean = false): Float {
    if (body.kind == BodyKind.Star) return (body.radius*zoom).coerceIn(14f*density,45f*density)
    if (body.kind == BodyKind.BlackHole) return (body.radius*zoom).coerceIn(9f*density,24f*density)
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) return if (largeVehicleIcons)
        (body.radius*zoom).coerceIn(14f*density,24f*density) else body.radius*zoom
    val solar = body.solar ?: return (body.radius * zoom).coerceIn(4f, 38f)
    val symbolDp = if (solar == SolarBody.Sun) 22.0 else
        (6.0 * (solar.radiusKm / 6371.0).pow(.40)).coerceIn(3.0, 17.0)
    return maxOf(body.radius * zoom, symbolDp.toFloat() * density).coerceAtMost(60f * density)
}

fun DrawScope.drawTrail(
    body: CelestialBody,
    viewport: IntSize,
    cameraCenter: com.xekep.space.sim.Vec2,
    zoom: Float,
    detailed: Boolean = true,
    cameraRotation: Double = 0.0,
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
    val width=if (cameraRotation == 0.0) viewport.width.toDouble() else hypot(viewport.width.toDouble(),viewport.height.toDouble())
    val height=if (cameraRotation == 0.0) viewport.height.toDouble() else width
    val left = cameraCenter.x - width / (2.0 * zoom); val top = cameraCenter.y - height / (2.0 * zoom)
    if (maxX < left || maxY < top || minX > left + width / zoom || minY > top + height / zoom) return

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
