package com.xekep.space.ui.space

import com.xekep.space.sim.*
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class SandboxShakeTest {
    @Test fun classicModePushesAllBodiesTogetherAndIntensityScalesBothModes() {
        for (mode in listOf(ShakeMode.Classic,ShakeMode.Inertial)) {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
            fun changes(intensity:Double):List<Vec2> {
                val before=game.bodies
                assertTrue(game.shakeSandbox(Vec2(30.0,-40.0),mode,intensity))
                val result=game.bodies.zip(before).map { (after,old) -> after.velocity-old.velocity }
                game.undo()
                return result
            }
            val weak=changes(.5); val strong=changes(2.0)
            weak.zip(strong).forEach { (a,b) ->
                assertEquals(a.x*4,b.x,1e-8); assertEquals(a.y*4,b.y,1e-8)
            }
            if (mode == ShakeMode.Classic) weak.forEach { assertEquals(Vec2(15.0,-20.0),it) }
            else assertNotEquals(weak.first(),weak.last())
        }
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val before=game.bodies
        assertFalse(game.shakeSandbox(Vec2(30.0,0.0),ShakeMode.Off))
        assertFalse(game.shakeSandbox(Vec2(30.0,0.0),intensity=Double.NaN))
        assertEquals(before,game.bodies)
    }
    @Test fun shakingAddsMomentumRebasesEnergyAndCanBeUndone() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val before = game.sandbox!!
        assertTrue(game.shakeSandbox(Vec2(75.0, -100.0)))
        game.bodies.zip(before.bodies).forEach { (after, old) ->
            val delta = after.velocity - old.velocity
            assertTrue(delta.x * 75.0 + delta.y * -100.0 > 0)
            assertTrue(delta.magnitude() > 100.0)
            assertEquals(old.position, after.position)
        }
        assertEquals(SimulationEngine.totalEnergy(game.bodies), game.sandbox!!.referenceEnergy, 1e-8)
        assertTrue(game.dirty)
        game.undo()
        assertEquals(before, game.sandbox)
    }

    @Test fun shakeStirsRelativeMotionAndRemainsVisibleWhenZoomedOut() {
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startSandbox(SandboxPresetKind.SolarSystem) }
        val before = game.bodies
        assertTrue(game.shakeSandbox(Vec2(-320.0, 0.0)))
        val changes = game.bodies.zip(before).map { (body, old) -> body.velocity - old.velocity }
        assertTrue(changes.all { it.x < 0 && it.magnitude() <= 320.0 * 8.0 * 1.2 + 1e-6 })
        assertTrue(changes.distinct().size > 10)
        assertTrue(changes.all { it.magnitude() * game.camera.zoom > 50.0 })
        assertEquals(before.map { it.position }, game.bodies.map { it.position })
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
        assertFalse(game.shakeSandbox(Vec2(321.0, 0.0)))
        game.enterMode(AppMode.Arcade)
        assertFalse(game.shakeSandbox(Vec2(30.0, 0.0)))
    }
}
