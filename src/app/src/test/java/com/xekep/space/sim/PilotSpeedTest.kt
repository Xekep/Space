package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PilotSpeedTest {
    private fun craft(kind: BodyKind,target: Double)=CelestialBody(1,Vec2.Zero,Vec2(0.0,-200.0),24.0,1f,Color.Cyan,kind,
        pilotTargetSpeed=target)
    @Test fun speedChangesGraduallyInBothDirectionsAndBothCraftTypesUseTheSameControl() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (target in listOf(0.0,100.0,500.0)) {
            var body=craft(kind,target)
            val first=steerManually(listOf(body),ManualFlightControl(1,0.0),1.0/60).single()
            assertTrue((first.velocity-body.velocity).magnitude() < 10)
            repeat(240) {
                val powered=steerManually(listOf(body),ManualFlightControl(1,0.0),1.0/60)
                body=NumericIntegrator.advance(powered,1.0/60,1.0/240,controlledId=1).single()
            }
            assertEquals(target,body.velocity.magnitude(),.1)
        }
    }
    @Test fun higherSpeedConsumesMoreFuelAndEmptyCraftCannotFollowTheSetpoint() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val remaining=listOf(100.0,700.0).map { target ->
                var body=craft(kind,target)
                repeat(120) { body=NumericIntegrator.advance(steerManually(listOf(body),ManualFlightControl(1,0.0),1.0/60),1.0/60,1.0/240,controlledId=1).single() }
                body.fuelRemaining
            }
            assertTrue(remaining[0] > remaining[1])
            assertTrue(remaining[1] > vehicleFuelCapacity(kind)-4)
            val empty=craft(kind,0.0).copy(fuelRemaining=0.0)
            assertEquals(empty,steerManually(listOf(empty),ManualFlightControl(1,1.0,1.0),1.0).single())
        }
    }
    @Test fun speedHandleTakesOverFromTheNavigatorWithoutTeleporting() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val body=craft(kind,400.0).copy(waypoints=listOf(Vec2(200.0,0.0)),
                routePath=FlightPath.through(Vec2.Zero,listOf(Vec2(200.0,0.0))),routeSpeed=120.0)
            val controlled=applyFlightControls(listOf(body),ManualFlightControl(1,0.0),1.0/60).single()
            assertTrue(controlled.waypoints.isEmpty())
            assertNull(controlled.routePath)
            assertEquals(body.position,controlled.position)
            assertTrue(controlled.velocity.magnitude() > body.velocity.magnitude())
            val automatic=applyFlightControls(listOf(body.copy(pilotTargetSpeed=null)),ManualFlightControl(1,0.0),1.0/60).single()
            assertNotNull(automatic.routePath)
            assertEquals(120.0,automatic.velocity.magnitude(),1e-5)
        }
    }
    @Test fun tiltStillTurnsAndNodsRaiseTheChosenCruiseSpeed() {
        val body=craft(BodyKind.Ship,300.0)
        val turned=steerManually(listOf(body),ManualFlightControl(1,1.0,1.0),.1).single()
        assertEquals(450.0,turned.pilotTargetSpeed!!,1e-8)
        assertNotEquals(body.heading,turned.heading)
        assertTrue(turned.velocity.x > 0)
    }
}
