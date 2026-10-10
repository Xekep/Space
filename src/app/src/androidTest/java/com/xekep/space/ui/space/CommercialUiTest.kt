package com.xekep.space.ui.space

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.storage.GameOptions
import com.xekep.space.input.FlightControlMode
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.util.UUID

class CommercialUiTest {
    @get:Rule val compose=createComposeRule()
    private val target=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="commercial_ui_"+UUID.randomUUID()
    @After fun cleanup() { target.deleteSharedPreferences(name) }
    private fun scene(game: SpaceGameState): GameOptions {
        lateinit var options: GameOptions
        compose.mainClock.autoAdvance=false
        compose.setContent {
            val host=LocalContext.current
            val context=androidx.compose.runtime.remember { object: ContextWrapper(host) {
                override fun getSharedPreferences(key: String,mode: Int): SharedPreferences=target.getSharedPreferences(name,mode)
            } }
            options=androidx.compose.runtime.remember { GameOptions(context) }
            CompositionLocalProvider(LocalContext provides context) { SpaceTheme { SpaceSceneRoot(game) } }
        }
        compose.mainClock.advanceTimeBy(64)
        return options
    }
    @Test fun firstStartOffersPracticeAndSkipIsRemembered() {
        val game=SpaceGameState();val options=scene(game)
        compose.onNodeWithTag("menu-primary").performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("welcome-practice").assertIsDisplayed()
        compose.onNodeWithTag("welcome-skip").performClick();compose.mainClock.advanceTimeBy(48)
        assertTrue(target.getSharedPreferences(name,0).getBoolean("learningOffered",false));assertTrue(game.hasSession);assertEquals(-1,game.tutorialStep);assertEquals(ArcadeDifficulty.Easy,game.arcade!!.difficulty)
        compose.runOnIdle { game.openMenu() };compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("menu-primary").performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("welcome-practice").assertDoesNotExist()
    }
    @Test fun firstStartPracticeIsSafeAndOptional() {
        val game=SpaceGameState();scene(game)
        compose.onNodeWithTag("menu-primary").performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("welcome-learn").performClick();compose.mainClock.advanceTimeBy(48)
        assertEquals(0,game.tutorialStep);assertTrue(game.arcade!!.practice)
        compose.runOnIdle { game.skipTutorial() };assertFalse(game.arcade!!.practice)
    }
    @Test fun privacyAndQualityAreReachableInSettings() {
        val game=SpaceGameState();val options=scene(game)
        compose.onNodeWithTag("open-settings").performClick();compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("graphics-quality").performScrollTo().performClick()
        assertEquals("Full",target.getSharedPreferences(name,0).getString("graphicsQuality",null))
        compose.onNodeWithTag("open-privacy").performScrollTo().performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("privacy-dialog").assertIsDisplayed()
    }
    @Test fun completionAndGoalsRemainReachableAndEndlessRequiresAChoice() {
        val game=SpaceGameState().apply { resize(IntSize(1080,2340));startArcade() }
        val saved=game.recoverySnapshot(0)!!.let { it.copy(arcade=it.arcade!!.copy(elapsed=560.0)) }
        game.restoreRecovery(saved);game.closeMenu();scene(game)
        compose.onNodeWithTag("arcade-completion").assertIsDisplayed()
        compose.onNodeWithTag("continue-endless").performClick();compose.mainClock.advanceTimeBy(48)
        assertTrue(game.arcade!!.endless);compose.onNodeWithTag("arcade-completion").assertDoesNotExist()
        compose.runOnIdle { game.openMenu() };compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("open-goals").performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("arcade-goals").assertIsDisplayed()
    }
    @Test fun flightPracticeCanBeLaunchedFromSettingsWithoutACombatRun() {
        val game=SpaceGameState();target.getSharedPreferences(name,0).edit().putString("flightControl",FlightControlMode.Joystick.name).commit();scene(game)
        compose.onNodeWithTag("open-settings").performClick();compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("flight-practice").performScrollTo().performClick();compose.mainClock.advanceTimeBy(64)
        assertTrue(game.flightPractice);assertNotNull(game.controlledVehicleId)
        compose.onNodeWithTag("flight-joystick").assertIsDisplayed()
        compose.onNodeWithTag("pitch-joystick").assertIsDisplayed()
    }
}
