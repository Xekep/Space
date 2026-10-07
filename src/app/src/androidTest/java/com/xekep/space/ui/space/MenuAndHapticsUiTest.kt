package com.xekep.space.ui.space

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.input.GameHaptics
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuAndHapticsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun notificationsAreCenteredAndTiltSwitchIsAbsentFromTheMenu() {
        val game=SpaceGameState().apply { resize(androidx.compose.ui.unit.IntSize(1080,2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle { game.inform(com.xekep.space.R.string.checkpoint_stored) }
        val notice=compose.onNodeWithText(context.getString(com.xekep.space.R.string.checkpoint_stored)).fetchSemanticsNode().boundsInRoot
        val root=compose.onNodeWithTag("space-scene").fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x,notice.center.x,1f)
        compose.onNodeWithTag("open-menu").performClick()
        compose.onNodeWithTag("motion-control-switch").assertDoesNotExist()
        compose.onNodeWithText(context.getString(com.xekep.space.R.string.motion_control)).assertDoesNotExist()
    }
    @Test fun collisionModeIsInSandboxToolsInsteadOfTheMainMenu() {
        val game=SpaceGameState().apply { startSandbox(); setCollisions(true); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("sandbox-collisions").assertDoesNotExist()
        compose.onNodeWithTag("collision-mode-Debris").assertDoesNotExist()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.onNodeWithTag("collision-mode-Debris").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(com.xekep.space.sim.SandboxCollisionMode.Debris,game.sandbox!!.collisionMode) }
        compose.onNodeWithTag("collision-mode-Merge").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(com.xekep.space.sim.SandboxCollisionMode.Merge,game.sandbox!!.collisionMode) }
    }

    @Test fun vibrationPermissionAndEnabledForegroundImpactsWork() {
        compose.setContent { SpaceTheme { SpaceSceneRoot(SpaceGameState()) } }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(Manifest.permission.VIBRATE))
        GameHaptics(context).use { haptic ->
            haptic.enabled=false; assertFalse(haptic.impact())
            haptic.enabled=true; haptic.setForeground(false); assertFalse(haptic.impact())
            haptic.setForeground(true); assertEquals(haptic.available,haptic.impact(strong=true))
            assertFalse(haptic.impact()); android.os.SystemClock.sleep(80)
            haptic.close(); assertFalse(haptic.impact())
        }
    }
}
