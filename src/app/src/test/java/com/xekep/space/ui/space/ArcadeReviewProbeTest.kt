package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Opt-in review probes; exact state access is an automated ceiling, not human play. */
class ArcadeReviewProbeTest {
    private fun output(name: String,rows: List<String>) {
        val folder=File(requireNotNull(System.getProperty("professionalAuditDir"))).apply { mkdirs() }
        File(folder,name).writeText(rows.joinToString("\n")+"\n")
    }
    @Test fun twoShipBudgetAcrossSwarmSiegeAndPincer() {
        assumeTrue(System.getProperty("professionalAudit") == "true")
        val rows=mutableListOf("encounter,mass,cost,remainingEnergy,killed,coreHits,remainingThreatMass,shipsSurvived")
        for (encounter in listOf("swarm","siege","pincer")) for (mass in listOf(24.0,60.0,96.0)) {
            val core=CelestialBody(1,Vec2(450.0,700.0),Vec2.Zero,8000.0,48f,Color.Cyan,BodyKind.Core)
            val ships=List(2) { i -> CelestialBody(10L+i,Vec2(350.0,650.0+i*100),Vec2(0.0,-80.0),mass,
                (8*vehicleSizeScale(BodyKind.Ship,mass)).toFloat(),Color.Cyan,BodyKind.Ship) }
            val enemies=when(encounter) {
                "swarm" -> List(5) { i -> CelestialBody(100L+i,Vec2(950.0,580.0+i*60),Vec2(-80.0,0.0),80.0,SimulationEngine.radiusForMass(80.0),Color.Yellow,BodyKind.Meteor) }
                "siege" -> listOf(CelestialBody(100,Vec2(1050.0,700.0),Vec2(-65.0,0.0),1000.0,SimulationEngine.radiusForMass(1000.0),Color.Yellow,BodyKind.Meteor))
                else -> List(4) { i -> CelestialBody(100L+i,Vec2(if (i%2 == 0) 1000.0 else -100.0,570.0+(i/2)*260),
                    Vec2(if (i%2 == 0) -80.0 else 80.0,0.0),180.0,SimulationEngine.radiusForMass(180.0),Color.Yellow,BodyKind.Meteor) }
            }
            SimulationEngine.reserveBodyIds(listOf(core)+ships+enemies)
            val price=ships.sumOf(::launchCost)
            var run=ArcadeSession(listOf(core)+ships+enemies,SpaceCamera(core.position,1f),IntSize(900,1400),ArcadeDifficulty.Normal,
                energy=120-price,spawnTimer=10000.0)
            repeat(18*60) { run=advanceArcade(run,1.0/60,Random(17)) }
            rows+="$encounter,$mass,$price,${120-price},${run.destroyed},${4-run.lives},${run.bodies.filter { it.kind == BodyKind.Meteor }.sumOf { it.mass }},${run.bodies.count { it.kind == BodyKind.Ship }}"
            assertTrue(run.energy in 0.0..run.maxEnergy)
        }
        output("role-encounters.csv",rows)
    }

    @Test fun solarManualControlClocksAtDifferentSimulationSpeeds() {
        assumeTrue(System.getProperty("professionalAudit") == "true")
        val rows=mutableListOf("timeScale,phase,speed,planarSpeed,height,pitch,fuel,target")
        for (scale in listOf(1.0,.25,.01,.0001)) {
            val game=SpaceGameState().apply {
                resize(IntSize(1080,2340)); startSandbox(SandboxPresetKind.SolarSystem); setTimeScale(scale)
                setMotionControlEnabled(true); chooseSpawnKind(BodyKind.Ship)
                launch(TouchPreview(Vec2(4000.0,4000.0),Vec2(4000.0,3900.0),0,Offset(0f,-100f)),0.0)
                setPilotTargetSpeed(720.0); setJoystickInput(Vec2(.3,0.0)); setAttitudeJoystickInput(Vec2(0.0,.8))
            }
            val id=game.controlledVehicleId!!
            fun record(phase: String) {
                val craft=game.bodies.first { it.id == id }
                rows+="$scale,$phase,${craft.flightSpeed()},${craft.velocity.magnitude()},${craft.flightHeight},${craft.pitch},${craft.fuelRemaining},${craft.pilotTargetSpeed}"
            }
            repeat(6) { game.update(1.0/60) }; record("held-100ms")
            repeat(54) { game.update(1.0/60) }; record("held-1s")
            game.setJoystickInput(Vec2.Zero); game.setAttitudeJoystickInput(Vec2.Zero)
            repeat(60) { game.update(1.0/60) }; record("neutral-1s")
            assertTrue(game.bodies.first { it.id == id }.fuelRemaining > 0)
        }
        output("solar-control-clocks.csv",rows)
    }

