package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
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
import com.xekep.space.sim.enginePowered
import com.xekep.space.sim.hullClass
import com.xekep.space.sim.VehicleHullClass
import com.xekep.space.sim.vehicleSizeScale
import kotlin.math.hypot
import kotlin.math.abs
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
    renderPosition: com.xekep.space.sim.Vec2 = body.position,
    renderHeading: com.xekep.space.sim.Vec2 = body.heading,
    simple: Boolean = false,
) {
    val center = worldToScreen(renderPosition, viewport, cameraCenter, zoom)
    val margin = maxOf(120.dp.toPx(),bodyScreenRadius(body,zoom,density,largeVehicleIcons)*2f)
    val extent = hypot(size.width,size.height) / 2 + margin
    if (cameraRotation == 0.0) {
        if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return
    } else if ((center-this.center).getDistance() > extent) return
    if (body.kind == BodyKind.Convoy) { drawConvoy(body,center,zoom,renderHeading); return }
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) {
        drawVehicle(body, center, zoom, piloted, largeVehicleIcons,renderHeading)
        return
    }
    if (simple && body.solar == null && body.kind in listOf(BodyKind.Ambient,BodyKind.Player)) {
        val radius=if (body.galaxySystemId != null) bodyScreenRadius(body,zoom,density) else (body.radius*zoom).coerceIn(2.5f,38f)
        if (body.orbitParentId == body.galaxySystemId && body.galaxySystemId != null && body.mass > 60 && zoom > .12f) {
            rotate(-25f,center) {
                drawOval(body.color.copy(alpha=.55f),center-Offset(radius*1.8f,radius*.65f),
                    Size(radius*3.6f,radius*1.3f),style=Stroke(maxOf(.7f,radius*.16f)))
            }
        }
        drawCircle(body.color,radius,center)
        return
    }
    val screenRadius = bodyScreenRadius(body, zoom, density)
    if (body.kind == BodyKind.BlackHole) {
        drawBlackHole(center,screenRadius)
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
    if (body.solar == SolarBody.Earth || body.kind == BodyKind.ArcadePlanet) {
        drawOval(Color(0xFF75BB83), center + Offset(-screenRadius * .50f, -screenRadius * .65f), androidx.compose.ui.geometry.Size(screenRadius * .6f, screenRadius * .9f))
        drawOval(Color(0xFF75BB83), center + Offset(screenRadius * .04f, screenRadius * .05f), androidx.compose.ui.geometry.Size(screenRadius * .42f, screenRadius * .66f))
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = screenRadius * 0.32f,
        center = center - Offset(screenRadius * 0.2f, screenRadius * 0.2f),
    )
}

