package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.sqrt

/** Heavy interceptors trade rate of fire for a siege round, only against heavy targets. */
internal fun interceptorWeapon(ship: CelestialBody, target: CelestialBody): Pair<Double,Double> {
    val siege=ship.hullClass == VehicleHullClass.Heavy && target.mass >= 400.0
    return (120.0*ship.vehicleDamageScale*(if (siege) 2.25 else 1.0)) to
        (.9*(if (siege) 2.0 else 1.0))
}

/** A contact blast has at most two secondary hits, no chain reaction or friendly damage.
 * Run before giant fragmentation so newly created fragments are never erased immediately.
 * The core/planet shield targets on the other side, and depth separates flight layers.
 */
internal fun heavyRocketBlasts(before: List<CelestialBody>, after: List<CelestialBody>, contacts: List<CollisionEvent>): Pair<List<CelestialBody>,List<CollisionEvent>> {
    val rockets=before.filter { it.kind == BodyKind.Rocket && it.hullClass == VehicleHullClass.Heavy }.associateBy { it.id }
    if (rockets.isEmpty()) return after to emptyList()
    val targets=after.toMutableList()
    val events=mutableListOf<CollisionEvent>()
    val used=mutableSetOf<Long>()
    for (impact in contacts) {
        if (impact.meteorId == null || impact.secondKind != BodyKind.Rocket) continue
        val rocket=rockets[impact.defenderId] ?: continue
        if (!used.add(rocket.id)) continue
        val center=impact.position
        val candidates=targets.filter { it.kind == BodyKind.Meteor }.map { body ->
            val dz=body.flightHeight-rocket.flightHeight
            body to sqrt((body.position-center).let { it.x*it.x+it.y*it.y }+dz*dz)
        }.filter { it.second <= 90.0 }.sortedWith(compareBy<Pair<CelestialBody,Double>> { it.second }.thenBy { it.first.id })
        var hits=0
        for ((body,_) in candidates) {
            if (hits >= 2) break
            val blocked=targets.any { obstacle ->
                (obstacle.kind == BodyKind.Core || obstacle.kind == BodyKind.ArcadePlanet) &&
                    firstSphereContact(center-obstacle.position,body.position-obstacle.position,
                        rocket.flightHeight-obstacle.flightHeight,body.flightHeight-obstacle.flightHeight,obstacle.radius.toDouble()) != null
            }
            if (blocked) continue
            val index=targets.indexOfFirst { it.id == body.id }
            if (index < 0) continue
            hits++
            val mass=body.mass-180.0
            if (mass < 45.0) {
                targets.removeAt(index)
                events+=CollisionEvent(BodyKind.Meteor,BodyKind.Rocket,body.position,body.id,rocket.id,
                    vehicleExplosion=true,seed=body.id.toInt())
            } else targets[index]=body.copy(mass=mass,radius=SimulationEngine.radiusForMass(mass),color=Color(0xFFFFB76B))
        }
    }
    return targets to events
}
