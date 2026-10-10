package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class CommercialFlowTest {
    @Test fun twentyWaveResultPausesTheRunUntilExplicitEndlessChoice() {
        val game=SpaceGameState(); game.resize(IntSize(900,1400)); game.startArcade()
        val saved=requireNotNull(game.recoverySnapshot(0)).let { it.copy(arcade=it.arcade!!.copy(elapsed=560.0)) }
        game.restoreRecovery(saved); game.closeMenu()
        assertTrue(game.arcadeCompletionPending); game.update(.5); assertEquals(560.0,game.arcade!!.elapsed,0.0)
        game.continueArcadeEndless(); assertFalse(game.arcadeCompletionPending)
        game.update(.1); assertTrue(game.arcade!!.elapsed > 560.0)
        game.restoreRecovery(requireNotNull(game.recoverySnapshot(1))); game.closeMenu(); assertFalse(game.arcadeCompletionPending)
    }
    @Test fun goalsAreEarnedOnlyByCompletedRegularEvents() {
        val run=ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),SpaceCamera(),IntSize(900,1400),ArcadeDifficulty.Normal)
        assertFalse(ArcadeGoal.Giant.earned(run.copy(challenge=ArcadeChallenge(1,emptySet(),failed=true,rewarded=true))))
        assertTrue(ArcadeGoal.Giant.earned(run.copy(challenge=ArcadeChallenge(1,emptySet(),rewarded=true))))
        assertFalse(ArcadeGoal.Convoy.earned(run.copy(convoy=ArcadeConvoy(2,status=ConvoyStatus.Lost))))
        assertTrue(ArcadeGoal.Convoy.earned(run.copy(convoy=ArcadeConvoy(2,status=ConvoyStatus.Delivered))))
        var writes=0
        val game=SpaceGameState(saveGoals={ writes++ });game.resize(IntSize(900,1400));game.startArcade()
        val saved=game.recoverySnapshot(0)!!.let { it.copy(arcade=it.arcade!!.copy(destroyed=3)) }
        game.restoreRecovery(saved);game.restoreRecovery(saved)
        assertEquals(1,writes);assertEquals(setOf(ArcadeGoal.Intercept),game.completedGoals)
    }
    @Test fun practiceHasImmediateReachableStepsForBothControlMethods() {
        for (joystick in listOf(false,true)) {
            val game=SpaceGameState();game.resize(IntSize(900,1400));game.beginFlightPractice(joystick,"Practice")
            game.setPilotTargetSpeed(250.0);game.update(1.0/60);assertEquals(1,game.tutorialStep)
            repeat(70) { if (joystick) game.setJoystickInput(Vec2(1.0,0.0)) else game.setSteeringInput(Vec2(1.0,0.0)); game.update(1.0/60) }
            if (joystick) {
                assertEquals(2,game.tutorialStep)
                repeat(70) { game.setAttitudeJoystickInput(Vec2(0.0,1.0));game.update(1.0/60) }
            }
            assertFalse(game.flightPractice);assertTrue(game.menuOpen)
        }
    }
    @Test fun experimentsStartPausedWithDifferentPhysicalTasks() {
        for (kind in ExperimentKind.entries) {
            val game=SpaceGameState();game.resize(IntSize(900,1400));game.beginExperiment(kind,"Lab")
            assertTrue(game.sandbox!!.paused);assertTrue(game.bodies.size in 2..3)
            assertEquals(kind == ExperimentKind.Collision,game.sandbox!!.collisionsEnabled)
            assertEquals(kind == ExperimentKind.Capture,game.bodies.any { it.kind == BodyKind.BlackHole })
            val before=game.bodies;game.update(1.0);assertEquals(before,game.bodies)
        }
    }
}
