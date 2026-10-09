package com.xekep.space.sim

import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

data class BodyContact(val fraction: Double, val position: Vec2)
private data class ContactCircle(val center: Vec2, val radius: Double, val height: Double = 0.0)

/** First contact along a relative segment, including starting overlaps and tangency. */
fun firstCircleContact(start: Vec2, end: Vec2, radius: Double): Double? {
    val c = start.x * start.x + start.y * start.y - radius * radius
    if (c <= 0.0) return 0.0
    val delta = end - start
    val a = delta.x * delta.x + delta.y * delta.y
    if (a <= 1e-24) return null
    val b = start.x * delta.x + start.y * delta.y
    if (b >= 0.0) return null
    val discriminant = b * b - a * c
    if (discriminant < 0.0) return null
    val time = (-b - sqrt(discriminant)) / a
    return time.takeIf { it in 0.0..1.0 }
}

/** Swept sphere contact. At zero height this is exactly the original 2D contact. */
fun firstSphereContact(start: Vec2, end: Vec2, startHeight: Double, endHeight: Double, radius: Double): Double? {
    if (startHeight == 0.0 && endHeight == 0.0) return firstCircleContact(start,end,radius)
    val c=start.x*start.x+start.y*start.y+startHeight*startHeight-radius*radius
    if (c <= 0.0) return 0.0
    val delta=end-start; val dz=endHeight-startHeight
    val a=delta.x*delta.x+delta.y*delta.y+dz*dz
    if (a <= 1e-24) return null
    val b=start.x*delta.x+start.y*delta.y+startHeight*dz
    if (b >= 0.0) return null
    val discriminant=b*b-a*c
    if (discriminant < 0.0) return null
    return ((-b-sqrt(discriminant))/a).takeIf { it in 0.0..1.0 }
}

/** Several small hull circles avoid treating empty space beside a thin rocket as solid. */
private fun contactCircles(body: CelestialBody): List<ContactCircle> {
    val r = body.radius.toDouble()
    val forward = body.heading
    val side = forward.perpendicular()
    fun circle(front: Double, lateral: Double, radius: Double) = ContactCircle(body.position + forward * (front * r * cos(body.pitch)) + side * (lateral * r), radius * r, body.flightHeight+sin(body.pitch)*front*r)
    return when (body.kind) {
        BodyKind.Rocket -> listOf(circle(.88, 0.0, .38), circle(.1, 0.0, .38), circle(-.65, 0.0, .65))
        BodyKind.Ship -> listOf(circle(.83, 0.0, .32), circle(-.1, 0.0, .36), circle(-.15, .78, .30), circle(-.15, -.78, .30))
        else -> listOf(ContactCircle(body.position, r, body.flightHeight))
    }
}

fun bodyContact(firstStart: CelestialBody, firstEnd: CelestialBody, secondStart: CelestialBody, secondEnd: CelestialBody): BodyContact? {
    if (!firstEnd.isVehicle && !secondEnd.isVehicle) {
        val fraction=firstCircleContact(firstStart.position-secondStart.position,firstEnd.position-secondEnd.position,
            firstEnd.radius.toDouble()+secondEnd.radius) ?: return null
        val point=firstStart.position+(firstEnd.position-firstStart.position)*fraction
        val target=secondStart.position+(secondEnd.position-secondStart.position)*fraction
        return BodyContact(fraction,point+(target-point).normalized()*firstEnd.radius.toDouble())
    }
    // Cheap conservative rejection before building hull shapes.
    if (firstSphereContact(firstStart.position - secondStart.position, firstEnd.position - secondEnd.position,
            firstStart.flightHeight-secondStart.flightHeight,firstEnd.flightHeight-secondEnd.flightHeight,
            (firstEnd.radius + secondEnd.radius) * 1.4) == null) return null
    val firstBefore = contactCircles(firstStart); val firstAfter = contactCircles(firstEnd)
    val secondBefore = contactCircles(secondStart); val secondAfter = contactCircles(secondEnd)
    var hit: BodyContact? = null
    for (i in firstBefore.indices) for (j in secondBefore.indices) {
        val before = firstBefore[i]; val after = firstAfter[i]
        val targetBefore = secondBefore[j]; val targetAfter = secondAfter[j]
        val fraction = firstSphereContact(before.center - targetBefore.center, after.center - targetAfter.center,
            before.height-targetBefore.height,after.height-targetAfter.height,after.radius + targetAfter.radius) ?: continue
        if (hit != null && fraction >= hit.fraction) continue
        val point = before.center + (after.center - before.center) * fraction
        val target = targetBefore.center + (targetAfter.center - targetBefore.center) * fraction
        hit = BodyContact(fraction, point + (target - point).normalized() * after.radius)
    }
    return hit
}
