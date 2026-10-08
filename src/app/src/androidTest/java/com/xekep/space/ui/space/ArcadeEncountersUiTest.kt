package com.xekep.space.ui.space

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ArcadeEncountersUiTest {
    @get:Rule val compose=createComposeRule()
    @Suppress("UNCHECKED_CAST")
    private fun replace(game: SpaceGameState, run: ArcadeSession) {
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        (field.get(game) as MutableState<ArcadeSession?>).value=run
        SimulationEngine.reserveBodyIds(run.bodies+run.pending.map { it.body })
    }
    private fun game(wave: Int): SpaceGameState = SpaceGameState().apply {
        resize(IntSize(1080,2340))
        startArcade()
        val run=arcade!!.copy(elapsed=(wave-1)*28.0+3.0,spawnTimer=1000.0)
        replace(this,advanceArcade(run,.01,Random(17)))
    }
    private fun shot(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        File(instrumentation.targetContext.externalCacheDir,name).outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun orbitingPlanetCanBeSelectedAndWaveCharacterStaysCompact() {
        compose.mainClock.autoAdvance=false
        val game=game(14)
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        val planet=game.bodies.single { it.kind == BodyKind.ArcadePlanet }
        val point=worldToScreen(planet.position,game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(planet.id,game.selectedBodyId); assertEquals(850.0,game.selectedBody!!.mass,0.0) }
        compose.onNodeWithTag("wave-character").assertIsDisplayed()
        compose.onNodeWithTag("body-details").assertIsDisplayed()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertIsDisplayed()
        shot("arcade-orbiting-planet.png")
    }

    @Test fun convoyFocusAndRealPinchTrackTheTransportWithoutUsingPlayerShipSlots() {
        compose.mainClock.autoAdvance=false
        val game=game(15)
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        val id=game.arcade!!.convoy!!.bodyId
        compose.onNodeWithTag("find-convoy").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-count-Ship",useUnmergedTree=true).assertTextEquals("0/6")
        val count=game.bodies.size
        compose.onNodeWithTag("space-scene").performTouchInput {
            val a=center+Offset(-150f,-220f); val b=center+Offset(150f,-220f)
            down(0,a); down(1,b)
            updatePointerTo(0,a+Offset(-50f,25f)); updatePointerTo(1,b+Offset(90f,25f)); move()
            up(1); up(0)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue(game.following); assertEquals(id,game.selectedBodyId)
            assertEquals(game.bodies.first { it.id == id }.position,game.camera.center)
            assertEquals(count,game.bodies.size)
        }
        shot("arcade-convoy-tracking.png")
    }

    @Test fun deliveryOffersABonusAndTheNormalWaveChoiceRemainsAvailable() {
        compose.mainClock.autoAdvance=false
        val game=game(15)
        val core=game.bodies.first { it.kind == BodyKind.Core }
        val id=game.arcade!!.convoy!!.bodyId
        replace(game,game.arcade!!.copy(bodies=game.bodies.map { if (it.id == id) it.copy(position=core.position+Vec2(105.0,0.0)) else it }))
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { game.update(.02) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ConvoyStatus.Delivered,game.arcade!!.convoy!!.status)
            assertTrue(game.arcade!!.upgradeOffer!!.convoyBonus)
            val frozen=game.arcade; game.update(.5); assertEquals(frozen,game.arcade)
        }
        shot("arcade-convoy-reward.png")
        val bonus=game.arcade!!.upgradeOffer!!.choices.first()
        compose.onNodeWithTag("arcade-upgrade-${bonus.name}").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertDoesNotExist()
        compose.runOnIdle { replace(game,game.arcade!!.copy(elapsed=14*28.0+24.0)); game.update(.02) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertIsDisplayed()
        compose.runOnIdle { assertFalse(game.arcade!!.upgradeOffer!!.convoyBonus); assertEquals(1,game.arcade!!.upgrades.values.sum()) }
    }

    @Test fun losingOptionalConvoyDoesNotEndTheRunOrCostCoreHull() {
        compose.mainClock.autoAdvance=false
        val game=game(15)
        val run=game.arcade!!
        val transport=game.bodies.first { it.kind == BodyKind.Convoy }
        val meteor=CelestialBody(SimulationEngine.newBodyId(),transport.position,Vec2.Zero,90.0,8f,Color.Red,BodyKind.Meteor)
        replace(game,run.copy(convoy=run.convoy!!.copy(hull=1),bodies=run.bodies+meteor))
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { game.update(.02) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("convoy-result").assertIsDisplayed()
        compose.onNodeWithTag("find-convoy").assertDoesNotExist()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(run.lives,game.arcade!!.lives); assertEquals(ConvoyStatus.Lost,game.arcade!!.convoy!!.status) }
    }
}
