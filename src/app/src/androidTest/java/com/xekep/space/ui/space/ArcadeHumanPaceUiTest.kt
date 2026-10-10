package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
import kotlin.random.Random

/** Opt-in diagnostic, not a claim of human play or an assertion that every run should win.
 * Actions read only visible bodies and HUD values: no pending threats, precise orbit velocity,
 * ballistic prediction, injected resources, or damage immunity. gentleLaunch compares shorter
 * finger gestures as a control hypothesis; it does not call the orbit solver. Game time includes reaction,
 * selection, holding and route authoring. UI buttons and touch gestures perform all actions.
 */
class ArcadeHumanPaceUiTest {
    @get:Rule val compose=createComposeRule()
    private enum class Skill { Basic, Familiar }
    private data class Plan(val kind: BodyKind, val guardian: Boolean, val point: Vec2,
        val drag: Vec2, val hold: Double, val route: List<Vec2>)

    @Test fun easyBasic()=play(ArcadeDifficulty.Easy,Skill.Basic)
    @Test fun easyFamiliar()=play(ArcadeDifficulty.Easy,Skill.Familiar)
    @Test fun normalBasic()=play(ArcadeDifficulty.Normal,Skill.Basic)
    @Test fun normalFamiliar()=play(ArcadeDifficulty.Normal,Skill.Familiar)
    @Test fun hardBasic()=play(ArcadeDifficulty.Hard,Skill.Basic)
    @Test fun hardFamiliar()=play(ArcadeDifficulty.Hard,Skill.Familiar)

