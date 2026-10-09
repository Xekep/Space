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
    @Test fun engineLoopFollowsPilotThrustAndStopsForMutePauseFocusLossAndRelease() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var sounds: GameSounds
        instrumentation.runOnMainSync { sounds=GameSounds(); sounds.updateEngine(false,.5f); assertFalse(sounds.isEnginePlaying); sounds.setForeground(true); sounds.updateEngine(false,.5f); assertTrue(sounds.isEnginePlaying) }
        try {
            Thread.sleep(100)
            instrumentation.runOnMainSync {
                sounds.retro=true; sounds.updateEngine(true,1f); assertTrue(sounds.isEnginePlaying)
                sounds.enabled=false; assertFalse(sounds.isEnginePlaying)
                sounds.enabled=true; sounds.updateEngine(true,.3f); assertTrue(sounds.isEnginePlaying)
                sounds.setAudioFocusAvailable(false); assertFalse(sounds.isEnginePlaying)
                sounds.updateEngine(false,.5f); assertFalse(sounds.isEnginePlaying)
                sounds.setAudioFocusAvailable(true); sounds.updateEngine(false,.5f); assertTrue(sounds.isEnginePlaying)
                sounds.setForeground(false); assertFalse(sounds.isEnginePlaying)
                sounds.setForeground(true); sounds.updateEngine(false,.5f); sounds.updateEngine(false,0f)
            }
            Thread.sleep(900)
            instrumentation.runOnMainSync { assertFalse(sounds.isEnginePlaying); sounds.close(); sounds.updateEngine(true,1f); assertFalse(sounds.isEnginePlaying) }
        } finally { instrumentation.runOnMainSync { sounds.close() } }
    }
}
