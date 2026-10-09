package com.xekep.space.audio

import kotlin.math.*

/** Sample-and-hold plus signed 8-bit quantisation. Stateless, runs when building a clip,
 * never during a physics/render frame. The final envelope keeps even stepped edges quiet. */
internal object RetroAudio {
    const val SAMPLE_RATE = 22050
    const val HOLD_FRAMES = 3 // Effective 7.35 kHz DAC.

    fun filter(samples: FloatArray): FloatArray = FloatArray(samples.size) { index ->
        val source = samples[index / HOLD_FRAMES * HOLD_FRAMES].coerceIn(-1f, 1f)
        (source * 127f).roundToInt() / 127f
    }

    fun event(hit: Boolean): ShortArray {
        val count = SAMPLE_RATE * 90 / 1000
        var phase = 0.0
        var noise = 0x51ace
        val source = FloatArray(count) { index ->
            val progress = index.toDouble() / (count - 1)
            val hz = if (hit) 420 - 230 * progress else if (progress < .48) 1046.5 else 1568.0
            phase += 2 * PI * hz / SAMPLE_RATE
            noise = noise xor (noise shl 13); noise = noise xor (noise ushr 17); noise = noise xor (noise shl 5)
            val grit = (noise and 65535) / 32767.5 - 1
            val wave = if (hit) sin(phase) * .7 + grit * .3 else sin(phase) * .78 + sin(phase * 2) * .22
            (wave * .48).toFloat()
        }
        val filtered = filter(source)
        return ShortArray(count) { index ->
            val attack = (index.toDouble() / (SAMPLE_RATE * .004)).coerceIn(0.0, 1.0)
            val release = ((count - 1 - index).toDouble() / (SAMPLE_RATE * .014)).coerceIn(0.0, 1.0)
            val envelope = sin(attack * PI / 2).pow(2) * sin(release * PI / 2).pow(2)
            (filtered[index] * envelope * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
