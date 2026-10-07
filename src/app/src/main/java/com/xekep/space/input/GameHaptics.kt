package com.xekep.space.input

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short game impacts, independent of weak text-selection haptics. */
class GameHaptics(context: Context) : AutoCloseable {
    @Suppress("DEPRECATION")
    private val vibrator=if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()
    var enabled=true
        set(value) { if (field && !value) vibrator?.cancel(); field=value }
    private var foreground=true
    private var lastImpact=Long.MIN_VALUE
    val available: Boolean get() = vibrator?.hasVibrator() == true
    fun setForeground(value: Boolean) { foreground=value; if (!value) vibrator?.cancel() }
    fun impact(strong: Boolean = false): Boolean {
        if (!enabled || !foreground || !available) return false
        val now=SystemClock.elapsedRealtime()
        if (lastImpact != Long.MIN_VALUE && now-lastImpact < 50) return false
        lastImpact=now
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 32L else 14L,VibrationEffect.DEFAULT_AMPLITUDE),attributes)
        return true
    }
    override fun close() { foreground=false; vibrator?.cancel() }
}
