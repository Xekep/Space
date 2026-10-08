package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChaoticSystemsTest {
    @Test fun variedGalaxiesHaveComparableVisibleBodiesClustersAndNoInitialContacts() {
        val shapes=mutableSetOf<List<Int>>()
        for (seed in 0..11) {
            var id=0L
            val bodies=RandomSystems.create(Random(seed)) { ++id }
            val stars=bodies.filter { it.kind == BodyKind.Star }
            assertEquals(500,bodies.size); assertTrue(stars.size > 400)
            assertEquals(1,bodies.count { it.kind == BodyKind.BlackHole })
            assertTrue(bodies.maxOf { it.position.magnitude() } < 6000)
            assertTrue(bodies.maxOf { it.radius }/bodies.minOf { it.radius } < 3)
            assertTrue(bodies.map { it.color }.distinct().size >= 8)
            val nearest=stars.map { star -> stars.filter { it.id != star.id }.minOf { (it.position-star.position).magnitude() } }.sorted()
            assertTrue("seed=$seed should have dense and sparse regions",nearest.last()/nearest[nearest.size/4] > 2)
            shapes+=List(24) { sector -> stars.count { body ->
                val angle=kotlin.math.atan2(body.position.y,body.position.x)+kotlin.math.PI
                (angle*24/(2*kotlin.math.PI)).toInt().coerceAtMost(23) == sector } }
            val momentum=bodies.fold(Vec2.Zero) { total,b -> total+b.velocity*b.mass }
            assertEquals(0.0,momentum.x,1e-5); assertEquals(0.0,momentum.y,1e-5)
            val center=bodies.fold(Vec2.Zero) { total,b -> total+b.position*b.mass }/bodies.sumOf { it.mass }
            assertEquals(0.0,center.x,1e-8); assertEquals(0.0,center.y,1e-8)
            for (i in bodies.indices) for (j in i+1 until bodies.size)
                assertTrue("seed=$seed initial contact $i/$j",(bodies[i].position-bodies[j].position).magnitude() > bodies[i].radius+bodies[j].radius)
        }
        assertTrue("Morphology must vary across seeds",shapes.size >= 10)
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
