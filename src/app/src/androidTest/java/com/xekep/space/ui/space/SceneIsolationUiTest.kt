package com.xekep.space.ui.space

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.Vec2
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SceneIsolationUiTest {
    @get:Rule val compose = createComposeRule()

    @Suppress("UNCHECKED_CAST")
    private fun replaceArcade(game: SpaceGameState, session: ArcadeSession) {
        val field = SpaceGameState::class.java.getDeclaredField("arcade\$delegate")
        field.isAccessible = true
        (field.get(game) as MutableState<ArcadeSession?>).value = session
    }

    private fun projectilePixels(image: Bitmap): Int {
        var count = 0
        for (y in image.height / 3 until image.height * 2 / 3)
            for (x in image.width / 3 until image.width * 2 / 3) {
                val pixel = image.getPixel(x, y)
                if (Color.red(pixel) in 156..160 && Color.green(pixel) in 246..250 && Color.blue(pixel) >= 253) count++
            }
        return count
    }

    private fun pixels(): Int = projectilePixels(compose.onNodeWithTag("space-scene").captureToImage().asAndroidBitmap())

    @Test fun newLoadedGeneratedAndRestoredWorldsHideInactiveArcadeProjectiles() {
        compose.mainClock.autoAdvance = false
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val empty = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }.snapshot(123L)!!
        val transitions = listOf<Pair<String, () -> Unit>>(
            "new world" to { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause() },
            "solar system" to { game.startSandbox(SandboxPresetKind.SolarSystem); game.toggleSandboxPause() },
            "loaded world" to { game.loadSandbox(empty) },
            "generated world" to { game.generateRandomSystems("Test"); game.fitCamera() },
            "checkpoint" to { game.restoreCheckpoint() },
        )
        for ((name, transition) in transitions) {
            compose.runOnIdle {
                transition()
                val shot = SpaceProjectile(game.camera.center + Vec2(50.0 / game.camera.zoom, 0.0), Vec2(650.0, 0.0), 42)
                replaceArcade(game, game.arcade!!.copy(combat = ArcadeCombat(projectiles = listOf(shot))))
            }
            compose.mainClock.advanceTimeByFrame()
            val withRetainedShot = pixels()
            val retained = game.arcade!!
            compose.runOnIdle { replaceArcade(game, retained.copy(combat = ArcadeCombat())) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals("An inactive arcade shot leaked into $name", pixels(), withRetainedShot)
            compose.runOnIdle { replaceArcade(game, retained) }
        }
    }

    @Test fun switchingModesPreservesShotsAndANewArcadeRunClearsThem() {
        compose.mainClock.autoAdvance = false
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val baseline = pixels()
        compose.runOnIdle {
            val shot = SpaceProjectile(game.camera.center + Vec2(50.0 / game.camera.zoom, 0.0), Vec2(650.0, 0.0), 42)
            replaceArcade(game, game.arcade!!.copy(combat = ArcadeCombat(projectiles = listOf(shot))))
        }
        compose.mainClock.advanceTimeByFrame()
        val active = pixels()
        assertTrue("Active arcade projectiles must remain visible", active > baseline + 20)
        val retained = game.arcade!!
        compose.runOnIdle { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { game.enterMode(AppMode.Arcade) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(retained, game.arcade)
        assertEquals(active, pixels())
        compose.runOnIdle { game.startArcade() }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(game.arcade!!.combat.projectiles.isEmpty())
        assertEquals(baseline, pixels())
    }
}
