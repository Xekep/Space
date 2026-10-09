package com.xekep.space.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class EngineAudioTest {
    @Test fun loopsAreDistinctNonClippingPeriodicAndContainNoHighFrequencyTones() {
        val ship=EngineAudio.loop(false); val rocket=EngineAudio.loop(true)
        assertFalse(ship.contentEquals(rocket))
        for (pcm in listOf(ship,rocket)) {
            assertEquals(EngineAudio.SAMPLE_RATE,pcm.size)
            assertTrue(pcm.maxOf { abs(it.toInt()) } in 5000..32000)
            assertTrue(abs(pcm.first().toInt()-pcm.last().toInt()) < 2000)
            // Harmonics were deliberately capped below 1 kHz at nominal playback rate.
            var real=0.0; var imaginary=0.0
            pcm.forEachIndexed { frame,value -> val phase=2*PI*8000*frame/pcm.size; real+=value*cos(phase); imaginary+=value*sin(phase) }
            assertTrue(hypot(real,imaginary)/pcm.size < 1.0)
        }
    }
}
