package com.xekep.space.ui.space

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.MainActivity
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import com.xekep.space.storage.SandboxStorage
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Runs the production frame loop, without Compose's test animation clock. Timings are device specific. */
class LargeWorldBenchmarkTest {
    @Test fun profileFiveHundredBodiesInTheRealActivity() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val preferences=context.getSharedPreferences("space_language",0)
        val wasChosen=preferences.getBoolean("chosen",false)
        val hadChosen=preferences.contains("chosen")
        preferences.edit().putBoolean("chosen",true).commit()
        val storage=SandboxStorage(context)
        val fixture=File(context.externalCacheDir,"benchmark-world.space.json")
        val reuse=InstrumentationRegistry.getArguments().getString("reuseWorld") == "true"
        var nextId=1L
        val snapshot=if (reuse) storage.decode(fixture.readText()) else {
            val bodies=RandomSystems.create(Random(17)) { nextId++ }
            SandboxSnapshot(bodies,Vec2.Zero,.025f,SimulationEngine.totalEnergy(bodies),0,
                preset=SandboxPresetKind.RandomSystems).also { fixture.writeText(storage.encode(it)) }
        }
        try {
            instrumentation.runOnMainSync { context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            var resumed: MainActivity?=null
            val deadline=SystemClock.elapsedRealtime()+10000
            while (resumed == null && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    resumed=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                }
                if (resumed == null) SystemClock.sleep(50)
            }
            val activity=requireNotNull(resumed) { "Game activity did not resume" }
            lateinit var game: SpaceGameState
            instrumentation.runOnMainSync { game=ViewModelProvider(activity)[SpaceViewModel::class.java].game }
            try {
                SystemClock.sleep(1000)
                val failures=ArrayList<String>()
                for ((speed,collisions) in listOf(1.0 to false,6.0 to false,1.0 to true)) {
                    val frameGaps=ArrayList<Double>()
                    val drawGaps=ArrayList<Double>()
                    val snapshotGaps=ArrayList<Double>()
                    var previousFrame=0L; var previousDraw=0L; var previousSnapshot=0L
                    var bodies=game.bodies
                    var publications=0
                    val callback=object : Choreographer.FrameCallback {
                        override fun doFrame(frame: Long) {
                            if (previousFrame != 0L) frameGaps+=(frame-previousFrame)/1e6
                            if (bodies !== game.bodies) {
                                if (previousSnapshot != 0L) snapshotGaps+=(frame-previousSnapshot)/1e6
                                previousSnapshot=frame; bodies=game.bodies; publications++
                            }
                            previousFrame=frame
                            Choreographer.getInstance().postFrameCallback(this)
                        }
                    }
                    val listener=Window.OnFrameMetricsAvailableListener { _,metrics,_ ->
                        val time=metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                        if (previousDraw != 0L && time > previousDraw) drawGaps+=(time-previousDraw)/1e6
                        previousDraw=time
                    }
                    instrumentation.runOnMainSync {
                        game.loadSandbox(snapshot.copy(collisionsEnabled=collisions,collisionMode=SandboxCollisionMode.Debris))
                        game.setTimeScale(speed)
                    }
                    SystemClock.sleep(800)
                    instrumentation.runOnMainSync {
                        activity.window.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()))
                        Choreographer.getInstance().postFrameCallback(callback)
                    }
                    SystemClock.sleep(3000)
                    instrumentation.runOnMainSync {
                        Choreographer.getInstance().removeFrameCallback(callback)
                        activity.window.removeOnFrameMetricsAvailableListener(listener)
                        if (game.menuOpen || game.sandbox!!.paused) failures+="speed=$speed collisions=$collisions menu=${game.menuOpen} paused=${game.sandbox!!.paused}"
                        assertTrue(game.bodies.all { body -> body.position.x.isFinite() && body.velocity.y.isFinite() })
                        game.openMenu()
                    }
                    fun p95(values: List<Double>): Double = values.sorted().let { sorted ->
                        if (sorted.isEmpty()) 0.0 else sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)]
                    }
                    if (publications <= 1 || drawGaps.isEmpty()) failures+="speed=$speed no updates: $publications draws=${drawGaps.size}"
                    Log.i("SpaceBenchmark","LARGE_WORLD,reuse=$reuse,speed=$speed,collisions=$collisions,publications=$publications,"+
                        "vsyncFps=${1000/frameGaps.average()},drawFps=${1000/drawGaps.average()},drawP95Ms=${p95(drawGaps)},physicsP95Ms=${p95(snapshotGaps)},bodies=${game.bodies.size},paused=${game.sandbox!!.paused}")
                }
                assertTrue(failures.joinToString(),failures.isEmpty())
            } finally { instrumentation.runOnMainSync { game.openMenu(); activity.finish() } }
        } finally {
            preferences.edit().apply { if (hadChosen) putBoolean("chosen",wasChosen) else remove("chosen") }.commit()
        }
    }
}
