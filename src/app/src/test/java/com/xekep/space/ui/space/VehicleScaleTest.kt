package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class VehicleScaleTest {
    @Test fun disablingLargeIconsMakesShipsAndRocketsShrinkWithWorldZoomAtBothScales() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (radius in listOf(8f,.00001f)) {
            val craft=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,radius,Color.Cyan,kind)
            var previous=Float.MAX_VALUE
            for (zoom in listOf(250f,6f,1f,.15f,.005f)) {
                val size=bodyScreenRadius(craft,zoom,3f,false)
                assertTrue(size < previous)
                assertEquals(radius*zoom,size,1e-8f)
                assertTrue(bodyScreenRadius(craft,zoom,3f,true) >= 42f)
                previous=size
            }
        }
    }
}
