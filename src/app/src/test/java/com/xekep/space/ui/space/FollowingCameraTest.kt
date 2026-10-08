package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FollowingCameraTest {
    @Test fun offCentreZoomAndPanKeepTheFollowedBodyCentredAtBothZoomLimits() {
        for (mode in AppMode.entries) {
            val game=SpaceGameState().apply {
                resize(IntSize(1080,1920))
                if (mode == AppMode.Sandbox) startSandbox() else startArcade()
            }
            val body=game.bodies.first()
            game.selectBody(body.id); game.followSelected()
            for (factor in listOf(1.7f,100000f,.000001f,2f)) {
                game.transformCamera(Offset(100f,300f),Offset(170f,-90f),factor)
                assertTrue(game.following)
                assertEquals(body.position,game.camera.center)
                assertEquals(body.id,game.cameraTarget!!.id)
            }
            repeat(60) { game.update(1.0/60) }
            assertEquals(game.selectedBody!!.position,game.camera.center)
        }
    }

    @Test fun orbitPlacementAndLaunchKeepFollowingTheOriginalParent() {
        for (wasFollowing in listOf(true,false)) {
            val game=SpaceGameState().apply { resize(IntSize(1080,1920)); startSandbox() }
            val earth=game.bodies.first { it.solar == SolarBody.Earth }
            game.selectBody(earth.id)
            if (wasFollowing) game.followSelected()
            game.prepareOrbit()
            game.transformCamera(Offset(130f,410f),Offset(90f,45f),.7f)
            assertEquals(earth.position,game.camera.center)
            assertEquals(earth.id,game.cameraTarget!!.id)
            val point=earth.position+Vec2(.35,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(18,game.bodies.size)
            assertEquals(wasFollowing,game.following)
            assertEquals(if (wasFollowing) earth.id else null,game.selectedBodyId)
            val zoom=game.camera.zoom
            repeat(120) { game.update(1.0/60) }
            if (wasFollowing) assertEquals(game.selectedBody!!.position,game.camera.center)
            else assertEquals(earth.position,game.camera.center)
            assertEquals(zoom,game.camera.zoom,0f)
        }
    }

    @Test fun followingSurvivesSatelliteCreationAndBackgroundPhysicsPublication() = runBlocking {
        val game=SpaceGameState().apply { resize(IntSize(1080,1920)); startSandbox() }
        val marker=game.bodies.first().copy(solar=null,mass=1e-8,radius=.00001f)
        val scene=game.bodies+List(80) { marker.copy(id=10000L+it,position=Vec2(100000.0+it*100,100000.0)) }
        game.loadSandbox(SandboxSnapshot(scene,Vec2.Zero,1f,0.0,0))
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        game.selectBody(earth.id); game.followSelected(); game.prepareOrbit()
        val point=earth.position+Vec2(.35,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        repeat(30) { game.updateSandboxAsync(1.0/60) }
        assertTrue(game.following)
        assertNotEquals(earth.position,game.selectedBody!!.position)
        assertEquals(game.selectedBody!!.position,game.camera.center)
    }
}
