package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class OrbitalDetailsTest {
    @Test fun generatedRingsHavePhysicalBandsAndEarthSatellitesInheritTheirMovingParent() {
        val bodies = SimulationEngine.sandboxPreset().bodies
        val saturn = bodies.first { it.solar == SolarBody.Saturn }
        val earth = bodies.first { it.solar == SolarBody.Earth }
        assertEquals(SolarSystem.RING_GRAINS, bodies.count { it.orbitalDetail == OrbitalDetail.RingGrain })
        assertEquals(SolarSystem.EARTH_SATELLITES, bodies.count { it.orbitalDetail == OrbitalDetail.ArtificialSatellite })
        for (body in bodies.filter { it.orbitalDetail != null }) {
            val parent = bodies.first { it.id == body.orbitParentId }
            val distance = (body.position - parent.position).magnitude()
            val speed = (body.velocity - parent.velocity).magnitude()
            assertEquals(400 * parent.mass / distance, speed * speed, 1e-6)
            assertTrue(body.radius < parent.radius * .001)
            if (parent.id == saturn.id) {
                val km = distance / AU_WORLD * AU_KM
                assertTrue(km in 74657.9..136780.1)
                assertFalse(km in 117508.0..122339.0)
            } else {
                assertEquals(earth.id, parent.id)
                assertTrue(distance > earth.radius)
            }
        }
    }

    @Test fun fastLocalOrbitsRemainBoundForSeveralYearsWithMergingEnabled() {
        var bodies = SimulationEngine.sandboxPreset().bodies
        val original = bodies.associateBy { it.id }
        val start = System.nanoTime()
        repeat(3600) { bodies = SimulationEngine.stepSandbox(bodies, 1.0 / 60, 0.0, true).bodies }
        assertEquals(SolarSystem.BODY_COUNT, bodies.size)
        for (body in bodies.filter { it.orbitalDetail != null }) {
            val parent = bodies.first { it.id == body.orbitParentId }
            val initial = original[body.id]!!
            val oldParent = original[parent.id]!!
            val radius = (initial.position - oldParent.position).magnitude()
            val distance = (body.position - parent.position).magnitude()
            assertEquals("${body.orbitalDetail} id=${body.id}", radius, distance, radius * .04)
        }
        println("SOLAR_DETAILS bodies=${bodies.size} seconds=60 collisionMode=Merge elapsedMs=${(System.nanoTime()-start)/1e6}")
    }

    @Test fun exactKeplerDriftPreservesEccentricAndRetrogradeOrbitsAcrossManyRevolutions() {
        for (entry in listOf(SolarBody.Mercury, SolarBody.Triton)) {
            val parent = SolarBody.valueOf(entry.parent)
            val mu = 400 * (parent.worldMass + entry.worldMass)
            val initial = entry.relativeState(parent.worldMass)
            val period = 2 * PI * sqrt((entry.axisAu * AU_WORLD).pow(3) / mu)
            val end = OrbitalDetails.keplerDrift(initial.first, initial.second, mu, period * 100)!!
            assertTrue((end.first - initial.first).magnitude() < 1e-6)
            assertTrue((end.second - initial.second).magnitude() < 1e-6)
        }
    }

    @Test fun particlesCanEscapeAndAreNotReattachedAfterTheirParentIsRemoved() {
        val bodies = SimulationEngine.sandboxPreset().bodies
        val grain = bodies.first { it.orbitalDetail == OrbitalDetail.RingGrain }
        val parent = bodies.first { it.id == grain.orbitParentId }
        val boosted = grain.copy(velocity = parent.velocity + (grain.velocity - parent.velocity) * 2.0)
        val escaped = SimulationEngine.stepSandbox(listOf(parent, boosted), .0001, 0.0).bodies.last()
        assertNull(escaped.orbitalDetail)
        assertTrue((escaped.position - boosted.position).magnitude() > 0)
        val released = SimulationEngine.stepSandbox(listOf(grain), .01, 0.0).bodies.single()
        assertNull(released.orbitalDetail)
        assertEquals(grain.position.x + grain.velocity.x * .01, released.position.x, 1e-8)
    }

    @Test fun aSolarBlackHoleCapturesAPlanetAtTidalEncounterButAllowsADistantOrbit() {
        val game = com.xekep.space.ui.space.SpaceGameState().apply { startSandbox(); chooseSpawnKind(BodyKind.BlackHole) }
        val earth = game.bodies.first { it.solar == SolarBody.Earth }
        val point = earth.position + Vec2(2.0, 0.0)
        val hole = game.previewBody(com.xekep.space.ui.space.TouchPreview(point,point,0), 0.0)!!.copy(id=1000,velocity=earth.velocity)
        assertTrue(hole.mass >= SolarBody.Sun.worldMass * 3)
        assertTrue(blackHoleCaptureRadius(hole,earth) > (point-earth.position).magnitude())
        val captured = SimulationEngine.stepSandbox(listOf(hole,earth), .001, 0.0, false)
        assertEquals(1,captured.bodies.size)
        assertEquals(hole.mass + earth.mass,captured.bodies.single().mass,1e-7)
        assertTrue(captured.collisions.single().collapseRadius > 0)
        val far = earth.copy(position=hole.position+Vec2(80.0,0.0))
        val orbit = far.copy(velocity=SimulationEngine.orbitVelocity(hole.copy(velocity=Vec2.Zero),far.position,far.mass,true))
        var current = listOf(hole.copy(velocity=Vec2.Zero),orbit)
        repeat(600) { current=SimulationEngine.stepSandbox(current,.001,0.0,false).bodies }
        assertEquals(2,current.size)
        assertEquals(80.0,(current[0].position-current[1].position).magnitude(),.01)
        val resting=far.copy(velocity=Vec2.Zero)
        current=listOf(hole.copy(velocity=Vec2.Zero),resting)
        repeat(1000) { current=SimulationEngine.stepSandbox(current,.001,0.0,false).bodies }
        assertEquals(1,current.size)
    }
}
