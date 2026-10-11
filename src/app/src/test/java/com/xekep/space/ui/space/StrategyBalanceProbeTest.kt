package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Exact-state diagnostic using legal construction, launches and orders, not a human win rate. */
class StrategyBalanceProbeTest {
    @Test fun mixedFleetAndEconomyCanCompleteEachDifficulty() {
        assumeTrue(System.getProperty("strategyReview") == "true")
        val lines=mutableListOf("difficulty,seed,seconds,lives,bases,launches,stations,victory,maxEnemies,maxFleet")
        for (difficulty in ArcadeDifficulty.entries) for (seed in listOf(17L,73L)) {
            val game=SpaceGameState(random=SessionRandom(seed)).apply { resize(IntSize(1080,2340));startStrategy(difficulty) }
            var time=0.0;var action=0.0;var maxEnemies=0;var maxFleet=0
            while (time < 360 && game.arcade!!.lives > 0 && !game.arcadeCompletionPending) {
                val run=game.arcade!!;val battle=run.strategy!!;val core=run.bodies.first { it.kind == BodyKind.Core }
                action-=1.0/30
                if (action <= 0) {
                    // Expand factories gradually, retaining enough energy for emergency launches.
                    if (battle.stations.none { it.kind == StationKind.Bastion }) {
                        if (run.energy >= 70) { val p=core.position+Vec2(-200.0,-220.0);game.chooseBuilding(StationKind.Bastion);game.finishGesture(TouchPreview(p,p,0),.01);game.clearFleet() }
                    } else if (battle.stations.count { it.kind == StationKind.Factory } < 2 && time > 3) {
                        if (run.energy >= 65) {
                        val p=core.position+if (battle.stations.count { it.kind == StationKind.Factory } == 1) Vec2(-280.0,220.0) else Vec2(0.0,-330.0)
                        game.chooseBuilding(StationKind.Factory);game.finishGesture(TouchPreview(p,p,0),.01)
                        if (game.selectedStationId != null) { game.cycleStationProduct();game.cycleStationProduct() }
                        game.clearFleet()
                        }
                    } else {
                        val ships=run.bodies.filter { it.kind == BodyKind.Ship }
                        val guards=ships.count { it.shipClass == ShipClass.Guardian }
                        val kind=if (guards < 2) BodyKind.Ship else BodyKind.Rocket
                        if (game.spawnCountFor(kind) < game.spawnLimitFor(kind) && run.energy >= 60) {
                            game.chooseSpawnKind(kind)
                            if (kind == BodyKind.Ship) game.chooseArcadeShipClass(ShipClass.Guardian)
                            val enemy=battle.bases.minByOrNull { (it.position-core.position).magnitude() }
                            val angle=time*.7;val radial=Vec2(cos(angle),sin(angle));val point=core.position+radial*250.0
                            val aim=if (kind == BodyKind.Ship) radial.perpendicular() else (enemy?.position ?: core.position)-point
                            val before=game.bodies.filter { it.isVehicle }.map { it.id }.toSet()
                            game.launch(TouchPreview(point,point+aim.normalized()*130.0,0,Offset(80f,0f)),if (kind == BodyKind.Rocket) .65 else .30)
                            val launched=game.bodies.firstOrNull { it.isVehicle && it.id !in before }
                            if (launched != null && kind == BodyKind.Rocket && enemy != null) {
                                game.clearFleet();game.chooseFleetCommand(FleetCommand.Move)
                                game.finishGesture(TouchPreview(launched.position,launched.position,0,tapBodyId=launched.id),.01)
                                game.chooseFleetCommand(FleetCommand.Attack)
                                game.finishGesture(TouchPreview(enemy.position,enemy.position,0,tapBodyId=enemy.id),.01);game.clearFleet()
                            }
                        }
                    }
                    action=1.25
                }
                game.update(1.0/30);time+=1.0/30
                maxEnemies=maxOf(maxEnemies,game.arcade!!.strategy!!.enemies.size)
                maxFleet=maxOf(maxFleet,game.bodies.count { it.isVehicle })
                assertTrue(game.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
            }
            val run=game.arcade!!;val battle=run.strategy!!
            lines+="${difficulty.name},$seed,$time,${run.lives},${battle.bases.size},${run.launches},${battle.stations.size},${battle.victory},$maxEnemies,$maxFleet"
        }
        val file=File(System.getProperty("user.dir"),"../../dist/strategy-balance.csv");file.parentFile!!.mkdirs();file.writeText(lines.joinToString("\n"))
        println(lines.joinToString("\n"))
        assertTrue("A legal mixed strategy must be capable of winning",lines.drop(1).all { it.split(",")[7] == "true" })
    }
}
