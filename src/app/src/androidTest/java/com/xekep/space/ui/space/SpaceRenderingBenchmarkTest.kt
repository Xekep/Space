package com.xekep.space.ui.space

import android.util.Log
import android.view.Choreographer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.Vec2
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*

/** Real display-frame intervals, including the HUD and trails. Emulator results are not phone FPS. */
class SpaceRenderingBenchmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileVisibleSandboxAtSixtyAndHundredBodies() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (count in listOf(60, 100)) for (speed in listOf(1.0, 6.0)) {
            val intervals = CopyOnWriteArrayList<Double>()
            var previous = 0L
            var simulationFrame = 0L
            var calculation: Job? = null
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            var updates = 0
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frame: Long) {
                    if (previous != 0L) {
                        val dt = (frame - previous) / 1e9
                        intervals += dt * 1000.0
                        if (calculation?.isActive != true) {
                            val elapsed = if (simulationFrame == 0L) dt else (frame - simulationFrame) / 1e9
                            simulationFrame = frame
                            calculation = scope.launch { game.updateSandboxAsync(elapsed); updates++ }
                        }
                    }
                    previous = frame
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            instrumentation.runOnMainSync {
                game.startSandbox(SandboxPresetKind.Empty)
                repeat(count) { i ->
                    val point = Vec2((i % 10) * 70.0 - 315.0, (i / 10) * 70.0 - 315.0)
                    game.launch(TouchPreview(point, point, 0), 0.0)
                }
                game.setTimeScale(speed)
                Choreographer.getInstance().postFrameCallback(callback)
            }
            Thread.sleep(2500)
            instrumentation.runOnMainSync { Choreographer.getInstance().removeFrameCallback(callback); scope.cancel(); game.openMenu() }
            val sorted = intervals.drop(5).sorted()
            assertTrue("No display frames recorded", sorted.isNotEmpty())
            Log.i("SpaceBenchmark", "RENDER,count=$count,speed=$speed,frames=${sorted.size},physicsUpdates=$updates,medianMs=${sorted[sorted.size / 2]},p95Ms=${sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)]},meanUiFps=${1000.0 / sorted.average()}")
        }
    }
}
