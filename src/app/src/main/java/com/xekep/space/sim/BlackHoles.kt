package com.xekep.space.sim

import kotlin.math.cbrt

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
            firstCircleContact(oldTarget.position-oldHole.position,target.position-hole.position,hole.radius.toDouble())?.let { it to target }
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
        }
        bodies[index]=absorber
    }
    return StepResult(bodies.filter { it.id !in removed },events)
}
