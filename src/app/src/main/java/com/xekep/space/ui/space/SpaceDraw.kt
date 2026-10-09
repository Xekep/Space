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
import androidx.compose.ui.graphics.drawscope.withTransform
import com.xekep.space.sim.flightVisualScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.SolarBody
import com.xekep.space.sim.OrbitalDetail
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
    pilotVisualZoom: Float? = null,
) {
    val center = worldToScreen(flightRenderPosition(body,renderPosition,viewport,zoom,cameraRotation), viewport, cameraCenter, zoom)
    val margin = maxOf(120.dp.toPx(),bodyScreenRadius(body,zoom,density,largeVehicleIcons)*2f)
    val extent = hypot(size.width,size.height) / 2 + margin
    if (cameraRotation == 0.0) {
        if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return
    } else if ((center-this.center).getDistance() > extent) return
    if (body.orbitalDetail != null) {
        val r = bodyScreenRadius(body, zoom, density)
        if (body.orbitalDetail == OrbitalDetail.RingGrain) {
            drawCircle(body.color.copy(alpha=.65f), r, center)
        } else {
            rotate((kotlin.math.atan2(renderHeading.y, renderHeading.x)*180/Math.PI).toFloat(), center) {
                drawRect(Color(0xFF689ABD), center-Offset(r*2.5f,r*.65f), Size(r*1.5f,r*1.3f))
                drawRect(Color(0xFF689ABD), center+Offset(r,r*-.65f), Size(r*1.5f,r*1.3f))
                drawRect(Color(0xFFE6EDF1), center-Offset(r*.55f,r*.55f), Size(r*1.1f,r*1.1f))
            }
        }
        return
    }
    if (body.kind == BodyKind.Convoy) { drawConvoy(body,center,zoom,renderHeading); return }
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) {
        drawVehicle(body, center, zoom, piloted, largeVehicleIcons,renderHeading,pilotVisualZoom)
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

private fun DrawScope.drawVehicle(body: CelestialBody, center: Offset, zoom: Float, piloted: Boolean, largeVehicleIcons: Boolean,heading: com.xekep.space.sim.Vec2,pilotVisualZoom: Float?) {
    val r = vehicleRenderRadius(body,zoom,density,largeVehicleIcons,if (piloted) pilotVisualZoom else null)
    val angle = (kotlin.math.atan2(heading.y, heading.x) * 180.0 / Math.PI + 90.0).toFloat()
    drawCircle(body.color.copy(alpha = .10f), r * 1.8f, center)
    rotate(angle,center) { drawVehicleMesh(body,center,r,piloted) }
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
    controlledId: Long?,largeIcons: Boolean,interpolation: SandboxInterpolation,large: Boolean,
    sceneBodies: List<CelestialBody> = bodies,pilotVisualZoom: Float? = null,
    vehicleTrail: (DrawScope.(CelestialBody)->Unit)? = null) {
    sceneBodies.filter { it.solar == SolarBody.Jupiter || it.solar == SolarBody.Saturn }.forEach {
        drawPlanetRings(it,sceneBodies,viewport,camera,interpolation.position(it))
    }
    val dots=if (large) LinkedHashMap<Pair<Color,Float>,MutableList<Offset>>() else null
    val stars=if (large) LinkedHashMap<Triple<Color,Float,Boolean>,MutableList<Offset>>() else null
    fun vehicle(body: CelestialBody) {
        vehicleTrail?.invoke(this,body)
        drawBody(body,viewport,camera.center,camera.zoom,rotation,body.id == controlledId,largeIcons,
            interpolation.position(body),if (body.id == controlledId) body.heading else interpolation.heading(body),simple=large,pilotVisualZoom=pilotVisualZoom)
    }
    val flying=bodies.filter { it.flightHeight != 0.0 }
    flying.filter { it.flightHeight < 0 }.sortedBy { it.flightHeight }.forEach { vehicle(it) }
    bodies.forEach { body ->
        if (body.flightHeight != 0.0) return@forEach
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
            interpolation.position(body),if (body.id == controlledId) body.heading else interpolation.heading(body),simple=large,pilotVisualZoom=pilotVisualZoom)
    }
    dots?.forEach { (style,points) -> drawPoints(points,PointMode.Points,style.first,strokeWidth=style.second,cap=StrokeCap.Round) }
    stars?.forEach { (style,points) ->
        val (color,radius,resolved)=style
        drawPoints(points,PointMode.Points,color.copy(alpha=if (resolved) .10f else .13f),strokeWidth=radius*(if (resolved) 3.8f else 5.5f),cap=StrokeCap.Round)
        drawPoints(points,PointMode.Points,color,strokeWidth=radius*2,cap=StrokeCap.Round)
        drawPoints(points,PointMode.Points,Color.White.copy(alpha=.25f),strokeWidth=radius*.65f,cap=StrokeCap.Round)
    }
    flying.filter { it.flightHeight > 0 }.sortedBy { it.flightHeight }.forEach { vehicle(it) }
}

