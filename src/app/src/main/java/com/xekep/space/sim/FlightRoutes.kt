package com.xekep.space.sim

import kotlin.math.*

const val MAX_WAYPOINTS = 32

fun routeCruiseSpeed(launchSpeed: Double): Double {
    return (if (launchSpeed > 2) launchSpeed else 120.0).coerceIn(.1,260.0)
}

/** Wheel input or an explicit speed setpoint overrides the route; neutral keeps the navigator. */
fun applyFlightControls(bodies: List<CelestialBody>, control: ManualFlightControl?, seconds: Double): List<CelestialBody> {
    if (!seconds.isFinite() || seconds <= 0) return bodies
    val manual=control?.takeIf { candidate -> abs(candidate.steering) > .025 || abs(candidate.pitch) > .025 || candidate.boost > 0 ||
        bodies.any { it.id == candidate.bodyId && it.pilotTargetSpeed != null } }
    val routed=bodies.map { body ->
        if (!body.isVehicle || body.waypoints.isEmpty()) return@map body
        if (!body.enginePowered || body.id == manual?.bodyId)
            return@map body.copy(waypoints=emptyList(),routePath=null,routeDistance=0.0,routeAvoiding=false)
        if (body.routeAvoiding) return@map body
        val path=body.routePath ?: FlightPath.through(body.position,body.waypoints)
            ?: return@map body.copy(waypoints=emptyList())
        val speed=body.routeSpeed.takeIf { it > 0 } ?: routeCruiseSpeed(body.velocity.magnitude())
        val sample=path.sample(body.routeDistance)
        body.copy(routePath=path,routeSpeed=speed,heading=sample.direction,velocity=sample.direction*speed)
    }
    val steering=control?.takeIf { candidate -> routed.none { it.id == candidate.bodyId && it.waypoints.isNotEmpty() } }
    return returnAutopilotsToPlane(steerManually(routed,steering,seconds),steering?.bodyId,seconds)
}

/** Swept arrival prevents skipping a waypoint when a craft crosses it between frames. */
fun advanceWaypoints(before: List<CelestialBody>, after: List<CelestialBody>): List<CelestialBody> {
    if (after.none { it.waypoints.isNotEmpty() }) return after
    val previous=before.associateBy { it.id }
    return after.map { body ->
        if (body.waypoints.isEmpty()) return@map body
        body.routePath?.let { path ->
            val remaining=path.remainingPoints(body.routeDistance)
            return@map body.copy(waypoints=remaining,routePath=path.takeIf { remaining.isNotEmpty() },
                routeDistance=if (remaining.isEmpty()) 0.0 else body.routeDistance,
                routeAvoiding=body.routeAvoiding && remaining.isNotEmpty())
        }
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
