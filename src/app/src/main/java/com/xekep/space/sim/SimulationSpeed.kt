package com.xekep.space.sim

/** Ordinary play first, then close-up orbital inspection, then accelerated time. */
val sandboxTimeScales = listOf(1.0, .25, .01, .001, .0001, 3.0, 6.0)
fun nextSandboxTimeScale(current: Double): Double = sandboxTimeScales[(sandboxTimeScales.indexOf(current)+1)%sandboxTimeScales.size]
fun simulationSpeedLabel(speed: Double): String = when (speed) {
    .25 -> "¼×"
    .01 -> "0.01×"
    .001 -> "0.001×"
    .0001 -> "0.0001×"
    else -> "${speed.toInt()}×"
}
