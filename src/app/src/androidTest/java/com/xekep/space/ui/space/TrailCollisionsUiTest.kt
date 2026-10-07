package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import com.xekep.space.ui.theme.SpaceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TrailCollisionsUiTest {
    @get:Rule val compose=createComposeRule()
    private fun shot(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun roundedTrailsRenderSharpTurnsAndReversalsWithoutLeavingTheHead() {
        compose.mainClock.autoAdvance=false
        val trails=listOf(
            listOf(Vec2(-180.0,-90.0),Vec2(-130.0,-90.0),Vec2(-80.0,-90.0),Vec2(-30.0,-90.0),Vec2(-30.0,-40.0),Vec2(-30.0,10.0),Vec2(20.0,50.0)),
            listOf(Vec2(-160.0,100.0),Vec2(-110.0,100.0),Vec2(-60.0,100.0),Vec2(-10.0,100.0),Vec2(-10.0,130.0),Vec2(-60.0,130.0),Vec2(-110.0,130.0)))
        val bodies=trails.mapIndexed { i,trail -> CelestialBody(i.toLong()+1,trail.last(),Vec2.Zero,300.0,10f,
            if (i == 0) Color.Cyan else Color(0xFFFFCF82),trail=trail) }
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(bodies,Vec2.Zero,2f,0.0,0,paused=true)) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("space-scene").assertIsDisplayed()
        shot("rounded-trails.png")
        compose.runOnIdle { assertEquals(bodies,game.bodies); assertFalse(game.menuOpen) }
    }
    @Test fun fiveHundredBodiesCollideWithBoundedDebrisAndReadableTrails() {
        compose.mainClock.autoAdvance=false
        val initial=List(500) { i ->
            val pair=i/2; val sign=if (i%2 == 0) -1.0 else 1.0
            val point=Vec2((pair%25-12)*65.0+sign*6,(pair/25-5)*65.0)
            CelestialBody(i.toLong()+1,point,Vec2(-sign*150,0.0),70.0,4f,
                if (i%2 == 0) Color(0xFF8BD3FF) else Color(0xFFFFCF82),
                trail=List(42) { point+Vec2(sign*(41-it)*2,0.0) })
        }
        val game=SpaceGameState().apply {
            loadSandbox(SandboxSnapshot(initial,Vec2.Zero,.55f,SimulationEngine.totalEnergy(initial),0,
                collisionsEnabled=true,collisionMode=SandboxCollisionMode.Debris,paused=true))
        }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        shot("collisions-500-before.png")
        compose.runOnIdle { runBlocking {
            game.toggleSandboxPause(); game.updateSandboxAsync(1.0/30,budgeted=true); game.toggleSandboxPause()
            assertTrue(game.bodies.size in 250..266); assertTrue(game.explosions.isNotEmpty())
        } }
        compose.mainClock.advanceTimeByFrame()
        shot("collisions-500-impact.png")
        compose.runOnIdle { runBlocking {
            game.toggleSandboxPause()
            repeat(30) { game.updateSandboxAsync(1.0/30,budgeted=true) }
            game.toggleSandboxPause()
            assertEquals(35000.0,game.bodies.sumOf { it.mass },1e-6)
            assertTrue(game.bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() })
            assertTrue(game.bodies.size <= 1000); assertFalse(game.menuOpen)
            assertTrue(visibleTrailBodies(game.bodies,game.viewport,game.camera,0.0,game.density,null,null).size <= 48)
        } }
        compose.mainClock.advanceTimeByFrame()
        shot("collisions-500-after.png")
        compose.onNodeWithTag("space-scene").assertIsDisplayed()
    }
}
