package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArcadeFleetUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun twoShipsLaunchedOnMeetingCoursesAvoidOneAnother()=meeting(BodyKind.Ship)
    @Test fun launchedRocketAndShipAvoidOneAnother()=meeting(BodyKind.Rocket)

    @Test @Suppress("UNCHECKED_CAST")
    fun launchedMixedFleetSharesTargetsAndReassignsAfterTheirRemoval() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        val state=field.get(game) as MutableState<ArcadeSession?>
        state.value=game.arcade!!.copy(spawnTimer=1000.0)
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        val core=game.bodies.first { it.kind == BodyKind.Core }
        for ((index,kind) in listOf(BodyKind.Ship,BodyKind.Rocket,BodyKind.Rocket).withIndex()) {
            compose.onNodeWithTag("arcade-spawn-${kind.name}").performClick()
            compose.mainClock.advanceTimeByFrame()
            val point=core.position+Vec2((index-1)*220.0,-350.0)
            val start=worldToScreen(point,game.viewport,game.camera.center,game.camera.zoom)
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(start); advanceEventTime(120); moveTo(start+Offset(0f,-15f*game.density)); up()
            }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle {
            assertEquals(3,game.arcade!!.launches)
            val first=CelestialBody(900001,core.position+Vec2(-1100.0,-1500.0),Vec2.Zero,
                800.0,20f,androidx.compose.ui.graphics.Color.Red,BodyKind.Meteor)
            val second=first.copy(id=900002,position=core.position+Vec2(1100.0,-1500.0))
            state.value=game.arcade!!.copy(bodies=game.bodies+listOf(first,second))
            repeat(120) {
                game.update(1.0/60)
                val counts=game.arcade!!.combat.craft.values.mapNotNull { it.targetId }.groupingBy { it }.eachCount()
                assertEquals(setOf(first.id,second.id),counts.keys)
                assertTrue(counts.values.all { it <= 2 })
            }
            state.value=game.arcade!!.copy(bodies=game.bodies.filterNot { it.id == first.id })
            game.update(1.0/60)
            val counts=game.arcade!!.combat.craft.values.mapNotNull { it.targetId }.groupingBy { it }.eachCount()
            assertEquals(mapOf(second.id to 2),counts)
            assertEquals(3,game.bodies.count { it.isVehicle })
        }
    }

    @Test @Suppress("UNCHECKED_CAST")
    fun aCloserRocketLaunchedByTouchTakesTheTargetAndRedirectsTheOldPursuer() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
        val state=field.get(game) as MutableState<ArcadeSession?>
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            lateinit var old: CelestialBody
            lateinit var first: CelestialBody
            lateinit var next: CelestialBody
            compose.runOnIdle {
                game.startArcade()
                val core=game.bodies.first { it.kind == BodyKind.Core }
                old=CelestialBody(900001,core.position+Vec2(-100.0,-650.0),Vec2.Zero,
                    24.0,8f,androidx.compose.ui.graphics.Color.Cyan,kind,heading=Vec2(1.0,0.0))
                first=old.copy(id=900002,position=core.position+Vec2(900.0,-650.0),
                    mass=800.0,radius=20f,kind=BodyKind.Meteor)
                next=first.copy(id=900003,position=first.position+Vec2(900.0,0.0))
                state.value=game.arcade!!.copy(bodies=listOf(core,old,first,next),spawnTimer=1000.0,
                    combat=ArcadeCombat(craft=mapOf(old.id to CraftStatus(cooldown=10.0,targetId=first.id))))
                game.fitCamera()
                game.transformCamera(Offset(game.viewport.width/2f,game.viewport.height/2f),Offset.Zero,.4f)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("arcade-spawn-Rocket").performClick()
            compose.mainClock.advanceTimeByFrame()
            val start=worldToScreen(first.position+Vec2(-240.0,0.0),game.viewport,game.camera.center,game.camera.zoom)
            compose.onNodeWithTag("space-scene").performTouchInput { click(start) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                game.update(1.0/60)
                assertEquals(1,game.arcade!!.launches)
                val launched=game.bodies.single { it.kind == BodyKind.Rocket && it.id != old.id }
                assertEquals(first.id,game.arcade!!.combat.craft.getValue(launched.id).targetId)
                assertEquals(next.id,game.arcade!!.combat.craft.getValue(old.id).targetId)
                repeat(30) { game.update(1.0/60) }
                assertEquals(first.id,game.arcade!!.combat.craft.getValue(launched.id).targetId)
                assertEquals(next.id,game.arcade!!.combat.craft.getValue(old.id).targetId)
                val counts=game.arcade!!.combat.craft.values.mapNotNull { it.targetId }.groupingBy { it }.eachCount()
                assertEquals(setOf(first.id,next.id),counts.keys)
                assertTrue(counts.values.all { it <= 2 })
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun meeting(second: BodyKind) {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("menu-primary").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            val run=game.arcade!!
            val field=SpaceGameState::class.java.getDeclaredField("arcade\$delegate").apply { isAccessible=true }
            (field.get(game) as MutableState<ArcadeSession?>).value=
                run.copy(bodies=run.bodies.filter { it.kind == BodyKind.Core },spawnTimer=1000.0)
        }
        for ((index,kind) in listOf(BodyKind.Ship,second).withIndex()) {
            compose.onNodeWithTag("arcade-spawn-${kind.name}").performClick()
            compose.mainClock.advanceTimeByFrame()
            val core=game.bodies.first { it.kind == BodyKind.Core }
            val point=core.position+Vec2(if (index == 0) -300.0 else 300.0,-650.0)
            val start=worldToScreen(point,game.viewport,game.camera.center,game.camera.zoom)
            val end=start+Offset((if (index == 0) 70f else -70f)*game.density,0f)
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(start); advanceEventTime(300); moveTo(end); up()
            }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { assertEquals(2,game.bodies.count { it.isVehicle }) }
        var nearest=Double.POSITIVE_INFINITY
        repeat(40) {
            compose.runOnIdle {
                repeat(6) {
                    game.update(1.0/60)
                    val craft=game.bodies.filter { it.isVehicle }
                    assertEquals("Friendly craft exploded",2,craft.size)
                    nearest=minOf(nearest,(craft[0].position-craft[1].position).magnitude())
                    assertTrue(nearest > craft.sumOf { it.radius.toDouble() })
                }
            }
            compose.mainClock.advanceTimeByFrame()
        }
        println("FLEET_UI_MEETING,second=$second,minDistance=$nearest,launches=${game.arcade!!.launches}")
    }
}
