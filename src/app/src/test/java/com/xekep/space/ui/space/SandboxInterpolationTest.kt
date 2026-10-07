package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class SandboxInterpolationTest {
    private fun body(x: Double)=CelestialBody(1,Vec2(x,0.0),Vec2.Zero,100.0,10f,Color.Cyan)
    @Test fun drawsBetweenPublicationsWithoutChangingPhysicsAndRebasesUnevenUpdates() {
        val display=SandboxInterpolation()
        val first=body(0.0); val second=body(20.0); val third=body(40.0)
        val initial=listOf(first); val next=listOf(second); val latest=listOf(third)
        display.begin(initial,1,0,true)
        display.begin(next,1,40_000_000,true)
        assertEquals(0.0,display.position(second).x,1e-9)
        display.begin(next,1,60_000_000,true)
        assertEquals(10.0,display.position(second).x,1e-9)
        assertEquals(20.0,second.position.x,1e-9)
        display.begin(latest,1,60_000_000,true)
        assertEquals(10.0,display.position(third).x,1e-9)
        display.begin(latest,1,80_000_000,true)
        assertEquals(40.0,display.position(third).x,1e-9)
        // Dense accelerated worlds may publish only once a second. Keep moving throughout
        // that interval, rather than reaching the next pose early and freezing between jobs.
        val slow=SandboxInterpolation()
        slow.begin(initial,1,0,true)
        slow.begin(next,1,1_000_000_000,true)
        slow.begin(next,1,1_750_000_000,true)
        assertEquals(15.0,slow.position(second).x,1e-9)
        slow.begin(next,1,1_950_000_000,true)
        assertEquals(19.0,slow.position(second).x,1e-9)
    }
    @Test fun pausesNewWorldsMergesAndNewBodiesSnapWithoutGhosts() {
        val display=SandboxInterpolation(); val first=body(0.0); val next=body(100.0)
        display.begin(listOf(first),1,0,true)
        val changed=listOf(next)
        display.begin(changed,1,40_000_000,true)
        display.begin(changed,1,50_000_000,false)
        assertEquals(next.position,display.position(next))
        display.begin(listOf(first),2,60_000_000,true)
        assertEquals(first.position,display.position(first))
        val merged=next.copy(mass=200.0)
        val newBody=next.copy(id=2)
        display.begin(listOf(merged,newBody),2,80_000_000,true)
        assertEquals(merged.position,display.position(merged))
        assertEquals(newBody.position,display.position(newBody))
    }
}
