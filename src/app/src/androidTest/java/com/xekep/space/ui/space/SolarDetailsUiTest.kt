package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SolarDetailsUiTest {
    @get:Rule val compose=createComposeRule()

    private fun screenshot(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun ringParticlesAndSatellitePanelsAreVisibleOnPlanetCloseupsAndPinchKeepsFollowing() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { game.focusSolar(SolarBody.Sun); game.clearSelection() }
        compose.mainClock.advanceTimeByFrame()
        screenshot("solar-overview.png")
        compose.runOnIdle { game.focusSolar(SolarBody.Jupiter); game.transformCamera(Offset.Zero,Offset.Zero,6f) }
        compose.mainClock.advanceTimeByFrame()
        screenshot("solar-jupiter-dust.png")
        compose.runOnIdle { game.focusSolar(SolarBody.Saturn) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(120,visibleSolarBodies(game.bodies,game.camera.zoom,2.75f).count { it.orbitalDetail == OrbitalDetail.RingGrain })
        screenshot("solar-saturn-rings.png")
        compose.runOnIdle { game.focusSolar(SolarBody.Earth); game.transformCamera(Offset.Zero,Offset.Zero,3f) }
        compose.mainClock.advanceTimeByFrame()
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        compose.onNodeWithTag("space-scene").performTouchInput {
            val a=center+Offset(-100f,300f); val b=center+Offset(100f,300f)
            down(0,a); down(1,b)
            updatePointerTo(0,a-Offset(50f,0f)); updatePointerTo(1,b+Offset(50f,0f)); move()
            up(1); up(0)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(earth.id,game.cameraTarget!!.id)
            assertEquals(earth.position,game.camera.center)
            assertEquals(SolarSystem.BODY_COUNT,game.bodies.size)
            assertTrue(visibleSolarBodies(game.bodies,game.camera.zoom,2.75f).any { it.orbitalDetail == OrbitalDetail.ArtificialSatellite })
        }
        screenshot("solar-earth-satellites.png")
    }

    @Test fun aHolePlacedByTouchNearEarthCapturesItWithMergingOffAndProducesAnEffect() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle { game.focusSolar(SolarBody.Earth); game.chooseSpawnKind(BodyKind.BlackHole) }
        compose.mainClock.advanceTimeByFrame()
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        // Zoom out enough to place the hole at a two-world-unit separation through the canvas.
        compose.runOnIdle { game.transformCamera(Offset(game.viewport.width/2f,game.viewport.height/2f),Offset.Zero,.15f) }
        compose.mainClock.advanceTimeByFrame()
        val point=worldToScreen(earth.position+Vec2(2.0,0.0),game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(BodyKind.BlackHole,game.bodies.last().kind)
            assertFalse(game.sandbox!!.collisionsEnabled)
            game.toggleSandboxPause(); game.update(1.0/60)
            assertFalse(game.bodies.any { it.id == earth.id })
            assertTrue(game.explosions.any { it.collapseRadius > 0 })
            game.toggleSandboxPause()
        }
        compose.mainClock.advanceTimeByFrame()
        screenshot("solar-black-hole-capture.png")
    }
}
