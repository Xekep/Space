package com.xekep.space.ui.space

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Debug
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.xekep.space.MainActivity
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Includes first frames, unlike steady-state probes. Run each preset in a fresh instrumentation process. */
class WorldStartBenchmarkTest {
    @Test fun compareFirstEntryWithRepeatedEntryInTheRealActivity() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val preset=InstrumentationRegistry.getArguments().getString("worldStartPreset") ?: "Arcade"
        val label=InstrumentationRegistry.getArguments().getString("probeLabel") ?: "current"
        val quality=InstrumentationRegistry.getArguments().getString("graphicsQuality")
        val options=context.getSharedPreferences("space_options",0)
        val previousQuality=options.getString("graphicsQuality",null)
        if (quality != null) { require(quality in listOf("Auto","Full","Economy")); options.edit().putString("graphicsQuality",quality).commit() }
        val trace=InstrumentationRegistry.getArguments().getString("traceStart") == "true"
        val language=context.getSharedPreferences("space_language",0)
        val hadChosen=language.contains("chosen"); val chosen=language.getBoolean("chosen",false)
        language.edit().putBoolean("chosen",true).commit()
        var activity: MainActivity?=null
        try {
            val launch=SystemClock.elapsedRealtimeNanos()
            instrumentation.runOnMainSync { context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            val deadline=SystemClock.elapsedRealtime()+10000
            while (activity == null && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                }
                if (activity == null) SystemClock.sleep(50)
            }
            val host=requireNotNull(activity)
            val resumedMs=(SystemClock.elapsedRealtimeNanos()-launch)/1e6
            lateinit var game: SpaceGameState
            instrumentation.runOnMainSync { game=ViewModelProvider(host)[SpaceViewModel::class.java].game }
            SystemClock.sleep(1000)
            val report=StringBuilder("preset,entry,resumedMs,createMs,firstVsyncMs,window,frames,totalP95Ms,totalMaxMs,over32ms,dropped\n")
            repeat(2) { entry ->
                val samples=ArrayList<Pair<Double,Double>>()
                val detail=StringBuilder("elapsedMs,totalMs,layoutMs,drawMs,syncMs,commandsMs,swapMs\n")
                var start=0L; var createMs=0.0; var dropped=0
                val listener=Window.OnFrameMetricsAvailableListener { _,metrics,missed ->
                    val intended=metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                    if (intended >= start && start != 0L) {
                        samples+=(intended-start)/1e6 to metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1e6
                        detail.appendLine(listOf((intended-start)/1e6,FrameMetrics.TOTAL_DURATION,FrameMetrics.LAYOUT_MEASURE_DURATION,
                            FrameMetrics.DRAW_DURATION,FrameMetrics.SYNC_DURATION,FrameMetrics.COMMAND_ISSUE_DURATION,
                            FrameMetrics.SWAP_BUFFERS_DURATION).mapIndexed { i,v -> if (i == 0) v.toDouble() else metrics.getMetric(v.toInt())/1e6 }.joinToString(","))
                        dropped+=missed
                    }
                }
                instrumentation.runOnMainSync {
                    host.window.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()))
                    if (trace && entry == 0) Debug.startMethodTracingSampling(File(context.externalCacheDir,"world-start-$label-$preset.trace").absolutePath,16*1024*1024,2000)
                    start=System.nanoTime()
                    if (preset == "Arcade" || preset == "Fleet") game.startArcade()
                    else game.startSandbox(SandboxPresetKind.valueOf(preset))
                    if (preset == "Fleet") {
                        game.chooseSpawnKind(BodyKind.Ship)
                        repeat(2) { i ->
                            val point=game.camera.center+Vec2(-320.0,100.0+i*130)
                            game.launch(TouchPreview(point,point+Vec2(0.0,-80.0),0),0.0)
                        }
                    }
                    createMs=(System.nanoTime()-start)/1e6
                }
                SystemClock.sleep(10000)
                instrumentation.runOnMainSync {
                    host.window.removeOnFrameMetricsAvailableListener(listener)
                    if (trace && entry == 0) Debug.stopMethodTracing()
                    assertFalse(game.menuOpen)
                    assertTrue(game.bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() })
                    game.openMenu()
                }
                assertTrue("No rendered frames: $preset",samples.isNotEmpty())
                File(context.externalCacheDir,"world-start-$label-$preset-$entry-frames.csv").writeText(detail.toString())
                val first=samples.minOf { it.first }
                for ((name,range) in listOf("firstSecond" to 0.0..1000.0,"firstFive" to 0.0..5000.0,"laterFive" to 5000.0..10000.0)) {
                    val times=samples.filter { it.first in range }.map { it.second }.sorted()
                    assertTrue("No $name frames: $preset",times.isNotEmpty())
                    val line="$preset,$entry,$resumedMs,$createMs,$first,$name,${times.size},${times[(times.size*.95).toInt().coerceAtMost(times.lastIndex)]},${times.last()},${times.count { it > 32 }},$dropped"
                    report.appendLine(line); Log.i("SpaceBenchmark","WORLD_START,$label,$line")
                }
                SystemClock.sleep(500)
            }
            File(context.externalCacheDir,"world-start-$label-$preset.csv").writeText(report.toString())
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            options.edit().apply { if (previousQuality == null) remove("graphicsQuality") else putString("graphicsQuality",previousQuality) }.commit()
            language.edit().apply { if (hadChosen) putBoolean("chosen",chosen) else remove("chosen") }.commit()
        }
    }
}
