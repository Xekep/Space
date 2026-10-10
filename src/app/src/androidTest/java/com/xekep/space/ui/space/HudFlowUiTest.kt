package com.xekep.space.ui.space

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.R
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HudFlowUiTest {
    @get:Rule val compose=createComposeRule()
    @Suppress("UNCHECKED_CAST")
    private fun setRun(game: SpaceGameState,run: ArcadeSession) {
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        (field.get(game) as MutableState<ArcadeSession?>).value=run
    }

    @Test fun restDistinguishesRemainingThreatsAndResultHidesCombatPanels() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        lateinit var localized: Context
        compose.setContent { localized=LocalContext.current; SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeBy(48)
        val clean=game.arcade!!.copy(elapsed=24.5,spawnTimer=1000.0,pending=emptyList())
        compose.runOnIdle { setRun(game,clean) }; compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("wave-rest-status").assertTextEquals(localized.getString(R.string.rest))
        val meteor=clean.bodies.first().copy(id=99999,position=Vec2(2000.0,2000.0),kind=BodyKind.Meteor)
        compose.runOnIdle { setRun(game,clean.copy(bodies=clean.bodies+meteor)) }; compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("wave-rest-status").assertTextEquals(localized.getString(R.string.arrivals_ended))
        compose.runOnIdle { setRun(game,clean.copy(pending=listOf(PendingThreat(meteor,1.0)))) }; compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("wave-rest-status").assertTextEquals(localized.getString(R.string.arrivals_ended))
        compose.runOnIdle { setRun(game,clean.copy(lives=0)) }; compose.mainClock.advanceTimeBy(48)
        for (tag in listOf("arcade-spawn-panel","arcade-launch-energy","arcade-top-hud","wave-rest-status","core-direction"))
            compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.onNodeWithText(localized.getString(R.string.try_again)).assertIsDisplayed()
    }

    @Test fun energyPriceAppearsBeforeLaunchIncludingWhilePiloting() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=target.getSharedPreferences("space_options",0)
        val old=prefs.getString("flightControl",null)
        prefs.edit().putString("flightControl","Joystick").commit()
        try {
            compose.mainClock.autoAdvance=false
            val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade(); chooseSpawnKind(BodyKind.Rocket) }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.mainClock.advanceTimeBy(48)
            for (pilot in listOf(false,true)) {
                if (pilot) compose.runOnIdle {
                    game.setMotionControlEnabled(true)
                    game.launch(TouchPreview(Vec2(4000.0,4000.0),Vec2(4000.0,3900.0),0),0.0)
                }
                compose.mainClock.advanceTimeBy(48)
                val count=game.bodies.count { it.isVehicle }
                compose.onNodeWithTag("space-scene").performTouchInput { down(center+Offset(100f,-100f)) }
                compose.mainClock.advanceTimeBy(48)
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                compose.waitForIdle(); android.os.SystemClock.sleep(150); instrumentation.waitForIdleSync()
                File(target.externalCacheDir,"launch-price-$pilot.png").outputStream().use {
                    assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                }
                println("LAUNCH_PRICE,pilot=$pilot,price=${compose.onNodeWithTag("launch-cost").fetchSemanticsNode().boundsInRoot},viewport=${game.viewport}")
                compose.onNodeWithTag("launch-cost").assertIsDisplayed()
                val label=compose.onNodeWithTag("launch-cost").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
                val price=label.substringAfterLast('−').toDouble()
                val energy=game.arcade!!.energy
                compose.onNodeWithTag("space-scene").performTouchInput { up() }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals(count+1,game.bodies.count { it.isVehicle })
                    assertEquals(price,energy-game.arcade!!.energy,2.0)
                }
                compose.onNodeWithTag("launch-cost").assertDoesNotExist()
                if (pilot) compose.onNodeWithTag("arcade-launch-energy").assertDoesNotExist()
            }
        } finally { prefs.edit().apply { if (old == null) remove("flightControl") else putString("flightControl",old) }.commit() }
    }
}
