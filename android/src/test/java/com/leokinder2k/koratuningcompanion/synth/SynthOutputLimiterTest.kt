package com.leokinder2k.koratuningcompanion.synth

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthOutputLimiterTest {
    @Test
    fun denseMaxGainBlock_isPulledBelowCleanPeakCeiling() {
        val limiter = SynthOutputLimiter(
            peakCeiling = 0.72f,
            hardCeiling = 0.88f,
            release = 0.06f
        )
        val samples = FloatArray(128) { index ->
            if (index % 2 == 0) 4f else -4f
        }

        limiter.applyInterleaved(samples, samples.size, requestedGain = 1f)

        val peak = samples.maxOf { abs(it) }
        assertTrue("peak was $peak", peak <= 0.7201f)
    }

    @Test
    fun normalBlock_usesRequestedGainWithoutHardClipping() {
        val limiter = SynthOutputLimiter(
            peakCeiling = 0.72f,
            hardCeiling = 0.88f,
            release = 0.06f
        )
        val samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)

        limiter.applyInterleaved(samples, samples.size, requestedGain = 0.5f)

        assertEquals(0.25f, samples[0], 0.0001f)
        assertEquals(-0.25f, samples[1], 0.0001f)
        assertEquals(0.125f, samples[2], 0.0001f)
        assertEquals(-0.125f, samples[3], 0.0001f)
    }
}
