package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class VehicleFuelTest {
    private fun craft(kind:BodyKind) = CelestialBody(1,Vec2.Zero,Vec2(0.0,-100.0),if (kind == BodyKind.Rocket) 12.0 else 24.0,2f,Color.Cyan,kind)

    @Test fun freeShipsAndRocketsAlignWithGravityDeflectedVelocityWhilePilotsAndRoutesKeepTheirHeading() {
        val star=CelestialBody(2,Vec2(500.0,0.0),Vec2.Zero,6000.0,5f,Color.Yellow,BodyKind.Star)
        val free=listOf(BodyKind.Ship,BodyKind.Rocket).map { kind ->
            val body=craft(kind)
            SimulationEngine.stepSandbox(listOf(body,star),.5,0.0,false).bodies.first()
        }
        assertTrue(free.all { it.heading.x > .01 && it.velocity.x > 0 })
        val coasting=craft(BodyKind.Rocket).copy(fuelRemaining=0.0)
        val drift=SimulationEngine.stepSandbox(listOf(coasting,star),.5,0.0,false).bodies.first()
        assertEquals(coasting.heading,drift.heading); assertTrue(drift.velocity.x > 0)
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val pilot=SimulationEngine.stepSandbox(listOf(craft(kind),star),.5,0.0,false,1).bodies.first()
            assertEquals(craft(kind).heading,pilot.heading)
            val routed=craft(kind).copy(waypoints=listOf(Vec2(100.0,100.0)))
            assertEquals(routed.heading,SimulationEngine.stepSandbox(listOf(routed,star),.5,0.0,false).bodies.first().heading)
        }
    }
    @Test fun bothModesExpireRoutesAndPilotsOnceWithAnExplosionAfterAModerateReserve() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (arcade in listOf(false,true)) {
            val body=craft(kind).copy(waypoints=listOf(Vec2(10000.0,0.0),Vec2.Zero))
            val capacity=vehicleFuelCapacity(kind)
            val remaining=if (arcade) SimulationEngine.stepArcade(listOf(body),capacity-1.0,1)
                else SimulationEngine.stepSandbox(listOf(body),capacity-1.0,0.0,false,1)
            assertEquals(1.0,remaining.bodies.single().fuelRemaining,1e-6)
            val emptyTank=if (arcade) SimulationEngine.stepArcade(remaining.bodies,1.1,1)
                else SimulationEngine.stepSandbox(remaining.bodies,1.1,0.0,false,1)
            if (kind == BodyKind.Rocket) {
                assertEquals(0.0,emptyTank.bodies.single().fuelRemaining,0.0)
                assertEquals(ROCKET_DRIFT_SECONDS-.1,emptyTank.bodies.single().driftRemaining,1e-6)
                assertTrue(emptyTank.collisions.isEmpty())
            }
            val end=if (kind != BodyKind.Rocket) emptyTank else if (arcade)
                SimulationEngine.stepArcade(emptyTank.bodies,ROCKET_DRIFT_SECONDS,1)
                else SimulationEngine.stepSandbox(emptyTank.bodies,ROCKET_DRIFT_SECONDS,0.0,false,1)
            assertTrue(end.bodies.isEmpty()); assertTrue(end.collisions.single().vehicleExplosion)
            assertTrue(SimulationEngine.stepSandbox(end.bodies,.1,0.0).collisions.isEmpty())
        }
    }

    @Test fun emptyRocketCannotThrustOrNavigateAndExplodesOnlyAfterItsDrift() {
        val initial=craft(BodyKind.Rocket).copy(fuelRemaining=0.0,waypoints=listOf(Vec2(500.0,0.0)),pilotThrottle=1.0)
        val routed=applyFlightControls(listOf(initial),ManualFlightControl(1,1.0,1.0),1.0).single()
        assertTrue(routed.waypoints.isEmpty()); assertNull(routed.routePath)
        assertEquals(initial.velocity,routed.velocity); assertEquals(initial.heading,routed.heading)
        for (arcade in listOf(false,true)) {
            val coast=if (arcade) SimulationEngine.stepArcade(listOf(routed),ROCKET_DRIFT_SECONDS-.1,1)
                else SimulationEngine.stepSandbox(listOf(routed),ROCKET_DRIFT_SECONDS-.1,0.0,false,1)
            assertEquals(initial.velocity,coast.bodies.single().velocity)
            assertTrue(coast.collisions.isEmpty())
            val end=if (arcade) SimulationEngine.stepArcade(coast.bodies,.2,1)
                else SimulationEngine.stepSandbox(coast.bodies,.2,0.0,false,1)
            assertTrue(end.bodies.isEmpty()); assertEquals(1,end.collisions.size)
        }
    }

    @Test fun aFullRocketHasSustainedFuelAndBoundedSpeed() {
        val next=SimulationEngine.stepSandbox(listOf(craft(BodyKind.Rocket)),60.0,0.0).bodies.single()
        assertEquals(60.0,next.fuelRemaining,1e-6)
        assertEquals(ROCKET_DRIFT_SECONDS,next.driftRemaining,1e-6)
        assertEquals(900.0,next.velocity.magnitude(),1e-6)
    }
    @Test fun highThrustBurnsOnlyFiftyPercentFasterAndTimePartitionsAgree() {
        val body=craft(BodyKind.Ship).copy(pilotThrottle=1.0)
        val baseline=SimulationEngine.stepSandbox(listOf(body),30.0,0.0).bodies.single()
        val powered=SimulationEngine.stepSandbox(listOf(body),30.0,0.0,false,1).bodies.single()
        assertEquals(150.0,baseline.fuelRemaining,1e-6); assertEquals(135.0,powered.fuelRemaining,1e-6)
        var partitioned=body
        repeat(300) { partitioned=SimulationEngine.stepSandbox(listOf(partitioned),.1,0.0,false,1).bodies.single() }
        assertEquals(powered.fuelRemaining,partitioned.fuelRemaining,1e-6)
    }
}
