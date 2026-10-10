package com.xekep.space.sim

import kotlin.math.*

const val MAX_FLIGHT_PITCH = PI*65/180
const val MAX_FLIGHT_ROLL = PI*70/180
const val STABILIZED_FLIGHT_PITCH = PI*45/180
const val STABILIZED_FLIGHT_ROLL = PI*50/180

fun CelestialBody.pilotSpeedLimit(): Double = if (physicalScale) 5000.0 else 900.0

data class ManualFlightControl(val bodyId: Long, val steering: Double, val boost: Double = 0.0, val pitch: Double = 0.0,
    val roll: Double = 0.0, val stabilizedAttitude: Boolean = false, val attitudeTimeScale: Double = 1.0)

/** The controlled craft uses wall time; the rest of a sandbox keeps world time. */
val ManualFlightControl.depthTimeScale: Double get() = if (attitudeTimeScale.isFinite()) attitudeTimeScale.coerceIn(1.0/6.0,10000.0) else 1.0

fun turnHeading(current: Vec2, desired: Vec2, seconds: Double, radiansPerSecond: Double = 3.6): Vec2 {
    if (desired.magnitude() < 1e-6 || seconds <= 0) return current
    val angle = atan2(current.y, current.x)
    val difference = atan2(sin(atan2(desired.y, desired.x) - angle), cos(atan2(desired.y, desired.x) - angle))
    val next = angle + difference.coerceIn(-radiansPerSecond * seconds, radiansPerSecond * seconds)
    return Vec2(cos(next), sin(next))
}

fun steerManually(bodies: List<CelestialBody>, control: ManualFlightControl?, seconds: Double): List<CelestialBody> {
    if (control == null || !control.steering.isFinite() || !control.boost.isFinite() || !control.pitch.isFinite() || !control.roll.isFinite() || !control.attitudeTimeScale.isFinite() || !seconds.isFinite() || seconds <= 0) return bodies
    val flightSeconds=seconds*control.depthTimeScale
    val attitudeSeconds=flightSeconds
    return bodies.map { body ->
        if (body.id != control.bodyId || !body.isVehicle || body.fuelRemaining <= 1e-9) body else {
            val limit=body.pilotSpeedLimit()
            val target=body.pilotTargetSpeed?.let { (it+control.boost.coerceIn(0.0,1.0)*limit/6).coerceIn(0.0,limit) }
                ?: if (control.pitch != 0.0 || body.pitch != 0.0 || control.roll != 0.0 || body.roll != 0.0) body.flightSpeed().coerceIn(70.0,limit) else null
            // A stopped engine provides neither steering torque nor braking thrust.
            if (target == 0.0) return@map body.copy(pilotThrottle=0.0)
            val desiredPitch = control.pitch.coerceIn(-1.0,1.0)*MAX_FLIGHT_PITCH
            // Stabilized Mode 2: deflection commands an angle, neutral commands level flight.
            val pitch = if (control.stabilizedAttitude) manualAttitude(body.pitch,control.pitch,STABILIZED_FLIGHT_PITCH,body.vehicleTurnScale,attitudeSeconds)
                else body.pitch+(desiredPitch-body.pitch)*(1-exp(-flightSeconds/.16))
            val roll = if (control.stabilizedAttitude) manualAttitude(body.roll,control.roll,STABILIZED_FLIGHT_ROLL,body.vehicleTurnScale,attitudeSeconds)
                else body.roll*(exp(-flightSeconds/.16))
            // Coordinated banking is assisted by manoeuvring jets, not atmospheric lift.
            val bankTurn=if (control.stabilizedAttitude) 1.6*sin(roll)*cos(pitch) else 0.0
            val yawChange=(control.steering.coerceIn(-1.0,1.0)*3.6*attitudeSeconds+bankTurn*flightSeconds)*body.vehicleTurnScale
            val angle=atan2(body.heading.y,body.heading.x)+yawChange
            val heading=Vec2(cos(angle),sin(angle))
            val right=Vec2(-heading.y,heading.x)
            val course=(heading+right*(sin(roll)*.65)).normalized()
            target?.let { target ->
                val throttle=target/limit
                val desired=course*(target*cos(pitch))
                val difference=desired-body.velocity
                val acceleration=(120+420*throttle)*body.vehicleAccelerationScale*(limit/900)
                val powered=minOf(flightSeconds,body.fuelRemaining/((.12+1.38*throttle)*body.fuelConsumptionScale*body.vehicleFuelBurnScale))
                val change=difference.normalized()*minOf(difference.magnitude(),acceleration*powered)
                val pull=if (body.physicalScale) flightGravity(body,bodies) else DepthGravity(Vec2.Zero,0.0)
                // Direct lift command starts with the input; the visible hull catches up smoothly.
                val depthSeconds=powered
                val depthAngle=if (control.stabilizedAttitude && control.pitch != 0.0) control.pitch.coerceIn(-1.0,1.0)*STABILIZED_FLIGHT_PITCH else pitch
                val lift=if (control.stabilizedAttitude) (180+900*throttle)*body.vehicleAccelerationScale*(limit/900) else acceleration
                val vertical=body.verticalVelocity+(target*sin(depthAngle)-body.verticalVelocity).coerceIn(-lift*depthSeconds,lift*depthSeconds)-pull.vertical*depthSeconds
                return@map body.copy(heading=heading,pitch=pitch,roll=roll,verticalVelocity=vertical,velocity=body.velocity+change-pull.planarCorrection*powered,pilotThrottle=throttle,pilotTargetSpeed=target)
            }
            val speed = body.velocity.magnitude().coerceAtLeast(70.0)
            val throttle=(body.pilotThrottle+control.boost.coerceIn(0.0,1.0)*.5).coerceIn(0.0,1.0)
            // Active manoeuvring thrusters cancel lateral drift so turns also change the flight path.
            val aligned=body.velocity+(heading*speed-body.velocity)*(1-exp(-flightSeconds/.10))
            val forwardThrust=(120+420*throttle)*body.vehicleAccelerationScale*
                minOf(flightSeconds,body.fuelRemaining/((1+.5*throttle)*body.fuelConsumptionScale*body.vehicleFuelBurnScale))
            val velocity=aligned.normalized()*speed+heading*minOf(forwardThrust,(900-speed).coerceAtLeast(0.0))
            val vertical=body.verticalVelocity+(speed*sin(pitch)-body.verticalVelocity)*(1-exp(-flightSeconds/.10))
            body.copy(heading = heading, pitch=pitch, roll=roll, verticalVelocity=vertical, velocity = velocity*cos(pitch), pilotThrottle = throttle)
        }
    }
}

private fun manualAttitude(angle: Double, input: Double, limit: Double, turnScale: Double, seconds: Double): Double {
    val target=input.coerceIn(-1.0,1.0)*limit
    val next=target+(angle-target)*exp(-seconds*turnScale/(if (input == 0.0) .28 else .055))
    return if (input == 0.0 && abs(next) < .001) 0.0 else next
}
