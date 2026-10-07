package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

/** Deterministic bots test progression and bounded workloads, not human difficulty. */
class ArcadeBalanceSmokeTest {
    @Test fun automatedDefenseRemainsFiniteAcrossSeedsAndAllDifficulties() {
        for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(7,17,73)) {
            val random=Random(seed)
            val initial=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0))
            var run=ArcadeSession(initial,SpaceCamera(initial.first().position),IntSize(900,1400),difficulty)
            var launchTimer=0.0
            var maxBodies=initial.size
            var chosen=0
            repeat(9000) {
                if (run.lives <= 0) return@repeat
                run.upgradeOffer?.let { offer ->
                    val preference=if (run.lives <= 2) listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines)
                        else listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Repair)
                    run=selectUpgrade(run,preference.first { it in offer.choices }); chosen++
                }
                launchTimer-=1.0/30
                val core=run.bodies.first { it.kind == BodyKind.Core }
                val threats=run.bodies.filter { it.kind == BodyKind.Meteor }
                if (launchTimer <= 0 && threats.isNotEmpty()) {
                    val ships=run.bodies.filter { it.kind == BodyKind.Ship }
                    val kind=if (ships.size < run.launchLimit(BodyKind.Ship)) BodyKind.Ship else BodyKind.Rocket
                    val guard=kind == BodyKind.Ship && run.guardianUnlocked && ships.count { it.shipClass == ShipClass.Guardian } < 2
                    val threat=threats.minBy { (it.position-core.position).magnitude() }
                    val radial=(threat.position-core.position).normalized()
                    val point=core.position+radial*240.0
                    val velocity=if (guard) SimulationEngine.orbitVelocity(core,point) else radial*(if (kind == BodyKind.Rocket) 310.0 else 220.0)
                    val craft=SimulationEngine.createBody(point,point,0.0,kind).copy(mass=if (kind == BodyKind.Ship) 24.0 else 12.0,
                        velocity=velocity,heading=velocity.normalized(),shipClass=if (guard) ShipClass.Guardian else ShipClass.Interceptor,
                        fuelConsumptionScale=run.fuelScale)
                    val cost=launchCost(craft)
                    if (run.energy >= cost && run.bodies.count { it.kind == kind } < run.launchLimit(kind)) {
                        run=run.copy(bodies=run.bodies+craft,energy=run.energy-cost,launches=run.launches+1)
                        launchTimer=1.0
                    }
                }
                run=advanceArcade(run,1.0/30,random)
                maxBodies=maxOf(maxBodies,run.bodies.size)
                assertTrue(run.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() && it.velocity.x.isFinite() && it.velocity.y.isFinite() })
                assertEquals(run.bodies.size,run.bodies.map { it.id }.distinct().size)
                assertTrue(run.combat.projectiles.size <= 64)
                assertTrue(run.energy in 0.0..run.maxEnergy)
                assertTrue(run.bodies.count { it.kind == BodyKind.Ship } <= run.launchLimit(BodyKind.Ship))
                assertTrue(run.bodies.count { it.kind == BodyKind.Rocket } <= run.launchLimit(BodyKind.Rocket))
            }
            println("ARCADE_BOT,difficulty=$difficulty,seed=$seed,seconds=${run.elapsed},wave=${run.wave},lives=${run.lives},upgrades=$chosen,maxBodies=$maxBodies,intercepts=${run.destroyed}")
            assertTrue(maxBodies < 80)
        }
    }
}
