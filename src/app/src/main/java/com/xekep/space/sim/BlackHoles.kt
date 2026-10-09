package com.xekep.space.sim

import kotlin.math.cbrt

/** Approximate tidal-disruption encounter for extended natural bodies in solar units.
 * This is not extra suction: outside this close-encounter radius gravity stays Newtonian.
 * Spacecraft and grains retain their geometric contact radius.
 */
internal fun blackHoleCaptureRadius(hole: CelestialBody, target: CelestialBody): Double {
    val contact = hole.radius.toDouble() + target.radius
    return if (hole.physicalScale && target.physicalScale && !target.isVehicle &&
        target.kind != BodyKind.BlackHole && target.orbitalDetail == null && target.mass > 0)
        maxOf(contact, target.radius * cbrt(hole.mass / target.mass)) else contact
}

/** Newtonian sandbox attraction with an absorbing horizon, independent of the merge switch. */
fun absorbBlackHoles(before: List<CelestialBody>, after: List<CelestialBody>): StepResult {
    if (after.none { it.kind == BodyKind.BlackHole }) return StepResult(after,emptyList())
    val previous=before.associateBy { it.id }
    val bodies=after.toMutableList(); val removed=mutableSetOf<Long>(); val events=mutableListOf<CollisionEvent>()
    val holes=after.filter { it.kind == BodyKind.BlackHole }.sortedWith(compareByDescending<CelestialBody> { it.mass }.thenBy { it.id })
    for (hole in holes) {
        if (hole.id in removed) continue
        val index=bodies.indexOfFirst { it.id == hole.id }
        var absorber=bodies[index]
        val contacts=after.filter { it.id != hole.id && it.id !in removed }.mapNotNull { target ->
            val oldHole=previous[hole.id] ?: hole; val oldTarget=previous[target.id] ?: target
            firstSphereContact(oldTarget.position-oldHole.position,target.position-hole.position,oldTarget.flightHeight-oldHole.flightHeight,target.flightHeight-hole.flightHeight,blackHoleCaptureRadius(hole,target))?.let { it to target }
        }.sortedWith(compareBy<Pair<Double,CelestialBody>> { it.first }.thenBy { it.second.id })
        for ((fraction,contact) in contacts) {
            val target=bodies.first { it.id == contact.id }
            if (!removed.add(target.id)) continue
            val total=absorber.mass+target.mass
            val mass=total.coerceAtMost(100000000.0)
            absorber=absorber.copy(mass=mass,velocity=(absorber.velocity*absorber.mass+target.velocity*target.mass)/total,
                radius=(absorber.radius*cbrt(mass/absorber.mass)).toFloat().coerceAtMost(10000f),solar=null)
            val oldTarget=previous[target.id] ?: target
            val impact=oldTarget.position+(target.position-oldTarget.position)*fraction
            if (target.isVehicle) events += CollisionEvent(target.kind,BodyKind.BlackHole,impact,
                vehicleExplosion=true,velocity=target.velocity,seed=target.id.toInt())
            else if (target.physicalScale && target.orbitalDetail == null) events += CollisionEvent(target.kind,BodyKind.BlackHole,impact,
                velocity=target.velocity,seed=target.id.toInt(),collapseRadius=target.radius)
        }
        bodies[index]=absorber
    }
    return StepResult(bodies.filter { it.id !in removed },events)
}

/** Gameplay threshold for an accreted remnant, not a stellar evolution calculation.
 * Use a separate solar-scale threshold so an ordinary Sun never collapses on creation. */
const val PLAYGROUND_COLLAPSE_MASS = 18000.0
const val SOLAR_COLLAPSE_MASS = 300000.0

internal fun collapseMassiveRemnants(after: List<CelestialBody>,before: List<CelestialBody>): StepResult {
    if (after.size == before.size && after.indices.all { after[it].id == before[it].id && after[it].mass == before[it].mass })
        return StepResult(after,emptyList())
    val old=before.associateBy { it.id }
    val events=ArrayList<CollisionEvent>()
    val bodies=after.map { body ->
        val threshold=if (body.physicalScale) SOLAR_COLLAPSE_MASS else PLAYGROUND_COLLAPSE_MASS
        if (body.isVehicle || body.isDebris || body.kind == BodyKind.BlackHole || body.mass < threshold ||
            body.mass <= (old[body.id]?.mass ?: body.mass)) body else {
            events+=CollisionEvent(body.kind,BodyKind.BlackHole,body.position,velocity=body.velocity,
                seed=body.id.toInt(),collapseRadius=body.radius)
            body.copy(kind=BodyKind.BlackHole,solar=null,color=androidx.compose.ui.graphics.Color(0xFFCB9BFF),
                radius=if (body.physicalScale) (.03*cbrt(body.mass/12000)).toFloat()
                    else (10*cbrt(body.mass/12000)).toFloat(),trail=listOf(body.position))
        }
    }
    return StepResult(bodies,events)
}
