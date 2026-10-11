package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*
import kotlin.random.Random

enum class FleetCommand { Move, Guard, Attack }
data class FleetOrder(val command: FleetCommand, val position: Vec2, val targetId: Long? = null)
enum class StationKind { Factory, Service, Bastion }
enum class FactoryProduct { Off, Rocket, Interceptor, Guardian }
data class OrbitalStation(val id: Long, val position: Vec2, val kind: StationKind, val level: Int = 1,
    val hull: Int = 3, val product: FactoryProduct = FactoryProduct.Off, val remaining: Double = if (kind == StationKind.Bastion) .2 else 18.0)
data class EnemyBase(val id: Long, val position: Vec2, val remaining: Double = 25.0, val launches: Int = 0)
data class EnemyCraft(val kind: BodyKind, val cooldown: Double = 3.0, val age: Double = 0.0)
data class StrategyBattle(val bases: List<EnemyBase>, val stations: List<OrbitalStation> = emptyList(),
    val orders: Map<Long,FleetOrder> = emptyMap(), val enemies: Map<Long,EnemyCraft> = emptyMap(), val victory: Boolean = false)

internal fun createStrategyBattle(run: ArcadeSession): ArcadeSession {
    val core=run.bodies.first { it.kind == BodyKind.Core }
    val hull=when (run.difficulty) { ArcadeDifficulty.Easy -> 3000.0; ArcadeDifficulty.Normal -> 4200.0; ArcadeDifficulty.Hard -> 5600.0 }
    val bases=(0..2).map { index ->
        val angle=-PI/2+index*2*PI/3
        EnemyBase(SimulationEngine.newBodyId(),core.position+Vec2(cos(angle),sin(angle))*1200.0,12.0+index*4)
    }
    val nodes=bases.map { CelestialBody(it.id,it.position,Vec2.Zero,hull,38f,Color(0xFFFF8275),BodyKind.Meteor) }
    val home=OrbitalStation(SimulationEngine.newBodyId(),core.position+Vec2(240.0,200.0),StationKind.Factory,
        product=FactoryProduct.Interceptor,remaining=3.0)
    return run.copy(bodies=run.bodies+nodes,strategy=StrategyBattle(bases,listOf(home)))
}

internal fun stationCost(kind: StationKind)=when(kind) { StationKind.Factory -> 65.0; StationKind.Service -> 55.0; StationKind.Bastion -> 70.0 }
internal fun canBuildStation(run: ArcadeSession, kind: StationKind, position: Vec2): Boolean {
    val battle=run.strategy ?: return false
    val core=run.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return false
    if (!position.x.isFinite() || !position.y.isFinite() || run.lives <= 0 || battle.victory || battle.stations.size >= 4 || run.energy < stationCost(kind)) return false
    val distance=(position-core.position).magnitude()
    return distance in 180.0..1800.0 && battle.stations.none { (it.position-position).magnitude() < 160.0 } &&
        run.bodies.none { (it.position-position).magnitude() < it.radius+65.0 }
}
internal fun buildStation(run: ArcadeSession, kind: StationKind, position: Vec2): ArcadeSession {
    if (!canBuildStation(run,kind,position)) return run
    val battle=run.strategy ?: return run
    return run.copy(energy=run.energy-stationCost(kind),strategy=battle.copy(stations=battle.stations+
        OrbitalStation(SimulationEngine.newBodyId(),position,kind)))
}
internal fun upgradeStation(run: ArcadeSession, id: Long): ArcadeSession {
    val battle=run.strategy ?: return run
    val station=battle.stations.firstOrNull { it.id == id } ?: return run
    if (station.level >= 2 || run.energy < 45.0 || run.lives <= 0 || battle.victory) return run
    return run.copy(energy=run.energy-45.0,strategy=battle.copy(stations=battle.stations.map {
        if (it.id == id) it.copy(level=2,hull=(it.hull+1).coerceAtMost(4)) else it }))
}
internal fun issueFleetOrder(run: ArcadeSession, ids: Set<Long>, command: FleetCommand, position: Vec2, targetId: Long? = null): ArcadeSession {
    val battle=run.strategy ?: return run
    if (!position.x.isFinite() || !position.y.isFinite() || run.lives <= 0 || battle.victory) return run
    val craft=run.bodies.filter { it.isVehicle && it.id in ids }.sortedBy { it.id }
    val count=craft.size
    val orders=craft.mapIndexed { index,body ->
        // Distinct slots keep the fleet from converging on the same point.
        val offset=if (count <= 1) Vec2.Zero else Vec2(cos(index*2*PI/count),sin(index*2*PI/count))*maxOf(70.0,count*12.0)
        body.id to FleetOrder(command,position+if (command == FleetCommand.Move) offset else Vec2.Zero,targetId)
    }.toMap()
    return run.copy(strategy=battle.copy(orders=battle.orders+orders),bodies=run.bodies.map {
        if (it.id in ids && it.isVehicle) it.copy(waypoints=emptyList(),routePath=null,routeAvoiding=false,pilotTargetSpeed=null) else it })
}

