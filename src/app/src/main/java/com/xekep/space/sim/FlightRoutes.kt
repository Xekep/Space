package com.xekep.space.sim

import kotlin.math.*

const val MAX_WAYPOINTS = 32

fun routeCruiseSpeed(start: Vec2, points: List<Vec2>, launchSpeed: Double): Double {
    val spacing=(listOf(start)+points).zipWithNext { a,b -> (b-a).magnitude() }.filter { it > 1e-6 }.minOrNull() ?: 1.0
    return minOf(if (launchSpeed > 2) launchSpeed else 120.0,spacing*2).coerceIn(.1,260.0)
}

/** Wheel input overrides the route; neutral lets the navigator fly the authored path. */
fun applyFlightControls(bodies: List<CelestialBody>, control: ManualFlightControl?, seconds: Double): List<CelestialBody> {
    if (!seconds.isFinite() || seconds <= 0) return bodies
    val manual=control?.takeIf { abs(it.steering) > .025 || it.boost > 0 }
    val routed=bodies.map { body ->
        if (!body.isVehicle || body.waypoints.isEmpty()) return@map body
        if (body.id == manual?.bodyId) return@map body.copy(waypoints=emptyList())
        val offset=body.waypoints.first()-body.position
        val distance=offset.magnitude()
        val speed=body.routeSpeed.takeIf { it > 0 } ?: routeCruiseSpeed(body.position,body.waypoints,body.velocity.magnitude())
        val desiredSpeed=if (body.waypoints.size == 1) speed else minOf(speed,sqrt(2*200*distance)*.7)
        val desired=offset.normalized()*desiredSpeed
        val correction=desired-body.velocity
        body.copy(heading=turnHeading(body.heading,offset,seconds),
            velocity=body.velocity+correction.normalized()*minOf(correction.magnitude(),240*seconds))
    }
    val steering=control?.takeIf { candidate -> routed.none { it.id == candidate.bodyId && it.waypoints.isNotEmpty() } }
    return steerManually(routed,steering,seconds)
}

/** Swept arrival prevents skipping a waypoint when a craft crosses it between frames. */
fun advanceWaypoints(before: List<CelestialBody>, after: List<CelestialBody>): List<CelestialBody> {
    if (after.none { it.waypoints.isNotEmpty() }) return after
    val previous=before.associateBy { it.id }
    return after.map { body ->
        if (body.waypoints.isEmpty()) return@map body
        val start=previous[body.id]?.position ?: body.position
        val radius=maxOf(body.radius*1.5,body.routeTolerance,.005)
        var passed=0; var lastFraction=0.0
        for (point in body.waypoints) {
            val fraction=firstCircleContact(start-point,body.position-point,radius) ?: break
            if (fraction+1e-9 < lastFraction) break
            passed++; lastFraction=fraction
        }
        if (passed == 0) body else body.copy(waypoints=body.waypoints.drop(passed))
    }
}
