package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test
    fun partialProgress() {
        val p = Progress(saved = 1_750, target = 7_000)
        assertEquals(0.25f, p.fraction, 0.0001f)
        assertEquals(5_250, p.remaining)
        assertEquals(0, p.overflow)
        assertFalse(p.isCompleted)
    }

    @Test
    fun overTargetIsCappedAndReportsOverflow() {
        val p = Progress(saved = 7_300, target = 7_000)
        assertEquals(1f, p.fraction, 0.0001f)
        assertEquals(0, p.remaining)
        assertEquals(300, p.overflow)
        assertTrue(p.isCompleted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroTargetIsRejected() {
        Progress(saved = 0, target = 0)
    }
}