private fun DrawScope.drawVehicle(body: CelestialBody, center: Offset, zoom: Float, piloted: Boolean, largeVehicleIcons: Boolean,heading: com.xekep.space.sim.Vec2) {
    val r = bodyScreenRadius(body, zoom, density, largeVehicleIcons)
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
        if (body.hullClass == VehicleHullClass.Heavy && body.kind == BodyKind.Ship) {
            val guardian=body.shipClass == com.xekep.space.sim.ShipClass.Guardian
            val trim=if (guardian) Color(0xFF81E5C4) else Color(0xFFE6B878)
            // Broad armored hull, four engine pods, layered plates and twin gun shoulders.
            hull(listOf(Offset(0f,-1.3f),Offset(.45f,-.85f),Offset(.48f,-.35f),Offset(1.15f,-.12f),
                Offset(1.05f,.8f),Offset(.4f,.92f),Offset(0f,.68f),Offset(-.4f,.92f),
                Offset(-1.05f,.8f),Offset(-1.15f,-.12f),Offset(-.48f,-.35f),Offset(-.45f,-.85f)),Color(0xFF354657))
            for (side in listOf(-1f,1f)) {
                hull(listOf(Offset(side*.45f,-.32f),Offset(side*1.08f,-.03f),Offset(side*.93f,.55f),Offset(side*.44f,.7f)),Color(0xFF8498A8))
                hull(listOf(Offset(side*.45f,-.32f),Offset(side*.88f,-.11f),Offset(side*.7f,.15f),Offset(side*.44f,.09f)),trim)
                drawLine(trim,center+Offset(side*.86f*r,-.09f*r),center+Offset(side*.86f*r,-.65f*r),r*.13f,StrokeCap.Round)
                drawLine(Color(0xFF233647),center+Offset(side*.5f*r,.3f*r),center+Offset(side*.96f*r,.4f*r),r*.07f)
            }
            for (x in listOf(-.85f,-.35f,.35f,.85f)) {
                hull(listOf(Offset(x-.11f,.48f),Offset(x+.11f,.48f),Offset(x+.11f,.98f),Offset(x-.11f,.98f)),body.color)
                if (body.enginePowered) hull(listOf(Offset(x-.08f,.95f),Offset(x,1.24f+(if (piloted) body.pilotThrottle.toFloat()*.45f else .18f)),Offset(x+.08f,.95f)),Color(0xFF9EEAFF))
            }
            hull(listOf(Offset(0f,-1.25f),Offset(.32f,-.7f),Offset(.3f,.42f),Offset(0f,.68f),Offset(-.3f,.42f),Offset(-.32f,-.7f)),Color(0xFFD8E0E5))
            hull(listOf(Offset(0f,-.87f),Offset(.19f,-.52f),Offset(.16f,-.12f),Offset(-.16f,-.12f),Offset(-.19f,-.52f)),Color(0xFF173D54))
            drawLine(trim,center+Offset(-.23f*r,.2f*r),center+Offset(.23f*r,.2f*r),r*.09f)
            drawLine(Color(0xFFA5E9FF),center+Offset(0f,-.74f*r),center+Offset(0f,-.31f*r),r*.07f,StrokeCap.Round)
        } else if (body.hullClass == VehicleHullClass.Heavy && body.kind == BodyKind.Rocket) {
            // Armored warhead with side boosters, segmented casing and gold identification bands.
            hull(listOf(Offset(0f,-1.3f),Offset(.48f,-.65f),Offset(.48f,.76f),Offset(-.48f,.76f),Offset(-.48f,-.65f)),Color(0xFFA4AFBA))
            hull(listOf(Offset(0f,-1.27f),Offset(.44f,-.69f),Offset(-.44f,-.69f)),Color(0xFFE6B878))
            hull(listOf(Offset(-.26f,-.62f),Offset(.26f,-.62f),Offset(.26f,.73f),Offset(-.26f,.73f)),Color(0xFFE0E7EB))
            for (x in listOf(-.65f,.65f)) {
                hull(listOf(Offset(x,-.5f),Offset(x+.14f,-.22f),Offset(x+.14f,.9f),Offset(x-.14f,.9f),Offset(x-.14f,-.22f)),Color(0xFF50667B))
                drawLine(body.color,center+Offset(x*r,-.17f*r),center+Offset(x*r,.45f*r),r*.08f)
                if (body.enginePowered) hull(listOf(Offset(x-.1f,.87f),Offset(x,1.3f),Offset(x+.1f,.87f)),Color(0xFFFFB86E))
            }
            for (y in listOf(-.53f,.16f,.57f)) drawLine(Color(0xFFE6B878),center+Offset(-.46f*r,y*r),center+Offset(.46f*r,y*r),r*.08f)
            drawCircle(Color(0xFF23465E),r*.15f,center+Offset(0f,-r*.27f))
            if (body.enginePowered) {
                hull(listOf(Offset(-.27f,.76f),Offset(0f,if (piloted) 1.5f+body.pilotThrottle.toFloat()*.7f else 1.8f),Offset(.27f,.76f)),Color(0xFFFF9851))
                hull(listOf(Offset(-.12f,.76f),Offset(0f,1.25f),Offset(.12f,.76f)),Color(0xFFFFE6A3))
            }
        } else if (body.kind == BodyKind.Ship && body.shipClass == com.xekep.space.sim.ShipClass.Guardian) {
            hull(listOf(Offset(0f,-1.15f),Offset(.65f,-.55f),Offset(.65f,.45f),Offset(0f,.8f),Offset(-.65f,.45f),Offset(-.65f,-.55f)),Color(0xFFD5F6EA))
            hull(listOf(Offset(-.6f,-.25f),Offset(-1.1f,0f),Offset(-1.05f,.75f),Offset(-.5f,.5f)),body.color)
            hull(listOf(Offset(.6f,-.25f),Offset(1.1f,0f),Offset(1.05f,.75f),Offset(.5f,.5f)),body.color)
            drawCircle(Color(0xFF245C59),r*.3f,center-Offset(0f,r*.28f))
            listOf(-.85f,.85f).forEach { x ->
                drawLine(Color(0xFFE3FFF1),center+Offset(x*r,0f),center+Offset(x*r,-.5f*r),r*.12f,StrokeCap.Round)
                if (body.enginePowered) drawLine(Color(0xFF81E5C4),center+Offset(x*r,.65f*r),
                    center+Offset(x*r,(if (piloted) 1.15f+body.pilotThrottle.toFloat()*.5f else .95f)*r),r*.13f,StrokeCap.Round)
            }
        } else if (body.kind == BodyKind.Ship) {
            // Twin nacelles, swept wings, central fuselage and luminous cockpit.
            hull(listOf(Offset(-.22f, -.45f), Offset(-1.15f, .35f), Offset(-1.05f, .85f), Offset(-.28f, .45f)), Color(0xFF537A9A))
            hull(listOf(Offset(.22f, -.45f), Offset(1.15f, .35f), Offset(1.05f, .85f), Offset(.28f, .45f)), Color(0xFF537A9A))
            listOf(-.78f, .78f).forEach { x ->
                hull(listOf(Offset(x - .14f, -.4f), Offset(x, -.65f), Offset(x + .14f, -.4f), Offset(x + .14f, .85f), Offset(x - .14f, .85f)), body.color)
                if (body.enginePowered) drawLine(Color(0xFF72E9FF), center + Offset(x * r, .8f * r), center + Offset(x * r, 1.18f * r), r * .12f, StrokeCap.Round)
            }
            hull(listOf(Offset(0f, -1.35f), Offset(.32f, -.48f), Offset(.29f, .67f), Offset(0f, .9f), Offset(-.29f, .67f), Offset(-.32f, -.48f)), Color(0xFFD6EAF5))
            hull(listOf(Offset(0f, -.92f), Offset(.18f, -.4f), Offset(.15f, .05f), Offset(-.15f, .05f), Offset(-.18f, -.4f)), Color(0xFF1D5E87))
            drawLine(Color(0xFFB0FBFF), center + Offset(0f, -.72f * r), center + Offset(0f, -.25f * r), r * .07f, StrokeCap.Round)
            if (piloted && body.enginePowered) listOf(-.78f,.78f).forEach { x ->
                hull(listOf(Offset(x-.1f,.85f),Offset(x,1.45f+body.pilotThrottle.toFloat()*.7f),Offset(x+.1f,.85f)),Color(0xFF9EF8FF))
            }
        } else {
            hull(listOf(Offset(0f, -1.3f), Offset(.35f, -.55f), Offset(.35f, .8f), Offset(-.35f, .8f), Offset(-.35f, -.55f)), Color(0xFFEAF3FF))
            hull(listOf(Offset(-.35f, .15f), Offset(-.75f, .9f), Offset(-.35f, .75f)), body.color)
            hull(listOf(Offset(.35f, .15f), Offset(.75f, .9f), Offset(.35f, .75f)), body.color)
            drawCircle(Color(0xFF276B95), r * .19f, center + Offset(0f, -r * .3f))
            if (body.enginePowered) {
                hull(listOf(Offset(-.25f, .8f), Offset(0f, if (piloted) 1.6f+body.pilotThrottle.toFloat()*.8f else 1.9f), Offset(.25f, .8f)), Color(0xFFFF9851))
                hull(listOf(Offset(-.13f, .8f), Offset(0f, 1.45f), Offset(.13f, .8f)), Color(0xFFFFE6A3))
            }
        }
    }
}

