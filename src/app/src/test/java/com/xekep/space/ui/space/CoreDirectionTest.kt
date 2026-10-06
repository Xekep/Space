package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import org.junit.Assert.*
import org.junit.Test

class CoreDirectionTest {
    private val viewport = IntSize(1000, 800)
    private val bounds = Rect(20f, 40f, 980f, 760f)

    @Test fun pointsTowardTheCoreAtAllEdgesAndCornersInsideSafeBounds() {
        for (point in listOf(Offset(-500f,400f), Offset(1500f,400f), Offset(500f,-500f), Offset(500f,1300f),
            Offset(-500f,-500f), Offset(1500f,1300f))) {
            val marker = coreDirectionMarker(point,20f,viewport,bounds)!!
            assertTrue(marker.position.x in bounds.left..bounds.right)
            assertTrue(marker.position.y in bounds.top..bounds.bottom)
            assertTrue(marker.position.x == bounds.left || marker.position.x == bounds.right ||
                marker.position.y == bounds.top || marker.position.y == bounds.bottom)
            val delta = point - Offset(500f,400f)
            assertEquals(1f,marker.direction.getDistance(),1e-6f)
            assertEquals(0f,delta.x*marker.direction.y-delta.y*marker.direction.x,1e-3f)
            assertTrue(delta.x*marker.direction.x+delta.y*marker.direction.y > 0f)
        }
    }

    @Test fun staysHiddenUntilTheCoreDiskIsCompletelyOutsideAndHandlesCornerVisibility() {
        for (point in listOf(Offset(500f,400f), Offset(500f,10f), Offset(-19f,400f), Offset(1019f,400f), Offset(-10f,-10f)))
            assertNull(coreDirectionMarker(point,20f,viewport,bounds))
        for (point in listOf(Offset(-21f,400f), Offset(1021f,400f), Offset(-15f,-15f)))
            assertNotNull(coreDirectionMarker(point,20f,viewport,bounds))
        assertNull(coreDirectionMarker(Offset(Float.NaN,0f),20f,viewport,bounds))
        assertNull(coreDirectionMarker(Offset(-100f,0f),20f,IntSize.Zero,bounds))
    }

    @Test fun directionFollowsCameraRotationAndZoom() {
        for (zoom in listOf(.15f,1f,6f)) for (rotation in listOf(0.0,.7,1.8,-2.4)) {
            val point = worldToScreen(Vec2(20000.0,-15000.0),viewport,Vec2(300.0,-100.0),zoom,rotation)
            val marker = coreDirectionMarker(point,20f,viewport,bounds)!!
            val expected = (point-Offset(500f,400f)).let { it/it.getDistance() }
            assertEquals(expected.x,marker.direction.x,1e-6f)
            assertEquals(expected.y,marker.direction.y,1e-6f)
        }
    }
}
