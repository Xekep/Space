package com.xekep.space.sim

import kotlin.math.*

/** A split flight-height layer over the planar N-body world. Natural bodies stay at z=0. */
internal data class DepthGravity(val planarCorrection: Vec2, val vertical: Double)
internal fun depthGravity(body: CelestialBody, scene: List<CelestialBody>): DepthGravity {
    if (!body.isVehicle || body.flightHeight == 0.0) return DepthGravity(Vec2.Zero,0.0)
    var correction=Vec2.Zero; var vertical=0.0
    for (source in scene) if (source.id != body.id) {
        val delta=source.position-body.position
        val dz=source.flightHeight-body.flightHeight
        val soft=forceSoftening(body,source)
        val planar=delta.x*delta.x+delta.y*delta.y+soft*soft
        val space=planar+dz*dz
        val factor=SimulationEngine.gravitationalConstant*source.gravityMass/(space*sqrt(space))
        correction+=delta*(factor-SimulationEngine.gravitationalConstant*source.gravityMass/(planar*sqrt(planar)))
        vertical+=dz*factor
    }
    return DepthGravity(correction,vertical)
}

internal fun returnAutopilotsToPlane(bodies: List<CelestialBody>, controlledId: Long?, seconds: Double) = bodies.map { body ->
    if (!body.isVehicle || body.id == controlledId || !body.enginePowered ||
        (body.flightHeight == 0.0 && body.verticalVelocity == 0.0 && body.pitch == 0.0 && body.roll == 0.0)) body else {
        val speed=body.velocity.magnitude().coerceAtLeast(70.0)
        val desired=(-body.flightHeight*.8).coerceIn(-speed*.7,speed*.7)
        val change=(desired-body.verticalVelocity).coerceIn(-180*seconds*body.vehicleAccelerationScale,180*seconds*body.vehicleAccelerationScale)
        val vertical=body.verticalVelocity+change
        val pitch=atan2(vertical,speed)
        body.copy(verticalVelocity=vertical,pitch=body.pitch+(pitch-body.pitch)*(1-exp(-seconds/.16)),roll=body.roll*exp(-seconds/.25))
    }
}

fun CelestialBody.flightSpeed(): Double = hypot(velocity.magnitude(),verticalVelocity)
fun CelestialBody.flightVisualScale(): Float = (1+.65*tanh(flightHeight/maxOf(40.0,radius*3.0))+.35*sin(pitch)).coerceIn(.35,1.85).toFloat()

/** Flight computer feed-forward in catalogue units; zero thrust always bypasses it. */
internal fun flightGravity(body: CelestialBody,scene: List<CelestialBody>): DepthGravity {
    var planar=Vec2.Zero; var vertical=0.0
    for (source in scene) if (source.id != body.id) {
        val delta=source.position-body.position; val dz=source.flightHeight-body.flightHeight
        val soft=forceSoftening(body,source)
        val square=delta.x*delta.x+delta.y*delta.y+dz*dz+soft*soft
        val factor=400.0*source.gravityMass/(square*sqrt(square))
        planar+=delta*factor; vertical+=dz*factor
    }
    return DepthGravity(planar,vertical)
}
