package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test

class SessionRandomTest {
    @Test fun restoredStreamProducesTheSameFutureSequence() {
        val original=SessionRandom(731L); repeat(200) { original.nextDouble() }
        val restored=SessionRandom(1L); restored.restore(original.state)
        repeat(1000) { assertEquals(original.nextInt(),restored.nextInt()); assertEquals(original.nextDouble(),restored.nextDouble(),0.0) }
    }
    @Test fun zeroSeedAndBitBoundsRemainValid() {
        val random=SessionRandom(0L); assertNotEquals(0L,random.state)
        repeat(1000) { assertTrue(random.nextInt(17) in 0..16) }
        assertThrows(IllegalArgumentException::class.java) { random.restore(0L) }
    }
}
