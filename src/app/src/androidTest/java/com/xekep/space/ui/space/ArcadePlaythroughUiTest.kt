package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Accelerated game time, ordinary menu/buttons/gestures and normal resource limits. */
class ArcadePlaythroughUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun normalRunPassesTwentyWavesUsingTheActualControls() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=instrumentation.targetContext.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val oldMotion=prefs.getBoolean("motionControl",false)
        prefs.edit().putBoolean("motionControl",false).commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState(random=Random(17))
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("difficulty-Normal").performClick()
            compose.onNodeWithTag("menu-primary").performClick()
            compose.mainClock.advanceTimeByFrame()
            var phase=0; var cycles=0; var upgrades=0
            val timings=mutableListOf<Double>()
            val seen=mutableSetOf<Int>()
            fun simulate(steps: Int) { compose.runOnIdle { repeat(steps) {
                val started=System.nanoTime(); game.update(1.0/60)
                timings+=(System.nanoTime()-started)/1e6
            } } }
            while (game.arcade!!.lives > 0 && game.arcade!!.wave <= 20 && cycles++ < 660) {
                game.arcade!!.upgradeOffer?.let { offer ->
                    val order=if (game.arcade!!.lives <= 2) listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines)
                        else listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Repair)
                    val choice=order.first { it in offer.choices }
                    compose.onNodeWithTag("arcade-upgrade-${choice.name}").performClick(); upgrades++
                    compose.mainClock.advanceTimeByFrame()
                }
                val run=game.arcade!!; val core=run.bodies.first { it.kind == BodyKind.Core }
                val danger=run.bodies.filter { it.kind == BodyKind.Meteor }.minByOrNull { (it.position-core.position).magnitude() }
                val ships=run.bodies.filter { it.kind == BodyKind.Ship }
                val guardian=run.guardianUnlocked && ships.count { it.shipClass == ShipClass.Guardian } < 2
                var kind=if (ships.size < game.spawnLimitFor(BodyKind.Ship)) BodyKind.Ship else BodyKind.Ambient
                if (danger != null && (danger.position-core.position).magnitude() < 260 && run.energy >= 24 &&
                    game.spawnCountFor(BodyKind.Rocket) < game.spawnLimitFor(BodyKind.Rocket)) kind=BodyKind.Rocket
                val angle=phase*PI*(3-sqrt(5.0)); val radial=Vec2(cos(angle),sin(angle))
                var point=core.position+radial*(if (kind == BodyKind.Ambient) 160.0 else if (guardian) 235.0 else 300.0)
                var velocity=SimulationEngine.orbitVelocity(core,point)
                var route=emptyList<Vec2>()
                if (kind == BodyKind.Rocket && danger != null) {
                    val inward=(core.position-danger.position).normalized(); point=danger.position+inward*75.0; velocity=inward*-420.0
                } else if (kind == BodyKind.Ship && !guardian)
                    route=(1..4).map { i -> core.position+Vec2(cos(angle+i*PI/2),sin(angle+i*PI/2))*300.0 }
                val hold=if (kind == BodyKind.Ambient) .35 else if (kind == BodyKind.Ship) 1.0 else 0.0
                val cost=when(kind) { BodyKind.Ship -> if (guardian) 52.5 else 42.5; BodyKind.Rocket -> 24.0; else -> 21.0 }
                if (run.energy >= cost && (kind != BodyKind.Ambient || run.bodies.count { it.kind == BodyKind.Player } < 6) &&
                    run.bodies.none { (it.position-point).magnitude() < it.radius+22.0 }) {
                    val tag=if (kind == BodyKind.Ship && guardian) "Guardian" else kind.name
                    compose.onNodeWithTag("arcade-spawn-$tag").performClick()
                    compose.mainClock.advanceTimeByFrame()
                    if (kind != BodyKind.Ambient && game.loopFlightRoutes != route.isNotEmpty()) compose.onNodeWithTag("route-loop").performClick()
                    compose.mainClock.advanceTimeByFrame()
                    val start=worldToScreen(point,game.viewport,game.camera.center,game.camera.zoom)
                    val end=start+Offset((velocity.x/3*game.density).toFloat(),(velocity.y/3*game.density).toFloat())
                    val checkpoints=route.map { worldToScreen(it,game.viewport,game.camera.center,game.camera.zoom) }
                    val before=run.launches
                    compose.onNodeWithTag("space-scene").performTouchInput {
                        down(0,start)
                    }
                    simulate((hold*60).toInt())
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNodeWithTag("space-scene").performTouchInput {
                        advanceEventTime((hold*1000).toLong())
                        for (checkpoint in checkpoints) { down(1,checkpoint); advanceEventTime(16); up(1); advanceEventTime(16) }
                        moveTo(0,end); up(0)
                    }
                    if (game.arcade!!.launches > before) phase++
                }
                simulate(60)
                compose.mainClock.advanceTimeByFrame()
                val next=game.arcade!!
                if (next.wave in listOf(4,10,11,15,20,21) && seen.add(next.wave)) {
                    println("UI_PLAYTHROUGH,wave=${next.wave},lives=${next.lives},seconds=${next.elapsed},intercepts=${next.destroyed},launches=${next.launches},convoy=${next.convoy?.status}")
                    android.util.Log.i("SpacePlaytest","wave=${next.wave} lives=${next.lives} intercepts=${next.destroyed}")
                    compose.mainClock.advanceTimeBy(48)
                    compose.waitForIdle()
                    compose.onNodeWithTag("arcade-top-hud").assert(hasAnyDescendant(hasText(next.wave.toString())))
                    android.os.SystemClock.sleep(350)
                    instrumentation.waitForIdleSync()
                    File(instrumentation.targetContext.externalCacheDir,"arcade-playthrough-wave-${next.wave}.png").outputStream().use {
                        assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                    }
                }
            }
            val result=game.arcade!!
            val summary="UI_PLAYTHROUGH_RESULT,wave=${result.wave},lives=${result.lives},seconds=${result.elapsed},score=${result.score},intercepts=${result.destroyed},launches=${result.launches},upgrades=$upgrades,convoy=${result.convoy?.status}"
            val sorted=timings.sorted()
            val performance="UI_PHYSICS_STEP,samples=${sorted.size},meanMs=${sorted.average()},p95Ms=${sorted[(sorted.size*.95).toInt()]},maxMs=${sorted.last()}"
            println(summary); println(performance)
            File(instrumentation.targetContext.externalCacheDir,"arcade-playthrough-result.csv").writeText(summary+"\n"+performance+"\n")
            assertTrue("Run ended at wave ${result.wave}",result.wave >= 21)
            assertTrue(result.challenge?.rewarded == true && result.challenge?.failed == false)
            assertNotNull(result.planetId)
            assertEquals(ConvoyStatus.Delivered,result.convoy!!.status)
        } finally { prefs.edit().putBoolean("motionControl",oldMotion).commit() }
    }
}
