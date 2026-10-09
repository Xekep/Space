package com.xekep.space.sim

import kotlin.math.*

const val MAX_FLIGHT_PITCH = PI*65/180
const val MAX_FLIGHT_ROLL = PI*70/180

fun CelestialBody.pilotSpeedLimit(): Double = if (physicalScale) 5000.0 else 900.0

data class ManualFlightControl(val bodyId: Long, val steering: Double, val boost: Double = 0.0, val pitch: Double = 0.0,
    val roll: Double = 0.0, val attitudeRates: Boolean = false)

fun turnHeading(current: Vec2, desired: Vec2, seconds: Double, radiansPerSecond: Double = 3.6): Vec2 {
    if (desired.magnitude() < 1e-6 || seconds <= 0) return current
    val angle = atan2(current.y, current.x)
    val difference = atan2(sin(atan2(desired.y, desired.x) - angle), cos(atan2(desired.y, desired.x) - angle))
    val next = angle + difference.coerceIn(-radiansPerSecond * seconds, radiansPerSecond * seconds)
    return Vec2(cos(next), sin(next))
}

fun steerManually(bodies: List<CelestialBody>, control: ManualFlightControl?, seconds: Double): List<CelestialBody> {
    if (control == null || !control.steering.isFinite() || !control.boost.isFinite() || !control.pitch.isFinite() || !control.roll.isFinite() || !seconds.isFinite() || seconds <= 0) return bodies
    return bodies.map { body ->
        if (body.id != control.bodyId || !body.isVehicle || body.fuelRemaining <= 1e-9) body else {
            val limit=body.pilotSpeedLimit()
            val target=body.pilotTargetSpeed?.let { (it+control.boost.coerceIn(0.0,1.0)*limit/6).coerceIn(0.0,limit) }
                ?: if (control.pitch != 0.0 || body.pitch != 0.0 || control.roll != 0.0 || body.roll != 0.0) body.flightSpeed().coerceIn(70.0,limit) else null
            // A stopped engine provides neither steering torque nor braking thrust.
            if (target == 0.0) return@map body.copy(pilotThrottle=0.0)
            // Like a steering wheel: input changes turn rate relative to the craft's course.
            val angle = atan2(body.heading.y, body.heading.x) + control.steering.coerceIn(-1.0, 1.0) * 3.6 * body.vehicleTurnScale * seconds
            val heading = Vec2(cos(angle), sin(angle))
            val desiredPitch = control.pitch.coerceIn(-1.0,1.0)*MAX_FLIGHT_PITCH
            // FPV sticks command angular rates; neutral stops rotation, retaining attitude.
            val pitch = if (control.attitudeRates) (body.pitch+control.pitch.coerceIn(-1.0,1.0)*1.8*body.vehicleTurnScale*seconds)
                .coerceIn(-MAX_FLIGHT_PITCH,MAX_FLIGHT_PITCH)
                else body.pitch+(desiredPitch-body.pitch)*(1-exp(-seconds/.16))
            val roll = if (control.attitudeRates) (body.roll+control.roll.coerceIn(-1.0,1.0)*2.4*body.vehicleTurnScale*seconds)
                .coerceIn(-MAX_FLIGHT_ROLL,MAX_FLIGHT_ROLL)
                else body.roll*(exp(-seconds/.16))
            // Assisted lateral manoeuvring jets, relative to the hull, for the 2.5D world.
            val right=Vec2(-heading.y,heading.x)
            val course=(heading+right*(sin(roll)*.65)).normalized()
            target?.let { target ->
                val throttle=target/limit
                val desired=course*(target*cos(pitch))
                val difference=desired-body.velocity
                val acceleration=(120+420*throttle)*body.vehicleAccelerationScale*(limit/900)
                val powered=minOf(seconds,body.fuelRemaining/((.12+1.38*throttle)*body.fuelConsumptionScale*body.vehicleFuelBurnScale))
                val change=difference.normalized()*minOf(difference.magnitude(),acceleration*powered)
                val pull=if (body.physicalScale) flightGravity(body,bodies) else DepthGravity(Vec2.Zero,0.0)
                val vertical=body.verticalVelocity+(target*sin(pitch)-body.verticalVelocity).coerceIn(-acceleration*powered,acceleration*powered)-pull.vertical*powered
                return@map body.copy(heading=heading,pitch=pitch,roll=roll,verticalVelocity=vertical,velocity=body.velocity+change-pull.planarCorrection*powered,pilotThrottle=throttle,pilotTargetSpeed=target)
            }
            val speed = body.velocity.magnitude().coerceAtLeast(70.0)
            val throttle=(body.pilotThrottle+control.boost.coerceIn(0.0,1.0)*.5).coerceIn(0.0,1.0)
            // Active manoeuvring thrusters cancel lateral drift so turns also change the flight path.
            val aligned=body.velocity+(heading*speed-body.velocity)*(1-exp(-seconds/.10))
            val forwardThrust=(120+420*throttle)*body.vehicleAccelerationScale*
                minOf(seconds,body.fuelRemaining/((1+.5*throttle)*body.fuelConsumptionScale*body.vehicleFuelBurnScale))
            val velocity=aligned.normalized()*speed+heading*minOf(forwardThrust,(900-speed).coerceAtLeast(0.0))
            val vertical=body.verticalVelocity+(speed*sin(pitch)-body.verticalVelocity)*(1-exp(-seconds/.10))
            body.copy(heading = heading, pitch=pitch, roll=roll, verticalVelocity=vertical, velocity = velocity*cos(pitch), pilotThrottle = throttle)
        }
    }
}
