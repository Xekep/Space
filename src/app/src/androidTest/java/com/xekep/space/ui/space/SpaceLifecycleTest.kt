package com.xekep.space.ui.space

import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import com.xekep.space.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpaceLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun menuChoicesSurviveActivityRecreation() {
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("preset-BinaryStars").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("mode-Sandbox").assertIsSelected()
        compose.onNodeWithTag("preset-BinaryStars").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle {
            val game = ViewModelProvider(compose.activity)[SpaceViewModel::class.java].game
            assertEquals(AppMode.Sandbox, game.mode)
            assertEquals(com.xekep.space.sim.SandboxPresetKind.BinaryStars, game.sandbox?.preset)
        }
    }

    @Test fun activityRecreationKeepsTheUniverseAndOpensThePauseMenu() {
        lateinit var original: SpaceGameState
        compose.runOnIdle {
            original = ViewModelProvider(compose.activity)[SpaceViewModel::class.java].game
            original.startSandbox(com.xekep.space.sim.SandboxPresetKind.Empty)
            original.toggleSandboxPause()
        }
        compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
        val before = original.sandbox
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle {
            val restored = ViewModelProvider(compose.activity)[SpaceViewModel::class.java].game
            assertSame(original, restored)
            assertEquals(before, restored.sandbox)
            assertEquals(1, restored.bodies.size)
        }
    }
}
