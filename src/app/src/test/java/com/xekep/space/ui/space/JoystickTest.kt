package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import com.xekep.space.sim.Vec2
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
}
