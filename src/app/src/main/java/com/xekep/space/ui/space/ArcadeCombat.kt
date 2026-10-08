package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*

data class SpaceProjectile(val position: Vec2, val velocity: Vec2, val ownerId: Long, val remaining: Double = 1.3, val damage: Double = 120.0)
data class CraftStatus(val age: Double = 0.0, val cooldown: Double = .15)
data class ArcadeCombat(val projectiles: List<SpaceProjectile> = emptyList(), val craft: Map<Long, CraftStatus> = emptyMap())
internal data class CombatResult(val bodies: List<CelestialBody>, val combat: ArcadeCombat, val events: List<CollisionEvent>)

fun launchCost(body: CelestialBody): Double = launchCost(body.kind,body.mass,body.shipClass)

internal fun launchCost(kind: BodyKind, mass: Double, shipClass: ShipClass = ShipClass.Interceptor): Double {
    val base = when (kind) {
        BodyKind.Ship -> (if (shipClass == ShipClass.Guardian) 48.0 else 38.0) + mass*.1
        BodyKind.Rocket -> 22.0 + mass*.1
        else -> return SimulationEngine.energyCostForMass(mass)
    }
    return base + if (vehicleHullClass(kind,mass) == VehicleHullClass.Heavy) heavyHullPremium(kind) else 0.0
}

/** Prefer the requested class; fall back to an affordable standard hull below its threshold. */
internal fun affordableVehicleMass(kind: BodyKind, requested: Double, energy: Double,
    shipClass: ShipClass = ShipClass.Interceptor): Double? {
    if (kind !in listOf(BodyKind.Ship,BodyKind.Rocket) || !requested.isFinite() || !energy.isFinite()) return null
    val minimum=if (kind == BodyKind.Ship) 24.0 else 12.0
    val base=launchCost(kind,0.0,shipClass)
    var mass=minOf(requested,(energy-base)/.1)
    if (vehicleHullClass(kind,mass) == VehicleHullClass.Heavy) {
        val heavyMaximum=(energy-base-heavyHullPremium(kind))/.1
        mass=if (heavyMaximum+1e-8 >= heavyHullMass(kind)) minOf(mass,heavyMaximum.coerceAtLeast(heavyHullMass(kind)))
            else minOf(mass,heavyHullMass(kind)-1.0)
    }
    return mass.takeIf { it+1e-8 >= minimum }?.coerceAtLeast(minimum)
}

/** Ship pilots/guns are arcade-only. Projectiles never enter the N-body solver. */
internal fun prepareCombat(bodies: List<CelestialBody>, current: ArcadeCombat, dt: Double, controlledId: Long? = null, gunIntervalScale: Double = 1.0): CombatResult {
    val shots = current.projectiles.toMutableList()
    val statuses = mutableMapOf<Long, CraftStatus>()
    val events = mutableListOf<CollisionEvent>()
    val targeting by lazy { ArcadeTargeting(bodies) }
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
                velocity+=correction.normalized()*minOf(correction.magnitude(),200.0*body.vehicleAccelerationScale*dt)
                if (velocity.magnitude() > 2.0) heading=turnHeading(heading,velocity,dt,3.6*body.vehicleTurnScale)
            }
            if (cooldown == 0.0) {
                val threats=bodies.filter { it.kind == BodyKind.Meteor && (it.position-body.position).magnitude() <= 480.0 }
                    .sortedWith(compareBy<CelestialBody> { if (it.mass <= 220) 0 else 1 }
                        .thenBy { (it.position-(core?.position ?: body.position)).magnitude() })
                var fired=0
                for (enemy in threats) {
                    if (shots.size >= 64 || fired >= 2) break
                    val aim=targeting.solution(body,enemy,velocity,700.0) ?: continue
                    shots+=SpaceProjectile(aim.origin,aim.velocity,body.id,damage=60.0*body.vehicleDamageScale)
                    fired++
                }
                if (fired > 0) cooldown=.65*gunIntervalScale
                else if (threats.isNotEmpty()) cooldown=.10 // Retry blocked/unreachable targets at a bounded rate.
            }
        } else if (target != null) {
            val offset = target.position - body.position
            val intercept = offset + target.velocity * (offset.magnitude() / 650.0).coerceAtMost(.7)
            val aim = intercept.normalized().takeIf { it.magnitude() > .5 } ?: heading
            if (autopilot) heading = turnHeading(heading,
                aim, dt,
                radiansPerSecond = (if (body.kind == BodyKind.Rocket) 7.2 else 3.6)*body.vehicleTurnScale)
            if (body.kind == BodyKind.Ship) {
                // Bounded acceleration; launching in a useful direction still matters.
                val desired = heading * 220.0
                val correction = desired - velocity
                if (autopilot) velocity += correction.normalized() * minOf(correction.magnitude(), 140.0 * body.vehicleAccelerationScale * dt)
                if (offset.magnitude() <= 650.0 && cooldown == 0.0 && shots.size < 64) {
                    val solution=bodies.filter { it.kind == BodyKind.Meteor && (it.position-body.position).magnitude() <= 650.0 }
                        .sortedBy { (it.position-body.position).magnitude() }
                        .firstNotNullOfOrNull { targeting.solution(body,it,velocity,650.0) }
                    if (solution != null) {
                        shots += SpaceProjectile(solution.origin,solution.velocity,body.id,damage=120.0*body.vehicleDamageScale)
                        cooldown = .9*gunIntervalScale
                    } else cooldown=.10
                }
            } else if (autopilot) {
                val correction = heading * 310.0 - velocity
                velocity += correction.normalized() * minOf(correction.magnitude(), 240.0 * body.vehicleAccelerationScale * dt)
            }
        } else if (autopilot && velocity.magnitude() > 2.0) heading = turnHeading(heading, velocity, dt,3.6*body.vehicleTurnScale)
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
        val hit = targets.withIndex().filter { it.value.kind == BodyKind.Meteor || it.value.kind == BodyKind.ArcadePlanet }.mapNotNull { (index, body) ->
            val before = body.position - body.velocity * dt
            firstCircleContact(shot.position - before, shot.position + travel - body.position, body.radius + 3.0)?.let { index to it }
        }.minByOrNull { it.second }
        if (hit != null) {
            val meteor = targets[hit.first]
            if (meteor.kind == BodyKind.ArcadePlanet) continue
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
