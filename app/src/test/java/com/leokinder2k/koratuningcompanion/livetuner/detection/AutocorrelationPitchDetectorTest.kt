package com.leokinder2k.koratuningcompanion.livetuner.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

class AutocorrelationPitchDetectorTest {

    private val detector = AutocorrelationPitchDetector()

    @Test
    fun detectsSinePitchWithinTenthCent() {
        val sampleRate = 44100
        val size = 16384
        val targets = listOf(82.4069, 110.0, 220.0, 440.0, 659.2551)

        targets.forEach { targetFrequency ->
            val frame = sineFrame(
                frequencyHz = targetFrequency,
                sampleRate = sampleRate,
                size = size,
                phaseOffsetRadians = 0.37
            )

            val result = detector.detect(frame, sampleRate)
            assertNotNull("Expected a detection for $targetFrequency Hz", result.frequencyHz)
            val centsError = centsDifference(result.frequencyHz!!, targetFrequency)
            assertTrue(
                "Expected <= 0.1 cent error at $targetFrequency Hz but got $centsError",
                centsError <= 0.1
            )
        }
    }

    @Test
    fun returnsNullPitchForSilence() {
        val sampleRate = 44100
        val frame = ShortArray(16384) { 0 }

        val result = detector.detect(frame, sampleRate)
        assertNull(result.frequencyHz)
        assertEquals(0.0, result.confidence, 0.0)
    }

    private fun sineFrame(
        frequencyHz: Double,
        sampleRate: Int,
        size: Int,
        phaseOffsetRadians: Double
    ): ShortArray {
        val data = ShortArray(size)
        for (index in 0 until size) {
            val value = sin((2.0 * PI * frequencyHz * index / sampleRate) + phaseOffsetRadians)
            data[index] = (value * 22000.0).toInt().toShort()
        }
        return data
    }

    private fun centsDifference(detectedFrequencyHz: Double, targetFrequencyHz: Double): Double {
        return abs(1200.0 * (ln(detectedFrequencyHz / targetFrequencyHz) / ln(2.0)))
    }
}

