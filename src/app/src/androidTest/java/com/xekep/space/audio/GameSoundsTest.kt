package com.xekep.space.audio

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class GameSoundsTest {
    @Test fun nativePcmVoicesSwitchStyleAndMuteAndForegroundGatePlayback() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val sounds=GameSounds()
            try {
                sounds.retro=true; sounds.play(true); assertNull(sounds.lastPlayedRetro)
                sounds.setForeground(true); sounds.play(true); assertEquals(true,sounds.lastPlayedRetro)
                sounds.enabled=false; sounds.retro=false; sounds.play(false); assertEquals(true,sounds.lastPlayedRetro)
                sounds.enabled=true; sounds.play(false); assertEquals(false,sounds.lastPlayedRetro)
                sounds.setForeground(false); sounds.retro=true; sounds.play(false); assertEquals(false,sounds.lastPlayedRetro)
                sounds.setForeground(true); sounds.play(false); assertEquals(true,sounds.lastPlayedRetro)
                sounds.close(); sounds.retro=false; sounds.play(true); assertEquals(true,sounds.lastPlayedRetro)
            } finally { sounds.close() }
        }
    }
}
