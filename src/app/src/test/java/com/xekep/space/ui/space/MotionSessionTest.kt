package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class MotionSessionTest {
    @Test fun everyNewGameStartsWithTiltOffAndAnUnrotatedCamera() {
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)) }
        for (mode in AppMode.entries) repeat(2) {
            if (mode == AppMode.Arcade) game.startArcade() else game.startSandbox(SandboxPresetKind.Empty)
            assertFalse(game.motionSteeringEnabled); assertNull(game.controlledVehicleId)
            game.chooseSpawnKind(BodyKind.Ship)
            game.launch(TouchPreview(Vec2(300.0,300.0),Vec2(300.0,300.0),0),0.0)
            game.setMotionControlEnabled(true); game.setSteeringInput(Vec2(1.0,1.0))
            repeat(20) { game.update(1.0/60) }
            assertTrue(game.motionSteeringEnabled); assertNotNull(game.controlledVehicleId)
            if (mode == AppMode.Arcade) game.startArcade() else game.startSandbox(SandboxPresetKind.Empty)
            assertFalse(game.motionSteeringEnabled); assertNull(game.controlledVehicleId)
            assertEquals(0.0,game.cameraRotation,0.0)
            game.chooseSpawnKind(BodyKind.Ship); game.launch(TouchPreview(Vec2(400.0,400.0),Vec2(400.0,400.0),0),0.0)
            assertNull(game.controlledVehicleId)
        }
    }
    @Test fun pausingAndContinuingTheSameGameKeepsTiltEnabled() {
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade(); setMotionControlEnabled(true) }
        game.openMenu(); game.closeMenu(); assertTrue(game.motionSteeringEnabled)
        game.startSandbox(SandboxPresetKind.Empty); game.setMotionControlEnabled(true)
        game.toggleSandboxPause(); game.toggleSandboxPause(); game.openMenu(); game.closeMenu()
        assertTrue(game.motionSteeringEnabled)
    }
    @Test fun loadingOrGeneratingAnotherWorldResetsTilt() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); setMotionControlEnabled(true) }
        val snapshot=game.snapshot(123)!!
        game.loadSandbox(snapshot); assertFalse(game.motionSteeringEnabled)
        game.setMotionControlEnabled(true); game.generateRandomSystems("Galaxy")
        assertFalse(game.motionSteeringEnabled)
    }
}
