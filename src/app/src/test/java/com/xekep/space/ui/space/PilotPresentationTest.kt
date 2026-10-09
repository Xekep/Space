package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class PilotPresentationTest {
    private fun craft(kind: BodyKind=BodyKind.Ship)=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,8f,Color.Cyan,kind)
    @Test fun oppositePitchReversesNearNoseAndTailPerspective() {
        val nose=Offset(10f,-20f); val tail=Offset(10f,20f)
        val up=vehiclePitchMatrix(.7,20f); val down=vehiclePitchMatrix(-.7,20f)
        assertTrue(up.map(nose).x > down.map(nose).x)
        assertTrue(up.map(tail).x < down.map(tail).x)
        assertEquals(up.map(nose).x,down.map(tail).x,1e-5f)
        assertEquals(nose,vehiclePitchMatrix(0.0,20f).map(nose))
    }
    @Test fun pilotSymbolsHaveIdenticalRelativeZoomResponseAcrossWorldScales() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val ordinary=craft(kind); val solar=ordinary.copy(physicalScale=true,radius=.000002f)
            for (zoom in listOf(.1f,1f,2f,6f)) for (large in listOf(false,true)) {
                assertEquals(pilotScreenRadius(ordinary,zoom,3f,large),pilotScreenRadius(solar,zoom,3f,large),0f)
            }
            assertEquals(2*pilotScreenRadius(solar,1f,3f,true),pilotScreenRadius(solar,2f,3f,true),0f)
            assertTrue(bodyScreenRadius(solar,1000f,3f,false) > bodyScreenRadius(solar,100f,3f,false))
            assertTrue(bodyScreenRadius(solar,1000f,3f,true) > bodyScreenRadius(solar,100f,3f,true))
            val up=solar.copy(flightHeight=100.0); val down=solar.copy(flightHeight=-100.0)
            assertTrue(pilotScreenRadius(up,1f,3f,true) > pilotScreenRadius(solar,1f,3f,true)*1.5f)
            assertTrue(pilotScreenRadius(down,1f,3f,true) < pilotScreenRadius(solar,1f,3f,true)*.5f)
        }
    }
    @Test fun ringsAreContinuousBandsWithFaintJupiterDustAndSaturnGapAndDisruption() {
        val jupiter=planetRingSystem(SolarBody.Jupiter)!!; val saturn=planetRingSystem(SolarBody.Saturn)!!
        assertTrue(jupiter.bands.maxOf { it.opacity } < saturn.bands.maxOf { it.opacity })
        assertTrue(saturn.bands.none { 120000.0 in it.innerKm..it.outerKm })
        val bodies=SimulationEngine.sandboxPreset().bodies; val parent=bodies.first { it.solar == SolarBody.Saturn }
        assertEquals(1f,ringCoverage(parent,bodies),0f)
        assertEquals(0f,ringCoverage(parent,bodies.filter { it.orbitalDetail != OrbitalDetail.RingGrain }),0f)
        assertEquals(.5f,ringCoverage(parent,bodies.filter { it.orbitalDetail != OrbitalDetail.RingGrain }+
            bodies.filter { it.orbitalDetail == OrbitalDetail.RingGrain }.take(60)),0f)
    }
    @Test fun selectingPilotUsesTheVisibleZoomedHullInsteadOfItsTinyPhysicsRadius() {
        val game=SpaceGameState().apply {
            resize(androidx.compose.ui.unit.IntSize(1080,2340)); startSandbox(SandboxPresetKind.Empty)
            setMotionControlEnabled(true); chooseSpawnKind(BodyKind.Ship)
            launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0)
            transformCamera(Offset(540f,1170f),Offset.Zero,2f)
        }
        val body=game.bodies.single()
        val extent=pilotScreenRadius(body,game.pilotVisualZoom,game.density,game.largeVehicleIcons)
        assertEquals(body.id,game.bodyAt(body.position+Vec2(extent*1.2/game.camera.zoom,0.0))!!.id)
    }

    @Test fun signedBankTiltsAndForeshortensOppositeSidesWithoutChangingPhysicsRadius() {
        val point=Offset(15f,0f)
        val right=vehiclePitchMatrix(.3,20f,.8).map(point)
        val left=vehiclePitchMatrix(.3,20f,-.8).map(point)
        assertTrue(right.x < point.x); assertNotEquals(right.x,left.x)
        val nose=Offset(0f,-20f)
        assertEquals(0f,vehiclePitchMatrix(0.0,20f,.8).map(nose).x,0f)
        assertEquals(0f,vehiclePitchMatrix(0.0,20f,-.8).map(nose).x,0f)
        assertTrue(right.y > 0); assertTrue(left.y < 0)
    }

    @Test fun hullPlaneAndSolidVerticesShareProjectionAndHeightDoesNotDependOnAttitude() {
        for (pitch in listOf(-1.0,0.0,1.0)) for (roll in listOf(-1.0,0.0,1.0)) {
            for (point in listOf(Offset(-15f,-25f),Offset(15f,18f),Offset.Zero)) {
                val plane=vehiclePitchMatrix(pitch,20f,roll).map(point)
                val solid=vehicleHullPoint(point,0f,pitch,roll,20f)
                assertEquals(plane.x,solid.x,.00001f); assertEquals(plane.y,solid.y,.00001f)
                val underside=vehicleHullPoint(point,-4f,pitch,roll,20f)
                assertTrue(underside.x.isFinite()); assertTrue(underside.y.isFinite())
                if (pitch != 0.0 || roll != 0.0) assertNotEquals(solid,underside)
            }
            assertEquals(craft().flightVisualScale(),craft().copy(pitch=pitch,roll=roll).flightVisualScale(),0f)
        }
        val up=vehicleHullPoint(Offset.Zero,-4f,.7,0.0,20f)
        val down=vehicleHullPoint(Offset.Zero,-4f,-.7,0.0,20f)
        assertEquals(up.x,down.x,0f); assertEquals(-up.y,down.y,.00001f)
        assertTrue(up.y < 0)
    }

}
