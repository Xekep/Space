package com.xekep.space.ui.space

import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.SessionRecovery
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class StrategyUiTest {
    @get:Rule val compose=createComposeRule()
    private fun game()=SpaceGameState().apply { resize(IntSize(1080,2340));startStrategy() }
    private fun replace(game: SpaceGameState, run: ArcadeSession) {
        val saved=game.recoverySnapshot(0)!!;game.restoreRecovery(saved.copy(strategy=run));game.closeMenu()
    }
    private fun show(game: SpaceGameState) { compose.mainClock.autoAdvance=false;compose.setContent { SpaceTheme { SpaceSceneRoot(game) } };compose.mainClock.advanceTimeBy(32) }
    private fun tap(game: SpaceGameState, p: Vec2) { compose.onNodeWithTag("space-scene").performTouchInput { click(worldToScreen(p,game.viewport,game.camera.center,game.camera.zoom)) };compose.mainClock.advanceTimeBy(32) }
    private fun ship(p: Vec2)=CelestialBody(SimulationEngine.newBodyId(),p,Vec2.Zero,36.0,7f,Color.Cyan,BodyKind.Ship)
    private fun shot(name: String) {
        val registry=InstrumentationRegistry.getInstrumentation();registry.waitForIdleSync()
        File(registry.targetContext.externalCacheDir,name).outputStream().use { registry.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun buildSelectProductionUpgradeAndExitTacticalModeUseActualButtons() {
        val game=game();show(game)
        val core=game.bodies.first { it.kind == BodyKind.Core }
        compose.onNodeWithTag("build-factory").assertIsDisplayed().performClick()
        tap(game,core.position+Vec2(-350.0,150.0))
        assertEquals(2,game.arcade!!.strategy!!.stations.size);assertEquals(55.0,game.arcade!!.energy,1.0)
        compose.onNodeWithTag("factory-product").assertIsDisplayed().performClick()
        assertEquals(FactoryProduct.Rocket,game.arcade!!.strategy!!.stations.last().product)
        compose.onNodeWithTag("station-upgrade").performClick();assertEquals(2,game.arcade!!.strategy!!.stations.last().level)
        shot("strategy-factory.png")
        compose.onNodeWithTag("tactical-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertFalse(game.tacticalMode);compose.onNodeWithTag("arcade-spawn-panel").assertExists()
    }
    @Test fun rectangleSelectionMoveAttackAndPinchNeverLaunchAnExtraCraft() {
        val game=game();val core=game.bodies.first { it.kind == BodyKind.Core }
        val a=ship(core.position+Vec2(-300.0,-100.0));val b=ship(core.position+Vec2(-120.0,-230.0))
        replace(game,game.arcade!!.copy(bodies=game.bodies+listOf(a,b)));show(game)
        val count=game.bodies.size
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(worldToScreen(core.position+Vec2(-360.0,-300.0),game.viewport,game.camera.center,game.camera.zoom))
            moveTo(worldToScreen(core.position+Vec2(-60.0,-60.0),game.viewport,game.camera.center,game.camera.zoom));up()
        }
        compose.mainClock.advanceTimeBy(32);assertEquals(setOf(a.id,b.id),game.fleetSelection)
        tap(game,core.position+Vec2(-450.0,220.0))
        assertEquals(2,game.arcade!!.strategy!!.orders.size)
        assertEquals(2,game.arcade!!.strategy!!.orders.values.map { it.position }.distinct().size)
        compose.onNodeWithTag("order-attack").performClick()
        val target=game.arcade!!.strategy!!.bases.first();tap(game,target.position)
        assertTrue(game.arcade!!.strategy!!.orders.values.all { it.command == FleetCommand.Attack && it.targetId == target.id })
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(0,center+Offset(-100f,0f));down(1,center+Offset(100f,0f))
            moveTo(0,center+Offset(-170f,30f));moveTo(1,center+Offset(170f,30f));up(1);up(0)
        }
        compose.mainClock.advanceTimeBy(32);assertEquals(count,game.bodies.size);assertEquals(0,game.arcade!!.launches)
        shot("strategy-orders.png")
    }
    @Test fun menuOffersStrategySeparatelyAndBothCombatSessionsRecoverTogether() {
        val game=game()
        game.startArcade(ArcadeDifficulty.Easy);game.openMenu();show(game)
        compose.onNodeWithTag("mode-Strategy").assertIsDisplayed().performClick()
        compose.onNodeWithTag("mode-Arcade").assertIsDisplayed()
        compose.onNodeWithTag("menu-primary").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(AppMode.Strategy,game.mode);assertTrue(game.tacticalMode)
        compose.runOnIdle { game.openMenu() };compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("mode-Arcade").performClick();compose.onNodeWithTag("menu-primary").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(AppMode.Arcade,game.mode);assertFalse(game.tacticalMode)
        compose.onNodeWithTag("arcade-spawn-panel").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"strategy-recovery-ui.json")
        try {
            val store=SessionRecovery(context,file);val captured=game.recoverySnapshot(43)!!
            val saved=captured.copy(arcade=captured.arcade!!.copy(bodies=captured.arcade!!.bodies.map { it.copy(trail=listOf(it.position)) }),
                strategy=captured.strategy!!.copy(bodies=captured.strategy!!.bodies.map { it.copy(trail=listOf(it.position)) }))
            store.save(saved);val restored=store.load()!!;assertEquals(saved,restored)
            val next=SpaceGameState();next.restoreRecovery(restored);next.enterMode(AppMode.Strategy)
            assertEquals(saved.strategy,next.arcade);next.enterMode(AppMode.Arcade);assertEquals(saved.arcade,next.arcade)
        } finally { file.delete();File(file.path+".bak").delete();File(file.path+".new").delete() }
    }
    @Test fun compactHudWorksInFiveLanguagesWithLargeText() {
        val game=game();val context=InstrumentationRegistry.getInstrumentation().targetContext
        var language by mutableStateOf("en")
        compose.mainClock.autoAdvance=false
        compose.setContent {
            val config=Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val base=LocalContext.current
            val localized=base.createConfigurationContext(config).resources
            val local=object:ContextWrapper(base) { override fun getResources()=localized }
            CompositionLocalProvider(LocalContext provides local,LocalDensity provides Density(3f,1.5f)) {
                SpaceTheme { Box(Modifier.requiredSize(320.dp,700.dp)) { SpaceSceneRoot(game) } }
            }
        }
        for (locale in listOf("en","ru","fr","de","zh-Hans")) {
            compose.runOnIdle { language=locale };compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("strategy-hud").assertIsDisplayed()
            compose.onNodeWithTag("build-factory").assertIsDisplayed();compose.onNodeWithTag("build-service").assertIsDisplayed()
            val root=compose.onNodeWithTag("strategy-hud").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("build-factory","build-service","build-bastion","fleet-all","tactical-toggle")) {
                val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue(tag+" stays inside HUD",bounds.left >= root.left-1 && bounds.right <= root.right+1)
            }
            val localized=context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) })
            val status=localized.getString(com.xekep.space.R.string.strategy_status,0,3)
            val result=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(status).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            assertTrue(result.isNotEmpty())
            val color=result.first().layoutInput.style.color
            assertTrue("HUD text needs readable contrast",color.red+color.green+color.blue > 1.5f)
            shot("strategy-hud-$locale.png")
            for (label in listOf(com.xekep.space.R.string.station_factory,com.xekep.space.R.string.station_service,com.xekep.space.R.string.station_bastion)) {
                val layouts=mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(localized.getString(label),useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                val layout=layouts.first()
                // Compose can retain a paragraph wider than its intrinsic text layout.
                // Check actual glyph advances and ellipsis, not the paragraph-width flag.
                assertFalse("Building names must fit: $locale / ${localized.getString(label)}",layout.isLineEllipsized(0))
                assertTrue(layout.getLineRight(0) <= layout.size.width+1)
                assertTrue(layout.getLineLeft(0) >= -1)
                assertTrue(layout.getLineBottom(0) <= layout.size.height+1)
            }
        }
    }
    @Test fun strategyDefeatUsesItsOwnResultAndRetryRebuildsTheBattle() {
        val game=game();replace(game,game.arcade!!.copy(lives=0));show(game)
        compose.onNodeWithTag("strategy-defeat").assertIsDisplayed()
        compose.onNodeWithTag("arcade-completion").assertDoesNotExist()
        val text=InstrumentationRegistry.getInstrumentation().targetContext.getString(com.xekep.space.R.string.strategy_replay)
        compose.onNodeWithText(text).performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(AppMode.Strategy,game.mode);assertEquals(3,game.arcade!!.strategy!!.bases.size)
        assertTrue(game.arcade!!.combat.projectiles.isEmpty());assertTrue(game.tacticalMode);assertFalse(game.motionSteeringEnabled)
    }
    @Test fun strategyVictoryHasNoEndlessArcadeButton() {
        val game=game();val run=game.arcade!!;val ids=run.strategy!!.bases.map { it.id }.toSet()
        replace(game,run.copy(bodies=run.bodies.filterNot { it.id in ids },strategy=run.strategy!!.copy(bases=emptyList(),victory=true)));show(game)
        compose.onNodeWithTag("strategy-victory").assertIsDisplayed()
        compose.onNodeWithTag("continue-endless").assertDoesNotExist()
    }

}
