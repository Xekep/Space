package com.xekep.space.ui.space

import android.content.res.Configuration
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/** Opt-in measured review. Layout/encounter fixtures are not full playthroughs or phone timings. */
class ProfessionalAuditUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun optIn() = assumeTrue(InstrumentationRegistry.getArguments().getString("professionalAudit") == "true")
    private fun options(block: () -> Unit) {
        val prefs=context.getSharedPreferences("space_options",0)
        val keys=listOf("flightControl","music","sound","retroConsole")
        val previous=keys.associateWith { prefs.all[it] }
        prefs.edit().putString("flightControl","Joystick").putBoolean("music",false).putBoolean("sound",false).commit()
        try { block() } finally {
            prefs.edit().apply { for ((key,value) in previous) when(value) {
                null -> remove(key); is Boolean -> putBoolean(key,value); is String -> putString(key,value)
            } }.commit()
        }
    }
    private fun save(name: String,text: String) { File(context.externalCacheDir,"professional-$name.txt").writeText(text) }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        File(context.externalCacheDir,"professional-$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun fixture(game: SpaceGameState,run: ArcadeSession) {
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        (field.get(game) as MutableState<ArcadeSession?>).value=run
        SimulationEngine.reserveBodyIds(run.bodies+run.pending.map { it.body })
    }
    private data class Screen(val width: Int,val font: Float,val language: String,val retro: Boolean=false,val height: Int=640)

    @Test fun compactLargeFontAndLocalizedLayoutsMeasureBoundsAndClipping() {
        optIn(); options {
            compose.mainClock.autoAdvance=false
            val landscape=InstrumentationRegistry.getArguments().getString("landscapeAudit") == "true"
            val specs=if (landscape) listOf(Screen(600,1f,"ru",height=320),Screen(600,1.5f,"de",height=320)) else
                listOf(Screen(360,1f,"ru"),Screen(320,1f,"en"),Screen(320,1.5f,"de"),Screen(360,1.5f,"fr"),Screen(360,1f,"zh-Hans",true))
            var screen by mutableStateOf(specs.first())
            var game by mutableStateOf(SpaceGameState())
            compose.setContent {
                val current=screen
                val config=remember(current) { Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(current.language)); fontScale=current.font; screenWidthDp=current.width; screenHeightDp=current.height
                } }
                // Keep the Activity in the ContextWrapper chain so launchers and BackHandler
                // retain their owners; only this fixture's resources change locale.
                val host=LocalContext.current
                val localized=remember(current,host) {
                    val configured=host.createConfigurationContext(config)
                    object : ContextWrapper(host) {
                        override fun getResources()=configured.resources
                        override fun getAssets()=configured.assets
                    }
                }
                val density=LocalDensity.current.density
                Box(Modifier.requiredSize(current.width.dp,current.height.dp).testTag("audit-frame")) {
                    CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,
                        LocalDensity provides Density(density,current.font)) { SpaceTheme { SpaceSceneRoot(game) } }
                }
            }
            val rows=mutableListOf("screen,scene,tag,widthDp,heightDp,leftDp,rightDp")
            val findings=mutableListOf<String>()
            for (spec in specs) {
                for (scene in listOf("menu","arcade15","pilot","solar")) {
                    val key="${spec.width}x${spec.height}-${spec.font}-${spec.language}-${spec.retro}-$scene"
                    compose.runOnIdle {
                        context.getSharedPreferences("space_options",0).edit().putBoolean("retroConsole",spec.retro).commit()
                        screen=spec
                        game=SpaceGameState(random=Random(73)).apply {
                            resize(IntSize((spec.width*compose.density.density).toInt(),(spec.height*compose.density.density).toInt()))
                            when(scene) {
                                "menu" -> Unit
                                "solar" -> startSandbox(SandboxPresetKind.SolarSystem)
                                else -> {
                                    startArcade()
                                    if (scene == "arcade15") fixture(this,advanceArcade(arcade!!.copy(elapsed=395.0,spawnTimer=1000.0,score=123456.0,combo=3.0),.01,Random(73)))
                                    if (scene == "pilot") {
                                        setMotionControlEnabled(true); chooseSpawnKind(BodyKind.Ship)
                                        launch(TouchPreview(Vec2(400.0,400.0),Vec2(400.0,300.0),0),0.0)
                                        setPilotTargetSpeed(240.0)
                                    }
                                }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeBy(48)
                    val frame=compose.onNodeWithTag("audit-frame").fetchSemanticsNode().boundsInRoot
                    val tags=listOf("menu-primary","menu-language","arcade-top-hud","arcade-spawn-Ship","arcade-spawn-Guardian",
                        "arcade-motion-control","pilot-hud","flight-joystick","pitch-joystick","pilot-speed","pilot-fuel")
                    val bounds=linkedMapOf<String,androidx.compose.ui.geometry.Rect>()
                    for (tag in tags) {
                        val node=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: continue
                        val b=node.boundsInRoot; bounds[tag]=b
                        rows+="$key,$scene,$tag,${b.width/compose.density.density},${b.height/compose.density.density},${(b.left-frame.left)/compose.density.density},${(b.right-frame.left)/compose.density.density}"
                        if (b.left < frame.left-.5 || b.right > frame.right+.5) findings+="$key OUTSIDE $tag $b frame=$frame"
                        if (tag == "pilot-speed") assertTrue("$key: speed touch zone ${b.width/compose.density.density}dp",b.width/compose.density.density >= 47.9f)
                    }
                    for ((a,b) in listOf("flight-joystick" to "pilot-speed","pilot-speed" to "pilot-fuel","pilot-fuel" to "pitch-joystick")) {
                        val x=bounds[a] ?: continue; val y=bounds[b] ?: continue
                        if (x.overlaps(y)) findings+="$key OVERLAP $a / $b ${(x.right-y.left)/compose.density.density}dp"
                    }
                    val matcher=SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
                    val texts=compose.onAllNodes(matcher,useUnmergedTree=true)
                    val count=texts.fetchSemanticsNodes().size
                    for (i in 0 until count) {
                        val layouts=mutableListOf<TextLayoutResult>()
                        texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        for (layout in layouts) if (layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
                            findings+="$key CLIPPED ${layout.layoutInput.text.text} height=${layout.didOverflowHeight}"
                    }
                    shot(key)
                    if (scene == "menu" && landscape) {
                        // A successful text-layout probe can miss clipping by a scroll parent.
                        // Measure the visible primary action, then verify both actions can be reached.
                        val initial=compose.onNodeWithTag("menu-primary").fetchSemanticsNode().boundsInRoot
                        val visibleHeight=initial.height/compose.density.density
                        if (visibleHeight < 48) findings+="$key PARTIAL primary action ${visibleHeight}dp at initial scroll"
                        // Scroll semantics schedule frames; a frozen diagnostic clock otherwise
                        // leaves performScrollTo waiting for its own scroll to become visible.
                        compose.mainClock.autoAdvance=true
                        try {
                            compose.onNodeWithTag("menu-primary").performScrollTo()
                            assertTrue(compose.onNodeWithTag("menu-primary").fetchSemanticsNode().boundsInRoot.height/compose.density.density >= 48)
                            compose.onNodeWithTag("menu-language").performScrollTo().assertIsDisplayed()
                        } finally { compose.mainClock.autoAdvance=false }
                        shot("$key-scrolled")
                    }
                }
            }
            val suffix=if (landscape) "-landscape" else ""
            save("layout-bounds$suffix",rows.joinToString("\n")); save("layout-findings$suffix",findings.joinToString("\n"))
            println("PROFESSIONAL_LAYOUT,states=${specs.size*4},findings=${findings.size}")
            assertTrue(findings.joinToString("\n"),findings.none { it.contains("PARTIAL primary") || it.contains("CLIPPED 100%") })
            assertTrue(rows.size > specs.size*4) // Report defects rather than hiding them behind an early exit.
        }
    }

    @Test fun realSticksMeasureThrottleCaptureFirstResponseNeutralAndContextSwitch() {
        optIn(); options {
            compose.mainClock.autoAdvance=false
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            val rows=mutableListOf("scene,kind,phase,elapsed,target,speed,height,pitch,roll,fuel,cameraRotation")
            fun record(scene: String,kind: BodyKind,phase: String) {
                val body=game.bodies.first { it.id == game.controlledVehicleId }
                rows+="$scene,$kind,$phase,${if (game.mode == AppMode.Arcade) game.arcade!!.elapsed else game.presentationAge},${body.pilotTargetSpeed},${body.flightSpeed()},${body.flightHeight},${body.pitch},${body.roll},${body.fuelRemaining},${game.cameraRotation}"
            }
            for (scene in listOf("Arcade","Empty","SolarSystem")) for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
                compose.runOnIdle {
                    if (scene == "Arcade") game.startArcade() else game.startSandbox(SandboxPresetKind.valueOf(scene))
                    if (scene == "SolarSystem") game.setTimeScale(.0001)
                    game.setMotionControlEnabled(true); game.chooseSpawnKind(kind)
                    game.launch(TouchPreview(Vec2(4000.0,4000.0),Vec2(4000.0,3900.0),0),0.0)
                    game.setPilotTargetSpeed(720.0)
                }
                compose.mainClock.advanceTimeBy(48)
                val id=game.controlledVehicleId!!
                record(scene,kind,"before-left-touch")
                compose.onNodeWithTag("flight-joystick").performTouchInput { down(center); moveTo(center+Offset(28*game.density,0f),16) }
                compose.mainClock.advanceTimeByFrame(); record(scene,kind,"yaw-only-at-center")
                compose.runOnIdle { assertEquals(720.0,game.bodies.first { it.id == id }.pilotTargetSpeed!!,1e-5) }
                compose.onNodeWithTag("flight-joystick").performTouchInput { up() }
                compose.onNodeWithTag("pitch-joystick").performTouchInput { down(center); moveTo(center+Offset(0f,32*game.density),16) }
                record(scene,kind,"pitch-before")
                compose.runOnIdle { repeat(6) { game.update(1.0/60) } }
                record(scene,kind,"pitch-100ms")
                compose.runOnIdle { repeat(24) { game.update(1.0/60) } }
                record(scene,kind,"pitch-500ms")
                shot("pilot-$scene-$kind-held")
                compose.onNodeWithTag("pitch-joystick").performTouchInput { up() }
                compose.runOnIdle { repeat(60) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame(); record(scene,kind,"neutral-1s")
                assertEquals(id,game.controlledVehicleId)
                assertTrue(abs(game.bodies.first { it.id == id }.pitch) < .04)
                val zoom=game.camera.zoom
                compose.onNodeWithTag("space-scene").performTouchInput {
                    down(0,center-Offset(90f,0f)); down(1,center+Offset(90f,0f))
                    moveTo(0,center-Offset(140f,0f)); moveTo(1,center+Offset(140f,0f)); up(0); up(1)
                }
                compose.mainClock.advanceTimeBy(48)
                assertEquals(id,game.controlledVehicleId); assertTrue(game.camera.zoom > zoom)
                compose.onNodeWithTag("open-menu").performClick(); compose.mainClock.advanceTimeBy(48)
                val age=if (game.mode == AppMode.Arcade) game.arcade!!.elapsed else game.presentationAge
                compose.runOnIdle { game.update(.8) }
                assertEquals(age,if (game.mode == AppMode.Arcade) game.arcade!!.elapsed else game.presentationAge,1e-8)
                compose.onNodeWithTag("menu-primary").performScrollTo().performClick(); compose.mainClock.advanceTimeBy(48)
                assertEquals(id,game.controlledVehicleId)
                record(scene,kind,"resume")
                compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(-180f,-250f)) }
                compose.mainClock.advanceTimeBy(48)
                record(scene,kind,"new-craft-takes-control")
                assertNotEquals(id,game.controlledVehicleId)
                compose.onNodeWithTag("pilot-exit").performClick(); compose.mainClock.advanceTimeBy(48)
                assertNull(game.controlledVehicleId)
            }
            save("controls",rows.joinToString("\n")); println("PROFESSIONAL_CONTROL,scenes=3,kinds=2")
        }
    }
}
