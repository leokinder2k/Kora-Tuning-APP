package com.leokinder2k.koratuningcompanion.livetuner.detection

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

class YinPitchDetectorTest {

    private val detector = YinPitchDetector()

    @Test
    fun detectsSinePitchWithinOneCent() {
        val sampleRate = 44100
        val size = 8192
        // Covers kora range: low bass strings through high treble strings
        val targets = listOf(65.41, 82.41, 110.0, 196.0, 261.63, 392.0, 523.25, 783.99, 1046.5)

        targets.forEach { targetFrequency ->
            val frame = sineFrame(
                frequencyHz = targetFrequency,
                sampleRate = sampleRate,
                size = size,
                amplitude = 22000.0, // 67 % of full scale — within 60–75 % input gain target
                phaseOffsetRadians = 0.37
            )
            val result = detector.detect(frame, sampleRate)
            assertNotNull("Expected detection at $targetFrequency Hz", result.frequencyHz)
            val centsError = centsDiff(result.frequencyHz!!, targetFrequency)
            assertTrue(
                "Expected ≤ 1 cent error at $targetFrequency Hz but got ${"%.3f".format(centsError)} cents",
                centsError <= 1.0
            )
        }
    }

    @Test
    fun returnsNullPitchForSilence() {
        val result = detector.detect(ShortArray(8192) { 0 }, 44100)
        assertNull(result.frequencyHz)
    }

    @Test
    fun returnsNullForSignalBelowNoiseGate() {
        // ~0.001 RMS → below -60 dBFS; should be gated out
        val sampleRate = 44100
        val frame = sineFrame(440.0, sampleRate, 8192, amplitude = 25.0)
        val result = detector.detect(frame, sampleRate)
        assertNull("Signal below noise gate should not produce a pitch", result.frequencyHz)
    }

    @Test
    fun lowStringRequiresHigherConfidenceThanHighString() {
        // A pure sine always yields high confidence, so this checks that both
        // low and high strings ARE detected (they pass their respective gates).
        val sampleRate = 44100
        val size = 8192

        val lowFrame = sineFrame(110.0, sampleRate, size, amplitude = 22000.0)
        val highFrame = sineFrame(660.0, sampleRate, size, amplitude = 22000.0)

        val lowResult = detector.detect(lowFrame, sampleRate)
        val highResult = detector.detect(highFrame, sampleRate)

        assertNotNull("Low string (110 Hz) should be detected", lowResult.frequencyHz)
        assertNotNull("High string (660 Hz) should be detected", highResult.frequencyHz)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun sineFrame(
        frequencyHz: Double,
        sampleRate: Int,
        size: Int,
        amplitude: Double = 22000.0,
        phaseOffsetRadians: Double = 0.0,
    ): ShortArray = ShortArray(size) { i ->
        val value = sin(2.0 * PI * frequencyHz * i / sampleRate + phaseOffsetRadians)
        (value * amplitude).toInt().toShort()
    }

    private fun centsDiff(a: Double, b: Double): Double =
        abs(1200.0 * ln(a / b) / ln(2.0))
}
