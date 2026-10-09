package com.xekep.space.audio

import android.media.*
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/** Cached static loops: the UI supplies a setpoint, a 25 Hz ramp only adjusts volume/rate. */
internal class EngineSound : AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private val tracks=listOf(false,true).map { rocket -> runCatching {
        val pcm=EngineAudio.loop(rocket)
        val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(EngineAudio.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
        try {
            check(track.state != AudioTrack.STATE_UNINITIALIZED)
            check(track.write(pcm,0,pcm.size) == pcm.size)
            check(track.setLoopPoints(0,pcm.size,-1) == AudioTrack.SUCCESS)
            track.setVolume(0f); track
        } catch (error: Exception) { track.release(); throw error }
    }.getOrNull() }
    private var closed=false
    private var running=false
    private var scheduled=false
    private var appliedRate=-1
    private var active=0
    private var gain=0f
    private var targetGain=0f
    private var targetRate=EngineAudio.SAMPLE_RATE
    internal val isPlaying get()=tracks.any { it?.playState == AudioTrack.PLAYSTATE_PLAYING }
    private val ramp=object : Runnable {
        override fun run() {
            scheduled=false
            if (closed) return
            val track=tracks[active] ?: return
            gain+=(targetGain-gain)*.28f
            if (abs(gain-targetGain) < .001f) gain=targetGain
            track.setVolume(gain)
            if (abs(appliedRate-targetRate) > 30) { track.setPlaybackRate(targetRate); appliedRate=targetRate }
            if (targetGain == 0f && gain < .001f) { silence(); return }
            if (gain != targetGain) { scheduled=true; handler.postDelayed(this,40) }
        }
    }
    fun update(rocket: Boolean, thrust: Float, retro: Boolean) {
        if (closed) return
        val desired=if (rocket) 1 else 0
        val amount=thrust.takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f
        if (desired != active) { silence(); active=desired; appliedRate=-1 }
        val nextGain=if (amount > 0f) (.025f+amount*.10f)*(if (retro) .85f else 1f) else 0f
        val nextRate=(EngineAudio.SAMPLE_RATE*(.75f+.60f*amount)*(if (retro) .90f else 1f)).toInt()
        val changed=targetGain != nextGain || targetRate != nextRate
        targetGain=nextGain; targetRate=nextRate
        if (!running && targetGain > 0f) {
            val track=tracks[active] ?: return
            running=true; track.play(); scheduled=true; handler.post(ramp)
        } else if (running && changed && !scheduled) { scheduled=true; handler.post(ramp) }
    }
    fun silence() {
        if (!running && gain == 0f) { targetGain=0f; return }
        handler.removeCallbacks(ramp); scheduled=false; running=false; gain=0f; targetGain=0f
        tracks.forEach { track -> track?.setVolume(0f); if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause() }
    }
    override fun close() { if (!closed) { silence(); closed=true; tracks.forEach { it?.release() } } }
}
