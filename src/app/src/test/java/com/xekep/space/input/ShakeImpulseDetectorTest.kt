package com.xekep.space.input

import com.xekep.space.sim.Vec2
import com.xekep.space.sim.ShakeMode
import org.junit.Assert.*
import org.junit.Test

class ShakeImpulseDetectorTest {
    private fun detector() = ShakeImpulseDetector().apply { sample(0.0, 0.0, 0.0, 0L) }

    @Test fun impulseModeKeepsItsStrengthButRearmsOnReleaseOrReversalAndOffIgnoresMotion() {
        val detector=detector()
        assertNull(detector.sample(11.99,0.0,0.0,500_000_000L,mode=ShakeMode.Classic))
        val first=detector.sample(20.0,0.0,0.0,600_000_000L,mode=ShakeMode.Classic)!!
        assertEquals(81.25,first.x,1e-9); assertEquals(0.0,first.y,1e-9)
        assertNull(detector.sample(-20.0,0.0,0.0,900_000_000L,mode=ShakeMode.Classic))
        assertEquals(-81.25,detector.sample(-20.0,0.0,0.0,950_000_000L,mode=ShakeMode.Classic)!!.x,1e-9)
        assertNull(detector.sample(-20.0,0.0,0.0,1_600_000_000L,mode=ShakeMode.Classic))
        detector.sample(0.0,0.0,0.0,1_650_000_000L,mode=ShakeMode.Classic)
        assertEquals(125.0,detector.sample(100.0,0.0,0.0,1_700_000_000L,mode=ShakeMode.Classic)!!.x,1e-9)
        assertNull(detector.sample(100.0,0.0,0.0,2_000_000_000L,mode=ShakeMode.Off))
        assertTrue(detector().sample(20.0,0.0,0.0,500_000_000L)!!.x < 0)
    }

    @Test fun ordinaryMotionAndTheWarmupProduceNoImpulse() {
        val detector = detector()
        assertNull(detector.sample(30.0, 0.0, 0.0, 100_000_000L))
        assertNull(detector.sample(2.0, 3.0, 4.0, 500_000_000L))
        assertNull(detector.sample(Double.NaN, 0.0, 0.0, 500_000_000L))
        assertNull(detector.sample(20.0, 0.0, 0.0, -1L))
    }

    @Test fun deliberateJoltsAreBoundedAndRateLimited() {
        val detector = detector()
        val impulse = detector.sample(100.0, 0.0, 0.0, 500_000_000L)!!
        assertEquals(-320.0, impulse.x, 1e-9); assertEquals(0.0, impulse.y, 1e-9)
        assertNull(detector.sample(100.0, 0.0, 0.0, 600_000_000L))
        assertNotNull(detector.sample(-30.0, 0.0, 0.0, 1_200_000_000L))
    }

    @Test fun screenRotationMapsTheSameDeviceMotionToTheCorrectDirection() {
        val directions = listOf(Vec2(-1.0, 0.0), Vec2(0.0, -1.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0))
        directions.forEachIndexed { rotation, expected ->
            val actual = detector().sample(20.0, 0.0, 0.0, 500_000_000L, rotation)!!.normalized()
            assertEquals(expected.x, actual.x, 1e-9); assertEquals(expected.y, actual.y, 1e-9)
        }
    }

    @Test fun forwardBackwardShakingAlsoHasAVisibleDirection() {
        val impulse = detector().sample(0.0, 0.0, 20.0, 500_000_000L)!!
        assertTrue(impulse.x < 0.0); assertTrue(impulse.y > 0.0)
        assertTrue(impulse.magnitude() in 80.0..320.0)
    }

    @Test fun easierJoltsScaleUpWithoutReactingToOrdinaryMotion() {
        assertNull(detector().sample(7.99, 0.0, 0.0, 500_000_000L))
        assertEquals(80.0, detector().sample(8.0, 0.0, 0.0, 500_000_000L)!!.magnitude(), 1e-8)
        assertEquals(248.0, detector().sample(20.0, 0.0, 0.0, 500_000_000L)!!.magnitude(), 1e-8)
        assertEquals(320.0, detector().sample(100.0, 0.0, 0.0, 500_000_000L)!!.magnitude(), 1e-8)
    }

    @Test fun heldAccelerationDoesNotRepeatButAlternatingShakesRespondQuickly() {
        val detector = detector()
        assertTrue(detector.sample(20.0, 0.0, 0.0, 500_000_000L)!!.x < 0)
        assertNull(detector.sample(20.0, 0.0, 0.0, 800_000_000L))
        assertTrue(detector.sample(-20.0, 0.0, 0.0, 850_000_000L)!!.x > 0)
        assertNull(detector.sample(20.0, 0.0, 0.0, 950_000_000L))
        assertTrue(detector.sample(20.0, 0.0, 0.0, 1_100_000_000L)!!.x < 0)
        assertNull(detector.sample(0.0, 0.0, 0.0, 1_200_000_000L))
        assertNotNull(detector.sample(20.0, 0.0, 0.0, 1_400_000_000L))
    }
}
