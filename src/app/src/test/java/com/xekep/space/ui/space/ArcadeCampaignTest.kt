package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeCampaignTest {
    private fun run(wave: Int=1, seconds: Double=0.0, difficulty: ArcadeDifficulty=ArcadeDifficulty.Normal)=
        ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),SpaceCamera(),IntSize(900,1400),difficulty,
            elapsed=(wave-1)*28.0+seconds,spawnTimer=1000.0)
    private fun ship(point: Vec2)=CelestialBody(SimulationEngine.newBodyId(),point,Vec2.Zero,24.0,6f,Color.Cyan,
        BodyKind.Ship,fuelRemaining=100.0)

    @Test fun cleanupWaitsForPendingThreatsThenGrantsFourQuietSecondsWithoutRepeatedReward() {
        val enemy=ship(Vec2(10000.0,10000.0)).copy(kind=BodyKind.Meteor)
        var state=run(2,23.99).copy(pending=listOf(PendingThreat(enemy,3.0)))
        state=advanceArcade(state,.02,Random(17))
        assertEquals(2,state.wave);assertFalse(state.resting);assertEquals(24.0,state.wavePhase.seconds,1e-7)
        assertEquals(0.0,state.score,0.0)
        // Remove the scripted pending threat to represent the final interception.
        state=advanceArcade(state.copy(pending=emptyList()),.01,Random(17))
        assertTrue(state.resting);val score=state.score
        repeat(239) { state=advanceArcade(state,1.0/60,Random(17)) }
        assertEquals(2,state.wave);assertEquals(score,state.score,0.0)
        state=advanceArcade(state,1.0/60,Random(17));assertEquals(3,state.wave)
    }

    @Test fun salvageNeedsAnIntentionalSortieAndRouteCanFinishAtThePickup() {
        val base=prepareCampaign(run(7),Random(4)); val salvage=base.salvage!!
        val body=ship(salvage.position)
        val passive=base.copy(bodies=base.bodies+body)
        assertEquals(SalvageStatus.Available,finishCampaign(passive,passive,.01,null).salvage!!.status)
        val manual=finishCampaign(passive,passive,.01,body.id)
        assertEquals(SalvageStatus.Collected,manual.salvage!!.status);assertEquals(1,manual.salvageCollected)
        val coasting=passive.copy(bodies=passive.bodies.map { if (it.id == body.id)
            it.copy(pilotTargetSpeed=0.0,fuelRemaining=0.0) else it })
        assertEquals(SalvageStatus.Collected,finishCampaign(coasting,coasting,.01,body.id).salvage!!.status)
        assertEquals(SalvageStatus.Available,finishCampaign(coasting,coasting,.01,null).salvage!!.status)
        val routed=passive.copy(bodies=passive.bodies.map { if (it.id == body.id) it.copy(waypoints=listOf(salvage.position)) else it })
        assertEquals(SalvageStatus.Collected,finishCampaign(routed,passive,.01,null).salvage!!.status)
        val high=passive.copy(bodies=passive.bodies.map { if (it.id == body.id) it.copy(flightHeight=250.0) else it })
        assertEquals(SalvageStatus.Available,finishCampaign(high,high,.01,body.id).salvage!!.status)
    }

    @Test fun pickupOffersOneUpgradeWithoutConsumingTheNormalWaveReward() {
        val collected=run(7).copy(salvage=ArcadeSalvage(7,Vec2.Zero,status=SalvageStatus.Collected),salvageCollected=1)
        val offered=offerUpgrade(collected,Random(4))
        assertTrue(offered.upgradeOffer!!.salvageBonus);assertFalse(offered.upgradeOffer!!.convoyBonus)
        assertTrue(offered.offeredUpgradeWaves.isEmpty())
        val selected=selectUpgrade(offered,offered.upgradeOffer!!.choices.first())
        assertTrue(selected.chosenUpgradeWaves.isEmpty());assertNull(offerUpgrade(selected,Random(4)).upgradeOffer)
        assertEquals(1,selected.salvageCollected)
    }

    @Test fun missedSalvageExpiresWithoutTakingCoreHullAndDoesNotRespawnInSameWave() {
        var state=prepareCampaign(run(13),Random(4))
        state=finishCampaign(state,state,32.0,null)
        assertEquals(SalvageStatus.Expired,state.salvage!!.status);assertEquals(4,state.lives)
        assertEquals(state.salvage,prepareCampaign(state,Random(9)).salvage)
        assertNull(prepareCampaign(run(7).copy(practice=true),Random(4)).salvage)
    }

    @Test fun carrierArrivalGrantsTheFullReactionWindowBeforeItsFirstVolley() {
        val justBefore=run(19,27.99).copy(spawnTimer=0.0)
        var state=advanceArcade(justBefore,.02,Random(17))
        assertEquals(20,state.wave);assertTrue(state.pending.isEmpty());assertNull(state.carrier)
        state=advanceArcade(state,.01,Random(17))
        assertNotNull(state.carrier);assertTrue(state.pending.isEmpty())
        repeat(358) { state=advanceArcade(state,1.0/60,Random(17)) }
        assertTrue(state.pending.isEmpty())
        repeat(3) { state=advanceArcade(state,1.0/60,Random(17)) }
        assertEquals(2,state.pending.size)
    }

    @Test fun waitingCannotWinTheFinaleOnAnyDifficulty() {
        for (difficulty in ArcadeDifficulty.entries) {
            var state=prepareCampaign(run(20,0.0,difficulty),Random(7))
            assertEquals(3,state.carrierNodesRemaining)
            assertEquals(3,prepareCampaign(state,Random(3)).carrierNodesRemaining)
            state=state.copy(carrier=state.carrier!!.copy(elapsed=state.carrierDeadline-.01),elapsed=19*28.0+24.0)
            val ended=advanceArcade(state,.02,Random(9))
            assertEquals(0,ended.lives);assertFalse(ended.campaignCleared);assertFalse(ArcadeGoal.Twenty.earned(ended))
        }
    }

    @Test fun lightRocketCannotEraseAnArmouredNodeAndOnlyTheFinalHitCountsAsAnIntercept() {
        var state=prepareCampaign(run(20),Random(17))
        val id=state.carrier!!.nodeIds.first()
        repeat(3) { hit ->
            val node=state.bodies.first { it.id == id }
            val rocket=ship(node.position).copy(kind=BodyKind.Rocket,mass=12.0,radius=3f)
            state=advanceArcade(state.copy(bodies=state.bodies+rocket,spawnTimer=1000.0),.01,Random(17))
            assertFalse(state.bodies.any { it.id == rocket.id })
            assertTrue(state.successfulLaunches.contains(rocket.id))
            if (hit < 2) { assertTrue(state.bodies.any { it.id == id });assertEquals(0,state.destroyed) }
        }
        assertFalse(state.bodies.any { it.id == id });assertEquals(1,state.destroyed)
        assertEquals(2,state.carrierNodesRemaining);assertFalse(state.carrier!!.defeated)
    }

    @Test fun heavyRocketDamagesArmourMoreWithoutHittingThePrimaryNodeTwice() {
        val prepared=prepareCampaign(run(20,difficulty=ArcadeDifficulty.Hard),Random(17))
        val node=prepared.bodies.first { it.id in prepared.carrier!!.nodeIds }
        val rocket=ship(node.position).copy(kind=BodyKind.Rocket,mass=heavyHullMass(BodyKind.Rocket),radius=3f)
        val after=advanceArcade(prepared.copy(bodies=prepared.bodies+rocket,spawnTimer=1000.0),.01,Random(17))
        val surviving=after.bodies.first { it.id == node.id }
        assertEquals(node.mass-400*rocket.vehicleDamageScale,surviving.mass,1e-6)
        assertTrue(surviving.mass < node.mass-220);assertFalse(after.bodies.any { it.id == rocket.id })
        // Ordinary meteors still obey their original one-contact interception rule.
        val regular=node.copy(id=SimulationEngine.newBodyId(),position=Vec2(5000.0,5000.0),mass=1000.0)
        val small=rocket.copy(id=SimulationEngine.newBodyId(),position=regular.position,mass=12.0)
        val hit=advanceArcade(prepared.copy(bodies=prepared.bodies+regular+small,spawnTimer=1000.0),.01,Random(17))
        assertFalse(hit.bodies.any { it.id == regular.id });assertEquals(1,hit.destroyed)
    }

    @Test fun allThreeNodesUseOrdinaryWeaponsAndVictoryRewardsOnlyOnce() {
        val prepared=prepareCampaign(run(20).copy(convoy=ArcadeConvoy(999,status=ConvoyStatus.Lost,elapsed=4.0)),Random(17))
        val shots=prepared.bodies.filter { it.id in prepared.carrier!!.nodeIds }.map {
            SpaceProjectile(it.position,Vec2.Zero,999,damage=2000.0)
        }
        var state=advanceArcade(prepared.copy(combat=ArcadeCombat(shots),spawnTimer=1000.0),.01,Random(17))
        assertTrue(state.carrier!!.defeated);assertEquals(0,state.carrierNodesRemaining);assertEquals(3,state.destroyed)
        val reward=state.score
        repeat(100) { state=advanceArcade(state,.1,Random(17)) }
        assertEquals(reward,state.score,1e-6)
        repeat(180) { state=advanceArcade(state,.1,Random(17)) }
        assertTrue(state.campaignCleared);assertTrue(ArcadeGoal.Twenty.earned(state))
        val alive=state.copy(carrier=state.carrier!!.copy(defeated=false))
        assertFalse(alive.campaignCleared)
    }
}
