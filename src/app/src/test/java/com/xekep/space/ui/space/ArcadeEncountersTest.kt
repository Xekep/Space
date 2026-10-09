package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class ArcadeEncountersTest {
    private fun run(wave: Int, cycle: Double = 0.0): ArcadeSession {
        val core=CelestialBody(SimulationEngine.newBodyId(),Vec2(450.0,700.0),Vec2.Zero,7800.0,28f,Color.Yellow,BodyKind.Core)
        return ArcadeSession(listOf(core),SpaceCamera(core.position),IntSize(900,1400),ArcadeDifficulty.Normal,
            elapsed=(wave-1)*28.0+cycle,spawnTimer=1000.0)
    }
    private fun meteor(point: Vec2, velocity: Vec2 = Vec2.Zero, mass: Double = 90.0)=
        CelestialBody(SimulationEngine.newBodyId(),point,velocity,mass,8f,Color.Red,BodyKind.Meteor)

    @Test fun planetAppearsAfterTenOnceAndRemainsInOrbitForTwoMinutes() {
        val random=Random(17)
        assertNull(advanceArcade(run(10),.01,random).planetId)
        val introduced=advanceArcade(run(11),.01,random)
        val planet=introduced.bodies.single { it.kind == BodyKind.ArcadePlanet }
        assertEquals(planet.id,introduced.planetId)
        var scene=introduced.bodies
        repeat(7200) {
            scene=SimulationEngine.stepArcade(scene,1.0/60).bodies
            val core=scene.first { it.kind == BodyKind.Core }
            val current=scene.first { it.id == planet.id }
            assertEquals(850.0,current.mass,0.0)
            assertEquals(430.0,(current.position-core.position).magnitude(),.01)
        }
        val next=advanceArcade(introduced,.01,random)
        assertEquals(1,next.bodies.count { it.kind == BodyKind.ArcadePlanet })
        assertEquals(planet.id,next.planetId)
        assertNull(advanceArcade(run(15).copy(practice=true),.01,random).planetId)
    }

    @Test fun planetBendsBothMeteorsAndUnpoweredRocketsThroughTheRealIntegrator() {
        val introduced=advanceArcade(run(11),.01,Random(17))
        val planet=introduced.bodies.single { it.kind == BodyKind.ArcadePlanet }
        for (kind in listOf(BodyKind.Meteor,BodyKind.Rocket)) {
            val body=meteor(planet.position+Vec2(70.0,0.0),Vec2(0.0,80.0)).copy(kind=kind,pilotTargetSpeed=0.0,radius=2f,fuelRemaining=120.0)
            val with=SimulationEngine.stepArcade(introduced.bodies+body,.15).bodies.first { it.id == body.id }
            val without=SimulationEngine.stepArcade(introduced.bodies.filterNot { it.id == planet.id }+body,.15).bodies.first { it.id == body.id }
            assertTrue("Planet must add attraction",with.velocity.x < without.velocity.x-8.0)
        }
    }

    @Test fun planetInterceptsWithoutLosingMassAndFastCraftExplodeOnItsSurface() {
        val base=advanceArcade(run(11),.01,Random(4))
        val planet=base.bodies.single { it.kind == BodyKind.ArcadePlanet }
        val enemy=meteor(planet.position)
        val intercept=advanceArcade(base.copy(bodies=base.bodies+enemy),.01,Random(4))
        assertEquals(1,intercept.destroyed); assertTrue(intercept.score > 0)
        assertEquals(planet.mass,intercept.bodies.first { it.id == planet.id }.mass,0.0)
        assertEquals(base.lives,intercept.lives)
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val craft=meteor(planet.position+Vec2(-80.0,0.0),Vec2(5000.0,0.0)).copy(kind=kind,radius=3f,pilotTargetSpeed=0.0,fuelRemaining=120.0)
            val hit=SimulationEngine.stepArcade(base.bodies+craft,.03)
            assertFalse(hit.bodies.any { it.id == craft.id })
            assertTrue(hit.collisions.any { it.vehicleExplosion && it.secondKind == BodyKind.ArcadePlanet })
        }
        val shot=SpaceProjectile(planet.position+Vec2(-60.0,0.0),Vec2(5000.0,0.0),-1)
        val behind=meteor(planet.position+Vec2(50.0,0.0))
        val blocked=advanceProjectiles(listOf(planet,behind),ArcadeCombat(listOf(shot)),.03)
        assertEquals(behind.mass,blocked.bodies.last().mass,0.0); assertTrue(blocked.combat.projectiles.isEmpty())
    }

    @Test fun wavesHaveDistinctGroupsMassesTimingAndOpposingApproaches() {
        val swarm=advanceArcade(run(12,3.0).copy(spawnTimer=0.0),.01,Random(7))
        val siege=advanceArcade(run(13,3.0).copy(spawnTimer=0.0),.01,Random(7))
        val calm=advanceArcade(run(11,3.0).copy(spawnTimer=0.0),.01,Random(7))
        val pincer=advanceArcade(run(14,3.0).copy(spawnTimer=0.0),.01,Random(7))
        assertEquals(WaveCharacter.Swarm,swarm.character); assertEquals(4,swarm.pending.size)
        assertTrue(swarm.pending.all { it.body.mass in 55.0..105.0 })
        assertEquals(4,siege.pending.size); assertEquals(1020.0,siege.pending.first().body.mass,0.0)
        assertTrue(siege.pending.drop(1).all { it.body.mass < 400 })
        assertEquals(1,calm.pending.size); assertTrue(calm.spawnTimer > siege.spawnTimer)
        val core=pincer.bodies.first().position
        val a=(pincer.pending[0].body.position-core).normalized()
        val b=(pincer.pending[1].body.position-core).normalized()
        assertTrue("Pincer must arrive from opposing sectors",a.x*b.x+a.y*b.y < -.7)
        val rest=advanceArcade(run(12,24.5).copy(spawnTimer=0.0),.01,Random(7))
        assertTrue(rest.pending.isEmpty()); assertTrue(rest.resting)
    }

    @Test fun latePressureGrowsGraduallyWithoutChangingDifficultyMultipliers() {
        val early=advanceArcade(run(6,3.0).copy(spawnTimer=0.0),.01,Random(17))
        val late=advanceArcade(run(12,3.0).copy(spawnTimer=0.0),.01,Random(17))
        val siege=advanceArcade(run(13,3.0).copy(spawnTimer=0.0),.01,Random(17))
        assertTrue(late.pending.size/late.spawnTimer <= 2*early.pending.size/early.spawnTimer)
        assertTrue(siege.spawnTimer >= 2.45)
        val hard=advanceArcade(run(13,3.0).copy(difficulty=ArcadeDifficulty.Hard,spawnTimer=0.0),.01,Random(17))
        val easy=advanceArcade(run(13,3.0).copy(difficulty=ArcadeDifficulty.Easy,spawnTimer=0.0),.01,Random(17))
        assertTrue(hard.spawnTimer < siege.spawnTimer); assertTrue(easy.spawnTimer > siege.spawnTimer)
        assertTrue(waveGroupSize(20,WaveCharacter.Swarm) > late.pending.size)
    }

    @Test fun easyLatePincerLeavesLongerGapsWithoutWeakeningHardOrChangingEarlyWaves() {
        fun spawn(wave: Int, difficulty: ArcadeDifficulty)=advanceArcade(
            run(wave,3.0).copy(difficulty=difficulty,lives=difficulty.lives,spawnTimer=0.0),.01,Random(17))
        val easy=spawn(18,ArcadeDifficulty.Easy); val normal=spawn(18,ArcadeDifficulty.Normal)
        val hard=spawn(18,ArcadeDifficulty.Hard)
        assertEquals(4,easy.pending.size); assertEquals(normal.pending.size,hard.pending.size)
        assertTrue(easy.spawnTimer > 2.95)
        assertEquals(1.39,normal.spawnTimer,1e-6); assertEquals(1.04,hard.spawnTimer,1e-6)
        val early=spawn(8,ArcadeDifficulty.Easy)
        assertEquals((4.6-8*.25)*1.3-.01,early.spawnTimer,1e-6)
    }

    @Test fun transportWaitsForEscortThenDepartsAndFlankingWarningsStartFarAway() {
        val random=Random(17)
        val started=advanceArcade(run(15).copy(spawnTimer=0.0),.01,random)
        val transport=started.bodies.single { it.kind == BodyKind.Convoy }
        assertEquals(3,started.pending.size)
        assertTrue(started.pending.take(2).all { (it.body.position-transport.position).magnitude() > 800.0 })
        var waiting=started
        repeat(150) { waiting=advanceArcade(waiting.copy(spawnTimer=1000.0),1.0/30,random) }
        val still=waiting.bodies.first { it.id == transport.id }
        assertTrue((still.position-transport.position).magnitude() < 2.0)
        val departure=prepareArcadeEncounters(waiting.copy(convoy=waiting.convoy!!.copy(elapsed=6.0)),.01,random)
        assertEquals(75.0,departure.bodies.first { it.id == transport.id }.velocity.magnitude(),1e-6)
    }

    @Test fun convoyAppearsOnceOutsideTheArenaAndDoesNotOccupyAPlayerShipSlot() {
        val base=advanceArcade(run(15),.01,Random(7))
        val transport=base.bodies.single { it.kind == BodyKind.Convoy }
        val core=base.bodies.first { it.kind == BodyKind.Core }
        assertEquals(transport.id,base.convoy!!.bodyId); assertEquals(3,base.convoy!!.hull)
        assertTrue((transport.position-core.position).magnitude() > 1200)
        assertTrue(transport.gravityMass < 1e-5); assertFalse(transport.isVehicle)
        assertEquals(0,base.bodies.count { it.kind == BodyKind.Ship })
        assertEquals(1,advanceArcade(base,.01,Random(7)).bodies.count { it.kind == BodyKind.Convoy })
        assertNull(advanceArcade(run(14),.01,Random(7)).convoy)
        assertNull(advanceArcade(run(15).copy(practice=true),.01,Random(7)).convoy)
    }

    @Test fun threeDistinctImpactsDestroyConvoyWithoutCoreLifePenaltyOrInterceptionCredit() {
        val base=advanceArcade(run(15),.01,Random(17))
        val transport=base.bodies.single { it.kind == BodyKind.Convoy }
        var next=base
        repeat(3) { index ->
            val current=next.bodies.first { it.id == transport.id }
            next=advanceArcade(next.copy(bodies=next.bodies+meteor(current.position)),.01,Random(17))
            assertEquals(2-index,next.convoy!!.hull)
            assertEquals(base.lives,next.lives); assertEquals(0,next.destroyed); assertEquals(base.score,next.score,0.0)
        }
        assertEquals(ConvoyStatus.Lost,next.convoy!!.status)
        assertFalse(next.bodies.any { it.id == transport.id }); assertNull(next.upgradeOffer)
        assertTrue(next.explosions.any { it.particles.isNotEmpty() })
        assertEquals(ConvoyStatus.Lost,advanceArcade(next,.01,Random(17)).convoy!!.status)
    }

    @Test fun deliveryAwardsOneExtraChoiceAndPreservesTheRegularFifteenthWaveUpgrade() {
        val base=advanceArcade(run(15),.01,Random(17))
        val core=base.bodies.first { it.kind == BodyKind.Core }
        val transport=base.bodies.single { it.kind == BodyKind.Convoy }
        val docking=base.copy(bodies=base.bodies.map { if (it.id == transport.id) it.copy(position=core.position+Vec2(105.0,0.0)) else it })
        val delivered=advanceArcade(docking,.01,Random(17))
        assertEquals(ConvoyStatus.Delivered,delivered.convoy!!.status)
        assertEquals(600.0,delivered.score,0.0); assertTrue(delivered.upgradeOffer!!.convoyBonus)
        assertFalse(15 in delivered.offeredUpgradeWaves)
        assertFalse(delivered.bodies.any { it.id == transport.id })
        assertEquals(delivered,advanceArcade(delivered,1.0,Random(17)))
        val chosen=selectUpgrade(delivered,delivered.upgradeOffer!!.choices.first())
        val later=advanceArcade(chosen,.01,Random(17))
        assertNull(later.upgradeOffer); assertEquals(600.0,later.score,0.0)
        val rest=advanceArcade(later.copy(elapsed=14*28.0+24.0),.01,Random(17))
        assertNotNull(rest.upgradeOffer); assertFalse(rest.upgradeOffer!!.convoyBonus)
        assertTrue(15 in rest.offeredUpgradeWaves)
        assertEquals(1,rest.upgrades.values.sum())
    }

    @Test fun missingExpiredOrPlanetCollidingConvoysFailWithoutHoldingTheWave() {
        val base=advanceArcade(run(15),.01,Random(17))
        val transport=base.bodies.single { it.kind == BodyKind.Convoy }
        val planet=base.bodies.single { it.kind == BodyKind.ArcadePlanet }
        val candidates=listOf(base.copy(bodies=base.bodies.filterNot { it.id == transport.id }),
            base.copy(convoy=base.convoy!!.copy(elapsed=42.0)),
            base.copy(bodies=base.bodies.map { if (it.id == transport.id) it.copy(position=planet.position) else it }))
        for (candidate in candidates) {
            val lost=advanceArcade(candidate,.01,Random(17))
            assertEquals(ConvoyStatus.Lost,lost.convoy!!.status); assertEquals(base.lives,lost.lives)
            assertNull(lost.upgradeOffer)
            assertTrue(advanceArcade(lost.copy(elapsed=15*28.0),.01,Random(17)).wave >= 16)
        }
    }

    @Test fun friendlyCraftDoNotDestroyTheTransportTheyEscort() {
        val base=advanceArcade(run(15),.01,Random(17))
        val transport=base.bodies.single { it.kind == BodyKind.Convoy }
        val craft=meteor(transport.position).copy(kind=BodyKind.Ship,pilotTargetSpeed=0.0,fuelRemaining=180.0)
        val next=advanceArcade(base.copy(bodies=base.bodies+craft),.01,Random(17))
        assertEquals(3,next.convoy!!.hull)
        assertTrue(next.bodies.any { it.id == transport.id }); assertTrue(next.bodies.any { it.id == craft.id })
    }

    @Test fun transportCanReachTheCoreAroundTheMovingPlanetAcrossDifferentArrivalDirections() {
        for (seed in listOf(7,17,53,73)) {
            val random=Random(seed)
            var next=advanceArcade(run(15),.01,random)
            repeat(2600) {
                if (next.convoy!!.status == ConvoyStatus.Approaching) {
                    next.upgradeOffer?.let { next=selectUpgrade(next,it.choices.first()) }
                    next=advanceArcade(next.copy(spawnTimer=1000.0),1.0/60,random)
                }
            }
            println("CONVOY_ROUTE,seed=$seed,status=${next.convoy!!.status},seconds=${next.elapsed-14*28},hull=${next.convoy!!.hull}")
            assertEquals("Seed $seed",ConvoyStatus.Delivered,next.convoy!!.status)
        }
    }

    @Test fun realEscortWavesAttackTheTransportAsWellAsTheCoreAndRequireDefence() {
        var lost=0; var damage=0
        for (seed in 1..12) {
            val random=Random(seed)
            var next=advanceArcade(run(15).copy(spawnTimer=0.0,lives=30),.01,random)
            repeat(1500) {
                if (next.convoy!!.status == ConvoyStatus.Approaching) next=advanceArcade(next,1.0/60,random)
            }
            damage+=3-next.convoy!!.hull
            if (next.convoy!!.status == ConvoyStatus.Lost) lost++
            println("CONVOY_UNESCORTED,seed=$seed,status=${next.convoy!!.status},hull=${next.convoy!!.hull},coreHits=${30-next.lives}")
            assertTrue(next.bodies.size < 40); assertTrue(next.pending.size <= 18)
        }
        assertTrue("The transport must face real danger",damage > 0)
        assertTrue("Some unescorted transports must be lost",lost > 0)
    }

    @Test fun sendingInterceptorsToTheConvoyCanSaveItWhileGuardiansRemainAtTheCore() {
        var delivered=0
        for (seed in listOf(7,17,53,73)) {
            val random=Random(seed)
            var next=advanceArcade(run(15).copy(spawnTimer=0.0,lives=30),.01,random)
            val transport=next.bodies.first { it.kind == BodyKind.Convoy }
            val core=next.bodies.first { it.kind == BodyKind.Core }
            val ships=List(2) { index ->
                CelestialBody(SimulationEngine.newBodyId(),transport.position+transport.heading.perpendicular()*((index*2-1)*70.0),
                    transport.velocity,24.0,8f,Color.Cyan,BodyKind.Ship,heading=transport.heading)
            }+CelestialBody(SimulationEngine.newBodyId(),core.position+Vec2(240.0,0.0),
                SimulationEngine.orbitVelocity(core,core.position+Vec2(240.0,0.0)),24.0,8f,Color.Green,BodyKind.Ship,shipClass=ShipClass.Guardian)
            next=next.copy(bodies=next.bodies+ships)
            repeat(2600) {
                if (next.convoy!!.status == ConvoyStatus.Approaching) {
                    next.upgradeOffer?.let { next=selectUpgrade(next,it.choices.first()) }
                    next=advanceArcade(next,1.0/60,random)
                }
            }
            if (next.convoy!!.status == ConvoyStatus.Delivered) delivered++
            println("CONVOY_ESCORTED,seed=$seed,status=${next.convoy!!.status},hull=${next.convoy!!.hull},intercepts=${next.destroyed}")
        }
        assertTrue("The available fleet must be able to escort the transport",delivered >= 2)
    }

    @Test fun identicalFleetProtectsConvoyBetterWhenTwoInterceptorsLeaveTheCorePatrol() {
        var passiveDeliveries=0; var escortDeliveries=0; var passiveHull=0; var escortHull=0
        for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(7,17,53,73)) {
            fun attempt(escort: Boolean): ArcadeSession {
                val random=Random(seed)
                var state=advanceArcade(run(15).copy(difficulty=difficulty,lives=difficulty.lives,spawnTimer=0.0),.01,random)
                val core=state.bodies.first { it.kind == BodyKind.Core }
                val transport=state.bodies.first { it.kind == BodyKind.Convoy }
                val guards=List(2) { index ->
                    val point=core.position+Vec2(if (index == 0) 240.0 else -240.0,0.0)
                    CelestialBody(SimulationEngine.newBodyId(),point,SimulationEngine.orbitVelocity(core,point),
                        24.0,8f,Color.Green,BodyKind.Ship,shipClass=ShipClass.Guardian)
                }
                val interceptors=List(2) { index ->
                    val point=if (escort) transport.position+transport.heading.perpendicular()*((index*2-1)*70.0)
                        else core.position+Vec2(if (index == 0) 300.0 else -300.0,0.0)
                    val knots=(0..3).map { phase -> core.position+Vec2(cos(index*PI+phase*PI/2),sin(index*PI+phase*PI/2))*300.0 }
                    val path=if (escort) null else FlightPath(knots,0)
                    CelestialBody(SimulationEngine.newBodyId(),point,if (escort) transport.velocity else path!!.sample(0.0).direction*220.0,
                        24.0,8f,Color.Cyan,BodyKind.Ship,heading=transport.heading,
                        waypoints=path?.knots?.drop(1) ?: emptyList(),routePath=path,routeSpeed=if (escort) 0.0 else 220.0)
                }
                state=state.copy(bodies=state.bodies+guards+interceptors)
                repeat(1290) {
                    if (state.convoy!!.status == ConvoyStatus.Approaching && state.lives > 0) {
                        state.upgradeOffer?.let { state=selectUpgrade(state,it.choices.first()) }
                        state=advanceArcade(state,1.0/30,random)
                    }
                }
                return state
            }
            val passive=attempt(false); val escorted=attempt(true)
            if (passive.convoy!!.status == ConvoyStatus.Delivered) passiveDeliveries++
            if (escorted.convoy!!.status == ConvoyStatus.Delivered) escortDeliveries++
            passiveHull+=passive.convoy!!.hull; escortHull+=escorted.convoy!!.hull
            println("CONVOY_PAIRED,difficulty=$difficulty,seed=$seed,passive=${passive.convoy!!.status}/${passive.convoy!!.hull},escort=${escorted.convoy!!.status}/${escorted.convoy!!.hull},corePassive=${passive.lives},coreEscort=${escorted.lives}")
        }
        assertTrue("Core patrol must not guarantee the optional reward",passiveDeliveries < 12)
        assertTrue("Moving the same ships should improve transport survival",escortDeliveries > passiveDeliveries)
        assertTrue("Escort must tolerate some mistakes",escortDeliveries >= 6)
        assertTrue(escortHull > passiveHull)
    }

    @Test fun aBusyPlanetOrbitDoesNotPreventTheConvoyFromArriving() {
        val base=run(15)
        val core=base.bodies.single()
        val occupied=List(24) { index ->
            val angle=index*2*PI/24
            meteor(core.position+Vec2(cos(angle),sin(angle))*430.0).copy(radius=100f)
        }
        val prepared=prepareArcadeEncounters(base.copy(bodies=base.bodies+occupied),.01,Random(17))
        assertNull(prepared.planetId)
        assertNotNull(prepared.convoy)
        assertEquals(1,prepared.bodies.count { it.kind == BodyKind.Convoy })
    }
}
