package com.xekep.space.input

import com.xekep.space.sim.Vec2
import kotlin.math.*

/** Calibrated wheel rotation; positive input turns clockwise in screen/world coordinates. */
class TiltSteeringDetector {
    private var neutral: Double? = null
    private var neutralPitch: Double? = null
    private var neutralQuaternion: DoubleArray? = null
    private var previous = 0L
    private var rotation = -1
    private var filtered = Vec2.Zero
    private var previousPitch = 0.0
    private var previousPitchTime = 0L
    private var lastBoost = -1L
    private var boostArmed = true
    fun reset() {
        neutral = null; neutralPitch = null; neutralQuaternion = null; previous = 0L; filtered = Vec2.Zero; rotation = -1
        previousPitch = 0.0; previousPitchTime = 0L; lastBoost = -1L; boostArmed = true
    }

    /** Twist about the screen normal works even when the phone is held flat. */
    fun sampleQuaternion(x: Double, y: Double, z: Double, w: Double, timestamp: Long, screenRotation: Int = 0): Vec2 {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !w.isFinite()) return Vec2.Zero
        val norm = sqrt(x*x+y*y+z*z+w*w)
        if (norm < 1e-6) return Vec2.Zero
        if (rotation != screenRotation) { reset(); rotation = screenRotation }
        val q=doubleArrayOf(x/norm,y/norm,z/norm,w/norm)
        val base=neutralQuaternion ?: run { neutralQuaternion=q; previous=timestamp; previousPitchTime=timestamp; return Vec2.Zero }
        // q(relative) = conjugate(q(neutral)) * q(current), then swing/twist about local Z.
        val relativeZ=base[3]*q[2]-base[2]*q[3]-base[0]*q[1]+base[1]*q[0]
        val relativeW=base[3]*q[3]+base[0]*q[0]+base[1]*q[1]+base[2]*q[2]
        val relativeX=base[3]*q[0]-base[0]*q[3]-base[1]*q[2]+base[2]*q[1]
        val relativeY=base[3]*q[1]-base[1]*q[3]-base[2]*q[0]+base[0]*q[2]
        val screenX=when (screenRotation) { 1 -> -relativeY; 2 -> -relativeX; 3 -> relativeY; else -> relativeX }
        val screenY=when (screenRotation) { 1 -> relativeX; 2 -> -relativeY; 3 -> -relativeX; else -> relativeY }
        val pitch=-atan2(2*(relativeW*screenX+screenY*relativeZ),1-2*(screenX*screenX+screenY*screenY))
        if (hypot(relativeZ,relativeW) < 1e-6) return Vec2.Zero
        return filter(-2*atan2(relativeZ,relativeW),timestamp).copy(y=forwardBoost(pitch,timestamp))
    }

    /** A forward flick is a one-shot boost, never a held throttle or a brake. */
    private fun forwardBoost(pitch: Double, timestamp: Long): Double {
        val dt=(timestamp-previousPitchTime)/1e9
        val speed=if (dt > 0 && dt <= .25) atan2(sin(pitch-previousPitch),cos(pitch-previousPitch))/dt else 0.0
        previousPitch=pitch; previousPitchTime=timestamp
        if (abs(pitch) < .035 || speed < -.5) boostArmed=true
        if (!boostArmed || pitch < .08 || speed < 1.2 || (lastBoost >= 0 && timestamp-lastBoost < 500_000_000L)) return 0.0
        boostArmed=false; lastBoost=timestamp
        return (.35+(speed-1.2)/2).coerceIn(.35,1.0)
    }

    private fun filter(delta: Double, timestamp: Long): Vec2 {
        val wrapped=atan2(sin(delta),cos(delta))
        val target=Vec2(sign(wrapped)*((abs(wrapped)-.04)/.30).coerceIn(0.0,1.0),0.0)
        val dt=((timestamp-previous)/1e9).coerceIn(0.0,.2)
        previous=timestamp
        filtered+=(target-filtered)*(1-exp(-dt/.08))
        return if (filtered.magnitude() < .025) Vec2.Zero else filtered
    }

    fun sample(x: Double, y: Double, z: Double, timestamp: Long, screenRotation: Int = 0): Vec2 {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || sqrt(x*x + y*y + z*z) < 1.0) return Vec2.Zero
        if (rotation != screenRotation) { reset(); rotation = screenRotation }
        val screen = when (screenRotation) {
            1 -> Vec2(-y, x)
            2 -> Vec2(-x, -y)
            3 -> Vec2(y, -x)
            else -> Vec2(x, y)
        }
        val roll = -atan2(screen.x, hypot(screen.y, z))
        val pitch=atan2(screen.y,z)
        val base = neutral ?: run { neutral = roll; neutralPitch=pitch; previous = timestamp; previousPitchTime=timestamp; return Vec2.Zero }
        val deltaPitch=atan2(sin(neutralPitch!!-pitch),cos(neutralPitch!!-pitch))
        return filter(roll-base,timestamp).copy(y=forwardBoost(deltaPitch,timestamp))
    }
}
