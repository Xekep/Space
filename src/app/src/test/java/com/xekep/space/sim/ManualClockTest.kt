package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ManualClockTest {
    private fun craft(kind: BodyKind) = CelestialBody(1,Vec2.Zero,Vec2(260.0,0.0),24.0,5f,Color.Cyan,kind,
        heading=Vec2(1.0,0.0),pilotTargetSpeed=720.0,pilotThrottle=.8)
    private val scales=listOf(.0001,.01,.25,1.0,6.0)

    @Test fun thrustFlightPositionAttitudeAndFuelUseOneClockForBothControlMethods() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (stabilized in listOf(false,true)) {
            fun fly(scale: Double): CelestialBody {
                var bodies=listOf(craft(kind))
                val control=ManualFlightControl(1,.3,pitch=.4,roll=if (stabilized) -.2 else 0.0,
                    stabilizedAttitude=stabilized,attitudeTimeScale=1/scale)
                repeat(60) {
                    bodies=NumericIntegrator.advance(applyFlightControls(bodies,control,scale/60),scale/60,scale/120,
                        controlledId=1,manualDepthScale=control.depthTimeScale)
                }
                return bodies.single()
            }
            val reference=fly(1.0)
            for (scale in scales) {
                val actual=fly(scale)
                assertEquals("$kind $stabilized scale=$scale",reference.position.x,actual.position.x,1e-5)
                assertEquals(reference.position.y,actual.position.y,1e-5)
                assertEquals(reference.velocity.x,actual.velocity.x,1e-5)
                assertEquals(reference.velocity.y,actual.velocity.y,1e-5)
                assertEquals(reference.flightHeight,actual.flightHeight,1e-5)
                assertEquals(reference.verticalVelocity,actual.verticalVelocity,1e-5)
                assertEquals(reference.pitch,actual.pitch,1e-5)
                assertEquals(reference.roll,actual.roll,1e-5)
                assertEquals(reference.fuelRemaining,actual.fuelRemaining,1e-5)
            }
        }
    }

    @Test fun onlyTheControlledCraftChangesClockIncludingInTreeGravityAndWithEngineOff() {
        for (count in listOf(2,BARNES_HUT_THRESHOLD+1)) for (scale in scales) {
            val body=craft(BodyKind.Ship).copy(pilotTargetSpeed=0.0,velocity=Vec2(260.0,0.0),verticalVelocity=25.0)
            val others=List(count-1) { i -> CelestialBody(100L+i,Vec2(100000.0+i*1000,100000.0),Vec2(10.0,0.0),
                .0001,1f,Color.White) }
            val moved=NumericIntegrator.advance(listOf(body)+others,scale*.1,scale*.05,controlledId=1,manualDepthScale=1/scale)
            assertEquals(26.0,moved.first().position.x,1e-4)
            assertEquals(2.5,moved.first().flightHeight,1e-4)
            assertEquals(body.fuelRemaining,moved.first().fuelRemaining,0.0)
            assertEquals(body.heading,moved.first().heading)
            assertEquals(100000+scale,moved[1].position.x,1e-4)
        }
    }

    @Test fun anEmptyRocketCoastsForSixRealSecondsAtSolarInspectionSpeedThenExplodes() {
        val scale=.0001
        val rocket=craft(BodyKind.Rocket).copy(fuelRemaining=0.0)
        var bodies=listOf(rocket)
        repeat(5*60) { bodies=SimulationEngine.stepSandbox(bodies,scale/60,0.0,controlledId=1,manualDepthScale=1/scale).bodies }
        assertEquals(1,bodies.size)
        assertEquals(1300.0,bodies.single().position.x,1e-4)
        assertEquals(1.0,bodies.single().driftRemaining,1e-5)
        var explosion=false
        repeat(61) {
            val next=SimulationEngine.stepSandbox(bodies,scale/60,0.0,controlledId=1,manualDepthScale=1/scale)
            explosion=explosion || next.collisions.any { it.vehicleExplosion }
            bodies=next.bodies
        }
        assertTrue(bodies.isEmpty()); assertTrue(explosion)
    }
}
