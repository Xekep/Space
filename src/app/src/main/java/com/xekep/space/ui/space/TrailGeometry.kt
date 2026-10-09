package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import kotlin.math.*

internal data class TrailCurve(val start: Offset,val control: Offset?,val end: Offset)

/** Quadratic joins stay inside the sample hull: no Catmull-Rom overshoot at a bounce. */
internal fun smoothTrail(points: List<Offset>): List<TrailCurve> {
    if (points.size < 2) return emptyList()
    if (points.size == 2) return listOf(TrailCurve(points[0],null,points[1]))
    val curves=ArrayList<TrailCurve>(); var start=points.first()
    for (i in 1 until points.lastIndex) {
        val end=(points[i]+points[i+1])*.5f
        curves+=TrailCurve(start,points[i],end); start=end
    }
    curves+=TrailCurve(start,null,points.last())
    return curves
}

/** Trim by visible arc length, merge subpixel samples, and clip future physics history
 * behind an interpolated head instead of drawing a trail ahead of its displayed body. */
internal fun trailScreenPoints(body: CelestialBody,viewport: IntSize,camera: SpaceCamera,
    head: Vec2,maxLength: Float,spacing: Float): List<Offset> {
    if (body.trail.isEmpty() || maxLength <= 0 || camera.zoom <= 0) return emptyList()
    val history=body.trail
    var end=history.lastIndex
    if (head != body.position && history.size > 1) {
        var distance=Double.POSITIVE_INFINITY
        for (i in max(0,history.lastIndex-8) until history.lastIndex) {
            val delta=history[i+1]-history[i]; val square=delta.x*delta.x+delta.y*delta.y
            val offset=head-history[i]
            val t=if (square < 1e-20) 0.0 else ((offset.x*delta.x+offset.y*delta.y)/square).coerceIn(0.0,1.0)
            val error=head-(history[i]+delta*t); val candidate=error.x*error.x+error.y*error.y
            if (candidate <= distance) { distance=candidate; end=i }
        }
    }
    val reverse=ArrayList<Offset>(); var point=worldToScreen(head,viewport,camera.center,camera.zoom)
    reverse+=point; var remaining=maxLength
    for (i in end downTo 0) {
        val next=worldToScreen(history[i],viewport,camera.center,camera.zoom)
        val gap=(next-point).getDistance()
        if (gap < .01f) continue
        if (gap >= remaining) { reverse+=point+(next-point)*(remaining/gap); break }
        reverse+=next; remaining-=gap; point=next
    }
    val points=reverse.asReversed()
    if (points.size < 2) return emptyList()
    val simplified=ArrayList<Offset>(); simplified+=points.first(); var travelled=0f
    for (i in 1 until points.lastIndex) {
        travelled+=(points[i]-points[i-1]).getDistance()
        val incoming=points[i]-points[i-1]; val outgoing=points[i+1]-points[i]
        val product=incoming.getDistance()*outgoing.getDistance()
        val corner=product > .01f && (incoming.x*outgoing.x+incoming.y*outgoing.y)/product < .85f
        if (travelled >= spacing || corner) { simplified+=points[i]; travelled=0f }
    }
    simplified+=points.last()
    return simplified
}