private fun DrawScope.drawConvoy(body: CelestialBody, center: Offset, zoom: Float, heading: com.xekep.space.sim.Vec2) {
    val r=bodyScreenRadius(body,zoom,density)
    drawCircle(body.color.copy(alpha=.1f),r*2f,center)
    rotate((kotlin.math.atan2(heading.y,heading.x)*180/Math.PI+90).toFloat(),center) {
        drawRoundRect(Color(0xFFD1E8E3),center+Offset(-r*.36f,-r*1.2f),
            androidx.compose.ui.geometry.Size(r*.72f,r*2.1f),androidx.compose.ui.geometry.CornerRadius(r*.2f))
        for (x in listOf(-.75f,.75f)) {
            drawRoundRect(Color(0xFF4B7D78),center+Offset((x-.3f)*r,-r*.45f),
                androidx.compose.ui.geometry.Size(r*.6f,r*1.45f),androidx.compose.ui.geometry.CornerRadius(r*.1f))
            drawLine(body.color,center+Offset(x*r,r),center+Offset(x*r,r*1.35f),r*.12f,StrokeCap.Round)
            for (y in listOf(-.15f,.3f,.75f)) drawLine(body.color.copy(alpha=.8f),
                center+Offset((x-.22f)*r,y*r),center+Offset((x+.22f)*r,y*r),r*.07f)
        }
        drawCircle(Color(0xFF286F71),r*.22f,center+Offset(0f,-r*.75f))
    }
}

