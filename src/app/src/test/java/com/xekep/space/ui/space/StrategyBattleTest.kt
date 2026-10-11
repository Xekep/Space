package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StrategyBattleTest {
    private fun run(difficulty: ArcadeDifficulty=ArcadeDifficulty.Normal)=createStrategyBattle(ArcadeSession(
        SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),SpaceCamera(),IntSize(900,1400),difficulty))
    private fun ship(position: Vec2, kind: BodyKind=BodyKind.Ship)=CelestialBody(SimulationEngine.newBodyId(),position,Vec2(20.0,0.0),36.0,6f,Color.Cyan,kind,heading=Vec2(1.0,0.0))
    private fun inert(run: ArcadeSession)=run.copy(strategy=run.strategy!!.copy(bases=run.strategy!!.bases.map { it.copy(remaining=60.0) },stations=run.strategy!!.stations.map { it.copy(product=FactoryProduct.Off) }))

    @Test fun battleHasThreeIndependentBasesAndAWorkingFactory() {
        val run=run();assertEquals(3,run.strategy!!.bases.size);assertEquals(1,run.strategy!!.stations.size)
        assertTrue(run.guardianUnlocked);assertEquals(12,run.launchLimit(BodyKind.Ship));assertEquals(24,run.launchLimit(BodyKind.Rocket))
        assertEquals(3,run.bodies.count { it.kind == BodyKind.Meteor });assertNull(run.carrier)
        var advanced=run
        repeat(181) { advanced=advanceStrategy(advanced,1.0/60,Random(17)) }
        assertEquals(1,advanced.launches);assertEquals(1,advanced.bodies.count { it.isVehicle })
        assertTrue(advanced.energy in (120.0-launchCost(BodyKind.Ship,36.0))..(120.4-launchCost(BodyKind.Ship,36.0)))
    }
    @Test fun buildingChecksEnergyClearanceRangeAndCapacityWithoutSpendingOnFailure() {
        var run=run();val core=run.bodies.first { it.kind == BodyKind.Core }
        assertSame(run,buildStation(run,StationKind.Factory,core.position))
        assertSame(run,buildStation(run,StationKind.Factory,core.position+Vec2(3000.0,0.0)))
        val point=core.position+Vec2(-350.0,80.0)
        val built=buildStation(run,StationKind.Factory,point)
        assertEquals(55.0,built.energy,0.0);assertEquals(2,built.strategy!!.stations.size)
        assertSame(built,buildStation(built,StationKind.Factory,point+Vec2(30.0,0.0)))
        assertSame(built,buildStation(built,StationKind.Factory,core.position+Vec2(300.0,-300.0)))
        run=built.copy(energy=120.0)
        run=buildStation(run,StationKind.Service,core.position+Vec2(-300.0,-300.0)).copy(energy=120.0)
        run=buildStation(run,StationKind.Service,core.position+Vec2(300.0,-300.0)).copy(energy=120.0)
        assertEquals(4,run.strategy!!.stations.size)
        assertSame(run,buildStation(run,StationKind.Factory,core.position+Vec2(0.0,400.0)))
    }
    @Test fun upgradedFactoryProducesHeavyHullsAndHonoursEnergyAndFleetLimits() {
        var run=run();val id=run.strategy!!.stations.first().id
        run=upgradeStation(run,id).copy(energy=120.0)
        assertEquals(2,run.strategy!!.stations.first().level)
        assertSame(run,upgradeStation(run,id))
        run=run.copy(strategy=run.strategy!!.copy(stations=run.strategy!!.stations.map { it.copy(remaining=0.0,product=FactoryProduct.Rocket) }))
        val produced=advanceStrategy(run,.01,Random(17))
        assertEquals(VehicleHullClass.Heavy,produced.bodies.first { it.isVehicle }.hullClass)
        assertEquals(10.0,produced.strategy!!.stations.first().remaining,0.0)
        val poor=advanceStrategy(run.copy(energy=10.0),.01,Random(17));assertEquals(0,poor.launches)
        val full=run.copy(bodies=run.bodies+(0 until 24).map { ship(Vec2(3000.0+it*100,3000.0),BodyKind.Rocket) })
        assertEquals(0,advanceStrategy(full,.01,Random(17)).launches)
    }
    @Test fun serviceRefuelsOnlyNearbyLivingCraftAndDoesNotExceedCapacity() {
        val run=inert(run());val core=run.bodies.first { it.kind == BodyKind.Core }
        val p=core.position+Vec2(-500.0,0.0)
        val service=OrbitalStation(SimulationEngine.newBodyId(),p,StationKind.Service,level=2)
        val near=ship(p).copy(fuelRemaining=100.0);val far=ship(p+Vec2(-500.0,0.0)).copy(fuelRemaining=100.0)
        val empty=ship(p+Vec2(0.0,80.0)).copy(fuelRemaining=0.0)
        val next=advanceStrategy(run.copy(bodies=run.bodies+listOf(near,far,empty),strategy=run.strategy!!.copy(stations=listOf(service))),.1,Random(17))
        assertTrue(next.bodies.first { it.id == near.id }.fuelRemaining>100.0)
        assertTrue(next.bodies.first { it.id == far.id }.fuelRemaining<100.0)
        assertFalse(next.bodies.any { it.id == empty.id })
    }
    @Test fun groupMoveUsesSeparateFormationSlotsAndClearsAuthoredRoutes() {
        val run=run();val a=ship(Vec2(2000.0,0.0)).copy(waypoints=listOf(Vec2.Zero));val b=ship(Vec2(2200.0,0.0))
        val point=Vec2(2600.0,0.0)
        val next=issueFleetOrder(run.copy(bodies=run.bodies+listOf(a,b)),setOf(a.id,b.id),FleetCommand.Move,point)
        val orders=next.strategy!!.orders
        assertTrue((orders.getValue(a.id).position-orders.getValue(b.id).position).magnitude()>=140)
        assertTrue(next.bodies.first { it.id==a.id }.waypoints.isEmpty())
        assertFalse(run.bodies.first().id in orders)
    }
    @Test fun commandsLeaveManualControlInChargeAndGuardUsesMovingAnchor() {
        val body=ship(Vec2(3000.0,0.0));val anchor=ship(Vec2(3100.0,0.0)).copy(velocity=Vec2(0.0,100.0))
        val guard=FleetOrder(FleetCommand.Guard,Vec2.Zero,anchor.id)
        val prepared=prepareCombat(listOf(body,anchor),ArcadeCombat(),.1,body.id,orders=mapOf(body.id to guard))
        assertEquals(body.velocity,prepared.bodies.first { it.id == body.id }.velocity)
        val one=fleetVelocity(body,guard,listOf(body,anchor),emptyList())!!
        val two=fleetVelocity(body,guard,listOf(body,anchor.copy(position=Vec2(2800.0,300.0))),emptyList())!!
        assertNotEquals(one,two)
    }
    @Test fun moveAndGuardCraftDoNotConsumeAutomaticPursuitSlots() {
        val a=ship(Vec2(3000.0,0.0));val b=ship(Vec2(3200.0,0.0));val enemy=ship(Vec2(3400.0,0.0)).copy(kind=BodyKind.Meteor)
        val prepared=prepareCombat(listOf(a,b,enemy),ArcadeCombat(),.01,orders=mapOf(a.id to FleetOrder(FleetCommand.Move,Vec2(2900.0,0.0))))
        assertNull(prepared.combat.craft[a.id]!!.targetId);assertEquals(enemy.id,prepared.combat.craft[b.id]!!.targetId)
    }
    @Test fun basesAreAnchoredAndEnemyProductionStopsAfterDestruction() {
        val initial=inert(run());var state=initial
        repeat(30) { state=advanceStrategy(state,1.0/60,Random(17)) }
        for (base in initial.strategy!!.bases) assertEquals(base.position,state.bodies.first { it.id == base.id }.position)
        val destroyed=state.strategy!!.bases.first().id
        state=state.copy(bodies=state.bodies.filterNot { it.id == destroyed },strategy=state.strategy!!.copy(bases=state.strategy!!.bases.map { it.copy(remaining=0.0) }))
        val next=advanceStrategy(state,.01,Random(17));assertEquals(2,next.strategy!!.bases.size)
        assertEquals(2,next.strategy!!.enemies.size)
    }
    @Test fun rocketImpactsDamageArmouredBasesRatherThanDestroyingThemInOneHit() {
        val initial=inert(run());val base=initial.strategy!!.bases.first()
        val rocket=ship(base.position+Vec2(44.0,0.0),BodyKind.Rocket).copy(mass=18.0,velocity=Vec2(-100.0,0.0),heading=Vec2(-1.0,0.0))
        val before=initial.bodies.first { it.id == base.id }.mass
        val next=advanceStrategy(initial.copy(bodies=initial.bodies+rocket),.01,Random(17),ManualFlightControl(rocket.id,0.0,0.0))
        assertFalse(next.bodies.any { it.id == rocket.id })
        val after=next.bodies.first { it.id == base.id }.mass
        assertTrue(after < before);assertTrue(after > before-300)
    }
    @Test fun hostileShotsHitFriendlyCraftWithoutDamagingHostileUnits() {
        val friendly=ship(Vec2(100.0,0.0));val enemy=ship(Vec2(50.0,0.0)).copy(kind=BodyKind.Meteor)
        val shot=SpaceProjectile(Vec2.Zero,Vec2(200.0,0.0),enemy.id,damage=50.0,hostile=true)
        val next=advanceProjectiles(listOf(enemy,friendly),ArcadeCombat(listOf(shot)),.6)
        assertTrue(next.bodies.any { it.id == enemy.id });assertFalse(next.bodies.any { it.id == friendly.id })
        assertTrue(next.combat.projectiles.isEmpty())
    }
    @Test fun victoryWaitsForAllBasesAndFreezesTheBattle() {
        val run=inert(run());val ids=run.strategy!!.bases.map { it.id }.toSet()
        val one=advanceStrategy(run.copy(bodies=run.bodies.filterNot { it.id == ids.first() }),.01,Random(17))
        assertFalse(one.strategy!!.victory)
        val won=advanceStrategy(one.copy(bodies=one.bodies.filterNot { it.id in ids }),.01,Random(17))
        assertTrue(won.strategy!!.victory);assertSame(won,advanceStrategy(won,1.0,Random(17)))
    }
    @Test fun allThreeModesKeepTheirOwnWorldAndStrategyDoesNotChangeArcadeRecords() {
        val game=SpaceGameState().apply { resize(IntSize(1080,2340));startArcade() }
        val arcade=game.arcade!!;game.startStrategy();val strategy=game.arcade!!
        game.startSandbox(SandboxPresetKind.BinaryStars);val sandbox=game.sandbox;assertFalse(game.tacticalMode)
        game.enterMode(AppMode.Arcade);assertEquals(arcade,game.arcade);assertFalse(game.tacticalMode)
        game.enterMode(AppMode.Strategy);assertEquals(strategy,game.arcade);assertTrue(game.tacticalMode)
        assertEquals(sandbox,game.sandbox);assertTrue(game.tacticalMode || !game.motionSteeringEnabled)
        val records=game.records;game.update(.02);assertEquals(records,game.records)
        val recovery=game.recoverySnapshot(42)!!;assertEquals(arcade,recovery.arcade);assertNotNull(recovery.strategy)
    }
    @Test fun bastionFiresOnlyAtEnemyCraftAndKeepsItsShotsBelowTheSharedCap() {
        val run=inert(run());val core=run.bodies.first { it.kind == BodyKind.Core }
        val p=core.position+Vec2(-500.0,-200.0)
        val station=OrbitalStation(SimulationEngine.newBodyId(),p,StationKind.Bastion,remaining=0.0)
        val enemy=ship(p+Vec2(-120.0,0.0)).copy(kind=BodyKind.Meteor,mass=300.0)
        val scene=run.copy(bodies=run.bodies+enemy,strategy=run.strategy!!.copy(stations=listOf(station),enemies=mapOf(enemy.id to EnemyCraft(BodyKind.Rocket))))
        val fired=advanceStrategy(scene,.01,Random(17))
        assertTrue(fired.combat.projectiles.any { it.ownerId == station.id })
        assertEquals(1.2,fired.strategy!!.stations.first().remaining,0.0)
        val noUnits=advanceStrategy(run.copy(strategy=run.strategy!!.copy(stations=listOf(station))),.01,Random(17))
        assertFalse(noUnits.combat.projectiles.any { it.ownerId == station.id })
        val cap=scene.copy(combat=ArcadeCombat(List(64) { SpaceProjectile(Vec2(10000.0,0.0),Vec2(0.0,1.0),-1) }))
        assertFalse(advanceStrategy(cap,.01,Random(17)).combat.projectiles.any { it.ownerId == station.id })
    }

}
