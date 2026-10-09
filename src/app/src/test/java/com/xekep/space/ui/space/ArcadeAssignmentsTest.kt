package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class ArcadeAssignmentsTest {
    private fun body(id: Long, kind: BodyKind, x: Double, y: Double = 0.0)=
        CelestialBody(id,Vec2(x,y),Vec2.Zero,60.0,8f,Color.Cyan,kind,heading=Vec2(1.0,0.0))
    private fun fleet(count: Int)=List(count) { body(it+1L,
        if (it%2 == 0) BodyKind.Ship else BodyKind.Rocket,0.0,it*40.0) }
    private fun combat(assignments: Map<Long,CelestialBody>)=ArcadeCombat(craft=
        assignments.mapValues { CraftStatus(targetId=it.value.id) })

    @Test fun mixedFleetCoversAllEnemiesBeforePairingAndNeverCrowdsOneTarget() {
        val vehicles=fleet(8)
        val enemies=List(3) { body(100L+it,BodyKind.Meteor,500.0+it*100,0.0) }
        val result=assignArcadeTargets(vehicles+enemies,ArcadeCombat(),null)
        assertEquals(6,result.size)
        assertEquals(enemies.map { it.id }.toSet(),result.values.map { it.id }.toSet())
        assertTrue(result.values.groupingBy { it.id }.eachCount().values.all { it == 2 })
        assertEquals(result.mapValues { it.value.id },
            assignArcadeTargets((vehicles+enemies).reversed(),ArcadeCombat(),null).mapValues { it.value.id })
    }

    @Test fun fewerCraftSpreadOutEvenWhenOneEnemyIsNearestToEveryone() {
        val enemies=List(4) { body(100L+it,BodyKind.Meteor,300.0+it*100) }
        val result=assignArcadeTargets(fleet(3)+enemies,ArcadeCombat(),null)
        assertEquals(3,result.values.map { it.id }.distinct().size)
    }

    @Test fun livingAssignmentsDoNotFlipForSmallDistanceChangesOrListOrderChanges() {
        val vehicles=fleet(4)
        val enemies=listOf(body(100,BodyKind.Meteor,500.0),body(101,BodyKind.Meteor,540.0))
        val initial=assignArcadeTargets(vehicles+enemies,ArcadeCombat(),null)
        val moved=enemies.map { it.copy(position=Vec2(1040.0-it.position.x,0.0)) }
        val result=assignArcadeTargets((vehicles+moved).reversed(),combat(initial),null)
        assertEquals(initial.mapValues { it.value.id },result.mapValues { it.value.id })
    }

    @Test fun destroyedTargetsFreeTheirCraftAndNewThreatsSplitAnExistingPair() {
        val vehicles=fleet(2)
        val first=body(100,BodyKind.Meteor,500.0)
        val second=body(101,BodyKind.Meteor,-500.0)
        val initial=assignArcadeTargets(vehicles+first,ArcadeCombat(),null)
        val spread=assignArcadeTargets(vehicles+listOf(first,second),combat(initial),null)
        assertEquals(setOf(100L,101L),spread.values.map { it.id }.toSet())
        assertEquals(2,assignArcadeTargets(vehicles+second,combat(spread),null).values.count { it.id == 101L })
        assertTrue(assignArcadeTargets(vehicles,combat(spread),null).isEmpty())
    }

    @Test fun closestUsefulCraftGetsTheOnlyPursuitSlotsAndLegacyOverbookingIsRepaired() {
        val enemy=body(100,BodyKind.Meteor,1000.0)
        val vehicles=listOf(body(1,BodyKind.Ship,-1500.0),body(2,BodyKind.Ship,750.0),
            body(3,BodyKind.Rocket,800.0))
        val result=assignArcadeTargets(vehicles+enemy,ArcadeCombat(),null)
        assertEquals(setOf(2L,3L),result.keys)
        val legacy=ArcadeCombat(craft=vehicles.associate { it.id to CraftStatus(targetId=enemy.id) })
        assertEquals(2,assignArcadeTargets(vehicles+enemy,legacy,null).size)
    }

    @Test fun manualRoutesGuardiansAndUnpoweredCraftReleaseTheirSlots() {
        val vehicles=fleet(5).mapIndexed { i,b -> when (i) {
            0 -> b.copy(shipClass=ShipClass.Guardian)
            1 -> b.copy(waypoints=listOf(Vec2(1000.0,0.0)))
            2 -> b.copy(pilotTargetSpeed=0.0)
            3 -> b.copy(fuelRemaining=0.0)
            else -> b
        } }
        val enemy=body(100,BodyKind.Meteor,500.0)
        val previous=ArcadeCombat(craft=vehicles.associate { it.id to CraftStatus(targetId=enemy.id) })
        assertTrue(assignArcadeTargets(vehicles+enemy,previous,vehicles.last().id).isEmpty())
        assertEquals(setOf(vehicles.last().id),assignArcadeTargets(vehicles+enemy,previous,null).keys)
    }

    @Test fun combatStoresAssignmentsAndReserveStillFiresWithoutJoiningThePursuit() {
        val core=body(50,BodyKind.Core,0.0).copy(mass=7800.0,radius=28f)
        val vehicles=List(3) { body(it+1L,BodyKind.Ship,350.0,it*50.0) }
        val enemy=body(100,BodyKind.Meteor,650.0,0.0)
        val ready=ArcadeCombat(craft=vehicles.associate { it.id to CraftStatus(cooldown=0.0) })
        val result=prepareCombat(listOf(core)+vehicles+enemy,ready,.05)
        assertEquals(2,result.combat.craft.values.count { it.targetId == enemy.id })
        val reserve=result.bodies.single { it.isVehicle && result.combat.craft[it.id]?.targetId == null }
        assertTrue("Reserve still defends with guns",result.combat.projectiles.any { it.ownerId == reserve.id })
        assertTrue("Reserve patrols rather than chasing rightward",reserve.velocity.y > reserve.velocity.x)
    }

    @Test fun closerUncoveredThreatReplacesDistantAssignmentWithoutSuppressingGuns() {
        val ship=body(1,BodyKind.Ship,0.0)
        val far=body(100,BodyKind.Meteor,2000.0)
        val near=body(101,BodyKind.Meteor,300.0)
        val ready=ArcadeCombat(craft=mapOf(ship.id to CraftStatus(cooldown=0.0,targetId=far.id)))
        val result=prepareCombat(listOf(ship,far,near),ready,.01)
        assertEquals(near.id,result.combat.craft.getValue(ship.id).targetId)
        assertEquals(1,result.combat.projectiles.size)
        val manual=prepareCombat(listOf(ship,near),ready,.01,controlledId=ship.id)
        assertNull(manual.combat.craft.getValue(ship.id).targetId)
        assertEquals(ship.velocity,manual.bodies.first().velocity)
        assertEquals(1,manual.combat.projectiles.size)
    }

    @Test fun aNewCloserRocketTakesTheTargetAndTheOldRocketCoversTheNextOne() {
        val far=body(1,BodyKind.Rocket,0.0)
        val near=body(2,BodyKind.Rocket,950.0)
        val first=body(100,BodyKind.Meteor,1000.0)
        val next=body(101,BodyKind.Meteor,2000.0)
        val previous=combat(mapOf(far.id to first))
        val scene=listOf(far,near,first,next)
        val result=assignArcadeTargets(scene,previous,null)
        assertEquals(mapOf(near.id to first.id,far.id to next.id),result.mapValues { it.value.id })
        assertEquals(result.mapValues { it.value.id },assignArcadeTargets(scene.reversed(),previous,null).mapValues { it.value.id })
    }

    @Test fun takeoverDisplacesTheSlowerMemberOfAPairAndKeepsOtherThreatsCovered() {
        val ship=body(1,BodyKind.Ship,0.0)
        val rocket=body(2,BodyKind.Rocket,0.0)
        val closer=body(3,BodyKind.Rocket,950.0)
        val first=body(100,BodyKind.Meteor,1000.0)
        val next=body(101,BodyKind.Meteor,1600.0)
        val result=assignArcadeTargets(listOf(ship,rocket,closer,first,next),
            combat(mapOf(ship.id to first,rocket.id to first)),null)
        assertEquals(first.id,result.getValue(closer.id).id)
        assertEquals(first.id,result.getValue(rocket.id).id)
        assertEquals(next.id,result.getValue(ship.id).id)
        assertEquals(mapOf(first.id to 2,next.id to 1),result.values.groupingBy { it.id }.eachCount())
    }

    @Test fun aCloserReserveReplacesAFullPursuitPairEvenWithoutAnotherTarget() {
        val ship=body(1,BodyKind.Ship,0.0)
        val rocket=body(2,BodyKind.Rocket,0.0)
        val closer=body(3,BodyKind.Rocket,950.0)
        val enemy=body(100,BodyKind.Meteor,1000.0)
        val result=assignArcadeTargets(listOf(ship,rocket,closer,enemy),
            combat(mapOf(ship.id to enemy,rocket.id to enemy)),null)
        assertEquals(setOf(rocket.id,closer.id),result.keys)
        assertTrue(result.values.all { it.id == enemy.id })
    }

    @Test fun existingPursuersCanExchangeTargetsAndDoNotSwapBackOnTheNextFrame() {
        val far=body(1,BodyKind.Rocket,0.0)
        val near=body(2,BodyKind.Rocket,950.0)
        val first=body(100,BodyKind.Meteor,1000.0)
        val next=body(101,BodyKind.Meteor,2000.0)
        var state=combat(mapOf(far.id to first,near.id to next))
        val expected=mapOf(far.id to next.id,near.id to first.id)
        repeat(60) { frame ->
            val scene=listOf(far,near,first,next).let { if (frame%2 == 0) it else it.reversed() }
            val result=assignArcadeTargets(scene,state,null)
            assertEquals(expected,result.mapValues { it.value.id })
            state=combat(result)
        }
    }

    @Test fun smallAdvantagesDoNotStealTargetsFromAnExistingPair() {
        val first=body(100,BodyKind.Meteor,1000.0)
        val next=body(101,BodyKind.Meteor,2000.0)
        val owners=listOf(body(1,BodyKind.Rocket,0.0),body(2,BodyKind.Rocket,30.0))
        val challenger=body(3,BodyKind.Rocket,100.0)
        val previous=combat(owners.associate { it.id to first }+mapOf(challenger.id to next))
        repeat(40) { frame ->
            val jitter=challenger.copy(position=Vec2(100.0+if (frame%2 == 0) 10 else -10,0.0))
            val result=assignArcadeTargets(owners+listOf(jitter,first,next),previous,null)
            assertEquals(previous.craft.mapValues { it.value.targetId },result.mapValues { it.value.id })
        }
    }

    @Test fun aCloserRocketFacingAwayIsNotAnEarlierInterceptor() {
        val first=body(100,BodyKind.Meteor,1000.0)
        val ship=body(1,BodyKind.Ship,850.0)
        val rocket=body(2,BodyKind.Rocket,890.0)
        val challenger=body(3,BodyKind.Rocket,900.0).copy(heading=Vec2(-1.0,0.0))
        val result=assignArcadeTargets(listOf(ship,rocket,challenger,first),
            combat(mapOf(ship.id to first,rocket.id to first)),null)
        assertEquals(setOf(ship.id,rocket.id),result.keys)
    }

    @Test fun aHandoffUpdatesCombatStatusWithoutResettingAgeOrGunCooldown() {
        val far=body(1,BodyKind.Ship,0.0)
        val near=body(2,BodyKind.Rocket,950.0)
        val first=body(100,BodyKind.Meteor,1000.0)
        val next=body(101,BodyKind.Meteor,2000.0)
        val previous=ArcadeCombat(craft=mapOf(far.id to CraftStatus(12.0,.6,first.id)))
        val result=prepareCombat(listOf(far,near,first,next),previous,.01)
        assertEquals(first.id,result.combat.craft.getValue(near.id).targetId)
        val status=result.combat.craft.getValue(far.id)
        assertEquals(next.id,status.targetId)
        assertEquals(12.01,status.age,1e-8)
        assertEquals(.59,status.cooldown,1e-8)
        assertEquals(far.fuelRemaining,result.bodies.first { it.id == far.id }.fuelRemaining,0.0)
    }
}
