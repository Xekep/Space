package com.xekep.space.ui.space

import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.R
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ArcadeFeedbackUiTest {
    @get:Rule val compose=createComposeRule()

    @Test @Suppress("UNCHECKED_CAST")
    fun selectingCraftShowsItsTaskAndFuelAndClosingDoesNotSpawn() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        val core=game.bodies.first { it.kind == BodyKind.Core }
        game.chooseSpawnKind(BodyKind.Ship)
        val point=core.position+Vec2(240.0,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        val ship=game.bodies.single { it.kind == BodyKind.Ship }
        val threat=ship.copy(id=900001,position=ship.position+Vec2(350.0,0.0),mass=900.0,radius=24f,kind=BodyKind.Meteor)
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        val state=field.get(game) as MutableState<ArcadeSession?>
        state.value=game.arcade!!.copy(elapsed=84.0,spawnTimer=1000.0,
            bodies=game.bodies.map { if (it.id == ship.id) it.copy(fuelRemaining=18.0) else it }+threat,
            combat=ArcadeCombat(craft=mapOf(ship.id to CraftStatus(targetId=threat.id))))
        game.selectBody(ship.id)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-craft-task").assertTextEquals(context.getString(R.string.craft_intercept))
        compose.onNodeWithTag("arcade-craft-fuel").assertContentDescriptionEquals(context.getString(R.string.pilot_fuel,10))
        compose.onNodeWithTag("arcade-fleet-count",useUnmergedTree=true).assertTextEquals(context.getString(R.string.fleet_count,1,3))
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(150) // Let the native surface present the Compose frames.
        instrumentation.waitForIdleSync()
        File(context.externalCacheDir,"arcade-feedback.png").outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        val before=game.arcade!!.launches
        compose.onNodeWithTag("clear-arcade-selection").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-craft-task").assertDoesNotExist()
        compose.onNodeWithTag("arcade-craft-fuel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(before,game.arcade!!.launches); assertNull(game.selectedBody) }
    }
}
