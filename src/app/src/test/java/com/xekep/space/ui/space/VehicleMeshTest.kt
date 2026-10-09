package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class VehicleMeshTest {
    private fun body(kind: BodyKind,mass: Double,guardian: Boolean=false)=CelestialBody(1,Vec2.Zero,Vec2.Zero,mass,8f,Color.Cyan,kind,shipClass=if (guardian) ShipClass.Guardian else ShipClass.Interceptor)
    @Test fun modelsHaveSolidDepthFiniteOutwardFacesAndRealRearNozzles() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (heavy in listOf(false,true)) for (guardian in listOf(false,true)) {
            val craft=body(kind,if (heavy) heavyHullMass(kind) else if (kind == BodyKind.Ship) 24.0 else 12.0,guardian)
            val mesh=vehicleMesh(craft)
            assertSame(mesh,vehicleMesh(craft)); assertTrue(mesh.faces.size in 20..180)
            assertTrue(mesh.vertices.maxOf { it.z }-mesh.vertices.minOf { it.z } > .3)
            assertTrue(mesh.nozzles.all { it.y > .7f }); assertTrue(mesh.nozzles.isNotEmpty())
            for (face in mesh.faces) {
                assertTrue(face.indices.size >= 3); assertEquals(1f,face.normal.dot(face.normal),.00001f)
                assertTrue(face.indices.all { it in mesh.vertices.indices })
            }
            for (pitch in listOf(-1.0,0.0,1.0)) for (roll in listOf(-1.0,0.0,1.0)) {
                val angled=craft.copy(pitch=pitch,roll=roll)
                val nozzle=vehicleNozzlePoint(angled,40f); val aft=vehicleNozzlePoint(angled,40f,.25f)
                assertTrue(nozzle.x.isFinite()); assertTrue(nozzle.y.isFinite()); assertTrue((aft-nozzle).getDistance() > 1f)
                for (v in mesh.vertices) {
                    val projected=vehicleHullPoint(Offset(v.x*40,v.y*40),v.z*40,pitch,roll,40f)
                    assertTrue(projected.x.isFinite()); assertTrue(projected.y.isFinite())
                }
            }
        }
    }
    @Test fun altitudeParallaxIsSignedBoundedAndIndependentOfZoomAndCameraRotation() {
        val viewport=IntSize(1080,1920)
        for (height in listOf(-100.0,0.0,100.0)) for (zoom in listOf(.001f,1f,6f)) for (rotation in listOf(-2.0,0.0,2.0)) {
            val craft=body(BodyKind.Ship,24.0).copy(flightHeight=height)
            val point=flightRenderPosition(craft,Vec2.Zero,viewport,zoom,rotation)
            val screen=rotateVector(point*zoom.toDouble(),rotation)
            assertEquals(0.0,screen.x,.00001)
            assertTrue(kotlin.math.abs(screen.y) <= 1080*.055)
            assertTrue(height == 0.0 || height*screen.y < 0)
        }
        assertEquals(Vec2.Zero,flightRenderPosition(body(BodyKind.Star,1000.0).copy(flightHeight=100.0),Vec2.Zero,viewport,1f,0.0))
    }
}