/** Tactical commands override pursuit, while manual input and existing authored routes win. */
internal fun fleetVelocity(body: CelestialBody, order: FleetOrder, scene: List<CelestialBody>, stations: List<OrbitalStation>, eventPosition: Vec2? = null): Vec2? {
    if (order.command == FleetCommand.Attack) return if (body.shipClass == ShipClass.Guardian && body.kind == BodyKind.Ship)
        scene.firstOrNull { it.id == order.targetId }?.let { (it.position-body.position).normalized()*220.0 } else null
    val target=scene.firstOrNull { it.id == order.targetId }
    val anchor=(if (order.targetId == -1L) eventPosition else null) ?: target?.position ?: stations.firstOrNull { it.id == order.targetId }?.position ?: order.position
    val anchorVelocity=target?.velocity ?: Vec2.Zero
    val offset=anchor-body.position
    return if (order.command == FleetCommand.Move) anchorVelocity+offset.normalized()*minOf(offset.magnitude()*1.6,180.0)
    else {
        val radial=(body.position-anchor).normalized().takeIf { it.magnitude() > .5 } ?: Vec2(1.0,0.0)
        val radius=150.0+(body.id%3)*42.0
        anchorVelocity+radial.perpendicular()*85.0+radial*((radius-offset.magnitude())*1.3).coerceIn(-150.0,150.0)
    }
}

