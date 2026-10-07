package com.xekep.space.sim

import kotlin.math.*

data class ManualFlightControl(val bodyId: Long, val steering: Double, val boost: Double = 0.0)

fun turnHeading(current: Vec2, desired: Vec2, seconds: Double, radiansPerSecond: Double = 3.6): Vec2 {
    if (desired.magnitude() < 1e-6 || seconds <= 0) return current
    val angle = atan2(current.y, current.x)
    val difference = atan2(sin(atan2(desired.y, desired.x) - angle), cos(atan2(desired.y, desired.x) - angle))
    val next = angle + difference.coerceIn(-radiansPerSecond * seconds, radiansPerSecond * seconds)
    return Vec2(cos(next), sin(next))
}

fun steerManually(bodies: List<CelestialBody>, control: ManualFlightControl?, seconds: Double): List<CelestialBody> {
    if (control == null || !control.steering.isFinite() || !control.boost.isFinite() || !seconds.isFinite() || seconds <= 0) return bodies
    return bodies.map { body ->
        if (body.id != control.bodyId || !body.isVehicle || body.fuelRemaining <= 1e-9) body else {
            val target=body.pilotTargetSpeed?.let { (it+control.boost.coerceIn(0.0,1.0)*150).coerceIn(0.0,900.0) }
            // A stopped engine provides neither steering torque nor braking thrust.
            if (target == 0.0) return@map body.copy(pilotThrottle=0.0)
            // Like a steering wheel: input changes turn rate relative to the craft's course.
            val angle = atan2(body.heading.y, body.heading.x) + control.steering.coerceIn(-1.0, 1.0) * 3.6 * seconds
            val heading = Vec2(cos(angle), sin(angle))
            target?.let { target ->
                val throttle=target/900
                val desired=heading*target
                val difference=desired-body.velocity
                val acceleration=120+420*throttle
                val powered=minOf(seconds,body.fuelRemaining/(.12+1.38*throttle))
                val change=difference.normalized()*minOf(difference.magnitude(),acceleration*powered)
                return@map body.copy(heading=heading,velocity=body.velocity+change,pilotThrottle=throttle,pilotTargetSpeed=target)
            }
            val speed = body.velocity.magnitude().coerceAtLeast(70.0)
            val throttle=(body.pilotThrottle+control.boost.coerceIn(0.0,1.0)*.5).coerceIn(0.0,1.0)
            // Active manoeuvring thrusters cancel lateral drift so turns also change the flight path.
            val aligned=body.velocity+(heading*speed-body.velocity)*(1-exp(-seconds/.10))
            val forwardThrust=(120+420*throttle)*minOf(seconds,body.fuelRemaining/(1+.5*throttle))
            val velocity=aligned.normalized()*speed+heading*minOf(forwardThrust,(900-speed).coerceAtLeast(0.0))
            body.copy(heading = heading, velocity = velocity, pilotThrottle = throttle)
        }
    }
}
