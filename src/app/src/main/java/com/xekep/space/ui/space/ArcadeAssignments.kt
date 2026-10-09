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
    // Preserve a living assignment; repairing overbooked input is deterministic too.
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
            val released=group.maxWith(compareBy<CelestialBody> { cost(it,enemy) }.thenBy { it.id })
            assignments.remove(released.id); group.remove(released)
            if (--missing == 0) break
        }
    }
    // Evaluate each pairing once per step; no ballistic forecasts or N-body work here.
    data class Candidate(val vehicle: CelestialBody, val enemy: CelestialBody, val cost: Double)
    val candidates=craft.filter { it.id !in assignments }.flatMap { vehicle ->
        threats.map { enemy -> Candidate(vehicle,enemy,cost(vehicle,enemy)) }
    }.sortedWith(compareBy<Candidate> { it.cost }.thenBy { it.vehicle.id }.thenBy { it.enemy.id })
    // Fill uncovered targets first, then allow a partner. Never exceed two pursuers.
    for (capacity in 1..2) for ((vehicle,enemy) in candidates) {
        if (vehicle.id in assignments || owners.getValue(enemy.id).size >= capacity) continue
        assignments[vehicle.id]=enemy
        owners.getValue(enemy.id)+=vehicle
    }
    return assignments
}
