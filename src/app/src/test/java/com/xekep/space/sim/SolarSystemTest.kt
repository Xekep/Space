package com.xekep.space.sim

import androidx.compose.ui.unit.IntSize
import com.xekep.space.ui.space.SpaceGameState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SolarSystemTest {
    @Test fun allPlanetsAndMajorMoonsHavePhysicalMassAndSizeRatios() {
        val bodies = SimulationEngine.sandboxPreset().bodies
        assertEquals(SolarSystem.BODY_COUNT, bodies.size)
        val sun = bodies.first { it.solar == SolarBody.Sun }
        val earth = bodies.first { it.solar == SolarBody.Earth }
        assertEquals(332900.0, sun.mass / earth.mass, 50.0)
        assertEquals(109.2, sun.radius.toDouble() / earth.radius, .1)
        assertEquals(8, bodies.count { it.solar != null && !it.solar!!.isMoon && it.solar != SolarBody.Sun && it.solar != SolarBody.Pluto })
        assertEquals(bodies.size, bodies.map { it.id }.distinct().size)
    }

    @Test fun initialEllipsesObeyVisVivaAndKeplersPeriodLaw() {
        for (entry in SolarBody.entries.filter { it != SolarBody.Sun }) {
            val parent = SolarBody.valueOf(entry.parent)
            val (point, velocity) = entry.relativeState(parent.worldMass)
            val mu = 400.0 * (parent.worldMass + entry.worldMass)
            assertEquals(mu * (2 / point.magnitude() - 1 / (entry.axisAu * AU_WORLD)), velocity.magnitude().pow(2), .001)
        }
        val earthPeriod = 2 * PI * sqrt(AU_WORLD.pow(3) / (400 * SolarBody.Sun.worldMass))
        assertEquals(31.416, earthPeriod, .01)
        assertEquals(164.89, SolarBody.Neptune.axisAu.pow(1.5), .1)
    }

    @Test fun moonRemainsBoundAndTritonMovesRetrogradeAfterSeveralEarthYears() {
        var bodies = SimulationEngine.sandboxPreset().bodies
        val energy = SimulationEngine.totalEnergy(bodies)
        repeat(6000) { bodies = SimulationEngine.stepSandbox(bodies, .02, energy).bodies }
        for (entry in SolarBody.entries.filter { it.isMoon }) {
            val moon = bodies.first { it.solar == entry }; val parent = bodies.first { it.solar?.name == entry.parent }
            val distance = (moon.position - parent.position).magnitude()
            assertTrue("${entry.name}: $distance", distance in (entry.axisAu * AU_WORLD * SolarSystem.ORBIT_SCALE * .65)..(entry.axisAu * AU_WORLD * SolarSystem.ORBIT_SCALE * 1.4))
            val p = moon.position - parent.position; val v = moon.velocity - parent.velocity
            assertTrue(if (entry.retrograde) p.x * v.y - p.y * v.x < 0 else p.x * v.y - p.y * v.x > 0)
        }
    }

    @Test fun planetNavigationFitsItsMoonsAndCanReturnToTheWholeSystem() {
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startSandbox() }
        val overview = game.camera.zoom
        game.focusSolar(SolarBody.Earth)
        assertEquals(SolarBody.Earth, game.selectedBody!!.solar); assertTrue(game.following)
        assertTrue(game.camera.zoom > overview * 100)
        game.fitCamera(); assertFalse(game.following); assertTrue(game.camera.zoom < .05)
    }

    @Test fun spacecraftHullAndNewSatellitesUseTheSolarPhysicalScale() {
        val game = SpaceGameState().apply { startSandbox(); chooseSpawnKind(BodyKind.Ship) }
        val earth = game.bodies.first { it.solar == SolarBody.Earth }
        val point = earth.position + Vec2(0.0, 5.0)
        game.launch(com.xekep.space.ui.space.TouchPreview(point, point, 0), 0.0)
        val craft = game.bodies.last()
        assertTrue(craft.radius < earth.radius * .01f)
        game.update(1.0 / 60)
        assertTrue(game.bodies.any { it.id == craft.id })
        game.selectBody(earth.id); game.prepareOrbit()
        val moonPoint = earth.position + Vec2(0.0, 3.0)
        game.launch(com.xekep.space.ui.space.TouchPreview(moonPoint, moonPoint, 0), 0.0)
        val moon = game.bodies.last()
        assertEquals(BodyKind.Ambient, moon.kind)
        assertTrue(moon.radius < earth.radius)
        assertTrue(moon.mass < earth.mass * .021)
        assertTrue(craft.physicalScale && moon.physicalScale)
    }

    @Test fun solarMergingKeepsPhysicalVolumeAndGravityScale() {
        val bodies = SimulationEngine.sandboxPreset().bodies
        val earth = bodies.first { it.solar == SolarBody.Earth }
        val moon = bodies.first { it.solar == SolarBody.Moon }.copy(position = earth.position, velocity = earth.velocity)
        val merged = SimulationEngine.stepSandbox(listOf(earth, moon), .001, 0.0, true).bodies.single()
        assertEquals(earth.mass + moon.mass, merged.mass, 1e-9)
        assertEquals(earth.radius.toDouble().pow(3) + moon.radius.toDouble().pow(3), merged.radius.toDouble().pow(3), 1e-9)
        assertTrue(merged.physicalScale); assertNull(merged.solar)
        assertTrue(merged.radius < .1)
    }

    @Test fun classicOrbitsRestoresTheOriginalNineBodyPlayground() {
        val scene = SimulationEngine.sandboxPreset(SandboxPresetKind.ClassicOrbits)
        assertEquals(9, scene.bodies.size)
        assertEquals(12000.0, scene.bodies.first().mass, 0.0)
        assertEquals(6.0, scene.bodies[3].mass, 0.0)
        assertEquals(149.6, scene.bodies[3].position.magnitude(), 1e-8)
        assertEquals(4495.1, scene.bodies.last().position.magnitude(), 1e-8)
        assertEquals(1f, scene.zoom, 0f)
        assertTrue(scene.bodies.all { !it.physicalScale && it.solar == null })
        assertEquals(SimulationEngine.totalEnergy(scene.bodies), scene.referenceEnergy, 1e-8)
    }
}
