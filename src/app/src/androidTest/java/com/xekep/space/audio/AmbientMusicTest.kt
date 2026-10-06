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
            instrumentation.targetContext.resources.openRawResourceFd(R.raw.quiet_orbits).use {
                metadata.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue(duration in 95_990L..96_010L)
            assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
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
}
