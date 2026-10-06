package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PilotCameraTest {
    @Test fun offCenterPinchingKeepsBothVehicleTypesCenteredInBothModesAndDoesNotRetarget() {
        listOf(AppMode.Sandbox,AppMode.Arcade).forEach { mode ->
            listOf(BodyKind.Ship,BodyKind.Rocket).forEach { kind ->
                val game=SpaceGameState().apply {
                    resize(IntSize(1080,1920))
                    if (mode == AppMode.Sandbox) startSandbox(SandboxPresetKind.Empty) else startArcade()
                    setMotionControlEnabled(true); chooseSpawnKind(kind)
                    val point=Vec2(5000.0,5000.0)
                    launch(TouchPreview(point,point,0),0.0)
                    setSteeringInput(Vec2(1.0,0.0)); repeat(20) { update(1.0/60) }; setSteeringInput(Vec2.Zero)
                }
                val id=game.controlledVehicleId!!
                val zoom=game.camera.zoom; val rotation=game.cameraRotation
                game.transformCamera(Offset(150f,350f),Offset(130f,-80f),1.7f)
                assertEquals(zoom*1.7f,game.camera.zoom,.001f)
                assertEquals(game.bodies.first { it.id == id }.position,game.camera.center)
                assertEquals(rotation,game.cameraRotation,0.0)
                val center=game.camera.center
                repeat(90) { game.update(1.0/60) }
                assertNotEquals(center,game.camera.center)
                val body=game.bodies.first { it.id == id }
                // Following remains smooth while moving, with a small speed-dependent camera lag.
                assertTrue((body.position-game.camera.center).magnitude() < body.velocity.magnitude()*.18+1.0)
                val heading=rotateVector(body.heading,game.cameraRotation)
                assertEquals(0.0,heading.x,.01); assertTrue(heading.y < -.99)
                game.chooseSpawnKind(BodyKind.Ambient)
                val point=body.position+Vec2(800.0,800.0)
                game.launch(TouchPreview(point,point,0),0.0)
                assertEquals(id,game.controlledVehicleId)
            }
        }
    }
    private fun pilot(): SpaceGameState = SpaceGameState().apply {
        resize(IntSize(1080,1920)); startSandbox(SandboxPresetKind.Empty); setMotionControlEnabled(true)
        chooseSpawnKind(BodyKind.Ship); launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0)
        setSteeringInput(Vec2(1.0,0.0)); repeat(20) { update(1.0/60) }; setSteeringInput(Vec2.Zero)
        repeat(90) { update(1.0/60) }
    }
    @Test fun cameraMakesThePilotedCraftFaceUpAndReturnsAfterLeavingControl() {
        val game=pilot()
        assertTrue(abs(game.cameraRotation) > 1)
        val onScreen=rotateVector(game.bodies.single().heading,game.cameraRotation)
        assertEquals(0.0,onScreen.x,.001); assertTrue(onScreen.y < -.99)
        game.openMenu(); val center=game.camera.center
        repeat(100) { game.update(1.0/60) }
        assertEquals(0.0,game.cameraRotation,0.0); assertEquals(center,game.camera.center)
        game.closeMenu(); repeat(90) { game.update(1.0/60) }
        assertTrue(abs(game.cameraRotation) > 1)
        game.setMotionControlEnabled(false); repeat(100) { game.update(1.0/60) }
        assertEquals(0.0,game.cameraRotation,0.0)
    }
    @Test fun destructionRestoresTheWorldOrientation() {
        val game=pilot()
        val point=game.bodies.single().position
        game.chooseSpawnKind(BodyKind.Ambient); game.launch(TouchPreview(point,point,0),0.0)
        game.update(1.0/60); assertNull(game.controlledVehicleId)
        repeat(100) { game.update(1.0/60) }
        assertEquals(0.0,game.cameraRotation,0.0)
    }
    @Test fun rotatedCameraKeepsTouchPositionsAndLaunchDirectionInWorldCoordinates() {
        val viewport=IntSize(1080,1920); val center=Vec2(53.0,-19.0); val point=Vec2(115.0,83.0)
        listOf(-PI,-1.4,0.0,1.7,PI).forEach { rotation ->
            val screen=worldToScreen(point,viewport,center,.7f,rotation)
            val back=screenToWorld(screen,viewport,center,.7f,rotation)
            assertEquals(point.x,back.x,.001); assertEquals(point.y,back.y,.001)
        }
        val game=pilot()
        val preview=game.previewAt(Offset(400f,700f),Offset(600f,700f),0)
        val velocity=game.launchVelocity(preview)
        val screenVelocity=rotateVector(velocity,game.cameraRotation)
        assertTrue(screenVelocity.x > 0); assertEquals(0.0,screenVelocity.y,1e-5)
        assertEquals(game.worldAt(Offset(400f,700f)),preview.startWorld)
    }
    @Test fun forwardFlickRaisesPersistentThrustOnceAndNeutralKeepsTheEngineRunning() {
        val game=SpaceGameState().apply {
            startSandbox(SandboxPresetKind.Empty); setMotionControlEnabled(true); chooseSpawnKind(BodyKind.Ship)
            launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0)
        }
        game.setSteeringInput(Vec2(0.0,1.0)); game.setSteeringInput(Vec2.Zero); game.update(.1)
        val speed=game.bodies.single().velocity.magnitude()
        assertEquals(103.0,speed,1e-6)
        assertEquals(.5,game.bodies.single().pilotThrottle,1e-6)
        game.update(.1); assertEquals(136.0,game.bodies.single().velocity.magnitude(),1e-6)
        assertEquals(.5,game.bodies.single().pilotThrottle,1e-6)
    }
    @Test fun boostFollowsTheNoseForBothVehicleTypesAndNeverBrakesFastCraft() {
        val template=pilot().bodies.single()
        listOf(BodyKind.Ship,BodyKind.Rocket).forEach { kind ->
            listOf(Vec2(1.0,0.0),Vec2(0.0,-1.0),Vec2(-1.0,0.0),Vec2(0.0,1.0)).forEach { heading ->
                val body=template.copy(kind=kind,heading=heading,velocity=heading*600.0)
                val boosted=steerManually(listOf(body),ManualFlightControl(body.id,0.0,1.0),.02).single()
                assertEquals(heading.x*606.6,boosted.velocity.x,1e-6)
                assertEquals(heading.y*606.6,boosted.velocity.y,1e-6)
                val released=steerManually(listOf(boosted),ManualFlightControl(body.id,0.0),.02).single()
                assertEquals(613.2,released.velocity.magnitude(),1e-6)
            }
        }
    }
}