    @Test fun exactMixedDefenseRecordsPressureAndThreatOriginAcrossWaves() {
        assumeTrue(System.getProperty("professionalAudit") == "true")
        val rows=mutableListOf("difficulty,seed,wave,character,activeSeconds,enemySeconds,restSeconds,restEnemySeconds,maxEnemies,coreHits,olderWaveHits")
        for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(17,73)) {
            val game=SpaceGameState(random=Random(seed)).apply { resize(IntSize(1080,2340)); startArcade(difficulty) }
            var action=0.0; var phase=0; var ticks=0
            val origins=mutableMapOf<Long,Int>()
            data class Stats(var active: Int=0,var enemy: Int=0,var rest: Int=0,var restEnemy: Int=0,var peak: Int=0,var hits: Int=0,var older: Int=0)
            val stats=linkedMapOf<Int,Stats>()
            while (game.arcade!!.lives > 0 && game.arcade!!.wave <= 20 && ticks++ < 36000) {
                val run=game.arcade!!
                run.upgradeOffer?.let { offer ->
                    val order=if (run.lives <= 2) listOf(ArcadeUpgrade.Repair,ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines)
                        else listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Reactor,ArcadeUpgrade.Fleet,ArcadeUpgrade.Engines,ArcadeUpgrade.Repair)
                    game.chooseArcadeUpgrade(order.first { it in offer.choices })
                }
                action-=1.0/30
                if (action <= 0) {
                    val current=game.arcade!!; val core=current.bodies.first { it.kind == BodyKind.Core }
                    val danger=current.bodies.filter { it.kind == BodyKind.Meteor }.minByOrNull { (it.position-core.position).magnitude() }
                    val ships=current.bodies.filter { it.kind == BodyKind.Ship }
                    val guard=current.guardianUnlocked && ships.count { it.shipClass == ShipClass.Guardian } < 2
                    var kind=if (ships.size < current.launchLimit(BodyKind.Ship)) BodyKind.Ship else BodyKind.Ambient
                    if (danger != null && (danger.position-core.position).magnitude() < 260 && current.energy >= 24 &&
                        current.bodies.count { it.kind == BodyKind.Rocket } < current.launchLimit(BodyKind.Rocket)) kind=BodyKind.Rocket
                    if (kind != BodyKind.Ambient || current.bodies.count { it.kind == BodyKind.Player } < 6) {
                        val angle=phase*PI*(3-sqrt(5.0)); val radial=Vec2(cos(angle),sin(angle))
                        var point=core.position+radial*(if (kind == BodyKind.Ambient) 160.0 else if (guard) 235.0 else 300.0)
                        var velocity=SimulationEngine.orbitVelocity(core,point)
                        var route=emptyList<Vec2>()
                        if (kind == BodyKind.Rocket && danger != null) {
                            val inward=(core.position-danger.position).normalized(); point=danger.position+inward*75.0; velocity=inward*-420.0
                        } else if (kind == BodyKind.Ship && !guard) route=(1..4).map { i ->
                            core.position+Vec2(cos(angle+i*PI/2),sin(angle+i*PI/2))*300.0
                        }
                        if (current.bodies.none { (it.position-point).magnitude() < it.radius+22.0 }) {
                            game.chooseSpawnKind(kind)
                            if (kind == BodyKind.Ship) game.chooseArcadeShipClass(if (guard) ShipClass.Guardian else ShipClass.Interceptor)
                            game.loopFlightRoutes=route.isNotEmpty()
                            val preview=TouchPreview(point,point+velocity/3.0,0,Offset((velocity.x/3).toFloat(),(velocity.y/3).toFloat()),route)
                            val hold=if (kind == BodyKind.Ambient) .35 else if (kind == BodyKind.Ship) 1.0 else .5
                            val candidate=game.previewBody(preview,hold)
                            if (candidate != null && current.energy >= launchCost(candidate)) {
                                val before=current.launches; game.launch(preview,hold)
                                if (game.arcade!!.launches > before) { phase++; action=.4 }
                            }
                        }
                    }
                    if (action <= 0) action=.2
                }
                val before=game.arcade!!
                for (body in before.bodies.filter { it.kind == BodyKind.Meteor }+before.pending.map { it.body }) origins.putIfAbsent(body.id,before.wave)
                val s=stats.getOrPut(before.wave) { Stats() }
                val enemies=before.bodies.count { it.kind == BodyKind.Meteor }
                if (before.resting) { s.rest++; if (enemies > 0) s.restEnemy++ } else { s.active++; if (enemies > 0) s.enemy++ }
                s.peak=maxOf(s.peak,enemies)
                game.update(1.0/30)
                val next=game.arcade!!
                if (next.lives < before.lives) {
                    s.hits+=before.lives-next.lives
                    val core=before.bodies.first { it.kind == BodyKind.Core }
                    // Infer only when a disappearing enemy was already touching the core at this step.
                    val contacts=before.bodies.filter { it.kind == BodyKind.Meteor && next.bodies.none { after -> after.id == it.id } &&
                        (it.position-core.position).magnitude() <= it.radius+core.radius+it.velocity.magnitude()/30 }
                    if (contacts.any { (origins[it.id] ?: before.wave) < before.wave }) s.older++
                }
            }
            for ((wave,s) in stats) rows+="$difficulty,$seed,$wave,${waveCharacter(wave)},${s.active/30.0},${s.enemy/30.0},${s.rest/30.0},${s.restEnemy/30.0},${s.peak},${s.hits},${s.older}"
            assertTrue(game.arcade!!.wave >= 21 || game.arcade!!.lives == 0)
        }
        output("wave-pressure.csv",rows)
    }
}
