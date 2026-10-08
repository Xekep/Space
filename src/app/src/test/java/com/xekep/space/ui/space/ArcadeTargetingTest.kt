package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class ArcadeTargetingTest {
    private fun body(id: Long,point: Vec2,velocity: Vec2,kind: BodyKind,mass: Double=90.0,radius: Float=8f) =
        CelestialBody(id,point,velocity,mass,radius,Color.Cyan,kind,pilotTargetSpeed=0.0)

    @Test fun ballisticAimAccountsForShooterVelocityAndLongerFlightTimes() {
        val ship=body(1,Vec2.Zero,Vec2(0.0,-250.0),BodyKind.Ship,24.0)
        val target=body(2,Vec2(600.0,0.0),Vec2(0.0,300.0),BodyKind.Meteor)
        val aim=ArcadeTargeting(listOf(ship,target)).solution(ship,target,ship.velocity,650.0)!!
        assertTrue(aim.seconds > .7)
        assertTrue((aim.origin+aim.velocity*aim.seconds-(target.position+target.velocity*aim.seconds)).magnitude() < .1)
    }

    @Test fun blockedOrUnreachableTargetsAreSkippedForAnotherAvailableEnemy() {
        val ship=body(1,Vec2.Zero,Vec2.Zero,BodyKind.Ship,24.0)
        val blocked=body(2,Vec2(300.0,0.0),Vec2.Zero,BodyKind.Meteor)
        val planet=body(3,Vec2(150.0,0.0),Vec2.Zero,BodyKind.ArcadePlanet,1e-8,27f)
        val clear=body(4,Vec2(0.0,400.0),Vec2.Zero,BodyKind.Meteor)
        val scene=listOf(ship,blocked,planet,clear)
        assertNull(ArcadeTargeting(scene).solution(ship,blocked,ship.velocity,650.0))
        val prepared=prepareCombat(scene,ArcadeCombat(craft=mapOf(1L to CraftStatus(cooldown=0.0))),.01)
        assertEquals(1,prepared.combat.projectiles.size)
        assertTrue(prepared.combat.projectiles.single().velocity.y > 600)
        val escaping=blocked.copy(velocity=Vec2(900.0,0.0))
        assertNull(ArcadeTargeting(listOf(ship,escaping)).solution(ship,escaping,ship.velocity,650.0))
    }

    @Test fun heavyShipsDealSlightlyMoreDamageWithoutIncreasingFireRate() {
        val target=body(2,Vec2(200.0,0.0),Vec2.Zero,BodyKind.Meteor)
        for (shipClass in ShipClass.entries) {
            val light=body(1,Vec2.Zero,Vec2.Zero,BodyKind.Ship,24.0).copy(shipClass=shipClass)
            val heavy=light.copy(mass=96.0)
            fun fire(ship: CelestialBody)=prepareCombat(listOf(ship,target),ArcadeCombat(craft=mapOf(1L to CraftStatus(cooldown=0.0))),.01)
            val a=fire(light); val b=fire(heavy)
            assertEquals(a.combat.projectiles.single().damage*1.2,b.combat.projectiles.single().damage,1e-6)
            assertEquals(a.combat.craft[1L]!!.cooldown,b.combat.craft[1L]!!.cooldown,0.0)
        }
    }

    @Test fun gravityAwareAimImprovesActualHitsOverTheOldLeadFormula() {
        val core=body(10,Vec2.Zero,Vec2.Zero,BodyKind.Core,6200.0,28f)
        val scenarios=mutableListOf<List<CelestialBody>>()
        for (sv in listOf(Vec2.Zero,Vec2(0.0,-250.0),Vec2(200.0,150.0)))
            for (tv in listOf(Vec2(0.0,300.0),Vec2(-220.0,250.0),Vec2(250.0,0.0)))
                scenarios+=listOf(body(1,Vec2.Zero,sv,BodyKind.Ship,24.0),body(2,Vec2(600.0,0.0),tv,BodyKind.Meteor))
        scenarios+=listOf(core,body(1,Vec2(-380.0,0.0),Vec2(0.0,-120.0),BodyKind.Ship,24.0),body(2,Vec2(200.0,-200.0),Vec2(180.0,220.0),BodyKind.Meteor))
        scenarios+=listOf(core,body(1,Vec2(0.0,-400.0),Vec2(160.0,0.0),BodyKind.Ship,24.0),body(2,Vec2(250.0,-70.0),Vec2(0.0,320.0),BodyKind.Meteor))
        scenarios+=listOf(core,body(10+1,Vec2(430.0,0.0),SimulationEngine.orbitVelocity(core,Vec2(430.0,0.0)),BodyKind.ArcadePlanet,850.0,27f),
            body(1,Vec2(320.0,-260.0),Vec2(90.0,-50.0),BodyKind.Ship,24.0),body(2,Vec2(520.0,-100.0),Vec2(-100.0,130.0),BodyKind.Meteor))
        fun hits(scene: List<CelestialBody>,shot: SpaceProjectile): Boolean {
            var bodies=scene; var combat=ArcadeCombat(projectiles=listOf(shot))
            repeat(160) {
                val moved=NumericIntegrator.advance(bodies,1.0/120,1.0/120,fixedCore=true,recordTrail=false,alignRockets=false)
                val step=advanceProjectiles(moved,combat,1.0/120)
                if (step.events.any { it.meteorId == 2L }) return true
                bodies=step.bodies; combat=step.combat
            }
            return false
        }
        var oldHits=0; var newHits=0; var reachable=0
        for (scene in scenarios) {
            val ship=scene.first { it.kind == BodyKind.Ship }; val target=scene.first { it.kind == BodyKind.Meteor }
            val offset=target.position-ship.position
            val old=(offset+target.velocity*(offset.magnitude()/650).coerceAtMost(.7)).normalized()
            if (hits(scene,SpaceProjectile(ship.position+old*(ship.radius+4.0),old*650.0+ship.velocity*.25,ship.id))) oldHits++
            val aim=ArcadeTargeting(scene).solution(ship,target,ship.velocity,650.0)
            if (aim != null) { reachable++; if (hits(scene,SpaceProjectile(aim.origin,aim.velocity,ship.id))) newHits++ }
        }
        println("AIM_COMPARISON,scenarios=${scenarios.size},reachable=$reachable,oldHits=$oldHits,newHits=$newHits")
        assertEquals(reachable,newHits)
        assertTrue(newHits >= 8)
        assertTrue(newHits >= oldHits+3)
    }
}
