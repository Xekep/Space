package com.xekep.space.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import com.xekep.space.R

/** Offline normal/retro loops share one focus request and crossfade at the current position. */
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
    private class Slot(val resource: Int) {
        var player: MediaPlayer? = null
        var prepared = false
        var gain = 0f
    }
    private val normal = Slot(R.raw.quiet_orbits)
    private val retro = Slot(R.raw.quiet_orbits_retro)
    private val slots = listOf(normal,retro)
    private val handler = Handler(Looper.getMainLooper())
    private var transition: Runnable? = null
    private var active: Slot? = null
    private var seeking: Slot? = null
    private var retroEnabled = false
    private var enabled = false
    private var foreground = false
    private var requestedFocus = false
    private var hasFocus = false
    private var closed = false
    val isReady: Boolean get() = desired().prepared
    val isPlaying: Boolean get() = slots.any { it.prepared && it.player?.isPlaying == true }
    internal val isRetroPlaying: Boolean get() = active == retro && retro.prepared && retro.player?.isPlaying == true
    internal val playbackPosition: Int get() = active?.takeIf { it.prepared }?.player?.currentPosition ?: 0
    private fun desired() = if (retroEnabled && retro.player != null) retro else normal

    init {
        slots.forEach { slot -> prepare(context,slot) }
    }

    private fun prepare(context: Context,slot: Slot) {
        val created = MediaPlayer()
        slot.player = created
        try {
            created.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(slot.resource).use {
                created.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            created.isLooping = true
            created.setVolume(0f,0f)
            created.setOnPreparedListener {
                if (!closed) {
                    slot.prepared = true
                    updatePlayback()
                }
            }
            created.setOnErrorListener { _, what, extra ->
                Log.w("SpaceMusic", "Playback unavailable ($what/$extra)")
                slot.prepared = false
                slot.player?.release(); slot.player = null
                if (active == slot) active = null
                if (seeking == slot) seeking = null
                updatePlayback()
                true
            }
            created.prepareAsync()
        } catch (error: Exception) {
            Log.w("SpaceMusic", "Cannot prepare ambient track", error)
            created.release(); slot.player = null
        }
    }

    fun setRetro(value: Boolean) {
        if (closed || retroEnabled == value) return
        retroEnabled = value
        updatePlayback()
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
        if (!desired().prepared) return
        if (!requestedFocus) {
            requestedFocus = true
            when (audioManager.requestAudioFocus(focusRequest)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> hasFocus = true
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> hasFocus = false
                else -> { hasFocus = false; requestedFocus = false }
            }
        }
        if (hasFocus) playDesired()
    }

    private fun playDesired() {
        val target = desired()
        if (active == target && transition != null && target.player?.isPlaying == true) return
        if (active == null || active == target) {
            active = target
            cancelTransition()
            slots.forEach { slot ->
                slot.gain = if (slot == target) 1f else 0f
                slot.player?.setVolume(slot.gain*.28f,slot.gain*.28f)
                if (slot != target && slot.prepared && slot.player?.isPlaying == true) slot.player?.pause()
            }
            if (target.player?.isPlaying != true) target.player?.start()
            return
        }
        if (target.player?.isPlaying == true) { fadeTo(target); return }
        if (seeking == target) return
        seeking = target
        val position = playbackPosition
        target.player?.setOnSeekCompleteListener {
            if (closed) return@setOnSeekCompleteListener
            if (seeking == target) seeking = null
            if (enabled && foreground && hasFocus && desired() == target) {
                target.player?.start()
                fadeTo(target)
            } else updatePlayback()
        }
        target.player?.seekTo(position.toLong(),MediaPlayer.SEEK_CLOSEST)
    }

    private fun fadeTo(target: Slot) {
        cancelTransition()
        active = target
        val start = SystemClock.uptimeMillis()
        val gains = slots.map { it.gain }
        val ramp = object : Runnable {
            override fun run() {
                if (closed || !enabled || !foreground || !hasFocus) return
                val progress = ((SystemClock.uptimeMillis()-start)/160f).coerceIn(0f,1f)
                slots.forEachIndexed { index,slot ->
                    val end = if (slot == target) 1f else 0f
                    slot.gain = gains[index]+(end-gains[index])*progress
                    slot.player?.setVolume(slot.gain*.28f,slot.gain*.28f)
                    if (progress >= 1f && slot != target && slot.prepared && slot.player?.isPlaying == true) slot.player?.pause()
                }
                if (progress < 1f) handler.postDelayed(this,16) else transition = null
            }
        }
        transition = ramp
        ramp.run()
    }

    private fun cancelTransition() {
        transition?.let(handler::removeCallbacks)
        transition = null
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
        cancelTransition()
        slots.forEach { if (it.prepared && it.player?.isPlaying == true) it.player?.pause() }
    }

    private fun abandonFocus() {
        if (requestedFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        requestedFocus = false
        hasFocus = false
    }

    private fun releasePlayer() {
        cancelTransition()
        abandonFocus()
        slots.forEach { it.prepared = false; it.player?.release(); it.player = null }
        active = null; seeking = null
    }

    override fun close() {
        if (closed) return
        closed = true
        releasePlayer()
    }
}
