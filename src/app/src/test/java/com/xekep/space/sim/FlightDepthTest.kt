package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class FlightDepthTest {
    private fun craft(kind: BodyKind=BodyKind.Ship)=CelestialBody(1,Vec2(-200.0,0.0),Vec2(300.0,0.0),24.0,5f,Color.Cyan,kind,
        heading=Vec2(1.0,0.0),pilotTargetSpeed=300.0,pilotThrottle=1.0/3)
    @Test fun positiveAndNegativePitchClimbAndDiveSmoothlyForBothVehicles() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1.0,1.0)) {
            var body=craft(kind)
            repeat(90) { body=NumericIntegrator.advance(applyFlightControls(listOf(body),ManualFlightControl(1,0.0,pitch=axis),1.0/60),1.0/60,1.0/120,controlledId=1).single() }
            assertTrue(body.flightHeight*axis > 100)
            assertTrue(body.pitch*axis > 1.0)
            assertEquals(300.0,body.flightSpeed(),1.0)
            assertTrue(body.fuelRemaining > vehicleFuelCapacity(kind)-3)
        }
    }
    @Test fun zeroEngineAndEmptyTankCannotSteerPitchOrCancelVerticalDrift() {
        for (body in listOf(craft().copy(pilotTargetSpeed=0.0),craft().copy(fuelRemaining=0.0))) {
            val initial=body.copy(pitch=.3,verticalVelocity=25.0,flightHeight=100.0)
            val next=applyFlightControls(listOf(initial),ManualFlightControl(1,1.0,pitch=-1.0),.2).single()
            assertEquals(initial.pitch,next.pitch,0.0); assertEquals(initial.verticalVelocity,next.verticalVelocity,0.0)
            assertEquals(initial.heading,next.heading)
            val drift=NumericIntegrator.advance(listOf(next),.2,1.0/120,controlledId=1).single()
            assertEquals(105.0,drift.flightHeight,1e-6)
        }
    }
    @Test fun sweptDepthPreventsFalseCollisionsButStillDetectsDescentThroughBody() {
        val planet=CelestialBody(2,Vec2.Zero,Vec2.Zero,100.0,30f,Color.White)
        val before=craft().copy(position=Vec2(-100.0,0.0))
        val after=before.copy(position=Vec2(100.0,0.0))
        assertNotNull(bodyContact(before,after,planet,planet))
        for (height in listOf(-60.0,60.0)) assertNull(bodyContact(before.copy(flightHeight=height),after.copy(flightHeight=height),planet,planet))
        assertNotNull(bodyContact(before.copy(position=Vec2.Zero,flightHeight=60.0),after.copy(position=Vec2.Zero,flightHeight=-60.0),planet,planet))
        assertNull(bodyContact(before.copy(flightHeight=60.0),after.copy(flightHeight=60.0),before.copy(id=3,position=Vec2.Zero,flightHeight=-60.0),before.copy(id=3,position=Vec2.Zero,flightHeight=-60.0)))
    }
    @Test fun returnToAutopilotDescendsWithoutTeleporting() {
        var body=craft().copy(flightHeight=100.0,pitch=.5)
        val first=applyFlightControls(listOf(body),null,1.0/60).single()
        assertEquals(100.0,first.flightHeight,0.0)
        repeat(600) { body=NumericIntegrator.advance(applyFlightControls(listOf(body),null,1.0/60),1.0/60,1.0/120).single() }
        assertTrue(kotlin.math.abs(body.flightHeight) < 1.0)
    }
    @Test fun heightReducesPlanarGravityAndAttractsTowardOrbitalPlane() {
        val star=CelestialBody(2,Vec2.Zero,Vec2.Zero,1000.0,10f,Color.White)
        val high=craft().copy(flightHeight=100.0)
        val gravity=depthGravity(high,listOf(high,star))
        assertTrue(gravity.planarCorrection.x < 0); assertTrue(gravity.vertical < 0)
        assertEquals(-gravity.vertical,depthGravity(high.copy(flightHeight=-100.0),listOf(high,star)).vertical,1e-8)
    }
    @Test fun perspectiveIsBoundedAndNeutralHeightPreservesOriginalSize() {
        assertEquals(1f,craft().flightVisualScale(),0f)
        assertTrue(craft().copy(flightHeight=100.0).flightVisualScale() > 1f)
        assertTrue(craft().copy(flightHeight=-100.0).flightVisualScale() < 1f)
        assertTrue(craft().copy(flightHeight=1e6).flightVisualScale() <= 1.65f)
    }
    @Test fun firstPitchWithoutSpeedSetpointKeepsAStableCruiseInsteadOfRepeatedCosineDecay() {
        var body=craft().copy(pilotTargetSpeed=null)
        repeat(180) { body=NumericIntegrator.advance(applyFlightControls(listOf(body),ManualFlightControl(1,0.0,pitch=1.0),1.0/60),1.0/60,1.0/120,controlledId=1).single() }
        assertEquals(300.0,body.pilotTargetSpeed!!,1e-6)
        assertEquals(300.0,body.flightSpeed(),1.0)
    }

}
