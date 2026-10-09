package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MotionControlTest {
    private fun game() = SpaceGameState().apply { resize(IntSize(1080,1920)); startSandbox(SandboxPresetKind.Empty); setMotionControlEnabled(true) }
    private fun SpaceGameState.launchAt(x: Double,kind: BodyKind = BodyKind.Rocket) {
        chooseSpawnKind(kind); launch(TouchPreview(Vec2(x,0.0),Vec2(x,0.0),0),0.0)
    }
    @Test fun onlyLastVehicleIsControlledAndShakingReturnsAfterItIsDestroyed() {
        val game = game()
        game.launchAt(-500.0); val first=game.bodies.last().id
        game.launchAt(500.0); val last=game.bodies.last().id
        assertEquals(last,game.controlledVehicleId)
        game.launchAt(900.0,BodyKind.Ambient)
        assertEquals(last,game.controlledVehicleId)
        assertFalse(game.shakeSandbox(Vec2(40.0,0.0)))
        game.setSteeringInput(Vec2(1.0,0.0)); game.update(.1)
        assertTrue(game.bodies.first { it.id == last }.heading.x > .3)
        assertTrue(kotlin.math.abs(game.bodies.first { it.id == first }.heading.x) < .01)
        val impactPoint=game.bodies.first { it.id == last }.position
        game.chooseSpawnKind(BodyKind.Ambient)
        game.launch(TouchPreview(impactPoint,impactPoint,0),0.0)
        game.update(.02)
        assertTrue(game.bodies.none { it.id == last })
        assertTrue(game.bodies.any { it.id == first })
        assertTrue(game.explosions.isNotEmpty())
        assertNull(game.controlledVehicleId)
        assertTrue(game.shakeSandbox(Vec2(40.0,0.0)))
    }
    @Test fun pauseMenuAndRestartClearInputAndControlDoesNotRetargetOlderCraft() {
        val game=game(); game.launchAt(500.0)
        game.setSteeringInput(Vec2(1.0,0.0)); game.openMenu(); game.closeMenu(); game.update(.02)
        assertEquals(0.0,game.bodies.single().heading.x,1e-7)
        game.toggleSandboxPause(); val before=game.bodies
        game.setSteeringInput(Vec2(1.0,0.0)); game.update(.1); assertEquals(before,game.bodies)
        game.startSandbox(SandboxPresetKind.Empty); assertNull(game.controlledVehicleId)
    }
    @Test fun arcadeManualPilotKeepsTurretAutomaticAndDoesNotExpireWhileControlled() {
        val scene=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0))
        val ship=scene[1].copy(id=99,kind=BodyKind.Ship,position=Vec2(700.0,200.0),velocity=Vec2(0.0,-100.0),heading=Vec2(0.0,-1.0),
            fuelRemaining=vehicleFuelCapacity(BodyKind.Ship))
        val enemy=ship.copy(id=100,kind=BodyKind.Meteor,position=Vec2(900.0,200.0),velocity=Vec2.Zero)
        val combat=ArcadeCombat(craft=mapOf(99L to CraftStatus(age=20.0,cooldown=0.0)))
        val prepared=prepareCombat(listOf(ship,enemy),combat,.1,99)
        assertEquals(ship.heading,prepared.bodies.first().heading)
        assertEquals(ship.velocity,prepared.bodies.first().velocity)
        assertEquals(1,prepared.combat.projectiles.size)
        assertTrue(prepared.combat.projectiles.single().velocity.x > 600)
    }
    @Test fun counterShowsCapacityAndFullScenesDoNotProduceABanner() {
        val game=game()
        repeat(MAX_SANDBOX_BODIES) { game.launchAt(it*100.0,BodyKind.Ambient) }
        assertEquals(MAX_SANDBOX_BODIES,game.spawnCount); assertEquals(MAX_SANDBOX_BODIES,game.spawnLimit)
        game.launchAt(300000.0); assertEquals(MAX_SANDBOX_BODIES,game.bodies.size); assertNull(game.feedback)
        game.startArcade(); game.chooseSpawnKind(BodyKind.Ship)
        assertEquals(3,game.spawnLimit)
    }
    @Test fun panningWhilePilotingKeepsTrackingAndDisablingControlRestoresFreePanning() {
        val game=game(); game.launchAt(500.0,BodyKind.Ship)
        game.update(.1)
        assertTrue(game.camera.center.x > 150)
        game.transformCamera(androidx.compose.ui.geometry.Offset(500f,900f),androidx.compose.ui.geometry.Offset(100f,0f),1f)
        assertEquals(game.bodies.single().position,game.camera.center)
        val camera=game.camera
        game.update(.1)
        assertNotEquals(camera.center,game.camera.center)
        game.setMotionControlEnabled(false)
        game.transformCamera(androidx.compose.ui.geometry.Offset(500f,900f),androidx.compose.ui.geometry.Offset(100f,0f),1f)
        val freeCamera=game.camera
        game.update(.1)
        assertEquals(freeCamera,game.camera)
    }
}
