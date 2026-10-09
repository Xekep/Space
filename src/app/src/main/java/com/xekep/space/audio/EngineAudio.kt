package com.xekep.space.audio

import kotlin.math.*

/** Integer-cycle harmonics give an exactly periodic one-second loop without a sharp hiss. */
internal object EngineAudio {
    const val SAMPLE_RATE=22050
    fun loop(rocket: Boolean): ShortArray {
        val base=if (rocket) 77 else 99
        return ShortArray(SAMPLE_RATE) { frame ->
            val phase=2*PI*frame/SAMPLE_RATE
            var value=0.0
            for (harmonic in 1..7) value+=sin(phase*base*harmonic)/ (harmonic*harmonic)
            value+=.15*sin(phase*23)+.08*sin(phase*41)
            (value*.55*Short.MAX_VALUE).roundToInt().coerceIn(-32767,32767).toShort()
        }
    }
}
