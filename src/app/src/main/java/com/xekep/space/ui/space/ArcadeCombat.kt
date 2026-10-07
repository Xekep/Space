package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*

data class SpaceProjectile(val position: Vec2, val velocity: Vec2, val ownerId: Long, val remaining: Double = 1.3, val damage: Double = 120.0)
data class CraftStatus(val age: Double = 0.0, val cooldown: Double = .15)
data class ArcadeCombat(val projectiles: List<SpaceProjectile> = emptyList(), val craft: Map<Long, CraftStatus> = emptyMap())
internal data class CombatResult(val bodies: List<CelestialBody>, val combat: ArcadeCombat, val events: List<CollisionEvent>)

fun launchCost(body: CelestialBody): Double = when (body.kind) {
    BodyKind.Ship -> (if (body.shipClass == ShipClass.Guardian) 48.0 else 38.0) + body.mass * .1
    BodyKind.Rocket -> 22.0 + body.mass * .1
    else -> SimulationEngine.energyCostForMass(body.mass)
}

/** Ship pilots/guns are arcade-only. Projectiles never enter the N-body solver. */
internal fun prepareCombat(bodies: List<CelestialBody>, current: ArcadeCombat, dt: Double, controlledId: Long? = null, gunIntervalScale: Double = 1.0): CombatResult {
    val shots = current.projectiles.toMutableList()
    val statuses = mutableMapOf<Long, CraftStatus>()
    val events = mutableListOf<CollisionEvent>()
    val next = bodies.mapNotNull { body ->
        if (!body.isVehicle) return@mapNotNull body
        if (body.fuelRemaining <= 1e-9) return@mapNotNull body
        val status = current.craft[body.id] ?: CraftStatus()
        val navigating=body.waypoints.isNotEmpty()
        val autopilot=body.id != controlledId && !navigating && body.enginePowered
        val age = if (navigating) 0.0 else status.age + dt
        val target = bodies.filter { it.kind == BodyKind.Meteor }
            .minByOrNull { (it.position - body.position).magnitude() }
        var velocity = body.velocity
        var heading = body.heading
        var cooldown = (status.cooldown - dt).coerceAtLeast(0.0)
        if (body.kind == BodyKind.Ship && body.shipClass == ShipClass.Guardian) {
            val core=bodies.firstOrNull { it.kind == BodyKind.Core }
            if (core != null && autopilot) {
                val radial=(body.position-core.position).normalized().takeIf { it.magnitude() > .5 } ?: Vec2(1.0,0.0)
                val distance=(body.position-core.position).magnitude()
                val tangent=radial.perpendicular()
                val desired=core.velocity+tangent*SimulationEngine.orbitVelocity(core,core.position+radial*240.0).magnitude()+
                    radial*((240.0-distance)*1.2).coerceIn(-160.0,160.0)
                val correction=desired-velocity
                velocity+=correction.normalized()*minOf(correction.magnitude(),200.0*dt)
                if (velocity.magnitude() > 2.0) heading=turnHeading(heading,velocity,dt)
            }
            if (cooldown == 0.0) {
                val threats=bodies.filter { it.kind == BodyKind.Meteor && (it.position-body.position).magnitude() <= 480.0 }
                    .sortedWith(compareBy<CelestialBody> { if (it.mass <= 220) 0 else 1 }
                        .thenBy { (it.position-(core?.position ?: body.position)).magnitude() }).take(2)
                for (enemy in threats) {
                    if (shots.size >= 64) break
                    val offset=enemy.position-body.position
                    val aim=(offset+enemy.velocity*(offset.magnitude()/700.0).coerceAtMost(.6)).normalized()
                    shots+=SpaceProjectile(body.position+aim*(body.radius+4.0),aim*700.0+velocity*.25,body.id,damage=60.0)
                }
                if (threats.isNotEmpty()) cooldown=.65*gunIntervalScale
            }
        } else if (target != null) {
            val offset = target.position - body.position
            val intercept = offset + target.velocity * (offset.magnitude() / 650.0).coerceAtMost(.7)
            val aim = intercept.normalized().takeIf { it.magnitude() > .5 } ?: heading
            if (autopilot) heading = turnHeading(heading,
                aim, dt,
                radiansPerSecond = if (body.kind == BodyKind.Rocket) 7.2 else 3.6)
            if (body.kind == BodyKind.Ship) {
                // Bounded acceleration; launching in a useful direction still matters.
                val desired = heading * 220.0
                val correction = desired - velocity
                if (autopilot) velocity += correction.normalized() * minOf(correction.magnitude(), 140.0 * dt)
                if (offset.magnitude() <= 650.0 && cooldown == 0.0 && shots.size < 64) {
                    shots += SpaceProjectile(body.position + aim * (body.radius.toDouble() + 4.0), aim * 650.0 + velocity * .25, body.id)
                    cooldown = .9*gunIntervalScale
                }
            } else if (autopilot) {
                val correction = heading * 310.0 - velocity
                velocity += correction.normalized() * minOf(correction.magnitude(), 240.0 * dt)
            }
        } else if (autopilot && velocity.magnitude() > 2.0) heading = turnHeading(heading, velocity, dt)
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
            val mass = meteor.mass - shot.damage
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
