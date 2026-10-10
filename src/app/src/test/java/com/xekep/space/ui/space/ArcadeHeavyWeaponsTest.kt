package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeHeavyWeaponsTest {
    private fun rocket(mass: Double=24.0)=CelestialBody(2,Vec2(1000.0,700.0),Vec2.Zero,mass,6f,Color.Cyan,BodyKind.Rocket)
    private fun enemy(id: Long=3,mass: Double=100.0,offset: Vec2=Vec2(40.0,0.0))=rocket().copy(id=id,kind=BodyKind.Meteor,
        position=rocket().position+offset,mass=mass,radius=SimulationEngine.radiusForMass(mass))
    private fun contact()=CollisionEvent(BodyKind.Meteor,BodyKind.Rocket,rocket().position,99,2,vehicleExplosion=true)

    @Test fun contactBlastHitsOnlyTwoNearbyEnemiesAndCreditsTheRocket() {
        val targets=listOf(enemy(3),enemy(4,offset=Vec2(0.0,45.0)),enemy(5,offset=Vec2(0.0,70.0)),enemy(6,offset=Vec2(91.0,0.0)))
        val (remaining,events)=heavyRocketBlasts(listOf(rocket()),targets,listOf(contact(),contact()))
        assertEquals(listOf(5L,6L),remaining.map { it.id }); assertEquals(setOf(3L,4L),events.map { it.meteorId }.toSet())
        assertTrue(events.all { it.defenderId == 2L && it.secondKind == BodyKind.Rocket })
    }
    @Test fun standardRocketsFuelExplosionsAndFriendlyCollisionsNeverCreateAreaDamage() {
        val targets=listOf(enemy(),rocket().copy(id=4,kind=BodyKind.Ship))
        assertEquals(targets,heavyRocketBlasts(listOf(rocket(12.0)),targets,listOf(contact())).first)
        val expiry=CollisionEvent(BodyKind.Rocket,BodyKind.Rocket,rocket().position,vehicleExplosion=true)
        val core=expiry.copy(secondKind=BodyKind.Core)
        assertEquals(targets,heavyRocketBlasts(listOf(rocket()),targets,listOf(expiry,core)).first)
    }
    @Test fun blastDamagesSiegeMassWithoutDeletingItOrChangingVelocity() {
        val heavy=enemy(mass=800.0).copy(velocity=Vec2(-90.0,20.0))
        val result=heavyRocketBlasts(listOf(rocket()),listOf(heavy),listOf(contact()))
        assertTrue(result.second.isEmpty()); assertEquals(620.0,result.first.single().mass,0.0)
        assertEquals(heavy.velocity,result.first.single().velocity)
    }
    @Test fun depthAndPlanetsShieldOtherFlightLayersAndTheFarSide() {
        val planet=enemy(10,offset=Vec2(20.0,0.0)).copy(kind=BodyKind.ArcadePlanet,radius=10f)
        val high=enemy(11,offset=Vec2(0.0,10.0)).copy(flightHeight=100.0)
        val shielded=enemy(12,offset=Vec2(50.0,0.0))
        val nearby=enemy(13,offset=Vec2(-40.0,0.0))
        val result=heavyRocketBlasts(listOf(rocket()),listOf(planet,high,shielded,nearby),listOf(contact()))
        assertEquals(setOf(10L,11L,12L),result.first.map { it.id }.toSet())
        assertEquals(13L,result.second.single().meteorId)
    }
    @Test fun giantSplitsAfterBlastAndItsNewChildrenStillRequireInterception() {
        val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
        val giant=enemy(3,1800.0,Vec2.Zero)
        val before=ArcadeSession(listOf(core,rocket(),giant),SpaceCamera(core.position),IntSize(900,1400),ArcadeDifficulty.Normal,
            elapsed=258.0,spawnTimer=1000.0,challenge=ArcadeChallenge(giant.id,setOf(giant.id)),launches=1)
        SimulationEngine.reserveBodyIds(before.bodies)
        val next=advanceArcade(before,.01,Random(1))
        assertEquals(3,next.challenge!!.ids.size); assertEquals(3,next.bodies.count { it.kind == BodyKind.Meteor })
        assertEquals(1,next.destroyed); assertEquals(setOf(rocket().id),next.successfulLaunches)
    }
    @Test fun heavyInterceptorUsesSiegeRoundOnlyForHeavyTargets() {
        val ship=rocket().copy(kind=BodyKind.Ship,mass=60.0)
        val standard=ship.copy(mass=24.0)
        val light=enemy(mass=100.0); val heavy=enemy(mass=800.0)
        assertEquals(120.0 to .9,interceptorWeapon(standard,heavy))
        assertEquals(132.0,interceptorWeapon(ship,light).first,1e-7)
        assertEquals(.9,interceptorWeapon(ship,light).second,1e-7)
        assertEquals(297.0,interceptorWeapon(ship,heavy).first,1e-7)
        assertEquals(1.8,interceptorWeapon(ship,heavy).second,1e-7)
        val ready=ArcadeCombat(craft=mapOf(ship.id to CraftStatus(cooldown=0.0)))
        val shot=prepareCombat(listOf(ship,heavy.copy(position=ship.position+Vec2(200.0,0.0))),ready,.01,gunIntervalScale=.85)
        assertEquals(297.0,shot.combat.projectiles.single().damage,1e-7)
        assertEquals(1.53,shot.combat.craft.getValue(ship.id).cooldown,1e-7)
    }
    @Test fun firingRangeMeasuresTheHeavyShipsTradeoffRatherThanMakingEveryTargetEasier() {
        fun timeToStop(mass: Double, enemyMass: Double): Double {
            val ship=rocket().copy(kind=BodyKind.Ship,mass=mass,position=Vec2.Zero,radius=8f)
            var bodies=listOf(ship,enemy(mass=enemyMass).copy(position=Vec2(300.0,0.0)))
            var combat=ArcadeCombat()
            var time=0.0
            while (bodies.any { it.kind == BodyKind.Meteor } && time < 15.0) {
                val prepared=prepareCombat(bodies,combat,1.0/60,controlledId=ship.id)
                val advanced=advanceProjectiles(prepared.bodies,prepared.combat,1.0/60)
                bodies=advanced.bodies; combat=advanced.combat; time+=1.0/60
            }
            assertTrue(time < 15.0)
            return time
        }
        val standard=timeToStop(24.0,800.0); val heavy=timeToStop(60.0,800.0)
        assertTrue(heavy < standard)
        assertEquals(timeToStop(24.0,100.0),timeToStop(60.0,100.0),1e-8)
        assertEquals(40.4,launchCost(BodyKind.Ship,24.0),1e-8)
        assertEquals(50.0,launchCost(BodyKind.Ship,60.0),1e-8)
        println("HEAVY_FIRING_RANGE standardSeconds=$standard heavySeconds=$heavy costs=40.4/50.0")
    }

}
