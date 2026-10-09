package com.xekep.space.ui.space

import androidx.compose.runtime.MutableState
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

/** Isolate wave 15; actual focus button and launch gestures, with ordinary energy and lives.
 * This checks that escort can be deployed during the warning window, not a full human run.
 */
class ConvoyEscortUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun easyEscort()=escort(ArcadeDifficulty.Easy)
    @Test fun normalEscort()=escort(ArcadeDifficulty.Normal)
    @Test fun hardEscort()=escort(ArcadeDifficulty.Hard)

    @Suppress("UNCHECKED_CAST")
    private fun escort(difficulty: ArcadeDifficulty) {
        val game=SpaceGameState(random=Random(17)).apply { resize(IntSize(1080,2340)); startArcade(difficulty) }
        val run=advanceArcade(game.arcade!!.copy(elapsed=14*28.0,spawnTimer=1.5),.01,Random(17))
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        (field.get(game) as MutableState<ArcadeSession?>).value=run
        SimulationEngine.reserveBodyIds(run.bodies)
        compose.mainClock.autoAdvance=false
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        fun simulate(ticks: Int) {
            compose.runOnIdle { repeat(ticks) { game.update(1.0/30) } }
            compose.mainClock.advanceTimeByFrame()
        }
        simulate(20) // Notice the transport and react.
        compose.onNodeWithTag("find-convoy").performClick()
        simulate(8)
        compose.onNodeWithTag("arcade-spawn-Ship").performClick()
        simulate(8)
        repeat(2) { index ->
            val transport=game.bodies.first { it.kind == BodyKind.Convoy }
            val point=transport.position+transport.heading.perpendicular()*(if (index == 0) 90.0 else -90.0)
            val start=worldToScreen(point,game.viewport,game.camera.center,game.camera.zoom)
            val end=start+Offset((transport.heading.x*28*game.density).toFloat(),(transport.heading.y*28*game.density).toFloat())
            compose.onNodeWithTag("space-scene").performTouchInput { down(0,start) }
            simulate(24) // Holding and flick cost game time too.
            compose.onNodeWithTag("space-scene").performTouchInput {
                advanceEventTime(600); moveTo(0,end,180); up(0)
            }
            simulate(7)
        }
        assertEquals(2,game.arcade!!.launches)
        compose.onNodeWithTag("find-core").performClick()
        repeat(129) {
            if (game.arcade!!.convoy!!.status == ConvoyStatus.Approaching && game.arcade!!.lives > 0) {
                game.arcade!!.upgradeOffer?.let { offer ->
                    val choice=listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,
                        ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines).first { it in offer.choices }
                    compose.onNodeWithTag("arcade-upgrade-${choice.name}").performClick()
                    compose.mainClock.advanceTimeByFrame()
                }
                simulate(10)
            }
        }
        val result=game.arcade!!
        val convoy=result.convoy!!
        println("CONVOY_UI_ESCORT,difficulty=$difficulty,status=${convoy.status},hull=${convoy.hull},lives=${result.lives},launches=${result.launches},intercepts=${result.destroyed},seconds=${result.elapsed-14*28.0}")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-upgrade-dialog").assertIsDisplayed()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        File(instrumentation.targetContext.externalCacheDir,"convoy-escort-${difficulty.name}.png").outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        assertEquals(ConvoyStatus.Delivered,convoy.status)
        assertTrue(result.lives > 0)
        assertTrue(convoy.hull > 0)
        assertTrue(result.upgradeOffer!!.convoyBonus)
    }
}
