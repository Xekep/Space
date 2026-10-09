package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Repeatable CPU/allocation probes; timings are diagnostics, not phone frame rates. */
class ArcadePerformanceProbeTest {
    private fun scene(enemies: Int): List<CelestialBody> = buildList {
        add(CelestialBody(1,Vec2.Zero,Vec2.Zero,7800.0,28f,Color.Yellow,BodyKind.Core))
        repeat(3) { i -> add(CelestialBody(2L+i,Vec2(-400.0,i*85.0),Vec2(220.0,0.0),24.0,8f,Color.Cyan,BodyKind.Ship,heading=Vec2(1.0,0.0))) }
        repeat(enemies) { i -> add(CelestialBody(20L+i,Vec2(200.0+i*85,100.0-i*30),Vec2(-80.0,-20.0),200.0,12f,Color.Red,BodyKind.Meteor)) }
    }
    @Test fun profileFirstShipsAndEarlyVolley() {
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val allocatedBytes=Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes",java.lang.Long.TYPE)
        fun bytes()=allocatedBytes.invoke(bean,Thread.currentThread().id) as Long
        for (enemies in listOf(0,3,12)) {
            val bodies=scene(enemies)
            val combat=ArcadeCombat(craft=bodies.filter { it.isVehicle }.associate { it.id to CraftStatus(cooldown=0.0) })
            repeat(1000) { prepareCombat(bodies,combat,1.0/60) }
            val times=LongArray(1000)
            val bytes=bytes()
            repeat(times.size) { val start=System.nanoTime(); val result=prepareCombat(bodies,combat,1.0/60); times[it]=System.nanoTime()-start; assertEquals(bodies.size,result.bodies.size) }
            val allocated=bytes()-bytes
            times.sort()
            println("SHIP_CPU,enemies=$enemies,medianUs=${times[500]/1000.0},p95Us=${times[950]/1000.0},bytesPerStep=${allocated/times.size}")
            val nav=ArcadeNavigation(bodies)
            for (body in bodies.filter { it.isVehicle }) {
                val wanted=nav.avoid(body,Vec2(220.0,0.0),140.0)!!
                val expected=if (body.id == 4L) Vec2(190.52558883257652,110.0) else Vec2(0.0,220.0)
                assertEquals(expected.x,wanted.x,1e-8); assertEquals(expected.y,wanted.y,1e-8)
            }
        }
    }
}
