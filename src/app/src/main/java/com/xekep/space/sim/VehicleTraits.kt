package com.xekep.space.sim

/** Small, bounded hull variants. Holding longer never turns a craft into a planet. */
fun vehicleMassFraction(kind: BodyKind, mass: Double): Double {
    val base = when (kind) { BodyKind.Ship -> 24.0; BodyKind.Rocket -> 12.0; else -> return 0.0 }
    val maximum = if (kind == BodyKind.Ship) 96.0 else 36.0
    return if (mass.isFinite()) ((mass - base) / (maximum - base)).coerceIn(0.0, 1.0) else 0.0
}

fun vehicleSizeScale(kind: BodyKind, mass: Double) = 1.0 + .18 * vehicleMassFraction(kind, mass)
val CelestialBody.vehicleDamageScale: Double get() = 1.0 + .20 * vehicleMassFraction(kind, mass)
val CelestialBody.vehicleAccelerationScale: Double get() = 1.0 + .06 * vehicleMassFraction(kind, mass)
val CelestialBody.vehicleTurnScale: Double get() = 1.0 - .08 * vehicleMassFraction(kind, mass)
val CelestialBody.vehicleFuelBurnScale: Double get() = 1.0 - .10 * vehicleMassFraction(kind, mass)

enum class VehicleHullClass { Standard, Heavy }

fun heavyHullMass(kind: BodyKind): Double = when (kind) {
    BodyKind.Ship -> 60.0
    BodyKind.Rocket -> 24.0
    else -> Double.POSITIVE_INFINITY
}
fun vehicleHullClass(kind: BodyKind, mass: Double): VehicleHullClass =
    if (mass.isFinite() && mass >= heavyHullMass(kind)) VehicleHullClass.Heavy else VehicleHullClass.Standard
val CelestialBody.hullClass: VehicleHullClass get() = vehicleHullClass(kind, mass)
fun heavyHullPremium(kind: BodyKind): Double = when (kind) {
    BodyKind.Ship -> 6.0
    BodyKind.Rocket -> 4.0
    else -> 0.0
}
