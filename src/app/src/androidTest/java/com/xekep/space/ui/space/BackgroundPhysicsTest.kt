package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.Vec2
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackgroundPhysicsTest {
    private fun scene() = SpaceGameState().apply {
        resize(IntSize(1080, 1920)); startSandbox(SandboxPresetKind.Empty)
        repeat(60) { i ->
            val point = Vec2((i % 10) * 150.0, (i / 10) * 150.0)
            launch(TouchPreview(point, point, 0), 0.0)
        }
        setTimeScale(6.0)
    }

    @Test fun workerMatchesSynchronousPhysicsAndPreservesCameraChanges() = runBlocking {
        withContext(Dispatchers.Main) {
            val game = scene()
            val reference = SpaceGameState().apply { loadSandbox(game.snapshot(0)!!) }
            reference.update(0.1)
            val work = async(start = CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(0.1) }
            game.transformCamera(Offset(540f, 960f), Offset(200f, -80f), 1.4f)
            val camera = game.camera
            work.await()
            assertEquals(reference.bodies, game.bodies)
            assertEquals(camera, game.camera)
        }
    }

    @Test fun editingDuringCalculationCannotBeOverwrittenByAnOldResult() = runBlocking {
        withContext(Dispatchers.Main) {
            val game = scene()
            val id = game.bodies.first().id
            val work = async(start = CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(0.1) }
            game.selectBody(id); game.deleteSelected()
            val edited = game.bodies
            work.await()
            assertEquals(59, game.bodies.size)
            assertEquals(edited, game.bodies)
        }
    }

    @Test fun creatingANewUniverseRejectsThePreviousUniversesCalculation() = runBlocking {
        withContext(Dispatchers.Main) {
            val game = scene()
            val work = async(start = CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(0.1) }
            game.startSandbox(SandboxPresetKind.Empty)
            work.await()
            assertTrue(game.bodies.isEmpty())
        }
    }

    @Test fun pausingWhileTheWorkerRunsKeepsTheDisplayedSceneFrozen() = runBlocking {
        withContext(Dispatchers.Main) {
            val game = scene()
            val before = game.bodies
            val work = async(start = CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(0.1) }
            game.toggleSandboxPause()
            work.await()
            assertTrue(game.sandbox!!.paused)
            assertEquals(before, game.bodies)
        }
    }

    @Test fun shakingDuringCalculationKeepsTheNewImpulse() = runBlocking {
        withContext(Dispatchers.Main) {
            val game = scene()
            val work = async(start = CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(0.1) }
            assertTrue(game.shakeSandbox(Vec2(40.0, -20.0)))
            val shaken = game.bodies
            work.await()
            assertEquals(shaken, game.bodies)
        }
    }

    @Test fun workerCapturesPilotInputAndChangingTheControlOptionRejectsItsResult() = runBlocking {
        withContext(Dispatchers.Main) {
            val game=scene()
            game.chooseSpawnKind(com.xekep.space.sim.BodyKind.Rocket)
            game.launch(TouchPreview(Vec2(5000.0,5000.0),Vec2(5000.0,4900.0),0),0.0)
            game.setMotionControlEnabled(true); game.setSteeringInput(Vec2(1.0,0.0))
            val id=game.controlledVehicleId!!
            val current=game.sandbox!!
            val control=com.xekep.space.sim.ManualFlightControl(id,1.0)
            var expected=current.bodies
            repeat(6) {
                val steered=com.xekep.space.sim.steerManually(expected,control,current.timeScale/60)
                expected=com.xekep.space.sim.SimulationEngine.stepSandbox(steered,current.timeScale/60,current.referenceEnergy,current.collisionsEnabled,id).bodies
            }
            val work=async(start=CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(.1) }
            game.setSteeringInput(Vec2(-1.0,0.0))
            work.await(); assertEquals(expected,game.bodies)
            val before=game.bodies
            val obsolete=async(start=CoroutineStart.UNDISPATCHED) { game.updateSandboxAsync(.1) }
            game.setMotionControlEnabled(false)
            obsolete.await(); assertEquals(before,game.bodies); assertNull(game.controlledVehicleId)
        }
    }
}
