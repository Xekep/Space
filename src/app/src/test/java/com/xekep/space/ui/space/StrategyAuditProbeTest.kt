package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/** Legal inputs with exact-state observation. These comparisons are not human win rates. */
class StrategyAuditProbeTest {
    private enum class Policy { Passive, Factories, Rockets, Combined }
    @Test fun compareStrategicChoicesOnAllDifficulties() {
        assumeTrue(System.getProperty("strategyAudit") == "true")
        val output=mutableListOf("policy,difficulty,seed,seconds,lives,bases,launches,stations,victory,maxEnemies,maxFleet,energyCapPercent,events")
        val field=SimulationEngine::class.java.getDeclaredField("idSource").apply { isAccessible=true }
        val ids=field.get(SimulationEngine) as AtomicLong
        val previous=ids.get()
        try {
            for (policy in Policy.entries) for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(17L,73L)) {
                ids.set(1) // Identifiers alone: stable patrol slots, never extra resources or damage.
                val game=SpaceGameState(random=SessionRandom(seed)).apply { resize(IntSize(1080,2340));startStrategy(difficulty) }
                var time=0.0;var decision=0.0;var cap=0.0;var maxEnemies=0;var maxFleet=0
                while (time<300 && game.arcade!!.lives>0 && !game.arcadeCompletionPending) {
                    val run=game.arcade!!;val battle=run.strategy!!;val core=run.bodies.first { it.kind==BodyKind.Core }
                    decision-=1.0/30
                    if (decision<=0 && policy!=Policy.Passive) {
                        val factories=battle.stations.count { it.kind==StationKind.Factory }
                        when {
                            policy==Policy.Combined && battle.stations.none { it.kind==StationKind.Bastion } && run.energy>=70 -> {
                                build(game,StationKind.Bastion,core.position+Vec2(-200.0,-220.0))
                            }
                            (policy==Policy.Factories && factories<3 || policy==Policy.Combined && factories<2 && time>3) && run.energy>=65 -> {
                                val point=core.position+if (factories==1) Vec2(-280.0,220.0) else Vec2(0.0,-330.0)
                                build(game,StationKind.Factory,point)
                                if (game.selectedStationId!=null) { game.cycleStationProduct();game.cycleStationProduct() }
                                game.clearFleet()
                            }
                            (policy==Policy.Rockets || policy==Policy.Combined) && run.energy>=60 -> {
                                val guards=run.bodies.count { it.kind==BodyKind.Ship && it.shipClass==ShipClass.Guardian }
                                val kind=if (policy==Policy.Combined && guards<2) BodyKind.Ship else BodyKind.Rocket
                                if (game.spawnCountFor(kind)<game.spawnLimitFor(kind)) {
                                    game.chooseSpawnKind(kind)
                                    if (kind==BodyKind.Ship) game.chooseArcadeShipClass(ShipClass.Guardian)
                                    val target=battle.bases.firstOrNull()
                                    val radial=Vec2(cos(time*.7),sin(time*.7));val point=core.position+radial*250.0
                                    val aim=if (kind==BodyKind.Ship) radial.perpendicular() else (target?.position ?: core.position)-point
                                    val before=game.bodies.filter { it.isVehicle }.map { it.id }.toSet()
                                    game.launch(TouchPreview(point,point+aim.normalized()*130.0,0,Offset(80f,0f)),if (kind==BodyKind.Rocket) .65 else .30)
                                    val craft=game.bodies.firstOrNull { it.isVehicle && it.id !in before }
                                    if (policy==Policy.Combined && craft?.kind==BodyKind.Rocket && target!=null) {
                                        game.clearFleet();game.chooseFleetCommand(FleetCommand.Move)
                                        game.finishGesture(TouchPreview(craft.position,craft.position,0,tapBodyId=craft.id),.01)
                                        game.chooseFleetCommand(FleetCommand.Attack)
                                        game.finishGesture(TouchPreview(target.position,target.position,0,tapBodyId=target.id),.01)
                                        game.clearFleet()
                                    }
                                }
                            }
                        }
                        decision=1.25
                    }
                    if (run.energy>=119) cap+=1.0/30
                    game.update(1.0/30);time+=1.0/30
                    maxEnemies=maxOf(maxEnemies,game.arcade!!.strategy!!.enemies.size)
                    maxFleet=maxOf(maxFleet,game.bodies.count { it.isVehicle })
                    assertTrue(game.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
                }
                val run=game.arcade!!;val battle=run.strategy!!
                val row="$policy,$difficulty,$seed,$time,${run.lives},${battle.bases.size},${run.launches},${battle.stations.size},${battle.victory},$maxEnemies,$maxFleet,${cap/time*100},${run.eventsCompleted}"
                output+=row;println(row)
            }
        } finally { ids.set(previous) }
        val folder=File(System.getProperty("strategyAuditDir") ?: "../../dist");folder.mkdirs()
        File(folder,"strategy-audit.csv").writeText(output.joinToString("\n"))
        assertEquals(25,output.size)
    }
    private fun build(game: SpaceGameState,kind: StationKind,point: Vec2) {
        game.clearFleet();game.chooseBuilding(kind);game.finishGesture(TouchPreview(point,point,0),.01)
        if (kind!=StationKind.Factory) game.clearFleet()
    }
}