internal fun visibleTrailBodies(bodies: List<CelestialBody>,viewport: IntSize,camera: SpaceCamera,
    rotation: Double,density: Float,selectedId: Long?,controlledId: Long?): List<CelestialBody> {
    val dense=bodies.size >= 160
    val budget=if (dense) 48 else if (bodies.size >= 60) 64 else bodies.size
    val width=if (abs(rotation) > .001) hypot(viewport.width.toDouble(),viewport.height.toDouble()) else viewport.width.toDouble()
    val height=if (abs(rotation) > .001) width else viewport.height.toDouble()
    val margin=120*density/camera.zoom
    val x=width/(2*camera.zoom)+margin; val y=height/(2*camera.zoom)+margin
    fun priority(body: CelestialBody)=body.id == selectedId || body.id == controlledId
    val candidates=bodies.asSequence().filter { body ->
        body.trail.size >= 2 && (priority(body) || (!body.isDebris && (body.isVehicle || !dense || body.radius*camera.zoom >= 1.5f))) &&
            abs(body.position.x-camera.center.x) <= x && abs(body.position.y-camera.center.y) <= y
    }.sortedWith(compareBy<CelestialBody> { if (priority(it)) 0 else if (it.isVehicle) 1 else 2 }
        .thenByDescending { it.radius*camera.zoom }.thenBy { it.id }).toList()
    if (!dense) return candidates.take(budget)
    // Spread history across the viewport instead of filling the budget with one dense cluster.
    val occupied=IntArray(8*6); val visible=ArrayList<CelestialBody>()
    for (body in candidates) {
        val screen=worldToScreen(body.position,viewport,camera.center,camera.zoom,rotation)
        val col=(screen.x*8/viewport.width.coerceAtLeast(1)).toInt().coerceIn(0,7)
        val row=(screen.y*6/viewport.height.coerceAtLeast(1)).toInt().coerceIn(0,5)
        val cell=row*8+col
        if (!priority(body) && occupied[cell] >= 2) continue
        visible+=body; occupied[cell]++
        if (visible.size == budget) break
    }
    return visible
}

/** Full history for light scenes; reduce screen length gradually as body count and measured
 * solver cost rise. Physics itself never depends on this presentation budget. */
internal fun adaptiveTrailLength(count: Int,solverLoad: Float = 0f,highlighted: Boolean = false): Float {
    if (count <= 60 && solverLoad <= 1.25f) return Float.POSITIVE_INFINITY
    val crowding=((count-60)/440f).coerceIn(0f,1f)
    val base=384f+(48f-384f)*crowding
    val pressure=(1f/(1f+(solverLoad-1f).coerceAtLeast(0f)*.35f)).coerceAtLeast(.35f)
    val length=base*pressure
    return if (highlighted) maxOf(96f,length) else length.coerceAtLeast(24f)
}


/** Attach a vehicle wake to its drawn stern, never to the physical centre or a stale pose.
 * The older flight path stays in world space; only the short hull-side join is rebuilt. */
internal fun vehicleTrailPoints(points: List<Offset>,body: CelestialBody,heading: Vec2,radius: Float): List<Offset> {
    if (!body.isVehicle || points.size < 2 || !radius.isFinite() || radius <= 0f) return points
    val centre=points.last()
    val stern=when {
        body.kind == BodyKind.Rocket -> if (body.hullClass == VehicleHullClass.Heavy) .76f else .8f
        body.hullClass == VehicleHullClass.Heavy -> .98f
        body.shipClass == ShipClass.Guardian -> .65f
        else -> .85f
    }
    val matrix=vehiclePitchMatrix(body.pitch,radius,body.roll)
    val angle=atan2(heading.y,heading.x)+PI/2
    fun project(y: Float): Offset {
        val p=matrix.map(Offset(0f,y*radius))
        return centre+Offset((p.x*cos(angle)-p.y*sin(angle)).toFloat(),(p.x*sin(angle)+p.y*cos(angle)).toFloat())
    }
    val nozzle=project(stern)
    val aft=project(stern+.25f)-nozzle
    val direction=aft/(aft.getDistance().coerceAtLeast(.001f))
    // Samples through the visible hull look like a wake issuing from a wing or the nose.
    val outside=points.indexOfLast { (it-centre).getDistance() > radius*1.45f }
    if (outside < 0) return emptyList()
    val old=points.subList(0,outside+1)
    val gap=old.last()-nozzle
    // A recently reversed craft can have its old path ahead of it. Keep that detached
    // history rather than drawing a fresh line through the hull or inventing exhaust.
    if (!body.enginePowered || gap.x*direction.x+gap.y*direction.y <= 0) return old
    val guide=nozzle+direction*minOf(radius*.4f,gap.getDistance()*.5f)
    return old+guide+nozzle
}