internal fun DrawScope.drawWorldBodies(bodies: List<CelestialBody>,viewport: IntSize,camera: SpaceCamera,rotation: Double,
    controlledId: Long?,largeIcons: Boolean,interpolation: SandboxInterpolation,large: Boolean) {
    val dots=if (large) LinkedHashMap<Pair<Color,Float>,MutableList<Offset>>() else null
    val stars=if (large) LinkedHashMap<Triple<Color,Float,Boolean>,MutableList<Offset>>() else null
    bodies.forEach { body ->
        val stellarRadius=bodyScreenRadius(body,camera.zoom,density)
        if (stars != null && body.kind == BodyKind.Star &&
            (body.galaxyParticle || body.galaxySystemId != null && camera.zoom < .08f) && stellarRadius <= 8.5*density) {
            val point=worldToScreen(interpolation.position(body),viewport,camera.center,camera.zoom)
            val margin=stellarRadius*3.4f
            val visible=if (abs(rotation) < .001) point.x >= -margin && point.y >= -margin && point.x <= size.width+margin && point.y <= size.height+margin
                else (point-center).getDistance() <= hypot(size.width,size.height)*.5f+margin
            if (visible) {
                val radius=kotlin.math.round(stellarRadius*2)/2
                stars.getOrPut(Triple(body.color,radius,body.galaxySystemId != null)) { ArrayList() }+=point
            }
        } else if (dots != null && body.kind == BodyKind.Ambient && body.solar == null && body.mass < 2 && body.radius*camera.zoom <= 2.5f) {
            val point=worldToScreen(interpolation.position(body),viewport,camera.center,camera.zoom)
            val visible=if (abs(rotation) < .001) point.x >= -4 && point.y >= -4 && point.x <= size.width+4 && point.y <= size.height+4
                else (point-center).getDistance() <= hypot(size.width,size.height)*.5f+4
            val width=if (body.galaxySystemId != null) kotlin.math.round(stellarRadius*4)/2 else 2.5f
            if (visible) dots.getOrPut(body.color to width) { ArrayList() }+=point
        } else drawBody(body,viewport,camera.center,camera.zoom,rotation,body.id == controlledId,largeIcons,
            interpolation.position(body),if (body.id == controlledId) body.heading else interpolation.heading(body),simple=large)
    }
    dots?.forEach { (style,points) -> drawPoints(points,PointMode.Points,style.first,strokeWidth=style.second,cap=StrokeCap.Round) }
    stars?.forEach { (style,points) ->
        val (color,radius,resolved)=style
        drawPoints(points,PointMode.Points,color.copy(alpha=if (resolved) .10f else .13f),strokeWidth=radius*(if (resolved) 3.8f else 5.5f),cap=StrokeCap.Round)
        drawPoints(points,PointMode.Points,color,strokeWidth=radius*2,cap=StrokeCap.Round)
        drawPoints(points,PointMode.Points,Color.White.copy(alpha=.25f),strokeWidth=radius*.65f,cap=StrokeCap.Round)
    }
}

