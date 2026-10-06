package com.xekep.space.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import com.xekep.space.R

/** One offline looping player owned by the scene's lifecycle. */
@MainThread
class AmbientMusic(context: Context) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
        .build()
    private var player: MediaPlayer? = null
    private var enabled = false
    private var foreground = false
    private var prepared = false
    private var requestedFocus = false
    private var hasFocus = false
    private var closed = false
    val isReady: Boolean get() = prepared
    val isPlaying: Boolean get() = prepared && player?.isPlaying == true

    init {
        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(R.raw.quiet_orbits).use {
                created.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            created.isLooping = true
            created.setVolume(0.28f, 0.28f)
            created.setOnPreparedListener {
                if (!closed) {
                    prepared = true
                    updatePlayback()
                }
            }
            created.setOnErrorListener { _, what, extra ->
                Log.w("SpaceMusic", "Playback unavailable ($what/$extra)")
                releasePlayer()
                true
            }
            created.prepareAsync()
        } catch (error: Exception) {
            Log.w("SpaceMusic", "Cannot prepare ambient track", error)
            releasePlayer()
        }
    }

    fun setEnabled(value: Boolean) {
        if (closed || enabled == value) return
        enabled = value
        updatePlayback()
    }

    fun setForeground(value: Boolean) {
        if (closed || foreground == value) return
        foreground = value
        updatePlayback()
    }

    private fun updatePlayback() {
        if (closed) return
        if (!enabled || !foreground) {
            pause()
            abandonFocus()
            return
        }
        if (!prepared) return
        if (!requestedFocus) {
            requestedFocus = true
            when (audioManager.requestAudioFocus(focusRequest)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> hasFocus = true
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> hasFocus = false
                else -> { hasFocus = false; requestedFocus = false }
            }
        }
        if (hasFocus && !isPlaying) player?.start()
    }

    private fun onFocusChange(change: Int) {
        if (closed) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                updatePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                requestedFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                pause()
            }
        }
    }

    private fun pause() {
        if (isPlaying) player?.pause()
    }

    private fun abandonFocus() {
        if (requestedFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        requestedFocus = false
        hasFocus = false
    }

    private fun releasePlayer() {
        prepared = false
        abandonFocus()
        player?.release()
        player = null
    }

    override fun close() {
        if (closed) return
        closed = true
        releasePlayer()
    }
}