fun bodyScreenRadius(body: CelestialBody, zoom: Float, density: Float, largeVehicleIcons: Boolean = false): Float {
    if (body.orbitalDetail != null) return if (body.orbitalDetail == OrbitalDetail.RingGrain)
        (body.radius*zoom).coerceIn(.25f*density,.6f*density) else (body.radius*zoom).coerceIn(.85f*density,1.5f*density)
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
    if (body.kind == BodyKind.BlackHole) return (body.radius*zoom).coerceIn((if (body.physicalScale) 2f else 9f)*density,24f*density)
    if (body.kind == BodyKind.Ship || body.kind == BodyKind.Rocket) {
        val hullScale=vehicleSizeScale(body.kind,body.mass).toFloat()
        // Symbolic catalogue hulls remain zoomable; contact radii stay physically tiny.
        val rendered=if (body.physicalScale) (if (body.kind == BodyKind.Ship) .08f else .06f)*hullScale*zoom else body.radius*zoom
        return (if (largeVehicleIcons) rendered.coerceIn(14f*density*hullScale,
            (if (body.physicalScale) 180f else 24f)*density*hullScale) else rendered)*body.flightVisualScale()
    }
    if (body.galaxySystemId != null) return (body.radius*zoom).coerceIn(minOf(3f,body.radius*.65f)*density,38f*density)
    val solar = body.solar ?: return (body.radius * zoom).coerceIn(4f, 38f)
    // Readable catalogue symbols at overview scale; zoom reveals physical size ratios.
    val minimum = when (solar) {
        SolarBody.Sun -> 14f
        SolarBody.Jupiter -> 9f
        SolarBody.Saturn -> 8f
        SolarBody.Uranus,SolarBody.Neptune -> 6.5f
        SolarBody.Earth,SolarBody.Venus -> 6f
        SolarBody.Mars -> 5f
        SolarBody.Mercury -> 3.5f
        SolarBody.Pluto -> 2.5f
        else -> 1.8f
    }
    return maxOf(body.radius*zoom,minimum*density).coerceAtMost(480f*density)
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
    vehicleRadius: Float? = null,
    renderHeading: com.xekep.space.sim.Vec2 = body.heading,
) {
    if (body.orbitalDetail != null) return
    val length=(if (body.isDebris) minOf(20f,maxLengthDp) else maxLengthDp)*density
    val history=trailScreenPoints(body,viewport,SpaceCamera(cameraCenter,zoom),flightRenderPosition(body,renderPosition,viewport,zoom,cameraRotation),length,
        (if (dense) 4f else 2f)*density)
    val points=if (vehicleRadius != null) vehicleTrailPoints(history,body,renderHeading,vehicleRadius) else history
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

/** Same initial pilot symbol and pinch response in arcade, sandbox and catalogue units. */
internal fun pilotScreenRadius(body: CelestialBody,relativeZoom: Float,density: Float,largeIcons: Boolean): Float {
    val base=(if (body.kind == BodyKind.Ship) 14f else 12f)*density*vehicleSizeScale(body.kind,body.mass).toFloat()
    val factor=relativeZoom.coerceIn(if (largeIcons) 1f else .02f,6f)
    return base*factor*body.flightVisualScale()
}


internal fun vehicleRenderRadius(body: CelestialBody,zoom: Float,density: Float,largeIcons: Boolean,pilotVisualZoom: Float?): Float =
    if (pilotVisualZoom != null) pilotScreenRadius(body,pilotVisualZoom,density,largeIcons)
    else bodyScreenRadius(body,zoom,density,largeIcons)
