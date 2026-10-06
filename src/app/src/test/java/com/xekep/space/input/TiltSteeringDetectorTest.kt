package com.xekep.space.input

import com.xekep.space.sim.Vec2
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TiltSteeringDetectorTest {
    @Test fun calibratesNeutralAndIgnoresSmallMotionAndInvalidValues() {
        val detector = TiltSteeringDetector()
        assertEquals(Vec2.Zero, detector.sample(0.0, 0.0, 9.81, 1))
        assertEquals(Vec2.Zero, detector.sample(.1, 0.0, 9.81, 100000001))
        assertEquals(Vec2.Zero, detector.sample(Double.NaN, 0.0, 9.81, 200000001))
        assertEquals(Vec2.Zero, detector.sample(0.0, 0.0, 0.0, 300000001))
    }
    @Test fun tiltIsSmoothedBoundedAndRecalibratesWithScreenRotation() {
        val detector = TiltSteeringDetector()
        detector.sample(0.0, 0.0, 9.81, 1)
        val first = detector.sample(5.0, 0.0, 8.44, 16000001)
        assertTrue(first.x in -.3.. -.05)
        val later = detector.sample(5.0, 0.0, 8.44, 216000001)
        assertTrue(later.x < first.x); assertTrue(later.magnitude() <= 1); assertEquals(0.0,later.y,0.0)
        assertEquals(Vec2.Zero, detector.sample(5.0, 0.0, 8.44, 232000001, 1))
        val landscape = detector.sample(5.0, -5.0, 8.44, 432000001, 1)
        assertTrue(landscape.x < 0)
    }
    @Test fun pitchWrapAroundDoesNotReverseTheSteering() {
        val detector = TiltSteeringDetector()
        detector.sample(0.0, .01, -9.81, 1)
        val neutral = detector.sample(0.0, -.01, -9.81, 100000001)
        assertEquals(Vec2.Zero, neutral)
    }
    @Test fun clockwiseWheelTurnsRightAndWorksWhenThePhoneIsFlat() {
        val upright=TiltSteeringDetector()
        upright.sampleQuaternion(0.0,0.0,0.0,1.0,1)
        assertTrue(upright.sampleQuaternion(0.0,0.0,-sin(.2),cos(.2),100000001).x > .5)
        val flat=TiltSteeringDetector()
        val a=sqrt(.5)
        flat.sampleQuaternion(a,0.0,0.0,a,1)
        val turn=flat.sampleQuaternion(a*cos(.2),a*sin(.2),-a*sin(.2),a*cos(.2),100000001)
        assertTrue(turn.x > .5)
        val pitch=TiltSteeringDetector()
        pitch.sampleQuaternion(0.0,0.0,0.0,1.0,1)
        assertEquals(Vec2.Zero,pitch.sampleQuaternion(sin(.2),0.0,0.0,cos(.2),100000001))
    }
    @Test fun forwardFlickBoostsOnceAndHeldOrSlowTiltDoesNot() {
        val detector=TiltSteeringDetector()
        detector.sampleQuaternion(0.0,0.0,0.0,1.0,1)
        val flick=detector.sampleQuaternion(-sin(.2),0.0,0.0,cos(.2),100000001)
        assertEquals(0.0,flick.x,1e-9); assertTrue(flick.y > .5)
        assertEquals(0.0,detector.sampleQuaternion(-sin(.2),0.0,0.0,cos(.2),200000001).y,0.0)
        assertEquals(0.0,detector.sampleQuaternion(-sin(.25),0.0,0.0,cos(.25),400000001).y,0.0)
        detector.sampleQuaternion(0.0,0.0,0.0,1.0,600000001)
        assertTrue(detector.sampleQuaternion(-sin(.2),0.0,0.0,cos(.2),700000001).y > .5)
        val slow=TiltSteeringDetector()
        slow.sampleQuaternion(0.0,0.0,0.0,1.0,1)
        repeat(20) { i ->
            val angle=(i+1)*.01
            assertEquals(0.0,slow.sampleQuaternion(-sin(angle/2),0.0,0.0,cos(angle/2),(i+1)*50_000_000L+1).y,0.0)
        }
    }
    @Test fun fallbackDetectsForwardFlickAndLandscapeUsesScreenPitchAxis() {
        val detector=TiltSteeringDetector()
        detector.sample(0.0,9.81,0.0,1)
        assertTrue(detector.sample(0.0,9.81*cos(.3),9.81*sin(.3),100000001).y > .5)
        val landscape=TiltSteeringDetector()
        landscape.sampleQuaternion(0.0,0.0,0.0,1.0,1,1)
        assertTrue(landscape.sampleQuaternion(0.0,sin(.2),0.0,cos(.2),100000001,1).y > .5)
    }
}
