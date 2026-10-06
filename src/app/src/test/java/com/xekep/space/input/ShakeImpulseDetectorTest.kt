package com.xekep.space.input

import com.xekep.space.sim.Vec2
import org.junit.Assert.*
import org.junit.Test

class ShakeImpulseDetectorTest {
    private fun detector() = ShakeImpulseDetector().apply { sample(0.0, 0.0, 0.0, 0L) }

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
        assertEquals(80.0, impulse.x, 1e-9); assertEquals(0.0, impulse.y, 1e-9)
        assertNull(detector.sample(100.0, 0.0, 0.0, 600_000_000L))
        assertNotNull(detector.sample(-30.0, 0.0, 0.0, 1_200_000_000L))
    }

    @Test fun screenRotationMapsTheSameDeviceMotionToTheCorrectDirection() {
        val directions = listOf(Vec2(1.0, 0.0), Vec2(0.0, 1.0), Vec2(-1.0, 0.0), Vec2(0.0, -1.0))
        directions.forEachIndexed { rotation, expected ->
            val actual = detector().sample(20.0, 0.0, 0.0, 500_000_000L, rotation)!!.normalized()
            assertEquals(expected.x, actual.x, 1e-9); assertEquals(expected.y, actual.y, 1e-9)
        }
    }

    @Test fun forwardBackwardShakingAlsoHasAVisibleDirection() {
        val impulse = detector().sample(0.0, 0.0, 20.0, 500_000_000L)!!
        assertTrue(impulse.x > 0.0); assertTrue(impulse.y < 0.0)
        assertTrue(impulse.magnitude() in 20.0..80.0)
    }
}
