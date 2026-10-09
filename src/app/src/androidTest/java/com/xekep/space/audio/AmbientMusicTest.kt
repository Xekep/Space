package com.xekep.space.audio

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.MainActivity
import com.xekep.space.R
import org.junit.Assert.*
import org.junit.Test

class AmbientMusicTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun packagedTrackDecodesAsNinetySixSecondsOfAudio() {
        val metadata = MediaMetadataRetriever()
        try {
            for (resource in listOf(R.raw.quiet_orbits,R.raw.quiet_orbits_retro)) {
                instrumentation.targetContext.resources.openRawResourceFd(resource).use {
                    metadata.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue(duration in 95_990L..96_010L)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
        } finally {
            metadata.release()
        }
    }

    @Test fun musicPausesWhenDisabledOrBackgroundedAndCanResume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var music: AmbientMusic
            scenario.onActivity {
                music = AmbientMusic(it)
                music.setEnabled(true)
                music.setForeground(true)
            }
            try {
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var playing = false
                while (!playing && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync { playing = music.isPlaying }
                    if (!playing) SystemClock.sleep(50)
                }
                assertTrue("The foreground player did not obtain audio focus", playing)
                instrumentation.runOnMainSync {
                    music.setEnabled(false)
                    assertFalse(music.isPlaying)
                    music.setEnabled(true)
                    assertTrue(music.isPlaying)
                    music.setForeground(false)
                    assertFalse(music.isPlaying)
                    music.setForeground(true)
                    assertTrue(music.isPlaying)
                    music.close()
                    assertFalse(music.isReady)
                    assertFalse(music.isPlaying)
                }
            } finally {
                instrumentation.runOnMainSync { music.close() }
            }
        }
    }
    @Test fun retroSwitchPreservesTimelineAndRapidChangesRespectMuteAndLifecycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var music: AmbientMusic
            scenario.onActivity { music=AmbientMusic(it); music.setForeground(true); music.setEnabled(true) }
            fun waitUntil(condition: () -> Boolean) {
                val deadline=SystemClock.elapsedRealtime()+10000
                var ready=false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync { ready=condition() }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("Audio state did not settle",ready)
            }
            try {
                waitUntil { music.isPlaying }; SystemClock.sleep(1100)
                var before=0
                instrumentation.runOnMainSync { before=music.playbackPosition; music.setRetro(true) }
                waitUntil { music.isRetroPlaying }
                SystemClock.sleep(250)
                instrumentation.runOnMainSync {
                    assertTrue(music.playbackPosition >= before-150)
                    assertTrue(music.playbackPosition < before+1000)
                    music.setRetro(false); music.setRetro(true); music.setRetro(false)
                }
                waitUntil { music.isPlaying && !music.isRetroPlaying }
                SystemClock.sleep(250)
                instrumentation.runOnMainSync {
                    music.setRetro(true); music.setEnabled(false); assertFalse(music.isPlaying)
                }
                SystemClock.sleep(250)
                instrumentation.runOnMainSync { assertFalse(music.isPlaying); music.setEnabled(true) }
                waitUntil { music.isRetroPlaying }
                instrumentation.runOnMainSync { music.setForeground(false); assertFalse(music.isPlaying) }
                SystemClock.sleep(200)
                instrumentation.runOnMainSync { assertFalse(music.isPlaying); music.setRetro(false); music.setForeground(true) }
                waitUntil { music.isPlaying && !music.isRetroPlaying }
            } finally { instrumentation.runOnMainSync { music.close(); music.close(); assertFalse(music.isPlaying) } }
        }
    }

    @Test fun mutedMusicCanOwnEngineFocusWithoutStartingTheMusicTrack() {
        // Android 15 requires a foreground activity for focus. Keep the host's own music
        // disabled so two deliberately constructed test players cannot steal each other's focus.
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("space_options",0)
        val existed=prefs.contains("music"); val previous=prefs.getBoolean("music",true)
        prefs.edit().putBoolean("music",false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var music: AmbientMusic
                var available=false
                scenario.onActivity { activity ->
                    music=AmbientMusic(activity) { available=it }
                    music.setForeground(true); music.setEffectsActive(true)
                    assertTrue(music.isFocusHeld); assertTrue(available); assertFalse(music.isPlaying)
                }
                try {
                    SystemClock.sleep(500)
                    instrumentation.runOnMainSync {
                        assertFalse(music.isPlaying)
                        music.setEffectsActive(false); assertFalse(music.isFocusHeld); assertFalse(available)
                        music.setEffectsActive(true); assertTrue(music.isFocusHeld)
                        music.setForeground(false); assertFalse(music.isFocusHeld); assertFalse(available)
                    }
                } finally { instrumentation.runOnMainSync { music.close() } }
            }
        } finally { prefs.edit().apply { if (existed) putBoolean("music",previous) else remove("music") }.commit() }
    }
}
