package com.xekep.space.sim

import kotlin.math.sqrt
import kotlin.math.cbrt

enum class SatellitePlacement { Clear, Overlap, StrongTides }

private fun overlaps(parent: CelestialBody, satellite: CelestialBody, scene: List<CelestialBody>): Boolean {
    val margin = if (parent.physicalScale || satellite.physicalScale) .0001 else minOf(8.0, parent.radius*.2)
    return (satellite.position-parent.position).magnitude() <= parent.radius+satellite.radius+margin || scene.any {
        it.id != parent.id && it.id != satellite.id &&
            (it.position-satellite.position).magnitude() <= it.radius+satellite.radius
    }
}

/** Conservative prograde Hill limit, then a whole-orbit check using the game's softened gravity.
 * Eccentric parent orbits use pericentre rather than the current separation.
 * See https://arxiv.org/abs/1308.4402 and https://arxiv.org/abs/2005.06521.
 */
private fun stableSatelliteRadius(parent: CelestialBody, satellite: CelestialBody,
    scene: List<CelestialBody>, requested: Double): Double? {
    val margin = if (parent.physicalScale || satellite.physicalScale) .0001 else minOf(8.0, parent.radius*.2)
    val minimum = (parent.radius+satellite.radius+margin)*1.01
    val others = scene.filter { it.id != parent.id && it.id != satellite.id }
    // Only the dominant attractor defines a meaningful two-body parent orbit.
    // Relative motion between two planets is not a Kepler orbit around each other.
    val dominant = others.maxByOrNull {
        val distance = (it.position-parent.position).magnitude()
        val softening = forceSoftening(parent, it)
        it.gravityMass/(distance*distance+softening*softening)
    }
    var upper = maxOf(requested, minimum)
    val gravity = SimulationEngine.gravitationalConstant
    for (other in others) {
        if (other.gravityMass < parent.gravityMass*.5) continue
        val delta = other.position-parent.position
        val distance = delta.magnitude()
        if (distance <= 0.0 || !distance.isFinite()) return null
        val velocity = other.velocity-parent.velocity
        val mu = gravity*(parent.gravityMass+other.gravityMass)
        val energy = velocity.magnitude().let { it*it*.5 }-mu/distance
        var separation = distance
        if (energy < 0.0 && other.id == dominant?.id && other.gravityMass >= parent.gravityMass*10) {
            val momentum = delta.x*velocity.y-delta.y*velocity.x
            val eccentricity = sqrt(maxOf(0.0, 1+2*energy*momentum*momentum/(mu*mu)))
            separation = minOf(distance, -mu/(2*energy)*maxOf(0.0, 1-eccentricity))
        }
        upper = minOf(upper, .35*separation*cbrt(parent.gravityMass/(3*other.gravityMass)))
    }
    if (!upper.isFinite() || upper < minimum) return null
    val softening = forceSoftening(parent, satellite)
    fun stable(radius: Double): Boolean {
        val square = radius*radius+softening*softening
        val restoring = gravity*(parent.gravityMass+satellite.gravityMass)/(square*sqrt(square))
        var tides = 0.0
        for (other in others) {
            val separation = kotlin.math.abs((other.position-parent.position).magnitude()-radius)
            if (separation <= other.radius+satellite.radius) return false
            val smoothing = forceSoftening(parent, other)
            val squared = separation*separation+smoothing*smoothing
            tides += 2*gravity*other.gravityMass/(squared*sqrt(squared))
        }
        val tideLimit = if (parent.physicalScale || satellite.physicalScale) .02 else .1
        return restoring.isFinite() && restoring > 0 && tides.isFinite() && tides <= restoring*tideLimit
    }
    if (stable(upper)) return upper
    if (!stable(minimum)) return null
    var low = minimum
    var high = upper
    repeat(20) {
        val middle = (low+high)*.5
        if (stable(middle)) low = middle else high = middle
    }
    return low
}

/** Adjust both placement and velocity; the preview and the launch use the same orbit. */
fun assistedSatellite(parent: CelestialBody, satellite: CelestialBody, scene: List<CelestialBody>): CelestialBody {
    if (overlaps(parent, satellite, scene)) return satellite
    val offset = satellite.position-parent.position
    val radius = stableSatelliteRadius(parent, satellite, scene, offset.magnitude()) ?: return satellite
    val position = parent.position+offset.normalized()*radius
    return satellite.copy(position = position,
        velocity = SimulationEngine.orbitVelocity(parent, position, satellite.mass, satellite.physicalScale),
        trail = listOf(position))
}

/** A local orbit assist, with a conservative check for disturbing differential gravity. */
fun satellitePlacement(parent: CelestialBody, satellite: CelestialBody, scene: List<CelestialBody>): SatellitePlacement {
    val offset=satellite.position-parent.position
    val distance=offset.magnitude()
    if (overlaps(parent, satellite, scene)) return SatellitePlacement.Overlap
    val safe = stableSatelliteRadius(parent, satellite, scene, distance) ?: return SatellitePlacement.StrongTides
    if (distance > safe*(1+1e-6)) return SatellitePlacement.StrongTides
    val smoothing=forceSoftening(parent,satellite)
    val square=distance*distance+smoothing*smoothing
    val attraction=SimulationEngine.gravitationalConstant*(parent.gravityMass+satellite.gravityMass)*distance/(square*sqrt(square))
    var tides=Vec2.Zero
    for (other in scene) {
        if (other.id == parent.id || other.id == satellite.id) continue
        fun acceleration(body: CelestialBody): Vec2 {
            val delta=other.position-body.position
            val softening=forceSoftening(other,body)
            val squared=delta.x*delta.x+delta.y*delta.y+softening*softening
            return delta*(SimulationEngine.gravitationalConstant*other.gravityMass/(squared*sqrt(squared)))
        }
        tides += acceleration(satellite)-acceleration(parent)
    }
    return if (!attraction.isFinite() || attraction <= 0.0 || !tides.magnitude().isFinite() ||
        tides.magnitude() > attraction*.2) SatellitePlacement.StrongTides else SatellitePlacement.Clear
}
