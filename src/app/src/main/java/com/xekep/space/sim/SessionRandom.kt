package com.xekep.space.sim

import kotlin.random.Random

/** A persisted stream for future arcade waves; not a security primitive. */
class SessionRandom(initial: Long = System.nanoTime()) : Random() {
    var state: Long = initial.takeUnless { it == 0L } ?: -7046029254386353131L
        private set
    fun restore(value: Long) { require(value != 0L); state = value }
    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 0..32)
        if (bitCount == 0) return 0
        var x = state
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        state = x
        return (x ushr (64 - bitCount)).toInt()
    }
}
