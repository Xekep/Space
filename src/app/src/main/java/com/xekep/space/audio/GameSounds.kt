package com.xekep.space.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import androidx.annotation.MainThread

/** Two cached 90 ms voices; an ongoing voice finishes its envelope when the style changes. */
@MainThread
class GameSounds : AutoCloseable {
    private val normal = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC,35) }.getOrNull()
    private data class Voice(val track: AudioTrack,val frames: Int)
    private val voices = listOf(false,true).map { hit -> runCatching {
        val pcm = RetroAudio.event(hit)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RetroAudio.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
        try {
            check(track.state != AudioTrack.STATE_UNINITIALIZED)
            check(track.write(pcm,0,pcm.size) == pcm.size)
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.setVolume(.35f)
            Voice(track,pcm.size)
        } catch (error: Exception) { track.release(); throw error }
    }.getOrNull() }
    private val engine=EngineSound()
    private var focusAvailable=true
    internal val isEnginePlaying get()=engine.isPlaying
    private var foreground = false
    private var closed = false
    var enabled = true
        set(value) { field = value; if (!value && !closed) stop() }
    var retro = false
    internal var lastPlayedRetro: Boolean? = null; private set

    private fun Voice.active() = track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < frames

    fun setForeground(value: Boolean) {
        if (closed) return
        foreground = value
        if (!value) stop()
    }

    private fun stop() {
        normal?.stopTone()
        voices.forEach { it?.track?.stop() }
        engine.silence()
    }

    fun setAudioFocusAvailable(value: Boolean) {
        focusAvailable=value
        if (!value && !closed) engine.silence()
    }
    fun updateEngine(rocket: Boolean, thrust: Float) {
        if (closed) return
        if (!enabled || !foreground || !focusAvailable) { engine.silence(); return }
        engine.update(rocket,thrust,retro)
    }

    fun play(hit: Boolean) {
        if (closed || !enabled || !foreground) return
        if (!retro) {
            normal?.startTone(if (hit) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK,90)
            lastPlayedRetro = false
            return
        }
        val voice = voices[if (hit) 1 else 0] ?: return
        // Avoid restarting a stepped waveform halfway through a sample on rapid intercepts.
        if (voice.active() || (!hit && voices[1]?.active() == true)) return
        voice.track.stop()
        voice.track.reloadStaticData()
        voice.track.play()
        lastPlayedRetro = true
    }

    override fun close() {
        if (closed) return
        setForeground(false)
        closed = true
        engine.close()
        normal?.release()
        voices.forEach { it?.track?.release() }
    }
}
