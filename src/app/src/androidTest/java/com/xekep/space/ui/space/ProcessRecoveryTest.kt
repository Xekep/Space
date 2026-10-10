package com.xekep.space.ui.space

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.xekep.space.MainActivity
import com.xekep.space.sim.*
import com.xekep.space.storage.SessionRecovery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit two-process protocol on an owned test AVD; never clear application data. */
class ProcessRecoveryTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun model(): SpaceViewModel {
        context.getSharedPreferences("space_language",0).edit().putBoolean("chosen",true).commit()
        instrumentation.runOnMainSync { context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        var model: SpaceViewModel?=null
        val deadline=SystemClock.elapsedRealtime()+15000
        while (SystemClock.elapsedRealtime()<deadline) {
            instrumentation.runOnMainSync {
                val host=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                host?.let { model=ViewModelProvider(it)[SpaceViewModel::class.java] }
            }
            var ready=false
            instrumentation.runOnMainSync { ready=model?.game?.recoveryLoading == false }
            if (ready) return requireNotNull(model)
            SystemClock.sleep(50)
        }
        error("Recovery did not finish")
    }
    @Test fun prepareDurableRun() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryProcess") == "prepare")
        val model=model()
        instrumentation.runOnMainSync {
            val game=model.game
            game.startSandbox(SandboxPresetKind.SolarSystem,"Process recovery")
            game.startArcade()
            val initial=game.recoverySnapshot(0)!!
            val core=initial.arcade!!.bodies.first()
            val threat=core.copy(id=SimulationEngine.newBodyId(),kind=BodyKind.Meteor,position=core.position+Vec2(900.0,0.0),mass=20.0,trail=listOf(core.position+Vec2(900.0,0.0)))
            val ship=core.copy(id=SimulationEngine.newBodyId(),kind=BodyKind.Ship,position=core.position+Vec2(100.0,0.0),mass=24.0,fuelRemaining=80.0,trail=listOf(core.position+Vec2(100.0,0.0)))
            val run=initial.arcade!!.copy(bodies=listOf(core,ship),elapsed=400.0,score=1234.0,lives=3,
                pending=listOf(PendingThreat(threat,.75)),upgrades=mapOf(ArcadeUpgrade.Guns to 1),
                offeredUpgradeWaves=setOf(3,6,10),chosenUpgradeWaves=setOf(3,6,10),
                combat=ArcadeCombat(listOf(SpaceProjectile(ship.position,Vec2(30.0,0.0),ship.id,.8)),mapOf(ship.id to CraftStatus(5.0,.2,threat.id))))
            game.restoreRecovery(initial.copy(arcade=run))
        }
        assertTrue(runBlocking { withTimeout(10000) { withContext(Dispatchers.Main) { model.flushRecovery() } } })
        File(context.cacheDir,"process-recovery-expected.json").writeText(SessionRecovery(context).encode(requireNotNull(SessionRecovery(context).load())))
    }
    @Test fun verifyAfterForceStopInAnotherProcess() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryProcess") == "verify")
        val expected=SessionRecovery(context).decode(File(context.cacheDir,"process-recovery-expected.json").readText())
        val model=model()
        instrumentation.runOnMainSync {
            val game=model.game
            assertEquals(expected.arcade,game.arcade)
            assertEquals(expected.sandbox!!.bodies,game.sandbox!!.bodies)
            assertTrue(game.menuOpen);assertFalse(game.motionSteeringEnabled)
            assertFalse(game.recoveryFailed);assertFalse(game.recoveryLoading)
            assertEquals(expected.randomState,game.recoverySnapshot(0)!!.randomState)
            val ids=game.bodies.map { it.id }+game.arcade!!.pending.map { it.body.id }
            assertEquals(ids.size,ids.toSet().size)
            assertTrue(SimulationEngine.newBodyId()>ids.max())
            game.closeMenu();game.update(1.0/30)
            assertTrue(game.arcade!!.combat.projectiles.all { it.remaining < .8 })
            game.openMenu()
        }
    }
}
