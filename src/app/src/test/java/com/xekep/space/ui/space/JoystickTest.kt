package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.flightVisualScale
import org.junit.Assert.*
import org.junit.Test

class JoystickTest {
    @Test fun stickHasADeadZoneAndStaysBoundedOutsideItsCircle() {
        assertEquals(Vec2.Zero,joystickDirection(Offset(2f,-2f),38f))
        val right=joystickDirection(Offset(10000f,0f),38f)
        assertEquals(1.0,right.x,1e-8); assertEquals(0.0,right.y,0.0)
        val up=joystickDirection(Offset(0f,-10000f),38f)
        assertEquals(0.0,up.x,0.0); assertEquals(-1.0,up.y,1e-8)
        val diagonal=joystickDirection(Offset(50f,-50f),38f)
        assertTrue(diagonal.x > .5 && diagonal.y < -.5 && diagonal.magnitude() <= 1.0)
    }
    @Test fun invalidStickCoordinatesCannotReachTheFlightController() {
        assertEquals(Vec2.Zero,joystickDirection(Offset(Float.NaN,1f),38f))
        assertEquals(Vec2.Zero,joystickDirection(Offset(1f,1f),0f))
        assertEquals(Vec2.Zero,joystickDirection(Offset(1f,1f),Float.POSITIVE_INFINITY))
    }
    @Test fun pullBackClimbsPushForwardDivesAndYawReleaseDoesNotCancelPitch() {
        for (kind in listOf(com.xekep.space.sim.BodyKind.Ship,com.xekep.space.sim.BodyKind.Rocket)) for (axis in listOf(-1.0,1.0)) {
            val game=SpaceGameState().apply {
                startSandbox(com.xekep.space.sim.SandboxPresetKind.Empty); setMotionControlEnabled(true); chooseSpawnKind(kind)
                launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0); setPilotTargetSpeed(220.0)
                setAttitudeJoystickInput(Vec2(0.0,axis)); setJoystickInput(Vec2(.5,0.0)); setJoystickInput(Vec2.Zero)
            }
            repeat(90) { game.update(1.0/60) }
            val craft=game.bodies.single()
            assertTrue(craft.pitch*axis > .75); assertTrue(craft.flightHeight*axis > 100)
            assertTrue(if (axis > 0) craft.flightVisualScale() > 1f else craft.flightVisualScale() < 1f)
            assertEquals(220.0,craft.pilotTargetSpeed!!,0.0)
            val pitch=craft.pitch
            game.setAttitudeJoystickInput(Vec2.Zero); repeat(90) { game.update(1.0/60) }
            assertTrue(kotlin.math.abs(game.bodies.single().pitch) < .01)
            assertTrue(game.bodies.single().flightHeight*axis > 100)
            assertEquals(220.0,game.bodies.single().pilotTargetSpeed!!,0.0)
            game.setMotionControlEnabled(false); repeat(90) { game.update(1.0/60) }
            assertTrue((game.bodies.single().pitch-pitch)*axis < 0)
        }
    }
    @Test fun throttleKeepsItsSetpointOnTouchAndYawAndReversesAtEndStops() {
        for (value in listOf(0.0,.2,.5,.8,1.0)) assertEquals(value,joystickThrottle(value,0f,0f,38f),0.0)
        assertEquals(1.0,joystickThrottle(0.0,38f,-38f,38f),0.0)
        assertEquals(0.0,joystickThrottle(1.0,-38f,38f,38f),0.0)
        assertEquals(.5,joystickThrottle(1.0,-38f,0f,38f),0.0)
        assertEquals(.2,joystickThrottle(.2,Float.NaN,0f,38f),0.0)
    }

}
