package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class StrategyAuditTest {
    private fun run()=createStrategyBattle(ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),
        SpaceCamera(),IntSize(900,1400),ArcadeDifficulty.Normal))
    private fun ship(position: Vec2)=CelestialBody(SimulationEngine.newBodyId(),position,Vec2.Zero,36.0,6f,Color.Cyan,BodyKind.Ship)

    @Test fun escortingTheSelectedLeaderDoesNotOrderItToGuardItselfOrCancelItsRoute() {
        val run=run();val leader=ship(Vec2(1500.0,1500.0)).copy(waypoints=listOf(Vec2(1800.0,1500.0)))
        val escort=ship(Vec2(1600.0,1500.0))
        val next=issueFleetOrder(run.copy(bodies=run.bodies+listOf(leader,escort)),setOf(leader.id,escort.id),FleetCommand.Guard,leader.position,leader.id)
        assertNull(next.strategy!!.orders[leader.id])
        assertEquals(leader.waypoints,next.bodies.first { it.id==leader.id }.waypoints)
        assertEquals(leader.id,next.strategy!!.orders.getValue(escort.id).targetId)
    }
    @Test fun projectileDamageDoesNotChangeAnAnchoredBasesCollisionRadiusOrFactionColor() {
        val run=run();val base=run.bodies.first { it.id==run.strategy!!.bases.first().id }
        val shot=SpaceProjectile(base.position-Vec2(100.0,0.0),Vec2(1000.0,0.0),999,damage=120.0)
        val next=advanceStrategy(run.copy(combat=ArcadeCombat(projectiles=listOf(shot))),.2,kotlin.random.Random(17))
        val hit=next.bodies.first { it.id==base.id }
        assertEquals(base.mass-120.0,hit.mass,0.0)
        assertEquals(base.radius,hit.radius,0f);assertEquals(base.color,hit.color)
    }
    @Test fun anEnemyBreakingThroughTheCoreDoesNotEarnAnInterceptionReward() {
        val run=run();val core=run.bodies.first { it.kind==BodyKind.Core }
        val enemy=CelestialBody(SimulationEngine.newBodyId(),core.position+Vec2(core.radius.toDouble(),0.0),Vec2.Zero,
            100.0,6f,Color.Red,BodyKind.Meteor)
        val before=run.copy(energy=20.0,bodies=run.bodies+enemy,
            strategy=run.strategy!!.copy(enemies=mapOf(enemy.id to EnemyCraft(BodyKind.Rocket))))
        val next=advanceStrategy(before,.05,kotlin.random.Random(17))
        assertEquals(before.lives-1,next.lives)
        assertEquals(20.4,next.energy,1e-8);assertEquals(0,next.destroyed);assertEquals(0.0,next.score,0.0)
    }
}