    private fun play(difficulty: ArcadeDifficulty, skill: Skill) {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Long balance diagnostics require -e balancePlaytest true",args.getString("balancePlaytest") == "true")
        val seed=args.getString("seed")?.toIntOrNull() ?: 17
        val gentle=args.getString("gentleLaunch") == "true"
        val heavyFleet=args.getString("heavyFleet") == "true"
        val stableIds=args.getString("stableBodyIds") == "true"
        // Isolated diagnostic worlds only. Reset tie-breaking IDs before either comparison
        // policy starts, so previous test methods cannot select a different avoidance side.
        if (stableIds) {
            val field=SimulationEngine::class.java.getDeclaredField("idSource").apply { isAccessible=true }
            (field.get(SimulationEngine) as AtomicLong).set(1L)
        }
        val game=SpaceGameState(random=Random(seed)) // callbacks deliberately do not write user records
        val hand=Random(seed+991)
        val key="${difficulty.name}-${skill.name}-$seed"+(if (gentle) "-gentle" else "")+(if (heavyFleet) "-heavy" else "")+(if (stableIds) "-stable" else "")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val rows=mutableListOf("seconds,wave,lives,energy,ships,rockets,launches,intercepts,convoy,event")
        var phase=0; var attempts=0; var blocked=0; var heavy=0; var upgrades=0
        var rockets=0; var ships=0; var guardians=0; var bodies=0
        var waits=0.0; var lowestEnergy=120.0; var lostCraft=0; var lastWave=0; var lastLives=difficulty.lives
        val lostByWave=linkedMapOf<Int,Int>()
        compose.mainClock.autoAdvance=false
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("difficulty-${difficulty.name}").performClick()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-motion-control").assertIsOff()

        fun record(event: String) {
            val r=game.arcade!!
            rows+="${r.elapsed},${r.wave},${r.lives},${r.energy},${r.bodies.count { it.kind == BodyKind.Ship }},${r.bodies.count { it.kind == BodyKind.Rocket }},${r.launches},${r.destroyed},${r.convoy?.status},$event"
        }
        fun simulate(seconds: Double) {
            compose.runOnIdle { repeat((seconds*30).roundToInt()) {
                val before=game.arcade!!
                val ids=before.bodies.filter { it.isVehicle }.map { it.id }.toSet()
                game.update(1.0/30)
                val r=game.arcade!!
                lostCraft+=(ids-r.bodies.map { it.id }.toSet()).size
                lowestEnergy=minOf(lowestEnergy,r.energy)
                assertTrue(r.energy in 0.0..r.maxEnergy+1e-8)
                assertTrue(r.combat.projectiles.size <= 64)
                assertTrue(r.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
                if (r.lives < lastLives) {
                    lostByWave[r.wave]=(lostByWave[r.wave] ?: 0)+lastLives-r.lives
                    lastLives=r.lives; record("core-hit")
                } else lastLives=r.lives
            } }
        }
        fun screenshot(suffix: String) {
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            android.os.SystemClock.sleep(150)
            instrumentation.waitForIdleSync()
            File(context.externalCacheDir,"balance-$key-$suffix.png").outputStream().use {
                assertTrue(instrumentation.uiAutomation.takeScreenshot()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
        }
        fun visible(body: CelestialBody): Boolean {
            val p=worldToScreen(body.position,game.viewport,game.camera.center,game.camera.zoom)
            return p.x in game.viewport.width*.06f..game.viewport.width*.94f &&
                p.y in game.viewport.height*.15f..game.viewport.height*.76f
        }
        fun plan(): Plan? {
            val r=game.arcade!!
            val seen=r.bodies.filter(::visible)
            val core=seen.firstOrNull { it.kind == BodyKind.Core } ?: return null
            val danger=seen.filter { it.kind == BodyKind.Meteor }.minByOrNull { (it.position-core.position).magnitude() }
            val urgent=danger != null && (danger.position-core.position).magnitude() < 330
            val guardian=r.guardianUnlocked && seen.count { it.kind == BodyKind.Ship && it.shipClass == ShipClass.Guardian } <
                (if (skill == Skill.Familiar) 2 else 1)
            val kind=when {
                urgent && r.energy >= 26 && game.spawnCountFor(BodyKind.Rocket) < game.spawnLimitFor(BodyKind.Rocket) -> BodyKind.Rocket
                game.spawnCountFor(BodyKind.Ship) < game.spawnLimitFor(BodyKind.Ship) && r.energy >= if (guardian) 54 else if (heavyFleet) 60 else 44 -> BodyKind.Ship
                game.spawnCountFor(BodyKind.Ambient) < minOf(6,game.spawnLimitFor(BodyKind.Ambient)) && r.energy >= 35 -> BodyKind.Ambient
                else -> return null
            }
            val angles=listOf(.2,1.3,2.4,3.6,4.8)
            val angle=angles[phase%angles.size]+hand.nextDouble(-.18,.18)
            val radial=Vec2(cos(angle),sin(angle))
            var point=core.position+radial*((if (kind == BodyKind.Ambient) 175 else if (guardian) 245 else 300)+hand.nextDouble(-18.0,18.0))
            var aim=radial.perpendicular()
            var flick=when {
                gentle && kind == BodyKind.Ship -> hand.nextDouble(33.0,45.0)
                gentle && kind == BodyKind.Ambient -> hand.nextDouble(43.0,51.0)
                else -> hand.nextDouble(68.0,82.0)
            } // fixed, approximate dp flick, no orbit solver
            if (kind == BodyKind.Rocket && danger != null) {
                val direction=(danger.position-core.position).normalized()
                point=core.position+direction*(minOf(250.0,(danger.position-core.position).magnitude()-70).coerceAtLeast(120.0))
                aim=(danger.position-point).normalized(); flick=hand.nextDouble(112.0,140.0)
            }
            aim=rotateVector(aim,hand.nextDouble(if (skill == Skill.Basic) -.2 else -.1,if (skill == Skill.Basic) .2 else .1))
            val route=if (skill == Skill.Familiar && kind == BodyKind.Ship && !guardian) (1..4).map { i ->
                val phaseAngle=angle+i*PI/2
                core.position+Vec2(cos(phaseAngle),sin(phaseAngle))*(300+hand.nextDouble(-12.0,12.0))
            } else emptyList()
            val strong=skill == Skill.Familiar && kind != BodyKind.Ambient && r.energy >= 80 &&
                danger != null && danger.radius > 26 && !urgent
            // Audit policy: heavy vehicles pay both their actual price and the longer hold time.
            // Guardians keep their original role; no energy, targets or damage are injected.
            val hold=if (heavyFleet && guardian && kind == BodyKind.Ship) .6
                else if (strong || (heavyFleet && (kind == BodyKind.Rocket || (kind == BodyKind.Ship && !guardian)))) 2.2
                else when(kind) { BodyKind.Ship -> .6; BodyKind.Rocket -> .12; else -> .35 }
            return Plan(kind,guardian,point,aim*flick,hold,route)
        }

        var cycles=0
        while (game.arcade!!.lives > 0 && game.arcade!!.wave <= 20 && game.arcade!!.elapsed < 700 && cycles++ < 1800) {
            val r=game.arcade!!
            if (r.wave != lastWave) {
                record("wave-start"); lastWave=r.wave
                println("HUMAN_PACE_WAVE,$key,wave=${r.wave},lives=${r.lives},energy=${r.energy},launches=${r.launches}")
                if (r.wave in listOf(1,4,10,11,15,20)) screenshot("wave-${r.wave}")
            }
            r.upgradeOffer?.let { offer ->
                val order=if (r.lives < difficulty.lives) listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines)
                    else listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Repair)
                val choice=order.first { it in offer.choices }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("arcade-upgrade-${choice.name}").performClick()
                compose.mainClock.advanceTimeByFrame(); upgrades++; record("upgrade-${choice.name}")
            }
            val action=plan()
            if (action == null) { simulate(.8); waits+=.8; compose.mainClock.advanceTimeByFrame(); continue }
            // The planned target is stale by the time the player reacts. Do not predict its future position.
            simulate(if (skill == Skill.Basic) .95 else .65)
            compose.mainClock.advanceTimeByFrame()
            if (game.arcade!!.lives <= 0 || game.arcadeUpgradePending) continue
            val tag=if (action.kind == BodyKind.Ship && action.guardian) "Guardian" else action.kind.name
            compose.onNodeWithTag("arcade-spawn-$tag").performClick()
            compose.mainClock.advanceTimeByFrame(); simulate(.25)
            if (game.arcadeUpgradePending) continue
            if (action.kind != BodyKind.Ambient && game.loopFlightRoutes != action.route.isNotEmpty()) {
                compose.onNodeWithTag("route-loop").performClick(); compose.mainClock.advanceTimeByFrame(); simulate(.15)
            }
            val start=worldToScreen(action.point,game.viewport,game.camera.center,game.camera.zoom)
            val width=game.viewport.width.toFloat(); val height=game.viewport.height.toFloat()
            if (start.x !in width*.06f..width*.94f || start.y !in height*.15f..height*.76f) {
                phase++; blocked++; continue
            }
            val end=start+Offset((action.drag.x*game.density).toFloat(),(action.drag.y*game.density).toFloat())
            val safeEnd=Offset(end.x.coerceIn(width*.05f,width*.95f),end.y.coerceIn(height*.15f,height*.76f))
            val checkpoints=action.route.map { worldToScreen(it,game.viewport,game.camera.center,game.camera.zoom) }
            val before=game.arcade!!.launches
            val ids=game.bodies.map { it.id }.toSet()
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(0,start)
                if (gentle) moveTo(0,safeEnd,180)
            }
            simulate(action.hold+.18+checkpoints.size*.16)
            compose.mainClock.advanceTimeByFrame()
            if (gentle && action.kind == BodyKind.Ship && ships == 0) screenshot("launch-preview")
            compose.onNodeWithTag("space-scene").performTouchInput {
                advanceEventTime((action.hold*1000).roundToLong())
                for (point in checkpoints) {
                    down(1,point); advanceEventTime(80); up(1); advanceEventTime(80)
                }
                if (!gentle) moveTo(0,safeEnd,180); up(0)
            }
            attempts++; phase++
            if (game.arcade!!.launches > before) {
                val created=game.bodies.firstOrNull { it.id !in ids && it.kind != BodyKind.Meteor }
                if (created?.hullClass == VehicleHullClass.Heavy) heavy++
                when (action.kind) { BodyKind.Ship -> { ships++; if (action.guardian) guardians++ }; BodyKind.Rocket -> rockets++; else -> bodies++ }
                record("launch-$tag")
            } else { blocked++; record("launch-blocked") }
            simulate(if (skill == Skill.Basic) .35 else .2)
            compose.mainClock.advanceTimeByFrame()
        }
        val result=game.arcade!!
        val summary="HUMAN_PACE_RESULT,$key,wave=${result.wave},lives=${result.lives},seconds=${result.elapsed},score=${result.score},intercepts=${result.destroyed},launches=${result.launches},attempts=$attempts,blocked=$blocked,ships=$ships,guardians=$guardians,rockets=$rockets,bodies=$bodies,heavy=$heavy,upgrades=$upgrades,lowEnergy=$lowestEnergy,waitingSeconds=$waits,lostCraft=$lostCraft,coreHits=$lostByWave,giantFailed=${result.challenge?.failed},convoy=${result.convoy?.status}"
        println(summary); record("end")
        File(context.externalCacheDir,"balance-$key.csv").writeText(rows.joinToString("\n")+"\n")
        File(context.externalCacheDir,"balance-$key-result.txt").writeText(summary+"\n")
        screenshot("final")
        assertTrue("Diagnostic hit its safety bound",result.wave >= 21 || result.lives == 0 || result.elapsed >= 700)
    }
}
