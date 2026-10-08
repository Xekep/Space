package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

/** Play through ordinary state/launch APIs: no injected enemies, fuel, energy or lives. */
class ArcadePlaythroughTest {
    @Test fun mixedDefenseAttemptsEveryImplementedMilestoneWithNormalRules() {
        var normalReached20=false
        for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(17,73)) {
            val game=SpaceGameState(random=Random(seed)).apply { resize(IntSize(1080,2340)); startArcade(difficulty) }
            var action=0.0; var phase=0; var upgrades=0; var ticks=0
            var planet=false; var convoy=false; var challenge=false; var maxShots=0
            while (game.arcade!!.lives > 0 && game.arcade!!.wave <= 20 && ticks < 21000) {
                val run=game.arcade!!
                run.upgradeOffer?.let { offer ->
                    val order=if (run.lives <= 2) listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines)
                        else listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Repair)
                    game.chooseArcadeUpgrade(order.first { it in offer.choices }); upgrades++
                }
                action-=1.0/30
                if (action <= 0) {
                    val current=game.arcade!!; val core=current.bodies.first { it.kind == BodyKind.Core }
                    val threats=current.bodies.filter { it.kind == BodyKind.Meteor }
                    val danger=threats.minByOrNull { (it.position-core.position).magnitude() }
                    val ships=current.bodies.filter { it.kind == BodyKind.Ship }
                    val guard=current.guardianUnlocked && ships.count { it.shipClass == ShipClass.Guardian } < 2
                    var kind=if (ships.size < current.launchLimit(BodyKind.Ship)) BodyKind.Ship else BodyKind.Ambient
                    if (danger != null && (danger.position-core.position).magnitude() < 260 && current.energy >= 24 &&
                        current.bodies.count { it.kind == BodyKind.Rocket } < current.launchLimit(BodyKind.Rocket)) kind=BodyKind.Rocket
                    if (kind == BodyKind.Ambient && current.bodies.count { it.kind == BodyKind.Player } >= 6) { action=.4; continue }
                    val angle=phase*PI*(3-sqrt(5.0)); val radial=Vec2(cos(angle),sin(angle))
                    var point=core.position+radial*(if (kind == BodyKind.Ambient) 160.0 else if (guard) 235.0 else 300.0)
                    var velocity=SimulationEngine.orbitVelocity(core,point)
                    var route=emptyList<Vec2>()
                    if (kind == BodyKind.Rocket && danger != null) {
                        val inward=(core.position-danger.position).normalized()
                        point=danger.position+inward*75.0; velocity=inward*-420.0
                    } else if (kind == BodyKind.Ship && !guard) {
                        route=(1..4).map { i -> core.position+Vec2(cos(angle+i*PI/2),sin(angle+i*PI/2))*300.0 }
                    }
                    if (current.bodies.none { (it.position-point).magnitude() < it.radius+22.0 }) {
                        game.chooseSpawnKind(kind)
                        if (kind == BodyKind.Ship) game.chooseArcadeShipClass(if (guard) ShipClass.Guardian else ShipClass.Interceptor)
                        game.loopFlightRoutes=route.isNotEmpty()
                        val drag=Offset((velocity.x/3).toFloat(),(velocity.y/3).toFloat())
                        val preview=TouchPreview(point,point+velocity/3.0,0,drag,route)
                        val hold=if (kind == BodyKind.Ambient) .35 else if (kind == BodyKind.Ship) 1.0 else .5
                        val candidate=game.previewBody(preview,hold)
                        if (candidate != null && current.energy >= launchCost(candidate)) {
                            val before=current.launches; game.launch(preview,hold)
                            if (game.arcade!!.launches > before) { phase++; action=.4 }
                        }
                    }
                    if (action <= 0) action=.2
                }
                game.update(1.0/30); ticks++
                val next=game.arcade!!
                planet=planet || next.planetId != null; convoy=convoy || next.convoy != null; challenge=challenge || next.challenge != null
                maxShots=maxOf(maxShots,next.combat.projectiles.size)
                assertTrue(next.energy in 0.0..next.maxEnergy)
                assertTrue(next.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
            }
            val result=game.arcade!!
            println("FULL_ARCADE,difficulty=$difficulty,seed=$seed,seconds=${result.elapsed},wave=${result.wave},lives=${result.lives},score=${result.score},intercepts=${result.destroyed},launches=${result.launches},upgrades=$upgrades,giant=$challenge,planet=$planet,convoy=${result.convoy?.status},maxShots=$maxShots")
            assertTrue(maxShots <= 64)
            if (difficulty == ArcadeDifficulty.Normal && result.wave >= 20) normalReached20=true
        }
        assertTrue("A normal run should reach the late milestones with a mixed defense",normalReached20)
    }
}
