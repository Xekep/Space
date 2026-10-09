package com.xekep.space.ui.space

import android.view.Choreographer
import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Display intervals are emulator evidence, not a measurement of a low-end phone. */
class ArcadeRenderingBenchmarkTest {
    @get:Rule val compose=createComposeRule()
    @Test fun profileNormalFirstFleet()=profile(false)
    @Test fun profileRetroFirstFleet()=profile(true)
    private fun profile(retro: Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=instrumentation.targetContext.getSharedPreferences("space_options",0)
        val existed=prefs.contains("retroConsole"); val previous=prefs.getBoolean("retroConsole",false)
        val game=SpaceGameState()
        prefs.edit().putBoolean("retroConsole",retro).commit()
        try {
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.waitForIdle()
            run {
                val frames=CopyOnWriteArrayList<Double>(); var last=0L
                val callback=object : Choreographer.FrameCallback {
                    override fun doFrame(time: Long) { if (last != 0L) frames.add((time-last)/1e6); last=time; Choreographer.getInstance().postFrameCallback(this) }
                }
                instrumentation.runOnMainSync {
                    game.startArcade(); game.chooseSpawnKind(BodyKind.Ship)
                    repeat(2) { i -> val point=game.camera.center+Vec2(-320.0,100.0+i*130); game.launch(TouchPreview(point,point+Vec2(0.0,-80.0),0),0.0) }
                    assertEquals(2,game.bodies.count { it.kind == BodyKind.Ship })
                    Choreographer.getInstance().postFrameCallback(callback)
                }
                Thread.sleep(3000)
                instrumentation.runOnMainSync { Choreographer.getInstance().removeFrameCallback(callback); game.openMenu() }
                val sorted=frames.drop(10).sorted(); assertTrue(sorted.isNotEmpty())
                Log.i("SpaceBenchmark","FIRST_FLEET,retro=$retro,frames=${sorted.size},medianMs=${sorted[sorted.size/2]},p95Ms=${sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)]}")
            }
        } finally { prefs.edit().apply { if (existed) putBoolean("retroConsole",previous) else remove("retroConsole") }.commit() }
    }
}
