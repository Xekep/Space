package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FlightRoutesTest {
    private fun craft(kind: BodyKind = BodyKind.Ship) = CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,8f,Color.Cyan,kind,
        waypoints=listOf(Vec2(150.0,0.0),Vec2(150.0,160.0),Vec2(300.0,160.0)),routeSpeed=100.0,
        burnRemaining=if (kind == BodyKind.Rocket) 3.0 else 0.0)

    @Test fun shipsAndRocketsVisitAllWaypointsInOrderAndContinueFlying() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            var bodies=listOf(craft(kind))
            var previousCount=3
            var reached=0
            repeat(1200) {
                val steered=applyFlightControls(bodies,null,1.0/60)
                val next=SimulationEngine.stepSandbox(steered,1.0/60,0.0,false)
                bodies=advanceWaypoints(steered,next.bodies)
                val current=bodies.single()
                assertTrue(current.position.x.isFinite() && current.position.y.isFinite())
                if (current.waypoints.size < previousCount) {
                    reached+=previousCount-current.waypoints.size
                    previousCount=current.waypoints.size
                }
            }
            assertEquals("$kind must finish the route",3,reached)
            assertTrue(bodies.single().waypoints.isEmpty())
            assertTrue(bodies.single().velocity.magnitude() > 20)
        }
    }
    @Test fun neutralWheelKeepsTheRouteAndTurningTakesOverOnlyTheLastCraft() {
        val body=craft()
        val neutral=applyFlightControls(listOf(body),ManualFlightControl(1,0.0),.1).single()
        assertEquals(body.waypoints,neutral.waypoints)
        val other=body.copy(id=2)
        val turned=applyFlightControls(listOf(body,other),ManualFlightControl(1,.5),.1)
        assertTrue(turned[0].waypoints.isEmpty()); assertEquals(other.waypoints,turned[1].waypoints)
    }
    @Test fun wheelTurnsRelativeToHeadingAndNeutralStopsTheTurn() {
        for (angle in listOf(-PI/2,0.0,PI/2,PI-.1)) {
            val body=craft().copy(waypoints=emptyList(),heading=Vec2(cos(angle),sin(angle)))
            val next=steerManually(listOf(body),ManualFlightControl(1,.5),.1).single()
            val difference=atan2(sin(atan2(next.heading.y,next.heading.x)-angle),cos(atan2(next.heading.y,next.heading.x)-angle))
            assertEquals(.18,difference,1e-8)
            val neutral=steerManually(listOf(next),ManualFlightControl(1,0.0),.1).single()
            assertEquals(next.heading.x,neutral.heading.x,1e-8); assertEquals(next.heading.y,neutral.heading.y,1e-8)
        }
    }
    @Test fun sweptWaypointsAdvanceInSequenceWithoutSkippingToALaterPoint() {
        val before=craft().copy(position=Vec2(-100.0,0.0),radius=1f,waypoints=listOf(Vec2.Zero,Vec2(50.0,0.0)))
        val after=before.copy(position=Vec2(100.0,0.0))
        assertTrue(advanceWaypoints(listOf(before),listOf(after)).single().waypoints.isEmpty())
        val reversed=before.copy(waypoints=listOf(Vec2(50.0,0.0),Vec2.Zero))
        assertEquals(listOf(Vec2.Zero),advanceWaypoints(listOf(reversed),listOf(reversed.copy(position=after.position))).single().waypoints)
    }
}
