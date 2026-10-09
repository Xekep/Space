package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/** Planar educational model: AU = 1000 world units, Sun = 100000 mass units.
 * New presets compress catalogue orbital distances by 30%; physical radii/masses stay unchanged.
 * Planet elements: JPL approximate positions, J2000; phases are illustrative.
 * https://ssd.jpl.nasa.gov/planets/approx_pos.html
 * https://nssdc.gsfc.nasa.gov/planetary/factsheet/
 */
enum class SolarBody(
    val massKg: Double, val radiusKm: Double, val axisAu: Double, val eccentricity: Double,
    val phase: Double, val periapsis: Double, val tint: Long, val parent: String = "Sun",
    val retrograde: Boolean = false,
) {
    Sun(1.9884e30, 695700.0, 0.0, 0.0, 0.0, 0.0, 0xFFFFD166),
    Mercury(3.30e23, 2439.5, .38709927, .20563593, 20.0, 77.4578, 0xFFBAA799),
    Venus(4.87e24, 6052.0, .72333566, .00677672, 75.0, 131.6025, 0xFFF1C889),
    Earth(5.9722e24, 6371.0, 1.00000261, .01671123, 145.0, 102.9377, 0xFF69B7FF),
    Mars(6.42e23, 3396.0, 1.52371034, .09339410, 210.0, -23.9436, 0xFFE18160),
    Jupiter(1.898e27, 71492.0, 5.202887, .04838624, 310.0, 14.7285, 0xFFE7C89A),
    Saturn(5.68e26, 60268.0, 9.53667594, .05386179, 15.0, 92.5989, 0xFFF1D58A),
    Uranus(8.68e25, 25559.0, 19.18916464, .04725744, 96.0, 170.9543, 0xFF9BE7FF),
    Neptune(1.02e26, 24764.0, 30.06992276, .00859048, 262.0, 44.9648, 0xFF577CFF),
    Pluto(1.30e22, 1188.0, 39.482, .2488, 190.0, 224.0668, 0xFFCCA99B),
    Moon(7.35e22, 1737.5, 384400.0 / AU_KM, .0549, 45.0, 0.0, 0xFFC7CCDA, "Earth"),
    Io(8.93e22, 1821.6, 421800.0 / AU_KM, .0041, 20.0, 0.0, 0xFFFFD471, "Jupiter"),
    Europa(4.80e22, 1560.8, 671100.0 / AU_KM, .0094, 120.0, 0.0, 0xFFE5D7C3, "Jupiter"),
    Ganymede(1.482e23, 2634.1, 1070400.0 / AU_KM, .0013, 230.0, 0.0, 0xFFA8A395, "Jupiter"),
    Callisto(1.076e23, 2410.3, 1882700.0 / AU_KM, .0074, 300.0, 0.0, 0xFF8B8178, "Jupiter"),
    Titan(1.345e23, 2574.7, 1221870.0 / AU_KM, .0288, 140.0, 0.0, 0xFFE3B968, "Saturn"),
    Triton(2.14e22, 1353.4, 354800.0 / AU_KM, .000016, 30.0, 0.0, 0xFFCFDDE5, "Neptune", true);

    val isMoon: Boolean get() = parent != "Sun"
    val worldMass: Double get() = massKg / Sun.massKg * 100000.0
    val worldRadius: Float get() = (radiusKm / AU_KM * AU_WORLD).toFloat()

    fun relativeState(parentMass: Double, anomaly: Double = phase * PI / 180.0, axisScale: Double = 1.0): Pair<Vec2, Vec2> {
        val a = axisAu * AU_WORLD * axisScale
        var eccentricAnomaly = anomaly
        repeat(10) { eccentricAnomaly -= (eccentricAnomaly - eccentricity * sin(eccentricAnomaly) - anomaly) /
            (1 - eccentricity * cos(eccentricAnomaly)) }
        val b = sqrt(1 - eccentricity * eccentricity)
        val rate = sqrt(400.0 * (parentMass + worldMass) / a.pow(3)) /
            (1 - eccentricity * cos(eccentricAnomaly)) * if (retrograde) -1 else 1
        val rotation = periapsis * PI / 180.0
        fun rotate(v: Vec2) = Vec2(v.x * cos(rotation) - v.y * sin(rotation), v.x * sin(rotation) + v.y * cos(rotation))
        return rotate(Vec2(a * (cos(eccentricAnomaly) - eccentricity), a * b * sin(eccentricAnomaly))) to
            rotate(Vec2(-a * sin(eccentricAnomaly) * rate, a * b * cos(eccentricAnomaly) * rate))
    }
}

const val AU_KM = 149597870.7
const val AU_WORLD = 1000.0

object SolarSystem {
    const val ORBIT_SCALE = .7
    const val RING_GRAINS = 120
    const val EARTH_SATELLITES = 12
    const val BODY_COUNT = 17 + RING_GRAINS + EARTH_SATELLITES
    fun create(newId: () -> Long): List<CelestialBody> {
        val bodies = mutableListOf<CelestialBody>()
        for (entry in SolarBody.entries) {
            val parent = bodies.firstOrNull { it.solar?.name == entry.parent }
            val state = if (entry == SolarBody.Sun) Vec2.Zero to Vec2.Zero else entry.relativeState(parent!!.mass, axisScale = ORBIT_SCALE)
            bodies += CelestialBody(newId(), state.first + (parent?.position ?: Vec2.Zero),
                state.second + (parent?.velocity ?: Vec2.Zero), entry.worldMass, entry.worldRadius,
                Color(entry.tint), if (entry == SolarBody.Sun) BodyKind.Core else BodyKind.Ambient, solar = entry,
                solarOrbitScale = ORBIT_SCALE, orbitParentId = parent?.id)
        }
        OrbitalDetails.populate(bodies, newId)
        val mass = bodies.sumOf { it.mass }
        val center = bodies.fold(Vec2.Zero) { acc, body -> acc + body.position * body.mass } / mass
        val velocity = bodies.fold(Vec2.Zero) { acc, body -> acc + body.velocity * body.mass } / mass
        return bodies.map { body -> val point = body.position - center
            body.copy(position = point, velocity = body.velocity - velocity, trail = listOf(point)) }
    }
}

internal const val GALAXY_SOFTENING = 260.0
internal val CelestialBody.gravitySoftening: Double get() = when {
    physicalScale -> .01
    galaxyParticle && (kind == BodyKind.Star || kind == BodyKind.Ambient) -> GALAXY_SOFTENING
    else -> 18.0
}
internal fun forceSoftening(first: CelestialBody, second: CelestialBody): Double =
    minOf(first.gravitySoftening,second.gravitySoftening)
