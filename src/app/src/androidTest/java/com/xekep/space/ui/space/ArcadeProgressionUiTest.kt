package com.xekep.space.ui.space

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ArcadeProgressionUiTest {
    @get:Rule val compose=createComposeRule()
    // Fixture injection stays in tests; no debug controls are exposed to players.
    @Suppress("UNCHECKED_CAST")
    private fun replaceSession(game: SpaceGameState, session: ArcadeSession) {
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate")
        field.isAccessible=true
        (field.get(game) as MutableState<ArcadeSession?>).value=session
        SimulationEngine.reserveBodyIds(session.bodies+session.pending.map { it.body })
    }
    private fun game(wave: Int=1)=SpaceGameState().apply {
        resize(IntSize(1080,2340)); startArcade()
        replaceSession(this,arcade!!.copy(elapsed=(wave-1)*28.0))
    }
    private fun shot(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let the native dialog window finish its entrance, independently of the Compose test clock.
        val context=instrumentation.targetContext
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun guardianHasRoleCountsSharedFleetLimitAndResetsForANewRun() {
        compose.mainClock.autoAdvance=false
        val game=game()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertDoesNotExist()
        compose.runOnIdle { replaceSession(game,game.arcade!!.copy(elapsed=84.0)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertIsSelected()
        compose.runOnIdle {
            val point=game.bodies.first { it.kind == BodyKind.Core }.position+Vec2(240.0,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(ShipClass.Guardian,game.bodies.last().shipClass)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-count-Ship",useUnmergedTree=true).assertTextEquals("0")
        compose.onNodeWithTag("arcade-count-Guardian",useUnmergedTree=true).assertTextEquals("1")
        compose.onNodeWithTag("arcade-spawn-Ship").performClick()
        compose.runOnIdle {
            val point=game.bodies.first { it.kind == BodyKind.Core }.position+Vec2(-240.0,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(ShipClass.Interceptor,game.bodies.last().shipClass)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-count-Guardian",useUnmergedTree=true).assertTextEquals("1")
        compose.onNodeWithTag("arcade-count-Ship",useUnmergedTree=true).assertTextEquals("1")
        compose.onNodeWithTag("arcade-fleet-count",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("arcade-spawn-Guardian").performClick()
        compose.mainClock.advanceTimeByFrame(); shot("arcade-guardian-controls.png")
        compose.runOnIdle { game.startArcade() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ShipClass.Interceptor,game.arcadeShipClass); assertTrue(game.arcade!!.upgrades.isEmpty()) }
    }
    @Test fun upgradeDialogStopsFlightSurvivesMenuAndAppliesExactlyOneChoice() {
        compose.mainClock.autoAdvance=false
        val game=game(3)
        val core=game.bodies.first { it.kind == BodyKind.Core }
        val craft=CelestialBody(SimulationEngine.newBodyId(),core.position+Vec2(240.0,0.0),Vec2(0.0,100.0),24.0,8f,Color.Cyan,BodyKind.Ship)
        replaceSession(game,game.arcade!!.copy(elapsed=80.0,bodies=listOf(core,craft),
            upgradeOffer=ArcadeUpgradeOffer(3,listOf(ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Reactor)),
            offeredUpgradeWaves=setOf(3)))
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertIsDisplayed()
        compose.runOnIdle {
            val before=game.arcade!!; game.update(1.0)
            game.launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0)
            assertEquals(before,game.arcade)
        }
        shot("arcade-upgrade-choice.png")
        compose.runOnIdle { game.openMenu() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertDoesNotExist()
        compose.runOnIdle { game.closeMenu() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-Engines").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertDoesNotExist()
        compose.onNodeWithTag("arcade-spawn-Guardian").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(.85,game.bodies.last().fuelConsumptionScale,1e-8)
            assertEquals(180.0,game.bodies.last().fuelRemaining,0.0)
            game.update(.1); assertTrue(game.bodies.last().fuelRemaining < 180.0)
            game.chooseArcadeUpgrade(ArcadeUpgrade.Reactor)
            assertEquals(1,game.arcade!!.upgrades.size)
        }
    }
    @Test fun tenthWaveChallengeShowsItsFragmentsWithoutGrowingTheHud() {
        compose.mainClock.autoAdvance=false
        val game=game(10)
        val core=game.bodies.first { it.kind == BodyKind.Core }
        val parent=CelestialBody(SimulationEngine.newBodyId(),core.position+Vec2(600.0,0.0),Vec2(-140.0,0.0),
            100.0,15f,Color(0xFFFF8B70),BodyKind.Meteor)
        replaceSession(game,game.arcade!!.copy(bodies=listOf(core,parent),elapsed=275.99,
            challenge=ArcadeChallenge(parent.id,setOf(parent.id)),spawnTimer=1000.0,
            combat=ArcadeCombat(projectiles=listOf(SpaceProjectile(parent.position-Vec2(20.0,0.0),Vec2(10000.0,0.0),99)))))
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-challenge").assertIsDisplayed()
        compose.runOnIdle { game.update(.02) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-challenge").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3,game.arcade!!.challenge!!.ids.size); assertEquals(10,game.arcade!!.wave) }
        shot("arcade-tenth-wave-fragments.png")
    }
}
