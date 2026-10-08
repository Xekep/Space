package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class FlightPathTest {
    private val points=listOf(Vec2(150.0,0.0),Vec2(150.0,160.0),Vec2(300.0,160.0))

    @Test fun loopHasContinuousPositionAndHeadingAcrossTheSeam() {
        for (path in listOf(FlightPath.through(Vec2.Zero,points,true)!!,
            FlightPath.through(Vec2.Zero,points+points.first())!!)) {
            assertTrue(path.isLoop)
            val a=path.sample(path.length-.001)
            val b=path.sample(path.length+.001)
            assertTrue((a.position-b.position).magnitude() < .003)
            assertTrue((a.direction-b.direction).magnitude() < .001)
            val lap=path.length-path.loopStartDistance
            assertTrue((path.sample(path.loopStartDistance+lap*10+.1).position-
                path.sample(path.loopStartDistance+.1).position).magnitude() < 1e-8)
        }
    }

    @Test fun returningNearLaunchOrFirstWaypointClosesTheCorrectLoop() {
        val launch=FlightPath.through(Vec2.Zero,points+Vec2(1.0,-1.0),closureTolerance=3.0)!!
        assertEquals(0,launch.loopStartIndex)
        assertEquals(points,launch.remainingPoints(launch.length*4))
        val first=FlightPath.through(Vec2.Zero,points+Vec2(151.0,1.0),closureTolerance=3.0)!!
        assertEquals(1,first.loopStartIndex)
        assertTrue(first.loopStartDistance > 0)
        assertFalse(FlightPath.through(Vec2.Zero,points+Vec2(450.0,450.0),closureTolerance=3.0)!!.isLoop)
    }

    @Test fun shipsAndRocketsRepeatMultipleLapsInBothModesAndStillExhaustTheirFuel() {
        val path=FlightPath.through(Vec2.Zero,points,true)!!
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (arcade in listOf(false,true)) {
            val body=CelestialBody(1,Vec2.Zero,path.sample(0.0).direction*100.0,if (kind == BodyKind.Rocket) 12.0 else 24.0,1f,Color.Cyan,kind,
                routePath=path,waypoints=path.remainingPoints(0.0),routeSpeed=100.0)
            val time=path.length*3/100.0
            val result=if (arcade) SimulationEngine.stepArcade(listOf(body),time)
                else SimulationEngine.stepSandbox(listOf(body),time,0.0,false)
            val looped=advanceWaypoints(listOf(body),result.bodies).single()
            assertSame(path,looped.routePath); assertFalse(looped.waypoints.isEmpty())
            assertTrue(looped.position.magnitude() < 1e-6)
            assertEquals(100.0,looped.velocity.magnitude(),1e-6)
            assertEquals(vehicleFuelCapacity(kind)-time,looped.fuelRemaining,1e-6)
            val finish=looped.fuelRemaining+(if (kind == BodyKind.Rocket) ROCKET_DRIFT_SECONDS else 0.0)+.1
            val expired=if (arcade) SimulationEngine.stepArcade(listOf(looped),finish)
                else SimulationEngine.stepSandbox(listOf(looped),finish,0.0,false)
            assertTrue(expired.bodies.isEmpty()); assertTrue(expired.collisions.single().vehicleExplosion)
        }
    }

    @Test fun curveInterpolatesEveryKnotWithContinuousDirection() {
        val path=FlightPath.through(Vec2.Zero,points)!!
        points.forEachIndexed { i,point ->
            val distance=path.waypointDistances[i]
            assertTrue((path.sample(distance).position-point).magnitude() < 1e-8)
            val left=path.sample(distance-.001).direction
            val right=path.sample(distance+.001).direction
            assertTrue((left-right).magnitude() < .001)
        }
        assertTrue(path.drawingPoints(0.0).any { it.y < -1 })
    }

    @Test fun shipsAndRocketsKeepSpeedThroughCornersAndPassTheExactPointsUnderGravity() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val body=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,1f,Color.Cyan,kind,waypoints=points,routeSpeed=100.0)
            var craft=applyFlightControls(listOf(body),null,.01).single()
            val path=craft.routePath!!
            val star=CelestialBody(2,Vec2(600.0,600.0),Vec2.Zero,6000.0,2f,Color.Yellow,BodyKind.Star)
            var traveled=0.0
            for ((index,distance) in path.waypointDistances.withIndex()) {
                val dt=(distance-traveled)/100.0
                val next=SimulationEngine.stepSandbox(listOf(craft,star),dt,0.0,false).bodies.first()
                assertTrue((next.position-points[index]).magnitude() < 1e-6)
                assertEquals(100.0,next.velocity.magnitude(),1e-6)
                craft=advanceWaypoints(listOf(craft),listOf(next)).single()
                assertEquals(points.drop(index+1),craft.waypoints)
                if (index < points.lastIndex) assertSame(path,craft.routePath)
                traveled=distance
            }
            assertNull(craft.routePath)
        }
    }

    @Test fun equalTimeSamplesHaveUniformTravelWithoutBrakingAtCorners() {
        val path=FlightPath.through(Vec2.Zero,points)!!
        val step=path.length/1000.0
        for (i in 0 until 1000) {
            val travel=(path.sample((i+1)*step).position-path.sample(i*step).position).magnitude()
            assertEquals(step,travel,step*.015)
        }
        assertEquals(200.0,routeCruiseSpeed(200.0),0.0)
    }

    @Test fun duplicatePointsTightTurnsAndClosedRoutesRemainFinite() {
        assertNull(FlightPath.through(Vec2.Zero,listOf(Vec2.Zero,Vec2.Zero)))
        for (route in listOf(listOf(Vec2(1.0,0.0),Vec2(1.0,0.0),Vec2.Zero),
            listOf(Vec2(100.0,0.0),Vec2(100.001,0.0),Vec2(100.0,200.0),Vec2.Zero))) {
            val path=FlightPath.through(Vec2.Zero,route)!!
            repeat(1001) { i ->
                val sample=path.sample(i*path.length/1000)
                assertTrue(sample.position.x.isFinite() && sample.position.y.isFinite())
                assertEquals(1.0,sample.direction.magnitude(),1e-6)
            }
        }
    }

    @Test fun curveStillCollidesWithBodiesBetweenControlPoints() {
        val body=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,1f,Color.Cyan,BodyKind.Ship,waypoints=points,routeSpeed=100.0)
        val routed=applyFlightControls(listOf(body),null,.01).single()
        val obstacle=CelestialBody(2,routed.routePath!!.sample(80.0).position,Vec2.Zero,1e-8,2f,Color.White)
        val result=SimulationEngine.stepSandbox(listOf(routed,obstacle),1.0,0.0,false)
        assertEquals(listOf(2L),result.bodies.map { it.id })
        assertTrue(result.collisions.single().vehicleExplosion)
    }
}
