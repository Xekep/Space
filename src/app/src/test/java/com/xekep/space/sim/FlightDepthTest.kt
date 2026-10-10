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

    @Test fun stabilizedAnglesBankInHullCoordinatesAndReleaseLevelsBothCraft() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (bank in listOf(-1.0,1.0)) {
            var body=craft(kind)
            val input=ManualFlightControl(1,0.0,pitch=.25,roll=bank,stabilizedAttitude=true)
            repeat(30) { body=steerManually(listOf(body),input,1.0/60).single() }
            assertEquals(.25*STABILIZED_FLIGHT_PITCH,body.pitch,.003)
            assertEquals(bank*STABILIZED_FLIGHT_ROLL,body.roll,.009)
            assertTrue(body.heading.y*bank > .15)
            assertTrue(body.velocity.y*bank > 40)
            val neutral=steerManually(listOf(body),input.copy(pitch=0.0,roll=0.0),.1).single()
            assertTrue(neutral.pitch in 0.0..<body.pitch)
            assertTrue(kotlin.math.abs(neutral.roll) < kotlin.math.abs(body.roll))
            assertEquals(body.flightHeight,neutral.flightHeight,0.0)
            val yaw=steerManually(listOf(neutral),input.copy(steering=.5,pitch=0.0,roll=0.0),.1).single()
            assertNotEquals(neutral.heading,yaw.heading); assertTrue(kotlin.math.abs(yaw.roll) < kotlin.math.abs(neutral.roll))
            for (unpowered in listOf(body.copy(pilotTargetSpeed=0.0),body.copy(fuelRemaining=0.0))) {
                val drift=steerManually(listOf(unpowered),input.copy(steering=1.0),.2).single()
                assertEquals(unpowered.velocity,drift.velocity); assertEquals(unpowered.pitch,drift.pitch,0.0)
                assertEquals(unpowered.roll,drift.roll,0.0); assertEquals(unpowered.heading,drift.heading)
            }
            val auto=applyFlightControls(listOf(body),null,.1).single()
            assertTrue(kotlin.math.abs(auto.roll) < kotlin.math.abs(body.roll))
        }
    }
    @Test fun invalidFpvAxisCannotPoisonTheSimulationAndAttitudeIsBounded() {
        val body=craft()
        assertEquals(listOf(body),steerManually(listOf(body),ManualFlightControl(1,0.0,roll=Double.NaN),.1))
        val max=steerManually(listOf(body),ManualFlightControl(1,0.0,pitch=1.0,roll=1.0,stabilizedAttitude=true),10.0).single()
        assertEquals(STABILIZED_FLIGHT_PITCH,max.pitch,1e-8); assertEquals(STABILIZED_FLIGHT_ROLL,max.roll,1e-8)
    }

    @Test fun partialStickHoldsItsAngleAndReversalIsImmediateAcrossFrameRatesAndMasses() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (mass in listOf(12.0,96.0)) {
            val initial=craft(kind).copy(mass=mass)
            val control=ManualFlightControl(1,0.0,pitch=.3,roll=-.4,stabilizedAttitude=true)
            var sixty=initial; var thirty=initial
            repeat(180) { sixty=steerManually(listOf(sixty),control,1.0/60).single() }
            repeat(90) { thirty=steerManually(listOf(thirty),control,1.0/30).single() }
            assertEquals(.3*STABILIZED_FLIGHT_PITCH,sixty.pitch,1e-8)
            assertEquals(-.4*STABILIZED_FLIGHT_ROLL,sixty.roll,1e-8)
            assertEquals(sixty.pitch,thirty.pitch,1e-10); assertEquals(sixty.roll,thirty.roll,1e-10)
            val reversed=steerManually(listOf(sixty),control.copy(pitch=-.3,roll=.4),1.0/60).single()
            assertTrue(reversed.pitch < sixty.pitch); assertTrue(reversed.roll > sixty.roll)
        }
    }

    @Test fun pitchHasAVisibleDepthCueInTheFirstTenthOfASecondWithoutTeleporting() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1.0,1.0)) {
            var body=craft(kind).copy(flightHeight=0.0)
            repeat(6) {
                val steered=steerManually(listOf(body),ManualFlightControl(1,0.0,pitch=axis,stabilizedAttitude=true),1.0/60)
                body=NumericIntegrator.advance(steered,1.0/60,1.0/120,controlledId=1).single()
            }
            assertTrue(body.pitch*axis > .3); assertTrue(body.verticalVelocity*axis > 0)
            assertTrue(body.flightHeight*axis in 0.0..10.0)
            assertTrue(if (axis > 0) body.flightVisualScale() > 1f else body.flightVisualScale() < 1f)
        }
    }

    @Test fun fpvAngularInputRespondsInWallTimeEvenAtSolarInspectionSpeed() {
        for (scale in listOf(.0001,.001,.01,.25,1.0,3.0,6.0)) {
            val body=steerManually(listOf(craft()),ManualFlightControl(1,.5,pitch=1.0,roll=-1.0,
                stabilizedAttitude=true,attitudeTimeScale=1.0/scale),.1*scale).single()
            assertEquals(STABILIZED_FLIGHT_PITCH*(1-kotlin.math.exp(-body.vehicleTurnScale/.55)),body.pitch,1e-8); assertEquals(-STABILIZED_FLIGHT_ROLL*(1-kotlin.math.exp(-body.vehicleTurnScale/.55)),body.roll,1e-8)
            assertEquals(1f,body.flightVisualScale(),0f)
            // Explicit lift responds in real time; natural bodies retain the world clock.
            assertTrue(body.verticalVelocity > 5.0)
        }
    }

    @Test fun releasedAxesLevelIndependentlyInWallTimeWithoutResettingHeightCourseOrThrottle() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1.0,1.0))
        for (scale in listOf(.0001,.25,1.0,6.0)) {
            val initial=craft(kind).copy(pitch=axis*.7,roll=axis*.9,flightHeight=123.0,heading=Vec2(0.0,1.0))
            val neutral=ManualFlightControl(1,0.0,stabilizedAttitude=true,attitudeTimeScale=1.0/scale)
            val first=steerManually(listOf(initial),neutral,scale/60).single()
            assertTrue(kotlin.math.abs(first.pitch) in .6.. .7)
            assertTrue(kotlin.math.abs(first.roll) in .8.. .9)
            assertEquals(initial.flightHeight,first.flightHeight,0.0)
            assertEquals(initial.pilotTargetSpeed!!,first.pilotTargetSpeed!!,0.0)
            val heldPitch=steerManually(listOf(initial),neutral.copy(pitch=axis),scale*.1).single()
            assertTrue(kotlin.math.abs(heldPitch.pitch) > kotlin.math.abs(initial.pitch))
            assertTrue(kotlin.math.abs(heldPitch.roll) < kotlin.math.abs(initial.roll))
            val heldRoll=steerManually(listOf(initial),neutral.copy(roll=axis),scale*.1).single()
            assertTrue(kotlin.math.abs(heldRoll.pitch) < kotlin.math.abs(initial.pitch))
            assertTrue(kotlin.math.abs(heldRoll.roll) > .85)
            var settled=initial
            repeat(120) { settled=steerManually(listOf(settled),neutral,scale/60).single() }
            assertEquals(0.0,settled.pitch,0.0); assertEquals(0.0,settled.roll,0.0)
            val next=steerManually(listOf(settled),neutral,scale/60).single()
            assertEquals(settled.heading,next.heading)
            for (unpowered in listOf(initial.copy(pilotTargetSpeed=0.0),initial.copy(fuelRemaining=0.0))) {
                val drift=steerManually(listOf(unpowered),neutral,scale*.1).single()
                assertEquals(unpowered.pitch,drift.pitch,0.0); assertEquals(unpowered.roll,drift.roll,0.0)
                assertEquals(unpowered.heading,drift.heading)
            }
        }
    }

    @Test fun shipsAndRocketsCrossAboveAndBelowStarsAndCoreButStillCrashInTheirVolume() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (targetKind in listOf(BodyKind.Star,BodyKind.Core)) {
            val star=CelestialBody(2,Vec2.Zero,Vec2.Zero,1000.0,30f,Color.Yellow,targetKind)
            for (height in listOf(-80.0,0.0,80.0)) {
                var bodies=listOf(star,craft(kind).copy(position=Vec2(-150.0,0.0),velocity=Vec2(900.0,0.0),flightHeight=height,pilotTargetSpeed=900.0))
                repeat(8) {
                    bodies=if (targetKind == BodyKind.Core) SimulationEngine.stepArcade(bodies,.05,1).bodies
                        else SimulationEngine.stepSandbox(bodies,.05,0.0,controlledId=1).bodies
                }
                assertEquals("$kind $targetKind height=$height",height != 0.0,bodies.any { it.id == 1L })
                if (height != 0.0) assertTrue(bodies.first { it.id == 1L }.position.x > 100)
            }
        }
        val sun=SimulationEngine.sandboxPreset().bodies.first { it.solar == SolarBody.Sun }
        val vehicle=craft().copy(radius=.000002f,physicalScale=true,position=sun.position+Vec2(-sun.radius*3.0,0.0),flightHeight=sun.radius*2.0)
        assertNull(bodyContact(vehicle,vehicle.copy(position=sun.position+Vec2(sun.radius*3.0,0.0)),sun,sun))
    }
    @Test fun directLiftBeginsOnTheFirstFrameAtEveryWorldSpeedAndDoesNotMoveOtherBodiesInWallTime() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1.0,1.0)) for (scale in listOf(.0001,.01,.25,1.0,6.0)) {
            val initial=craft(kind)
            val control=ManualFlightControl(1,0.0,pitch=axis,stabilizedAttitude=true,attitudeTimeScale=1/scale)
            val steered=steerManually(listOf(initial),control,scale/60)
            val first=NumericIntegrator.advance(steered,scale/60,scale/60,controlledId=1,manualDepthScale=control.depthTimeScale).single()
            assertTrue(first.flightHeight*axis > .05); assertTrue(first.verticalVelocity*axis > 3)
            assertTrue(first.pitch*axis > .15)
            assertTrue(kotlin.math.abs(first.flightHeight) < 1)
            val off=first.copy(pilotTargetSpeed=0.0,velocity=Vec2.Zero)
            val drift=NumericIntegrator.advance(listOf(off),scale/60,scale/60,controlledId=1,manualDepthScale=control.depthTimeScale).single()
            assertEquals(off.flightHeight+off.verticalVelocity/60,drift.flightHeight,1e-8)
        }
    }
}
