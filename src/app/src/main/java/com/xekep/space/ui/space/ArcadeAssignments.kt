package com.xekep.space.ui.space

import com.xekep.space.sim.*
import kotlin.math.acos

/** Shared pursuit slots. Guns may still intercept an opportunistic target without chasing it. */
internal fun assignArcadeTargets(
    bodies: List<CelestialBody>, current: ArcadeCombat, controlledId: Long?,
): Map<Long, CelestialBody> {
    val threats=bodies.filter { it.kind == BodyKind.Meteor }
    val craft=bodies.filter { it.isVehicle && it.enginePowered && it.fuelRemaining > 1e-9 &&
        it.id != controlledId && it.waypoints.isEmpty() &&
        !(it.kind == BodyKind.Ship && it.shipClass == ShipClass.Guardian) }
    if (threats.isEmpty() || craft.isEmpty()) return emptyMap()
    val enemies=threats.associateBy { it.id }
    val assignments=mutableMapOf<Long,CelestialBody>()
    val owners=threats.associate { it.id to mutableListOf<CelestialBody>() }
    fun cost(vehicle: CelestialBody, target: CelestialBody): Double {
        val offset=target.position-vehicle.position
        val speed=if (vehicle.kind == BodyKind.Rocket) 310.0 else 220.0
        val direction=(offset+target.velocity*(offset.magnitude()/speed).coerceAtMost(.7)).normalized()
        val heading=vehicle.heading.normalized()
        val turn=acos((heading.x*direction.x+heading.y*direction.y).coerceIn(-1.0,1.0)) /
            ((if (vehicle.kind == BodyKind.Rocket) 7.2 else 3.6)*vehicle.vehicleTurnScale)
        return offset.magnitude()/speed+turn
    }
    // Cache every pairing once; living assignments can now be handed to a better interceptor too.
    data class Candidate(val vehicle: CelestialBody, val enemy: CelestialBody, val cost: Double)
    val candidates=craft.flatMap { vehicle ->
        threats.map { enemy -> Candidate(vehicle,enemy,cost(vehicle,enemy)) }
    }.sortedWith(compareBy<Candidate> { it.cost }.thenBy { it.vehicle.id }.thenBy { it.enemy.id })
    val costs=candidates.groupBy { it.vehicle.id }.mapValues { (_, pairs) -> pairs.associate { it.enemy.id to it.cost } }
    fun travel(vehicle: CelestialBody, target: CelestialBody)=costs.getValue(vehicle.id).getValue(target.id)
    fun earlier(candidate: Double, incumbent: Double)=
        incumbent-candidate > maxOf(.35,incumbent*.20)
    fun assign(vehicle: CelestialBody, target: CelestialBody?) {
        assignments.remove(vehicle.id)?.let { owners.getValue(it.id).remove(vehicle) }
        if (target != null) { assignments[vehicle.id]=target; owners.getValue(target.id)+=vehicle }
    }
    // Start from living assignments. Tiny differences do not reset pursuit every frame.
    for (vehicle in craft.sortedBy { it.id }) {
        val enemy=enemies[current.craft[vehicle.id]?.targetId] ?: continue
        val group=owners.getValue(enemy.id)
        if (group.size < 2) { assignments[vehicle.id]=enemy; group+=vehicle }
    }
    // A new threat gets a defender before an old threat gets a second one.
    var missing=owners.values.count { it.isEmpty() }-(craft.size-assignments.size)
    if (missing > 0) {
        for (enemy in threats.sortedBy { it.id }) {
            val group=owners.getValue(enemy.id)
            if (group.size < 2) continue
            val released=group.maxWith(compareBy<CelestialBody> { travel(it,enemy) }.thenBy { it.id })
            assignments.remove(released.id); group.remove(released)
            if (--missing == 0) break
        }
    }
    // Fill uncovered targets first, then allow a partner. Never exceed two pursuers.
    for (capacity in 1..2) for ((vehicle,enemy) in candidates) {
        if (vehicle.id in assignments || owners.getValue(enemy.id).size >= capacity) continue
        assignments[vehicle.id]=enemy
        owners.getValue(enemy.id)+=vehicle
    }
    // Improve the earliest interceptions first. A handoff must beat both the incumbent
    // and the challenger's own pursuit by 20% and at least .35s to avoid target jitter.
    for ((vehicle,enemy,seconds) in candidates) {
        val previous=assignments[vehicle.id]
        if (previous?.id == enemy.id || (previous != null && !earlier(seconds,travel(vehicle,previous)))) continue
        val group=owners.getValue(enemy.id)
        val oldCoverage=previous?.let { owners.getValue(it.id).size } ?: 0
        if (group.size < 2 && (oldCoverage != 1 || group.isEmpty())) {
            assign(vehicle,enemy)
            continue
        }
        val displaced=group.maxWithOrNull(compareBy<CelestialBody> { travel(it,enemy) }.thenBy { it.id }) ?: continue
        if (!earlier(seconds,travel(displaced,enemy))) continue
        // Swap, rather than abandon the challenger's old threat. A reserve replacement
        // frees the slower incumbent to patrol until another pursuit slot becomes available.
        assign(displaced,null)
        assign(vehicle,enemy)
        assign(displaced,previous)
    }
    return assignments
}