/** Bases and orbital platforms are anchored installations, outside the N-body solver. */
internal fun advanceStrategy(input: ArcadeSession, dt: Double, random: Random, control: ManualFlightControl? = null): ArcadeSession {
    val battle=input.strategy ?: return input
    if (dt <= 0 || !dt.isFinite() || input.lives <= 0 || battle.victory) return input
    val baseIds=battle.bases.map { it.id }.toSet()
    val core=input.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return input
    var energy=minOf(input.maxEnergy,input.energy+(8.0+2.0*input.level(ArcadeUpgrade.Reactor))*dt)
    var bodies=input.bodies
    var enemies=battle.enemies.toMutableMap()
    var launches=input.launches
    val bases=battle.bases.map { base ->
        if (bodies.none { it.id == base.id }) return@map base
        val remaining=base.remaining-dt
        if (remaining > 0 || enemies.size >= 24) return@map base.copy(remaining=maxOf(0.0,remaining))
        val kind=if (base.launches%3 == 1) BodyKind.Ship else BodyKind.Rocket
        val direction=(core.position-base.position).normalized()
        val position=base.position+direction*85.0
        val id=SimulationEngine.newBodyId()
        val mass=if (kind == BodyKind.Ship) 180.0 else 100.0
        bodies=bodies+CelestialBody(id,position,direction*140.0,mass,SimulationEngine.radiusForMass(mass),Color(0xFFFF8275),BodyKind.Meteor,heading=direction)
        enemies[id]=EnemyCraft(kind)
        base.copy(remaining=when(input.difficulty) { ArcadeDifficulty.Easy -> 12.0; ArcadeDifficulty.Normal -> 10.0; ArcadeDifficulty.Hard -> 8.0 },launches=base.launches+1)
    }
    var stationShots=input.combat.projectiles
    val targeting by lazy { ArcadeTargeting(bodies,baseIds) }
    var stations=battle.stations.map { station ->
        if (station.kind == StationKind.Bastion) {
            val remaining=maxOf(0.0,station.remaining-dt)
            if (remaining > 0 || stationShots.size >= 64) return@map station.copy(remaining=remaining)
            val gun=CelestialBody(station.id,station.position,Vec2.Zero,0.0,38f,Color.Cyan,BodyKind.Ship)
            val enemy=bodies.filter { it.id in enemies && (it.position-station.position).magnitude() <= 600.0 }
                .sortedBy { (it.position-core.position).magnitude() }.firstNotNullOfOrNull { target -> targeting.solution(gun,target,Vec2.Zero,650.0) }
            if (enemy != null) stationShots=stationShots+SpaceProjectile(enemy.origin,enemy.velocity,station.id,damage=90.0,height=enemy.height,verticalVelocity=enemy.verticalVelocity)
            return@map station.copy(remaining=if (enemy == null) .2 else if (station.level == 2) .9 else 1.2)
        }
        if (station.kind != StationKind.Factory || station.product == FactoryProduct.Off) return@map station
        val remaining=maxOf(0.0,station.remaining-dt)
        if (remaining > 0) return@map station.copy(remaining=remaining)
        val kind=if (station.product == FactoryProduct.Rocket) BodyKind.Rocket else BodyKind.Ship
        val mass=if (kind == BodyKind.Rocket) (if (station.level == 2) 24.0 else 18.0) else (if (station.level == 2) 60.0 else 36.0)
        val shipClass=if (station.product == FactoryProduct.Guardian) ShipClass.Guardian else ShipClass.Interceptor
        val cost=launchCost(kind,mass,shipClass)
        if (energy < cost+20 || bodies.count { it.kind == kind } >= input.launchLimit(kind)) return@map station.copy(remaining=0.0)
        val direction=(station.position-core.position).normalized()
        val point=station.position+direction*75.0
        if (bodies.any { (it.position-point).magnitude() < it.radius+35.0 }) return@map station.copy(remaining=0.0)
        val craft=CelestialBody(SimulationEngine.newBodyId(),point,direction*120.0,mass,SimulationEngine.radiusForMass(mass),
            if (kind == BodyKind.Rocket) Color(0xFFFFB36B) else Color(0xFF8BD3FF),kind,heading=direction,shipClass=shipClass,fuelConsumptionScale=input.fuelScale)
        bodies=bodies+craft; energy-=cost; launches++
        station.copy(remaining=if (station.level == 2) 10.0 else 18.0)
    }
    bodies=bodies.map { body ->
        if (!body.isVehicle || body.fuelRemaining <= 1e-9) body else {
            val service=stations.filter { it.kind == StationKind.Service && (it.position-body.position).magnitude() < 170.0 && abs(body.flightHeight) < 60.0 }.maxOfOrNull { it.level } ?: 0
            if (service == 0) body else body.copy(fuelRemaining=minOf(vehicleFuelCapacity(body.kind),body.fuelRemaining+dt*service*2))
        }
    }
    val anchored=bodies.filter { it.id in baseIds }
    val routed=applyFlightControls(bodies.filterNot { it.id in baseIds },control,dt)+anchored
    var enemyShots=stationShots
    bodies=routed.map { body ->
        val enemy=enemies[body.id] ?: return@map body
        val target=input.bodies.filter { it.isVehicle && (it.position-body.position).magnitude() < 450.0 }.minByOrNull { (it.position-body.position).magnitude() }
        val destination=target?.position ?: core.position
        val heading=turnHeading(body.heading,destination-body.position,dt,2.2)
        val desired=heading*(if (enemy.kind == BodyKind.Rocket) 190.0 else 140.0)
        val correction=desired-body.velocity
        val velocity=body.velocity+correction.normalized()*minOf(correction.magnitude(),110.0*dt)
        var cooldown=maxOf(0.0,enemy.cooldown-dt)
        if (enemy.kind == BodyKind.Ship && cooldown == 0.0 && target != null && enemyShots.size < 64) {
            enemyShots=enemyShots+SpaceProjectile(body.position+heading*(body.radius+5.0),
                (target.position-body.position+target.velocity*.3).normalized()*430.0+velocity,body.id,remaining=1.6,damage=18.0,hostile=true)
            cooldown=3.0
        }
        enemies[body.id]=enemy.copy(cooldown=cooldown,age=enemy.age+dt)
        body.copy(velocity=velocity,heading=heading)
    }
    val prepared=prepareCombat(bodies,input.combat.copy(projectiles=enemyShots),dt,control?.bodyId,input.gunIntervalScale,baseIds,battle.orders,stations,input.sideEvent?.position)
    val step=SimulationEngine.stepArcade(prepared.bodies.filterNot { it.id in baseIds },dt,control?.bodyId,control?.depthTimeScale ?: 1.0)
    // Include anchored bases only for swept contacts/projectiles, never for gravity integration.
    val stationHits=mutableListOf<CollisionEvent>()
    var live=step.bodies.toMutableList()
    for (node in anchored) {
        var mass=node.mass
        for (craft in live.filter { it.isVehicle }) {
            val previous=prepared.bodies.firstOrNull { it.id == craft.id } ?: craft
            if (firstSphereContact(previous.position-node.position,craft.position-node.position,previous.flightHeight,craft.flightHeight,(node.radius+craft.radius).toDouble()) == null) continue
            mass-=if (craft.kind == BodyKind.Rocket) (if (craft.hullClass == VehicleHullClass.Heavy) 400.0 else 220.0)*craft.vehicleDamageScale else craft.mass*.85
            live.removeAll { it.id == craft.id }
            stationHits+=CollisionEvent(BodyKind.Meteor,craft.kind,node.position,defenderId=craft.id,vehicleExplosion=true)
        }
        if (mass >= 45.0) live.add(node.copy(mass=mass,velocity=Vec2.Zero,trail=listOf(node.position)))
        else stationHits+=CollisionEvent(BodyKind.Meteor,BodyKind.Rocket,node.position,node.id,vehicleExplosion=true)
    }
    stations=stations.mapNotNull { station ->
        var hull=station.hull
        for (enemy in live.filter { it.id in enemies }) {
            val previous=prepared.bodies.firstOrNull { it.id == enemy.id } ?: enemy
            if (firstSphereContact(previous.position-station.position,enemy.position-station.position,0.0,0.0,enemy.radius+38.0) != null) {
                live.removeAll { it.id == enemy.id }; hull--
                stationHits+=CollisionEvent(BodyKind.Meteor,BodyKind.ArcadePlanet,station.position,enemy.id,vehicleExplosion=true)
            }
        }
        if (hull <= 0) { stationHits+=CollisionEvent(BodyKind.ArcadePlanet,BodyKind.Meteor,station.position,vehicleExplosion=true); null }
        else station.copy(hull=hull)
    }
    val impacts=advanceProjectiles(advanceWaypoints(prepared.bodies,live),prepared.combat,dt)
    val events=step.collisions+stationHits+impacts.events
    val hit=events.any { it.secondKind == BodyKind.Core && it.firstKind == BodyKind.Meteor }
    val lives=if (hit && input.immunity <= 0) maxOf(0,input.lives-1) else input.lives
    val ids=impacts.bodies.map { it.id }.toSet()
    val enemiesAlive=enemies.filter { (id,status) -> id in ids && status.age < 100.0 }
    val ended=enemies.keys-enemiesAlive.keys
    val stopped=events.mapNotNull { it.meteorId }.distinct().size
    val aliveBases=bases.filter { it.id in ids }
    val next=input.copy(bodies=impacts.bodies.filterNot { it.id in ended },energy=minOf(input.maxEnergy,energy+stopped*4),elapsed=input.elapsed+dt,
        lives=lives,launches=launches,immunity=if (hit && input.immunity <= 0) .6 else maxOf(0.0,input.immunity-dt),
        hitFlash=if (hit) 1.0 else maxOf(0.0,input.hitFlash-dt*1.8),destroyed=input.destroyed+stopped,
        score=input.score+stopped*100*input.difficulty.scoreFactor,combat=impacts.combat,
        explosions=advanceExplosions(input.explosions,events,dt),
        strategy=battle.copy(bases=aliveBases,stations=stations,enemies=enemiesAlive,
            orders=battle.orders.filter { (id,order) -> id in ids && (order.command != FleetCommand.Attack || order.targetId in ids) },victory=aliveBases.isEmpty() && lives > 0))
    return advanceSideEvent(input,prepareSideEvent(next,random),dt,control?.bodyId)
}
