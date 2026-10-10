package com.xekep.space.ui.space

import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import java.util.Locale
import androidx.compose.ui.geometry.Offset
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

class ArcadeCampaignUiTest {
    @get:Rule val compose=createComposeRule()
    private fun game(wave: Int): SpaceGameState=SpaceGameState(random=Random(17)).apply {
        resize(IntSize(1080,2340));startArcade()
        val snapshot=recoverySnapshot(0)!!
        restoreRecovery(snapshot.copy(arcade=prepareCampaign(arcade!!.copy(elapsed=(wave-1)*28.0,spawnTimer=1000.0),Random(17))))
        closeMenu()
    }
    private fun show(game: SpaceGameState) {
        compose.mainClock.autoAdvance=false
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeBy(64)
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(48)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.externalCacheDir,name).outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun salvageFocusAndRealTwoFingerRouteEarnAnUpgrade() {
        val game=game(7);show(game)
        compose.onNodeWithTag("find-salvage").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(48)
        assertEquals(game.arcade!!.salvage!!.position,game.camera.center)
        screenshot("campaign-salvage.png")
        compose.onNodeWithTag("arcade-spawn-Ship").performClick();compose.mainClock.advanceTimeByFrame()
        val point=game.arcade!!.salvage!!.position
        val start=worldToScreen(point+Vec2(-180.0,0.0),game.viewport,game.camera.center,game.camera.zoom)
        val goal=worldToScreen(point,game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(0,start);advanceEventTime(300);down(1,goal);up(1)
            moveTo(0,start+Offset(60f*game.density,0f));advanceEventTime(200);up(0)
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(game.bodies.any { it.kind == BodyKind.Ship && it.waypoints.isNotEmpty() })
        compose.runOnIdle { repeat(180) { game.update(1.0/60) } }
        compose.mainClock.advanceTimeBy(48)
        assertEquals(SalvageStatus.Collected,game.arcade!!.salvage!!.status)
        compose.onNodeWithTag("arcade-upgrade-dialog").assertIsDisplayed()
        assertTrue(game.arcade!!.upgradeOffer!!.salvageBonus)
        val upgrade=game.arcade!!.upgradeOffer!!.choices.first()
        compose.onNodeWithTag("arcade-upgrade-${upgrade.name}").performClick();compose.mainClock.advanceTimeByFrame()
        assertEquals(1,game.arcade!!.salvageCollected);assertNull(game.arcade!!.upgradeOffer)
        compose.onNodeWithTag("find-salvage").assertDoesNotExist()
    }
    @Test fun finalCarrierFocusShowsNodesAndDoesNotBreakManualFollowing() {
        val game=game(20);show(game)
        compose.onNodeWithTag("find-carrier").assertIsDisplayed().performClick();compose.mainClock.advanceTimeBy(48)
        assertTrue("Carrier moves during rendering",(game.arcade!!.carrierPosition-game.camera.center).magnitude() < 12.0)
        assertEquals(3,game.arcade!!.carrierNodesRemaining)
        screenshot("campaign-carrier.png")
        compose.runOnIdle {
            game.setMotionControlEnabled(true);game.chooseSpawnKind(BodyKind.Rocket)
            val point=game.arcade!!.carrierPosition+Vec2(-200.0,0.0)
            game.launch(TouchPreview(point,point+Vec2(0.0,-100.0),0,Offset(0f,-80f)),.5)
            game.update(.01)
        }
        val controlled=game.controlledVehicleId;assertNotNull(controlled)
        val center=game.camera.center
        compose.mainClock.advanceTimeBy(48);compose.onNodeWithTag("find-carrier").performClick()
        compose.runOnIdle { assertEquals(controlled,game.controlledVehicleId);assertTrue("Pilot camera must move with the craft, not jump to the carrier",(center-game.camera.center).magnitude() < 40.0) }
    }
    @Test fun carrierTimeoutExplainsTheDefeatAndRetryStartsAQuietNewRun() {
        val game=game(20)
        val saved=game.recoverySnapshot(0)!!
        val run=saved.arcade!!
        game.restoreRecovery(saved.copy(arcade=run.copy(carrier=run.carrier!!.copy(elapsed=90.0))))
        game.closeMenu();game.update(.01);show(game)
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(com.xekep.space.R.string.carrier_overrun)).assertIsDisplayed()
        val retry=InstrumentationRegistry.getInstrumentation().targetContext.getString(com.xekep.space.R.string.try_again)
        compose.onNodeWithText(retry).performClick();compose.mainClock.advanceTimeBy(48)
        assertEquals(1,game.arcade!!.wave);assertNull(game.arcade!!.carrier);assertNull(game.arcade!!.salvage)
        assertFalse(game.motionSteeringEnabled);assertTrue(game.arcade!!.combat.projectiles.isEmpty())
    }
    @Test fun objectiveHudFitsFiveLanguagesAt320dpAndLargeFont() {
        compose.mainClock.autoAdvance=false
        var language by mutableStateOf("ru")
        var currentGame by mutableStateOf(game(7))
        compose.setContent {
            val host=LocalContext.current
            val config=remember(language) { Configuration(host.resources.configuration).apply { setLocale(Locale.forLanguageTag(language));fontScale=1.5f } }
            val localized=remember(language,host) {
                val configured=host.createConfigurationContext(config)
                object: ContextWrapper(host) { override fun getResources()=configured.resources;override fun getAssets()=configured.assets }
            }
            val density=LocalDensity.current.density
            Box(Modifier.requiredSize(320.dp,600.dp)) {
                CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,
                    LocalDensity provides Density(density,1.5f)) { SpaceTheme { SpaceSceneRoot(currentGame) } }
            }
        }
        for (locale in listOf("ru","en","fr","de","zh-Hans")) for (wave in listOf(7,20)) {
            compose.runOnIdle { language=locale;currentGame=game(wave) }
            compose.mainClock.advanceTimeBy(64)
            val tag=if (wave == 7) "find-salvage" else "find-carrier"
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val button=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(button.height/compose.density.density >= 47.9f)
            val texts=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
            val count=texts.fetchSemanticsNodes().size;assertTrue(count > 0)
            for (i in 0 until count) {
                val layouts=mutableListOf<TextLayoutResult>()
                texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                for (layout in layouts) assertFalse("$locale/$wave: ${layout.layoutInput.text.text}",layout.didOverflowHeight ||
                    (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
            }
        }
    }
    @Test fun carrierVictoryGoesStraightToResultAndEndlessIsExplicit() {
        val game=game(20);val saved=game.recoverySnapshot(0)!!;val run=saved.arcade!!
        game.restoreRecovery(saved.copy(arcade=run.copy(bodies=run.bodies.filterNot { it.id in run.carrier!!.nodeIds },
            carrier=run.carrier!!.copy(defeated=true),elapsed=560.0,salvageCollected=1)))
        game.closeMenu();show(game)
        compose.onNodeWithTag("arcade-completion").assertIsDisplayed()
        screenshot("campaign-victory.png")
        assertTrue(game.arcadeCompletionPending)
        compose.onNodeWithTag("continue-endless").performClick();compose.mainClock.advanceTimeBy(48)
        assertFalse(game.arcadeCompletionPending);assertTrue(game.arcade!!.endless)
    }
}
