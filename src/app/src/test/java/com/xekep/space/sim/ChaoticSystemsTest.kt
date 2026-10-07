package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChaoticSystemsTest {
    @Test fun variedSeedsHaveSeparatedSystemsColdBeltsAndNoInitialContacts() {
        for (seed in 0..11) {
            var id=0L
            val bodies=RandomSystems.create(Random(seed)) { ++id }
            val stars=bodies.filter { it.kind == BodyKind.Star }
            assertEquals(500,bodies.size); assertEquals(10,stars.size)
            assertEquals(80,bodies.count { it.mass in .7..14.0 })
            assertEquals(80,bodies.count { it.radius < .3f })
            for (i in stars.indices) for (j in i+1 until stars.size)
                assertTrue((stars[i].position-stars[j].position).magnitude() > 20000)
            val momentum=bodies.fold(Vec2.Zero) { total,b -> total+b.velocity*b.mass }
            assertEquals(0.0,momentum.x,1e-5); assertEquals(0.0,momentum.y,1e-5)
            for (i in bodies.indices) for (j in i+1 until bodies.size)
                assertTrue("seed=$seed initial contact $i/$j",(bodies[i].position-bodies[j].position).magnitude() > bodies[i].radius+bodies[j].radius)
        }
    }
    @Test fun unmodifiedGeneratedSystemsKeepAllFiveHundredBodiesForAMinuteWithCollisionsEnabled() {
        for (seed in listOf(7,17,53)) {
            var id=0L
            var bodies=RandomSystems.create(Random(seed)) { ++id }
            SimulationEngine.reserveBodyIds(bodies)
            val initialMass=bodies.sumOf { it.mass }
            val energy=SimulationEngine.totalEnergy(bodies)
            var impacts=0
            repeat(1800) {
                val result=SimulationEngine.stepSandbox(bodies,1.0/30,energy,true,collisionMode=SandboxCollisionMode.Debris)
                bodies=result.bodies; impacts+=result.collisions.size
                assertEquals("seed=$seed tick=$it must keep all initial bodies",500,bodies.size)
                assertTrue(bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() && it.mass > 0 })
            }
            assertEquals(0,impacts); assertEquals(initialMass,bodies.sumOf { it.mass },1e-8)
            println("Stable 500-body system seed=$seed: 60 seconds, $impacts impacts, ${bodies.size} bodies")
        }
    }
}
