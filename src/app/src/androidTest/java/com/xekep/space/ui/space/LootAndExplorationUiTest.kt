package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
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

class LootAndExplorationUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        File(context.externalCacheDir,name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun threeSolidLootModelsRotateAndKeepAReadableStaticPoseWithoutDecorativeMotion() {
        val time=mutableStateOf(0.0);val animate=mutableStateOf(true)
        compose.setContent { Canvas(Modifier.fillMaxSize().testTag("loot-models")) {
            drawRect(Color(0xFF081125))
            for (kind in LootAppearance.entries) drawLootAppearance(kind,Offset(size.width/2,size.height*(kind.ordinal+1)/4),42*density,time.value,animate.value)
        } }
        val first=compose.onRoot().captureToImage().asAndroidBitmap();screenshot("loot-three-models.png")
        compose.runOnIdle { time.value=4.0 }
        val turned=compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Loot must slowly tumble",first.sameAs(turned));screenshot("loot-three-models-turned.png")
        compose.runOnIdle { animate.value=false }
        val still=compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle { time.value=40.0 }
        assertTrue("Reduced motion must keep the static shaded model",still.sameAs(compose.onRoot().captureToImage().asAndroidBitmap()))
        screenshot("loot-three-models-static.png")
    }
    @Test fun allRandomTrophyAppearancesSurviveRecoveryAndOldSavesChooseAContainer() {
        val game=SpaceGameState(proceduralWorlds=false).apply { resize(androidx.compose.ui.unit.IntSize(1080,2340));startArcade() }
        val codec=SessionRecovery(context)
        for (kind in LootAppearance.entries) {
            val saved=game.recoverySnapshot(73)!!.let { it.copy(arcade=it.arcade!!.copy(salvage=ArcadeSalvage(7,Vec2(1200.0,700.0),appearance=kind))) }
            val decoded=codec.decode(codec.encode(saved))
            assertEquals(kind,decoded.arcade!!.salvage!!.appearance)
            val old=org.json.JSONObject(codec.encode(saved))
            val payload=org.json.JSONObject(old.getString("payload"))
            payload.getJSONObject("arcade").getJSONObject("salvage").remove("appearance")
            val raw=payload.toString()
            old.put("payload",raw).put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
            assertEquals(LootAppearance.Container,codec.decode(old.toString()).arcade!!.salvage!!.appearance)
        }
    }
    @Test fun realTwoFingerTravelDiscoversPersistentRegionsInArcadeWithoutDuplicateBodies() {
        val game=SpaceGameState().apply { resize(androidx.compose.ui.unit.IntSize(1080,2340));startArcade() }
        compose.mainClock.autoAdvance=false
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } };compose.mainClock.advanceTimeBy(48)
        val initial=game.bodies.size
        repeat(9) {
            compose.onNodeWithTag("space-scene").performTouchInput {
                val a=Offset(width*.72f,height*.42f);val b=Offset(width*.76f,height*.49f)
                down(0,a);down(1,b)
                updatePointerTo(0,a-Offset(width*.45f,0f));updatePointerTo(1,b-Offset(width*.45f,0f));move(96)
                up(0);up(1)
            }
            compose.mainClock.advanceTimeBy(32)
        }
        assertTrue("camera=${game.camera} bodies=${game.bodies.size} visited=${game.arcade!!.exploration?.visited}",game.bodies.size>initial);assertTrue(game.arcade!!.exploration!!.visited.isNotEmpty())
        assertTrue(game.bodies.any { it.explorationRegion!=null && it.isDebris })
        screenshot("arcade-distant-regions.png")
        repeat(12) {
            if (game.bodies.none { it.explorationRegion!=null && it.kind==BodyKind.Star }) {
                compose.runOnIdle { game.transformCamera(Offset(540f,1000f),Offset(-900f,0f),1f) }
                compose.mainClock.advanceTimeBy(32)
            }
        }
        val star=game.bodies.first { it.explorationRegion!=null && it.kind==BodyKind.Star }
        compose.runOnIdle {
            val delta=(game.camera.center-star.position)*game.camera.zoom.toDouble()
            game.transformCamera(Offset(540f,1000f),Offset(delta.x.toFloat(),delta.y.toFloat()),1f)
        }
        compose.mainClock.advanceTimeBy(32);screenshot("arcade-distant-system.png")
        val codec=SessionRecovery(context);val saved=codec.decode(codec.encode(game.recoverySnapshot(73)!!))
        val keys=game.arcade!!.exploration!!.visited;val ids=game.bodies.map { it.id }.toSet()
        compose.runOnIdle { game.restoreRecovery(saved);game.closeMenu() };compose.mainClock.advanceTimeBy(32)
        assertTrue(game.arcade!!.exploration!!.visited.containsAll(keys))
        assertTrue(game.bodies.map { it.id }.toSet().containsAll(ids))
        assertEquals(game.bodies.size,game.bodies.map { it.id }.distinct().size)
    }
    @Test fun explorationWorksInStrategyAndOrdinarySandboxWithoutChangingTheirLaunchBudgets() {
        for (mode in AppMode.entries) {
            val random=SessionRandom(17)
            val game=SpaceGameState(random=random).apply { resize(androidx.compose.ui.unit.IntSize(1080,2340))
                when(mode) { AppMode.Arcade -> startArcade();AppMode.Strategy -> startStrategy();AppMode.Sandbox -> startSandbox(SandboxPresetKind.Empty) } }
            val state=random.state;val limit=game.spawnLimitFor(BodyKind.Ship);val energy=game.arcade?.energy
            repeat(6) { game.transformCamera(Offset(540f,1000f),Offset(-900f,0f),1f) }
            assertTrue(game.bodies.any { it.explorationRegion!=null })
            assertEquals(state,random.state);assertEquals(limit,game.spawnLimitFor(BodyKind.Ship))
            if (mode!=AppMode.Sandbox) assertEquals(energy,game.arcade?.energy)
            val saved=game.recoverySnapshot(73)!!;val codec=SessionRecovery(context)
            val decoded=codec.decode(codec.encode(saved))
            if (mode==AppMode.Sandbox) assertEquals(saved.sandbox!!.exploration,decoded.sandbox!!.exploration)
            else assertEquals(game.arcade!!.exploration,(if (mode==AppMode.Strategy) decoded.strategy else decoded.arcade)!!.exploration)
        }
    }
}
