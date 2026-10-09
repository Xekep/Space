package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SystemGalaxyTest {
    private fun scene(seed: Int): List<CelestialBody> { var id=1L; return SystemGalaxy.create(Random(seed)) { id++ } }

    @Test fun fullHierarchyHasClearSurfacesInheritedVelocitiesAndBoundMoons() {
        for (seed in listOf(7,17,53,73)) {
            val bodies=scene(seed); val byId=bodies.associateBy { it.id }
            val stars=bodies.filter { it.kind == BodyKind.Star }
            val planets=bodies.filter { it.orbitParentId == it.galaxySystemId && it.orbitParentId != null }
            val moons=bodies.filter { it.orbitParentId != null && it.orbitParentId != it.galaxySystemId }
            assertEquals(1000,bodies.size); assertEquals(1000,byId.size)
            assertEquals(111,stars.size); assertEquals(333,planets.size); assertEquals(555,moons.size)
            assertEquals(1,bodies.count { it.kind == BodyKind.BlackHole })
            assertTrue(stars.maxOf { it.radius }/stars.minOf { it.radius } > 8f)
            assertTrue(stars.maxOf { it.mass }/stars.minOf { it.mass } > 3.5)
            assertTrue(bodies.all { it.physicalScale && !it.galaxyParticle })
            for (moon in moons) {
                val planet=byId.getValue(moon.orbitParentId!!); val star=byId.getValue(moon.galaxySystemId!!)
                val radius=(moon.position-planet.position).magnitude()
                val hill=(planet.position-star.position).magnitude()*kotlin.math.cbrt(planet.mass/(3*star.mass))
                assertTrue(radius < hill*.36)
                val expected=SimulationEngine.orbitVelocity(planet,moon.position,moon.mass)-planet.velocity
                assertEquals(expected.magnitude(),(moon.velocity-planet.velocity).magnitude(),.2)
            }
            for (i in bodies.indices) for (j in 0 until i) {
                assertTrue("Initial contact seed $seed: ${bodies[i].id}/${bodies[j].id}",
                    (bodies[i].position-bodies[j].position).magnitude() > bodies[i].radius+bodies[j].radius)
            }
            val momentum=bodies.fold(Vec2.Zero) { sum,body -> sum+body.velocity*body.mass }
            assertTrue(momentum.magnitude() < 1e-4)
            assertTrue(bodies.all { it.velocity.magnitude() < 5000 && it.position.x.isFinite() })
        }
    }

    @Test fun seedsVaryClustersColoursMassesAndOrbitSizes() {
        val first=scene(17); val second=scene(73)
        assertNotEquals(first.map { it.position },second.map { it.position })
        assertTrue(first.filter { it.kind == BodyKind.Star }.map { it.color }.distinct().size >= 3)
        assertTrue(first.filter { it.orbitParentId != null }.map { it.mass }.distinct().size > 800)
        val stars=first.filter { it.kind == BodyKind.Star }
        val sizes=stars.map { com.xekep.space.ui.space.bodyScreenRadius(it,.006f,2.75f) }
        assertTrue(sizes.max()/sizes.min() > 3f)
        assertTrue(first.filter { it.orbitParentId != null }.maxOf { it.radius }/first.filter { it.orbitParentId != null }.minOf { it.radius } > 10f)
    }

    @Test fun physicalSystemsRemainBoundForTwoMinutesWithRealCollisionsEnabled() {
        for (seed in listOf(17,73)) {
            val initial=scene(seed); val original=initial.associateBy { it.id }
            var bodies=initial; var impacts=0
            val started=System.nanoTime()
            repeat(3600) {
                val next=SimulationEngine.stepSandbox(bodies,1.0/30,0.0,true,collisionMode=SandboxCollisionMode.Debris)
                impacts+=next.collisions.size; bodies=next.bodies
                assertEquals("Seed $seed, tick $it",1000,bodies.size)
                assertTrue(bodies.all { b -> b.position.x.isFinite() && b.velocity.x.isFinite() })
            }
            val now=bodies.associateBy { it.id }; var maxPlanet=0.0; var maxMoon=0.0
            for (body in bodies.filter { it.orbitParentId != null }) {
                val old=original.getValue(body.id); val parent=now.getValue(body.orbitParentId!!)
                val before=(old.position-original.getValue(body.orbitParentId!!).position).magnitude()
                val ratio=(body.position-parent.position).magnitude()/before
                if (body.orbitParentId == body.galaxySystemId) maxPlanet=maxOf(maxPlanet,kotlin.math.abs(1-ratio))
                else maxMoon=maxOf(maxMoon,kotlin.math.abs(1-ratio))
                assertTrue("Unbound $seed/${body.id}: $ratio",ratio in .65..1.4)
            }
            println("SYSTEM_GALAXY_STABILITY,seed=$seed,impacts=$impacts,planetDrift=$maxPlanet,moonDrift=$maxMoon,seconds=${(System.nanoTime()-started)/1e9}")
            assertEquals(0,impacts)
        }
    }
}
