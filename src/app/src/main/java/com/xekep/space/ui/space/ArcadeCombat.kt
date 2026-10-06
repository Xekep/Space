package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*

data class SpaceProjectile(val position: Vec2, val velocity: Vec2, val ownerId: Long, val remaining: Double = 1.3)
data class CraftStatus(val age: Double = 0.0, val cooldown: Double = .15)
data class ArcadeCombat(val projectiles: List<SpaceProjectile> = emptyList(), val craft: Map<Long, CraftStatus> = emptyMap())
internal data class CombatResult(val bodies: List<CelestialBody>, val combat: ArcadeCombat, val events: List<CollisionEvent>)

fun launchCost(body: CelestialBody): Double = when (body.kind) {
    BodyKind.Ship -> 38.0 + body.mass * .1
    BodyKind.Rocket -> 22.0 + body.mass * .1
    else -> SimulationEngine.energyCostForMass(body.mass)
}

/** Ship pilots/guns are arcade-only. Projectiles never enter the N-body solver. */
internal fun prepareCombat(bodies: List<CelestialBody>, current: ArcadeCombat, dt: Double, controlledId: Long? = null): CombatResult {
    val shots = current.projectiles.toMutableList()
    val statuses = mutableMapOf<Long, CraftStatus>()
    val events = mutableListOf<CollisionEvent>()
    val next = bodies.mapNotNull { body ->
        if (!body.isVehicle) return@mapNotNull body
        if (body.fuelRemaining <= 1e-9) return@mapNotNull body
        val status = current.craft[body.id] ?: CraftStatus()
        val navigating=body.waypoints.isNotEmpty()
        val age = if (navigating) 0.0 else status.age + dt
        val target = bodies.filter { it.kind == BodyKind.Meteor }
            .minByOrNull { (it.position - body.position).magnitude() }
        var velocity = body.velocity
        var heading = body.heading
        var cooldown = (status.cooldown - dt).coerceAtLeast(0.0)
        if (target != null) {
            val offset = target.position - body.position
            val intercept = offset + target.velocity * (offset.magnitude() / 650.0).coerceAtMost(.7)
            val aim = intercept.normalized().takeIf { it.magnitude() > .5 } ?: heading
            if (body.id != controlledId && !navigating) heading = turnHeading(heading,
                aim, dt,
                radiansPerSecond = if (body.kind == BodyKind.Rocket) 7.2 else 3.6)
            if (body.kind == BodyKind.Ship) {
                // Bounded acceleration; launching in a useful direction still matters.
                val desired = heading * 220.0
                val correction = desired - velocity
                if (body.id != controlledId && !navigating) velocity += correction.normalized() * minOf(correction.magnitude(), 140.0 * dt)
                if (offset.magnitude() <= 650.0 && cooldown == 0.0 && shots.size < 48) {
                    shots += SpaceProjectile(body.position + aim * (body.radius.toDouble() + 4.0), aim * 650.0 + velocity * .25, body.id)
                    cooldown = .9
                }
            } else if (body.id != controlledId && !navigating) {
                val correction = heading * 310.0 - velocity
                velocity += correction.normalized() * minOf(correction.magnitude(), 240.0 * dt)
            }
        } else if (body.id != controlledId && !navigating && velocity.magnitude() > 2.0) heading = turnHeading(heading, velocity, dt)
        statuses[body.id] = CraftStatus(age, cooldown)
        body.copy(velocity = velocity, heading = heading)
    }
    return CombatResult(next, ArcadeCombat(shots, statuses), events)
}

internal fun advanceProjectiles(bodies: List<CelestialBody>, combat: ArcadeCombat, dt: Double): CombatResult {
    val targets = bodies.toMutableList()
    val shots = mutableListOf<SpaceProjectile>()
    val events = mutableListOf<CollisionEvent>()
    for (shot in combat.projectiles) {
        val travel = shot.velocity * dt
        // Earliest hit, not list order: one projectile cannot damage several enemies.
        val hit = targets.withIndex().filter { it.value.kind == BodyKind.Meteor }.mapNotNull { (index, body) ->
            val before = body.position - body.velocity * dt
            firstCircleContact(shot.position - before, shot.position + travel - body.position, body.radius + 3.0)?.let { index to it }
        }.minByOrNull { it.second }
        if (hit != null) {
            val meteor = targets[hit.first]
            val mass = meteor.mass - 120.0
            if (mass < 45.0) {
                targets.removeAt(hit.first)
                events += CollisionEvent(BodyKind.Meteor, BodyKind.Ship, meteor.position, meteor.id, shot.ownerId,
                    vehicleExplosion = true, seed = meteor.id.toInt())
            } else targets[hit.first] = meteor.copy(mass = mass, radius = SimulationEngine.radiusForMass(mass), color = Color(0xFFFFB76B))
        } else if (shot.remaining > dt) shots += shot.copy(position = shot.position + travel, remaining = shot.remaining - dt)
    }
    val liveIds = targets.filter { it.isVehicle }.map { it.id }.toSet()
    return CombatResult(targets, ArcadeCombat(shots, combat.craft.filterKeys { it in liveIds }), events)
}
