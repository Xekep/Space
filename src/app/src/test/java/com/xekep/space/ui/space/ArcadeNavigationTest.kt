package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class ArcadeNavigationTest {
    private fun core()=CelestialBody(1,Vec2.Zero,Vec2.Zero,7800.0,28f,Color.Yellow,BodyKind.Core)
    private fun craft(kind: BodyKind, point: Vec2, speed: Double)=CelestialBody(2,point,Vec2(speed,0.0),
        if (kind == BodyKind.Ship) 24.0 else 12.0,8f,Color.Cyan,kind,heading=Vec2(1.0,0.0))
    private fun target()=CelestialBody(10,Vec2(6000.0,0.0),Vec2.Zero,60.0,8f,Color.Red,BodyKind.Meteor)

    @Test fun bothAutopilotsPassAroundCoreInsteadOfChasingThroughIt() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val vehicle=craft(kind,Vec2(-450.0,0.0),if (kind == BodyKind.Ship) 220.0 else 310.0)
            var bodies=listOf(core(),vehicle,target()); var combat=ArcadeCombat(); var nearest=450.0
            repeat(360) {
                val prepared=prepareCombat(bodies,combat,1.0/60)
                bodies=SimulationEngine.stepArcade(prepared.bodies,1.0/60).bodies; combat=prepared.combat
                val moving=bodies.firstOrNull { it.id == vehicle.id }
                assertNotNull("$kind hit the core on tick $it",moving)
                nearest=minOf(nearest,moving!!.position.magnitude())
            }
            println("CORE_DETOUR,kind=$kind,clearance=$nearest,end=${bodies.first { it.id == vehicle.id }.position}")
            assertTrue(nearest > core().radius+vehicle.radius+20)
            assertTrue("Detour must still make progress",bodies.first { it.id == vehicle.id }.position.x > 100.0)
        }
    }

    @Test fun movingSatellitesAreAvoidedThroughTheActualGravityAndCollisionSolver() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (sign in listOf(-1,1)) {
            val vehicle=craft(kind,Vec2(-650.0,sign*40.0),if (kind == BodyKind.Ship) 220.0 else 310.0)
            val point=Vec2(-320.0,sign*80.0)
            val satellite=CelestialBody(3,point,SimulationEngine.orbitVelocity(core(),point),850.0,27f,Color.Blue,BodyKind.ArcadePlanet)
            var bodies=listOf(core(),satellite,vehicle,target()); var combat=ArcadeCombat()
            repeat(300) {
                val prepared=prepareCombat(bodies,combat,1.0/60)
                bodies=SimulationEngine.stepArcade(prepared.bodies,1.0/60).bodies; combat=prepared.combat
                assertTrue("$kind / $sign hit an obstacle on tick $it",bodies.any { it.id == vehicle.id })
            }
        }
    }

    @Test fun coastingPoweredCraftEvadesEvenBeforeAnEnemyAppears() {
        val vehicle=craft(BodyKind.Ship,Vec2(-400.0,0.0),220.0)
        var bodies=listOf(core(),vehicle)
        repeat(300) {
            val prepared=prepareCombat(bodies,ArcadeCombat(),1.0/60)
            bodies=SimulationEngine.stepArcade(prepared.bodies,1.0/60).bodies
            assertTrue(bodies.any { it.id == vehicle.id })
        }
    }

    @Test fun detouringHullStillFiresAtTheEnemyWithBoundedAcceleration() {
        val vehicle=craft(BodyKind.Ship,Vec2(-360.0,0.0),220.0)
        val enemy=target().copy(position=Vec2(-110.0,0.0),mass=200.0)
        val result=prepareCombat(listOf(core(),vehicle,enemy),ArcadeCombat(craft=mapOf(2L to CraftStatus(cooldown=0.0))),.01)
        assertEquals(1,result.combat.projectiles.size)
        assertTrue(result.combat.projectiles.single().velocity.x > 600)
        val moved=result.bodies.first { it.id == vehicle.id }
        assertTrue((moved.velocity-vehicle.velocity).magnitude() <= 140*.01+1e-8)
        assertTrue(kotlin.math.abs(moved.heading.y) > .001)
    }

    @Test fun manualRoutesAndUnpoweredEnginesRetainTheirOwnSteering() {
        val vehicle=craft(BodyKind.Ship,Vec2(-360.0,0.0),220.0)
        fun check(body: CelestialBody, controlled: Long? = null) {
            val moved=prepareCombat(listOf(core(),body,target()),ArcadeCombat(),.1,controlled).bodies.first { it.id == body.id }
            assertEquals(body.velocity,moved.velocity); assertEquals(body.heading,moved.heading)
        }
        check(vehicle,vehicle.id)
        check(vehicle.copy(waypoints=listOf(Vec2(6000.0,0.0))))
        check(vehicle.copy(pilotTargetSpeed=0.0))
    }

    @Test fun meetingCrossingAndOvertakingFleetPassWithoutFriendlyExplosions() {
        val cases=listOf(
            craft(BodyKind.Ship,Vec2(-450.0,0.0),220.0) to craft(BodyKind.Ship,Vec2(450.0,0.0),-220.0),
            craft(BodyKind.Ship,Vec2(-450.0,0.0),220.0) to craft(BodyKind.Rocket,Vec2(450.0,0.0),-310.0),
            craft(BodyKind.Rocket,Vec2(-500.0,0.0),310.0) to craft(BodyKind.Rocket,Vec2(500.0,0.0),-310.0),
            craft(BodyKind.Ship,Vec2(-400.0,0.0),220.0) to craft(BodyKind.Ship,Vec2(0.0,-400.0),0.0).copy(velocity=Vec2(0.0,220.0)),
            craft(BodyKind.Rocket,Vec2(-400.0,0.0),310.0) to craft(BodyKind.Ship,Vec2(-100.0,0.0),160.0),
        )
        for ((index,pair) in cases.withIndex()) {
            var bodies=listOf(pair.first,pair.second.copy(id=3,heading=pair.second.velocity.normalized()))
            var nearest=Double.POSITIVE_INFINITY
            repeat(360) {
                val prepared=prepareCombat(bodies,ArcadeCombat(),1.0/60)
                bodies=SimulationEngine.stepArcade(prepared.bodies,1.0/60).bodies
                assertEquals("Friendly collision case $index tick $it",2,bodies.size)
                nearest=minOf(nearest,(bodies[0].position-bodies[1].position).magnitude())
            }
            println("FLEET_DETOUR,case=$index,minDistance=$nearest,end=${bodies.map { it.position }}")
            assertTrue(nearest > pair.first.radius+pair.second.radius)
            assertTrue("Avoidance must keep making progress",bodies[0].position.x > 200.0)
        }
    }

    @Test fun authoredRoutesTakeTemporaryDetoursAroundOtherCraftThenResume() {
        for (crossing in listOf(false,true)) {
            val a=craft(BodyKind.Ship,Vec2(-450.0,0.0),220.0).copy(waypoints=listOf(Vec2(1400.0,0.0)),routeSpeed=220.0)
            val b=craft(BodyKind.Rocket,if (crossing) Vec2(0.0,-450.0) else Vec2(450.0,0.0),0.0).copy(id=3,
                velocity=if (crossing) Vec2(0.0,220.0) else Vec2(-220.0,0.0),
                waypoints=listOf(if (crossing) Vec2(0.0,1400.0) else Vec2(-1400.0,0.0)),routeSpeed=220.0)
            var bodies=listOf(a,b); var detoured=false
            repeat(360) {
                val routed=applyFlightControls(bodies,null,1.0/60)
                val prepared=prepareCombat(routed,ArcadeCombat(),1.0/60)
                detoured=detoured || prepared.bodies.any { it.routeAvoiding }
                bodies=advanceWaypoints(prepared.bodies,SimulationEngine.stepArcade(prepared.bodies,1.0/60).bodies)
                assertEquals("Routes collided crossing=$crossing tick=$it",2,bodies.size)
            }
            assertTrue(detoured)
            assertTrue(bodies.all { it.routePath != null && it.waypoints.isNotEmpty() })
            assertTrue(bodies.first().position.x > 400.0)
            assertTrue(bodies.all { !it.routeAvoiding })
        }
    }
}
