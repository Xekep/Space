package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class SandboxVehiclesTest {
    private fun rocket(burn: Double = 3.0) = CelestialBody(1, Vec2.Zero, Vec2.Zero, 12.0, 6f, Color.White,
        BodyKind.Rocket, burnRemaining = burn, heading = Vec2(1.0, 0.0), fuelRemaining = burn)

    @Test fun rocketBurnsItsRemainingFuelThenCoastsWithoutThrust() {
        var body = rocket()
        repeat(3) { body = SimulationEngine.stepSandbox(listOf(body), 1.0, 0.0).bodies.single() }
        assertEquals(240.0, body.velocity.x, 1e-7)
        assertEquals(360.0, body.position.x, 1e-7)
        assertEquals(0.0, body.burnRemaining, 1e-7)
        assertEquals(0.0, body.fuelRemaining, 1e-7)
        assertEquals(ROCKET_DRIFT_SECONDS, body.driftRemaining, 1e-7)
        body = SimulationEngine.stepSandbox(listOf(body), 1.0, 0.0).bodies.single()
        assertEquals(240.0, body.velocity.x, 1e-7)
        assertEquals(600.0, body.position.x, 1e-7)
        assertEquals(ROCKET_DRIFT_SECONDS-1, body.driftRemaining, 1e-7)
    }

    @Test fun partialFuelAndTimePartitionsDeliverTheSameImpulse() {
        val partial = SimulationEngine.stepSandbox(listOf(rocket(0.003)), 0.1, 0.0).bodies.single()
        assertEquals(0.24, partial.velocity.x, 1e-9)
        var partitioned = rocket()
        repeat(200) { partitioned = SimulationEngine.stepSandbox(listOf(partitioned), 0.02, 0.0).bodies.single() }
        val batched = SimulationEngine.stepSandbox(listOf(rocket()), 4.0, 0.0).bodies.single()
        assertEquals(batched.velocity.x, partitioned.velocity.x, 1e-7)
        assertEquals(batched.position.x, partitioned.position.x, 1e-7)
    }

    @Test fun creationUsesChosenKindAndWorksFarFromTheOriginalCamera() {
        val game = SpaceGameState().apply { resize(IntSize(1080, 1920)); startSandbox(SandboxPresetKind.Empty) }
        listOf(BodyKind.Ship, BodyKind.Rocket).forEach { kind ->
            game.chooseSpawnKind(kind)
            game.launch(TouchPreview(Vec2(10000.0, -10000.0), Vec2(10020.0, -10000.0), 0), 1.0)
            assertEquals(kind, game.bodies.last().kind)
            assertTrue(game.bodies.last().mass < 100.0)
        }
        game.selectBody(game.bodies.last().id)
        game.prepareOrbit()
        assertEquals(BodyKind.Rocket, game.spawnKind)
        assertNull(game.orbitSource)
        game.clearSelection()
        game.undo()
        assertEquals(BodyKind.Ship, game.bodies.single().kind)
    }

    @Test fun vehicleImpactsExplodeEvenWhenMergingIsOff() {
        val ship = rocket().copy(id = 2, kind = BodyKind.Ship, burnRemaining = 0.0, mass = 24.0)
        for (merging in listOf(false, true)) {
            val impact = SimulationEngine.stepSandbox(listOf(rocket(), ship), 0.01, 0.0, merging)
            assertTrue(impact.bodies.isEmpty())
            assertEquals(1, impact.collisions.size)
            assertTrue(impact.collisions.single().vehicleExplosion)
        }
    }

    @Test fun acceleratedTimeConsumesFuelAndPanelsFreezeIt() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); chooseSpawnKind(BodyKind.Rocket) }
        game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.0)
        game.setTimeScale(6.0)
        game.sandboxOverlayOpen = true
        game.update(0.5)
        assertEquals(3.0, game.bodies.single().burnRemaining, 0.0)
        game.sandboxOverlayOpen = false
        repeat(30) { game.update(1.0 / 60.0) }
        assertEquals(0.0, game.bodies.single().burnRemaining, 1e-7)
        assertEquals(117.0, game.bodies.single().fuelRemaining, 1e-7)
        assertEquals(-240.0, game.bodies.single().velocity.y, 1e-7)
    }

    @Test fun energyCorrectionDoesNotCancelThrustInLargeScenes() {
        val planet = rocket().copy(id = 2, kind = BodyKind.Ambient, mass = 1.0, position = Vec2(3000.0, 0.0), burnRemaining = 0.0)
        val bodies = listOf(rocket(), planet)
        val next = SimulationEngine.stepSandbox(bodies, 1.0, SimulationEngine.totalEnergy(bodies)).bodies.first()
        assertTrue(next.velocity.x > 79.0)
    }
}
