package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class TrailGeometryTest {
    @Test fun sharpBounceHasContinuousRoundedJoinsWithoutOvershoot() {
        val points=listOf(Offset(0f,0f),Offset(50f,0f),Offset(50f,50f),Offset(20f,50f))
        val curves=smoothTrail(points)
        assertEquals(points.first(),curves.first().start); assertEquals(points.last(),curves.last().end)
        curves.zipWithNext().forEach { (a,b) -> assertEquals(a.end,b.start) }
        for (curve in curves) for (step in 0..20) {
            val t=step/20f; val c=curve.control
            val p=if (c == null) curve.start*(1-t)+curve.end*t else
                curve.start*((1-t)*(1-t))+c*(2*t*(1-t))+curve.end*(t*t)
            assertTrue(p.x in 0f..50f && p.y in 0f..50f)
        }
        val a=curves[0]; val b=curves[1]
        assertEquals(a.end-a.control!!,b.control!!-b.start)
    }
    @Test fun tailHasBoundedArcLengthAndNeverRunsAheadOfAnInterpolatedHead() {
        val trail=(0..20).map { Vec2(it*10.0,0.0) }
        val body=CelestialBody(1,trail.last(),Vec2(100.0,0.0),70.0,8f,Color.Cyan,trail=trail)
        val viewport=IntSize(400,400)
        val points=trailScreenPoints(body,viewport,SpaceCamera(Vec2.Zero,1f),Vec2(155.0,0.0),60f,2f)
        assertEquals(Offset(355f,200f),points.last())
        assertEquals(Offset(295f,200f),points.first())
        assertTrue(points.all { it.x <= 355f })
        assertEquals(60.0,points.zipWithNext().sumOf { (a,b) -> (b-a).getDistance().toDouble() },1e-6)
        assertTrue(trailScreenPoints(body.copy(trail=listOf(body.position)),viewport,SpaceCamera(),body.position,60f,2f).isEmpty())
    }
    @Test fun denseWorldHasAStableBudgetWithSelectedAndControlledTrailsTakingPriority() {
        val bodies=List(500) { i -> CelestialBody(i.toLong(),Vec2((i%20-10)*45.0,(i/20-12)*35.0),Vec2(100.0,0.0),70.0,4f,Color.Cyan,
            trail=listOf(Vec2(-30.0,0.0),Vec2.Zero),isDebris=i%7 == 0) }
        val viewport=IntSize(1000,1000); val camera=SpaceCamera()
        val visible=visibleTrailBodies(bodies,viewport,camera,0.0,1f,0,1)
        assertEquals(48,visible.size); assertEquals(listOf(0L,1L),visible.take(2).map { it.id })
        assertTrue(visible.drop(2).none { it.isDebris })
        val cluster=bodies.map { it.copy(position=Vec2.Zero) }
        assertTrue(visibleTrailBodies(cluster,viewport,camera,0.0,1f,null,null).size <= 2)
        assertEquals(visible.map { it.id },visibleTrailBodies(bodies.reversed(),viewport,camera,0.0,1f,0,1).map { it.id })
        assertTrue(visibleTrailBodies(bodies,viewport,SpaceCamera(Vec2(10000.0,0.0),1f),0.0,1f,null,null).isEmpty())
    }
    @Test fun lightScenesKeepFullHistoryAndDenseOrBusyScenesGraduallyShortenIt() {
        assertTrue(adaptiveTrailLength(17).isInfinite())
        val lengths=listOf(80,160,300,500).map { adaptiveTrailLength(it) }
        assertTrue(lengths.zipWithNext().all { (a,b) -> a > b })
        assertEquals(48f,lengths.last(),0f)
        assertTrue(adaptiveTrailLength(160,4f) < adaptiveTrailLength(160,1f))
        assertTrue(adaptiveTrailLength(17,4f).isFinite())
        assertTrue(adaptiveTrailLength(500,4f,true) >= 96f)
        val trail=(0..41).map { Vec2(it*10.0,0.0) }
        val body=CelestialBody(1,trail.last(),Vec2.Zero,70.0,8f,Color.Cyan,trail=trail)
        val points=trailScreenPoints(body,IntSize(1000,1000),SpaceCamera(),body.position,adaptiveTrailLength(2),2f)
        assertEquals(410.0,points.zipWithNext().sumOf { (a,b) -> (b-a).getDistance().toDouble() },1e-6)
    }
}
