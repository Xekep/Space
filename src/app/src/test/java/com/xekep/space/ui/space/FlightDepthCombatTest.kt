package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class FlightDepthCombatTest {
    @Test fun highShipAimsAtTheOrbitalPlaneAndItsProjectileCanHit() {
        val ship=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,5f,Color.Cyan,BodyKind.Ship,flightHeight=150.0)
        val target=CelestialBody(2,Vec2(200.0,0.0),Vec2.Zero,100.0,10f,Color.Red,BodyKind.Meteor)
        val solution=ArcadeTargeting(listOf(ship,target)).solution(ship,target,Vec2.Zero,650.0)!!
        assertTrue(solution.verticalVelocity < 0)
        assertEquals(0.0,solution.height+solution.verticalVelocity*solution.seconds,.2)
        val shot=SpaceProjectile(solution.origin,solution.velocity,1,height=solution.height,verticalVelocity=solution.verticalVelocity)
        val impact=advanceProjectiles(listOf(ship,target),ArcadeCombat(listOf(shot)),solution.seconds+.01)
        assertFalse(impact.bodies.any { it.id == 2L }); assertEquals(1,impact.events.size)
    }
    @Test fun ProjectileAboveOrBelowMeteorCannotDamageIt() {
        val target=CelestialBody(2,Vec2.Zero,Vec2.Zero,100.0,10f,Color.Red,BodyKind.Meteor)
        for (height in listOf(-30.0,30.0)) {
            val shot=SpaceProjectile(Vec2(-100.0,0.0),Vec2(200.0,0.0),1,height=height)
            val result=advanceProjectiles(listOf(target),ArcadeCombat(listOf(shot)),1.0)
            assertEquals(target,result.bodies.single()); assertTrue(result.events.isEmpty())
        }
    }
}
