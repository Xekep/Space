package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class SandboxShakeTest {
    @Test fun shakingAddsMomentumRebasesEnergyAndCanBeUndone() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val before = game.sandbox!!
        assertTrue(game.shakeSandbox(Vec2(30.0, -20.0)))
        game.bodies.zip(before.bodies).forEach { (after, old) ->
            assertEquals(old.velocity + Vec2(30.0, -20.0), after.velocity)
            assertEquals(old.position, after.position)
        }
        assertEquals(SimulationEngine.totalEnergy(game.bodies), game.sandbox!!.referenceEnergy, 1e-8)
        assertTrue(game.dirty)
        game.undo()
        assertEquals(before, game.sandbox)
    }

    @Test fun pausedScenesAndOpenPanelsCannotBeShaken() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val before = game.bodies
        game.toggleSandboxPause()
        assertFalse(game.shakeSandbox(Vec2(30.0, 0.0)))
        game.toggleSandboxPause(); game.sandboxOverlayOpen = true
        assertFalse(game.shakeSandbox(Vec2(30.0, 0.0)))
        game.sandboxOverlayOpen = false; game.openMenu()
        assertFalse(game.shakeSandbox(Vec2(30.0, 0.0)))
        assertEquals(before, game.bodies)
    }

    @Test fun shakeDoesNotChangeArcadeAndRejectsInvalidImpulses() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        assertFalse(game.shakeSandbox(Vec2(Double.NaN, 0.0)))
        assertFalse(game.shakeSandbox(Vec2(200.0, 0.0)))
        game.enterMode(AppMode.Arcade)
        assertFalse(game.shakeSandbox(Vec2(30.0, 0.0)))
    }
}
