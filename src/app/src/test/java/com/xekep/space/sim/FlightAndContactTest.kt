package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FlightAndContactTest {
    private fun craft(id: Long, x: Double = 0.0, y: Double = 0.0, kind: BodyKind = BodyKind.Rocket) =
        CelestialBody(id, Vec2(x,y), Vec2(0.0,-100.0), 1e-8, 1f, Color.Cyan, kind)
    @Test fun headingUsesShortestTurnAndIsRateLimited() {
        val before = Vec2(cos(PI-.01), sin(PI-.01))
        val target = Vec2(cos(-PI+.1), sin(-PI+.1))
        val next = turnHeading(before, target, .01, 2.0)
        assertEquals(-PI+.01, atan2(next.y,next.x), 1e-8)
        assertEquals(before, turnHeading(before, Vec2.Zero, 1.0))
        assertEquals(1.0, next.magnitude(), 1e-8)
    }
    @Test fun manualHeadingControlsRocketThrustAndDoesNotTouchOtherCraft() {
        val bodies = listOf(craft(1).copy(burnRemaining = 3.0), craft(2,100.0))
        val input = ManualFlightControl(1, 1.0)
        val steered = steerManually(bodies,input,.1)
        assertEquals(bodies[1],steered[1]); assertTrue(steered[0].heading.x > .3)
        val result = SimulationEngine.stepSandbox(steered,.05,0.0,false,1).bodies
        assertEquals(steered[0].heading,result[0].heading)
        assertEquals(steered[0].velocity.x,result[0].velocity.x,1e-8)
        assertEquals(2.95,result[0].burnRemaining,1e-8)
    }
    @Test fun circleSweepReturnsSurfaceEntryAndMovingAwayMisses() {
        assertEquals(.4,firstCircleContact(Vec2(-10.0,0.0),Vec2(10.0,0.0),2.0)!!,1e-8)
        assertNull(firstCircleContact(Vec2(10.0,0.0),Vec2(12.0,0.0),2.0))
        assertEquals(0.0,firstCircleContact(Vec2.Zero,Vec2.Zero,2.0)!!,0.0)
    }
    @Test fun thinRocketDoesNotCollideWithEmptySpaceAlongItsSide() {
        val rocket = craft(1).copy(velocity = Vec2.Zero)
        val planet = craft(2,.9,.5,BodyKind.Ambient).copy(radius=.1f)
        assertNull(bodyContact(rocket,rocket,planet,planet))
        val nose = planet.copy(position=Vec2(0.0,-1.2))
        assertNotNull(bodyContact(rocket,rocket,nose,nose))
    }
    @Test fun earliestImpactWinsRegardlessOfTargetListOrder() {
        val rocket = craft(1,-10.0).copy(velocity=Vec2(5000.0,0.0), radius=.1f,heading=Vec2(1.0,0.0))
        val far = craft(2,20.0,0.0,BodyKind.Meteor).copy(velocity=Vec2.Zero,radius=.1f)
        val near = far.copy(id=3,position=Vec2(0.0,0.0))
        val hit = SimulationEngine.stepArcade(listOf(rocket,far,near),.008)
        assertEquals(3L,hit.collisions.single().meteorId)
        assertEquals(listOf(2L),hit.bodies.map { it.id })
        assertTrue(abs(hit.collisions.single().position.x) < .2)
    }
}