fun bodyScreenRadius(body: CelestialBody, zoom: Float, density: Float, largeVehicleIcons: Boolean = false): Float {
    if (body.kind == BodyKind.Convoy) return (body.radius*zoom).coerceIn(10f*density,22f*density)
    if (body.kind == BodyKind.Star) {
        // Small stellar populations remain points at galaxy scale, rather than 500 giant icons.
        val minimum=when {
            body.galaxySystemId != null -> (1.5f+body.radius*.10f).coerceAtMost(8.5f)
            body.galaxyParticle -> minOf(4f,body.radius*.4f)
            else -> 14f
        }*density
        return (body.radius*zoom).coerceIn(minimum,45f*density)
    }
    if (body.kind == BodyKind.BlackHole) return (body.radius*zoom).coerceIn(9f*density,24f*density)
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) return if (largeVehicleIcons)
        (body.radius*zoom).coerceIn(14f*density*vehicleSizeScale(body.kind,body.mass).toFloat(),
            24f*density*vehicleSizeScale(body.kind,body.mass).toFloat()) else body.radius*zoom
    if (body.galaxySystemId != null) return (body.radius*zoom).coerceIn(minOf(3f,body.radius*.65f)*density,38f*density)
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
    renderPosition: com.xekep.space.sim.Vec2 = body.position,
    dense: Boolean = false,
    highlighted: Boolean = false,
    maxLengthDp: Float = if (dense) 48f else Float.POSITIVE_INFINITY,
) {
    val length=(if (body.isDebris) minOf(20f,maxLengthDp) else maxLengthDp)*density
    val points=trailScreenPoints(body,viewport,SpaceCamera(cameraCenter,zoom),renderPosition,length,
        (if (dense) 4f else 2f)*density)
    if (points.size < 2 || points.zipWithNext().sumOf { (a,b) -> (b-a).getDistance().toDouble() } < 1.5) return
    val curves=smoothTrail(points)
    val bands=if (detailed && !dense) 4 else 3
    val paths=Array(bands) { Path() }
    var previousBand=-1
    curves.forEachIndexed { i,curve ->
        val band=((i+1)*bands/curves.size-1).coerceIn(0,bands-1)
        val path=paths[band]
        if (band != previousBand) path.moveTo(curve.start.x,curve.start.y)
        val control=curve.control
        if (control == null) path.lineTo(curve.end.x,curve.end.y)
        else path.quadraticBezierTo(control.x,control.y,curve.end.x,curve.end.y)
        previousBand=band
    }
    val boost=if (body.kind == BodyKind.Meteor) .08f else if (highlighted) .1f else 0f
    for (band in paths.indices) {
        val age=(band+1f)/bands
        val opacity=.025f+(if (dense && !highlighted) .15f else .28f)*age*age+boost*age
        drawPath(paths[band],body.color.copy(alpha=opacity),style=Stroke(
            (if (dense && !highlighted) .65f else .85f)*density,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
    }
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
