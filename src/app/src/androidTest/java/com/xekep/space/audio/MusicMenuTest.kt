package com.xekep.space.audio

import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MusicMenuTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("space_options", Context.MODE_PRIVATE)
    private val previousMusic = preferences.getBoolean("music", true)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun chooseLanguageOnFirstLaunch() {
        if (compose.onAllNodesWithTag("first-language-screen").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("language-en").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("open-settings").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @After fun restorePreference() {
        preferences.edit().putBoolean("music", previousMusic).commit()
    }

    @Test fun musicSwitchImmediatelyControlsPlaybackInThePausedMenu() {
        compose.onNodeWithTag("open-settings").performClick()
        val audio = context.getSystemService(AudioManager::class.java)
        val control = compose.onNodeWithTag("ambient-music-switch").performScrollTo()
        if (control.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] != ToggleableState.On) {
            control.performClick()
        }
        compose.waitUntil(10_000) { audio.isMusicActive }
        control.assertIsOn().performClick()
        compose.waitUntil(5_000) { !audio.isMusicActive }
        control.assertIsOff().performClick()
        compose.waitUntil(5_000) { audio.isMusicActive }
        control.assertIsOn()
    }
}
