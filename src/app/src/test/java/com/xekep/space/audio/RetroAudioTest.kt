package com.xekep.space.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RetroAudioTest {
    @Test fun filterHoldsQuantizedSamplesAndClipsOverload() {
        val filtered=RetroAudio.filter(floatArrayOf(2f,-.3f,.2f,.126f,.9f,-.9f,-2f))
        assertArrayEquals(floatArrayOf(1f,1f,1f,16f/127,16f/127,16f/127,-1f),filtered,1e-6f)
        assertTrue(filtered.all { abs(it) <= 1f })
    }
    @Test fun eventVoicesAreDeterministicDistinctAndHaveQuietEndpoints() {
        val success=RetroAudio.event(false); val hit=RetroAudio.event(true)
        assertEquals(1984,success.size); assertEquals(success.size,hit.size)
        assertArrayEquals(hit,RetroAudio.event(true)); assertFalse(success.contentEquals(hit))
        for (clip in listOf(success,hit)) {
            assertEquals(0,clip.first().toInt()); assertEquals(0,clip.last().toInt())
            assertTrue(clip.maxOf { abs(it.toInt()) } in 8000..16000)
            assertTrue(clip.take(20).maxOf { abs(it.toInt()) } < 1500)
            assertTrue(clip.takeLast(20).maxOf { abs(it.toInt()) } < 200)
        }
    }
}
