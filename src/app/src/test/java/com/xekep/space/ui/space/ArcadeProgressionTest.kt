package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeProgressionTest {
    private fun core()=CelestialBody(1,Vec2(450.0,700.0),Vec2.Zero,7800.0,20f,Color.Yellow,BodyKind.Core)
    private fun run(wave: Int = 1, cycle: Double = 0.0)=ArcadeSession(listOf(core()),SpaceCamera(core().position),
        IntSize(900,1400),ArcadeDifficulty.Normal,elapsed=(wave-1)*28.0+cycle,spawnTimer=1000.0)
    private fun ship(id: Long=2,className: ShipClass=ShipClass.Interceptor)=CelestialBody(id,Vec2(690.0,700.0),
        SimulationEngine.orbitVelocity(core(),Vec2(690.0,700.0)),24.0,8f,Color.Cyan,BodyKind.Ship,shipClass=className)

    @Test fun limitsGrowAtMilestonesAndBothShipClassesShareTheSamePool() {
        for ((wave,ships,rockets) in listOf(Triple(1,3,10),Triple(5,4,12),Triple(10,5,14),Triple(15,6,16))) {
            assertEquals(ships,run(wave).launchLimit(BodyKind.Ship))
            assertEquals(rockets,run(wave).launchLimit(BodyKind.Rocket))
        }
        assertFalse(run(3).guardianUnlocked); assertTrue(run(4).guardianUnlocked)
        assertEquals(5,run(5).copy(upgrades=mapOf(ArcadeUpgrade.Fleet to 1)).launchLimit(BodyKind.Ship))
        assertFalse(run(10).copy(practice=true).guardianUnlocked)
        assertEquals(3,run(10).copy(practice=true).launchLimit(BodyKind.Ship))
    }
    @Test fun upgradeOfferFreezesFuelThreatsAndClocksAndCannotBeChosenTwice() {
        val before=run(3,23.99).copy(bodies=listOf(core(),ship()))
        val offered=advanceArcade(before,.02,Random(42))
        assertEquals(3,offered.upgradeOffer!!.wave); assertEquals(3,offered.upgradeOffer!!.choices.distinct().size)
        assertFalse(ArcadeUpgrade.Repair in offered.upgradeOffer!!.choices)
        assertEquals(offered,advanceArcade(offered,1.0,Random(42)))
        val choice=offered.upgradeOffer!!.choices.first()
        val picked=selectUpgrade(offered,choice)
        assertNull(picked.upgradeOffer); assertEquals(1,picked.level(choice)); assertTrue(picked.guardianUnlocked)
        assertEquals(picked,selectUpgrade(picked,choice))
        assertNull(advanceArcade(picked,.1,Random(42)).upgradeOffer)
        assertNull(advanceArcade(before.copy(practice=true),.02,Random(42)).upgradeOffer)
    }
    @Test fun upgradesAffectActualWeaponsEnergyAndFuelWithoutRefillingTheTank() {
        val base=run(3,24.0).copy(bodies=listOf(core(),ship()),energy=90.0)
        fun choose(upgrade: ArcadeUpgrade)=selectUpgrade(base.copy(upgradeOffer=ArcadeUpgradeOffer(3,listOf(upgrade))),upgrade)
        val engines=choose(ArcadeUpgrade.Engines)
        val craft=engines.bodies.last(); assertEquals(180.0,craft.fuelRemaining,0.0)
        val stepped=SimulationEngine.stepArcade(listOf(craft),1.0).bodies.single()
        assertEquals(179.15,stepped.fuelRemaining,1e-7)
        val reactor=choose(ArcadeUpgrade.Reactor)
        assertEquals(140.0,reactor.maxEnergy,0.0); assertEquals(110.0,reactor.energy,0.0)
        assertEquals(14.0,reactor.energyRegen,0.0)
        val guns=choose(ArcadeUpgrade.Guns)
        val enemy=core().copy(id=3,kind=BodyKind.Meteor,position=Vec2(1000.0,700.0),mass=100.0)
        val combat=prepareCombat(listOf(ship(),enemy),ArcadeCombat(craft=mapOf(2L to CraftStatus(cooldown=0.0))),.01,
            gunIntervalScale=guns.gunIntervalScale)
        assertEquals(.765,combat.combat.craft[2]!!.cooldown,1e-8)
        val repair=selectUpgrade(base.copy(lives=2,upgradeOffer=ArcadeUpgradeOffer(3,listOf(ArcadeUpgrade.Repair))),ArcadeUpgrade.Repair)
        assertEquals(3,repair.lives)
        val capped=base.copy(upgrades=ArcadeUpgrade.entries.associateWith { 2 })
        assertNull(offerUpgrade(capped,Random(1)).upgradeOffer)
    }
    @Test fun guardianPatrolsTheCoreAndShootsTwoSmallThreatsWithWeakerRounds() {
        val guardian=ship(className=ShipClass.Guardian)
        val enemies=listOf(100.0,180.0,900.0).mapIndexed { i,mass ->
            core().copy(id=(10+i).toLong(),position=Vec2(900.0,650.0+i*50),mass=mass,kind=BodyKind.Meteor) }
        val ready=ArcadeCombat(craft=mapOf(2L to CraftStatus(cooldown=0.0)))
        val result=prepareCombat(listOf(core(),guardian)+enemies,ready,.1)
        assertEquals(2,result.combat.projectiles.size); assertTrue(result.combat.projectiles.all { it.damage == 60.0 })
        assertTrue(result.bodies[1].velocity.y > 0)
        assertEquals(guardian.velocity,prepareCombat(listOf(core(),guardian)+enemies,ready,.1,controlledId=2).bodies[1].velocity)
        val routed=guardian.copy(waypoints=listOf(Vec2(900.0,300.0)))
        assertEquals(routed.velocity,prepareCombat(listOf(core(),routed)+enemies,ready,.1).bodies[1].velocity)
        assertEquals(50.4,launchCost(guardian),1e-8)
        assertEquals(40.4,launchCost(ship()),1e-8)
    }
    @Test fun guardianMaintainsPatrolForSixtySecondsInsteadOfFallingIntoTheCore() {
        var state=run().copy(bodies=listOf(core(),ship(className=ShipClass.Guardian)))
        repeat(1800) {
            val prepared=prepareCombat(state.bodies,state.combat,1.0/30)
            state=state.copy(bodies=SimulationEngine.stepArcade(prepared.bodies,1.0/30).bodies,combat=prepared.combat)
            val guardian=state.bodies.first { it.kind == BodyKind.Ship }
            assertTrue((guardian.position-core().position).magnitude() in 180.0..320.0)
        }
        assertEquals(120.0,state.bodies.first { it.kind == BodyKind.Ship }.fuelRemaining,1e-6)
    }
    @Test fun tenthWaveSpawnsOneGiantWithAnOrdinaryDistantWarning() {
        val first=advanceArcade(run(10,3.0).copy(spawnTimer=0.0),.01,Random(5))
        assertNotNull(first.challenge); assertEquals(1,first.pending.size)
        val giant=first.pending.single().body
        assertEquals(1800.0,giant.mass,0.0); assertEquals(setOf(giant.id),first.challenge!!.ids)
        assertTrue((giant.position-core().position).magnitude() > 900.0)
        val next=advanceArcade(first.copy(spawnTimer=0.0),.01,Random(5))
        assertEquals(1,next.pending.count { it.body.id == giant.id })
        assertEquals(first.challenge!!.parentId,next.challenge!!.parentId)
    }
    @Test fun destroyingGiantSplitsOnceAndHoldsWaveUntilAllFragmentsAreResolved() {
        val parent=core().copy(id=20,position=Vec2(1100.0,700.0),mass=100.0,kind=BodyKind.Meteor,velocity=Vec2(-140.0,0.0))
        val shot=SpaceProjectile(Vec2(1080.0,700.0),Vec2(10000.0,0.0),30)
        val before=run(10,23.99).copy(bodies=listOf(core(),parent),challenge=ArcadeChallenge(20,setOf(20)),
            combat=ArcadeCombat(projectiles=listOf(shot)))
        SimulationEngine.reserveBodyIds(before.bodies)
        val split=advanceArcade(before,.02,Random(1))
        assertEquals(3,split.challenge!!.ids.size); assertEquals(3,split.bodies.count { it.kind == BodyKind.Meteor })
        assertEquals(10,split.wave); assertFalse(split.resting); assertNull(split.upgradeOffer)
        assertEquals(split.bodies.size,split.bodies.map { it.id }.distinct().size)
        val held=advanceArcade(split,.1,Random(1)); assertEquals(10,held.wave)
        assertTrue(held.elapsed > split.elapsed); assertEquals(split.waveTime,held.waveTime,1e-8)
        val rounds=held.bodies.filter { it.kind == BodyKind.Meteor }.map {
            SpaceProjectile(it.position-Vec2(20.0,0.0),Vec2(10000.0,0.0),30,damage=500.0) }
        val clear=advanceArcade(held.copy(combat=ArcadeCombat(projectiles=rounds)),.01,Random(1))
        assertTrue(clear.challenge!!.ids.isEmpty()); assertTrue(clear.challenge!!.rewarded)
        assertEquals(3,clear.destroyed-held.destroyed); assertNotNull(clear.upgradeOffer)
        val resumed=selectUpgrade(clear,clear.upgradeOffer!!.choices.first())
        assertEquals(resumed.score,advanceArcade(resumed,.01,Random(1)).score,0.0)
    }
    @Test fun failedOrEscapedChallengeDoesNotSplitRewardOrLockTheWave() {
        val parent=core().copy(id=20,kind=BodyKind.Meteor,mass=1800.0)
        val hit=advanceArcade(run(10,24.0).copy(bodies=listOf(core(),parent),challenge=ArcadeChallenge(20,setOf(20))),.01,Random(1))
        assertTrue(hit.challenge!!.failed); assertTrue(hit.challenge!!.ids.isEmpty())
        assertEquals(0.0,hit.score,0.0); assertEquals(3,hit.lives)
        val escaped=parent.copy(position=Vec2(4000.0,700.0),velocity=Vec2(300.0,0.0))
        val missed=advanceArcade(run(10,24.0).copy(bodies=listOf(core(),escaped),challenge=ArcadeChallenge(20,setOf(20))),.01,Random(1))
        assertTrue(missed.challenge!!.failed); assertTrue(missed.challenge!!.ids.isEmpty())
        val resumed=selectUpgrade(missed,missed.upgradeOffer!!.choices.first())
        var next=resumed
        repeat(300) { next=advanceArcade(next,1.0/60,Random(1)) }
        assertTrue(next.wave >= 11)
    }
}
