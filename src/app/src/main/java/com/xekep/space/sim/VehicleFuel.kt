package com.xekep.space.sim

const val ROCKET_DRIFT_SECONDS = 6.0

/** Seconds of baseline flight. Both navigation and manual piloting use the same reserve. */
fun vehicleFuelCapacity(kind: BodyKind): Double = when (kind) {
    BodyKind.Ship -> 180.0
    BodyKind.Rocket -> 120.0
    else -> 0.0
}

val CelestialBody.fuelFraction: Float
    get() = (fuelRemaining / vehicleFuelCapacity(kind).coerceAtLeast(1.0)).coerceIn(0.0,1.0).toFloat()

internal fun expireVehicles(bodies: List<CelestialBody>): StepResult {
    fun expired(body: CelestialBody) = body.isVehicle && body.fuelRemaining <= 1e-9 &&
        (body.kind != BodyKind.Rocket || body.driftRemaining <= 1e-9)
    val exhausted=bodies.filter(::expired)
    if (exhausted.isEmpty()) return StepResult(bodies,emptyList())
    return StepResult(bodies.filterNot(::expired),exhausted.map {
        CollisionEvent(it.kind,it.kind,it.position,vehicleExplosion=true,velocity=it.velocity*.12,seed=it.id.toInt())
    })
}

/** An explicit zero on the speed/thrust slider cuts the engine; null retains automatic flight. */
val CelestialBody.enginePowered: Boolean get() = isVehicle && fuelRemaining > 1e-9 && pilotTargetSpeed != 0.0
