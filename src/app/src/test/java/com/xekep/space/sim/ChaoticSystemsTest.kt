package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.*

class ChaoticSystemsTest {
    @Test fun variedSeedsHaveThreeBinariesCrossingStreamsRetrogradeOrbitsAndNoInitialContacts() {
        for (seed in 0..11) {
            var id=0L
            val bodies=RandomSystems.create(Random(seed)) { ++id }
            val stars=bodies.filter { it.kind == BodyKind.Star }
            assertEquals(500,bodies.size); assertEquals(10,stars.size)
            val pairs=stars.indices.sumOf { i -> (i+1 until stars.size).count { j -> (stars[i].position-stars[j].position).magnitude() < 1200 } }
            assertEquals(3,pairs)
            assertTrue(stars.maxOf { it.mass }/stars.minOf { it.mass } > 1.5)
            val angular=bodies.filter { it.kind != BodyKind.Star && it.mass > 2 }.map { body ->
                val star=stars.minBy { (body.position-it.position).magnitude() }
                val p=body.position-star.position; val v=body.velocity-star.velocity
                p.x*v.y-p.y*v.x
            }
            assertTrue(angular.any { it < 0 }); assertTrue(angular.any { it > 0 })
            assertTrue(bodies.count { body -> body.mass < 1 && stars.all { (body.position-it.position).magnitude() > 1000 } } >= 60)
            val momentum=bodies.fold(Vec2.Zero) { total,b -> total+b.velocity*b.mass }
            assertEquals(0.0,momentum.x,1e-5); assertEquals(0.0,momentum.y,1e-5)
            for (i in bodies.indices) for (j in i+1 until bodies.size)
                assertTrue("seed=$seed initial contact $i/$j",(bodies[i].position-bodies[j].position).magnitude() > bodies[i].radius+bodies[j].radius)
        }
    }
    @Test fun chaoticWorldStaysFiniteWithDebrisOverSeveralSeconds() {
        var id=0L
        var bodies=RandomSystems.create(Random(17)) { ++id }
        SimulationEngine.reserveBodyIds(bodies)
        val energy=SimulationEngine.totalEnergy(bodies)
        repeat(240) {
            bodies=SimulationEngine.stepSandbox(bodies,1.0/60,energy,true,collisionMode=SandboxCollisionMode.Debris).bodies
            assertEquals("Fragments must keep unique IDs",bodies.size,bodies.map { it.id }.distinct().size)
        }
        assertTrue(bodies.size in 400..1000)
        assertTrue(bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() && it.mass > 0 })
    }
}
