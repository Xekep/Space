package com.xekep.space.sim

/** Indexed discrete steps, in increasing order for both the cycle button and slider. */
val sandboxTimeScales = listOf(.0001, .001, .01, .25, 1.0, 3.0, 6.0)
fun sandboxTimeScalesFor(preset: SandboxPresetKind) = if (preset == SandboxPresetKind.SolarSystem) sandboxTimeScales else sandboxTimeScales.filter { it >= .25 }
fun nextSandboxTimeScale(current: Double, preset: SandboxPresetKind = SandboxPresetKind.SolarSystem): Double {
    val speeds=sandboxTimeScalesFor(preset)
    return speeds[(speeds.indexOf(current)+1)%speeds.size]
}
fun simulationSpeedLabel(speed: Double): String = when (speed) {
    .25 -> "¼×"
    .01 -> "0.01×"
    .001 -> "0.001×"
    .0001 -> "0.0001×"
    else -> "${speed.toInt()}×"
}
