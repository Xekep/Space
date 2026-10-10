package com.xekep.space.ui.space

import kotlin.math.abs
import kotlin.math.floor

internal data class ArcadeWavePhase(val wave: Int, val seconds: Double) {
    val resting: Boolean get() = seconds >= 24.0
}

/** Snap only floating-point noise at boundaries. Wave and phase must share one clock. */
internal fun arcadeWavePhase(time: Double): ArcadeWavePhase {
    val elapsed=time.coerceAtLeast(0.0)
    val index=floor((elapsed+1e-7)/28.0).toInt()
    var seconds=(elapsed-index*28.0).coerceAtLeast(0.0)
    if (abs(seconds-24.0) <= 1e-7) seconds=24.0
    return ArcadeWavePhase(index+1,seconds)
}
