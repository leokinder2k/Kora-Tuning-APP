package com.leokinder2k.koratuningcompanion.livetuner.detection

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

class AutocorrelationPitchDetector(
    private val minFrequencyHz: Double = 60.0,
    private val maxFrequencyHz: Double = 1200.0,
    private val rmsGate: Double = 0.008,
    private val correlationThreshold: Double = 0.55,
    private val refinementSearchHz: Double = 4.0,
    private val refinementIterations: Int = DEFAULT_REFINEMENT_ITERATIONS
) : PitchDetector {

    override fun detect(frame: ShortArray, sampleRate: Int): PitchDetectionResult {
        if (frame.isEmpty()) {
            return PitchDetectionResult(
                frequencyHz = null,
                confidence = 0.0,
                rms = 0.0
            )
        }

        val normalized = DoubleArray(frame.size) { index ->
            frame[index] / PCM_FULL_SCALE
        }
        val dcOffset = normalized.average()
        for (index in normalized.indices) {
            normalized[index] -= dcOffset
        }

        val rms = sqrt(normalized.sumOf { value -> value * value } / normalized.size.toDouble())
        if (rms < rmsGate) {
            return PitchDetectionResult(
                frequencyHz = null,
                confidence = 0.0,
                rms = rms
            )
        }

        val minLag = maxOf(1, floor(sampleRate / maxFrequencyHz).toInt())
        val maxLag = minOf(
            normalized.lastIndex,
            ceil(sampleRate / minFrequencyHz).toInt()
        )
        if (maxLag <= minLag) {
            return PitchDetectionResult(
                frequencyHz = null,
                confidence = 0.0,
                rms = rms
            )
        }

        var bestLag = -1
        var bestCorrelation = Double.NEGATIVE_INFINITY
        val correlationByLag = DoubleArray(maxLag + 1) { Double.NEGATIVE_INFINITY }
        for (lag in minLag..maxLag) {
            val correlation = normalizedCorrelation(normalized, lag)
            correlationByLag[lag] = correlation
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }

        if (bestLag < 0 || bestCorrelation < correlationThreshold) {
            return PitchDetectionResult(
                frequencyHz = null,
                confidence = bestCorrelation.coerceIn(0.0, 1.0),
                rms = rms
            )
        }

        val nearPeakThreshold = maxOf(bestCorrelation * LOCAL_PEAK_RELATIVE_THRESHOLD, correlationThreshold)
        val selectedLag = (minLag + 1 until maxLag).firstOrNull { lag ->
            val center = correlationByLag[lag]
            center >= nearPeakThreshold &&
                center >= correlationByLag[lag - 1] &&
                center >= correlationByLag[lag + 1]
        } ?: bestLag

        val refinedLag = refineLag(
            lag = selectedLag,
            correlationByLag = correlationByLag,
            minLag = minLag,
            maxLag = maxLag
        )
        if (refinedLag <= 0.0 || !refinedLag.isFinite()) {
            return PitchDetectionResult(
                frequencyHz = null,
                confidence = bestCorrelation.coerceIn(0.0, 1.0),
                rms = rms
            )
        }

        val roughFrequencyHz = sampleRate / refinedLag
        val windowedSignal = hannWindow(normalized)
        val refinedFrequencyHz = refineFrequency(
            signal = windowedSignal,
            sampleRate = sampleRate,
            initialFrequencyHz = roughFrequencyHz
        )
        val frequencyHz = refinedFrequencyHz.coerceIn(minFrequencyHz, maxFrequencyHz)

        return PitchDetectionResult(
            frequencyHz = frequencyHz,
            confidence = bestCorrelation.coerceIn(0.0, 1.0),
            rms = rms
        )
    }

    private fun refineLag(
        lag: Int,
        correlationByLag: DoubleArray,
        minLag: Int,
        maxLag: Int
    ): Double {
        if (lag <= minLag || lag >= maxLag) {
            return lag.toDouble()
        }

        val left = correlationByLag[lag - 1]
        val center = correlationByLag[lag]
        val right = correlationByLag[lag + 1]
        val denominator = left - (2.0 * center) + right
        if (abs(denominator) < 1e-9) {
            return lag.toDouble()
        }

        val delta = 0.5 * (left - right) / denominator
        return lag + delta.coerceIn(-1.0, 1.0)
    }

    private fun hannWindow(signal: DoubleArray): DoubleArray {
        val size = signal.size
        if (size <= 1) {
            return signal.copyOf()
        }

        val windowed = DoubleArray(size)
        val denominator = (size - 1).toDouble()
        for (index in signal.indices) {
            val weight = 0.5 * (1.0 - cos((2.0 * Math.PI * index) / denominator))
            windowed[index] = signal[index] * weight
        }
        return windowed
    }

    private fun refineFrequency(
        signal: DoubleArray,
        sampleRate: Int,
        initialFrequencyHz: Double
    ): Double {
        val lowerBound = maxOf(minFrequencyHz, initialFrequencyHz - refinementSearchHz)
        val upperBound = minOf(maxFrequencyHz, initialFrequencyHz + refinementSearchHz)
        if (lowerBound >= upperBound) {
            return initialFrequencyHz
        }

        var left = lowerBound
        var right = upperBound
        repeat(refinementIterations.coerceAtLeast(1)) {
            val oneThird = (right - left) / 3.0
            val mid1 = left + oneThird
            val mid2 = right - oneThird
            val power1 = tonePower(signal, sampleRate, mid1)
            val power2 = tonePower(signal, sampleRate, mid2)

            if (power1 < power2) {
                left = mid1
            } else {
                right = mid2
            }
        }
        return (left + right) / 2.0
    }

    private fun tonePower(
        signal: DoubleArray,
        sampleRate: Int,
        frequencyHz: Double
    ): Double {
        if (frequencyHz <= 0.0) {
            return 0.0
        }

        val angularStep = (2.0 * Math.PI * frequencyHz) / sampleRate.toDouble()
        var cosAccumulator = 0.0
        var sinAccumulator = 0.0
        for (index in signal.indices) {
            val phase = angularStep * index
            val sample = signal[index]
            cosAccumulator += sample * cos(phase)
            sinAccumulator += sample * sin(phase)
        }
        return (cosAccumulator * cosAccumulator) + (sinAccumulator * sinAccumulator)
    }

    private fun normalizedCorrelation(signal: DoubleArray, lag: Int): Double {
        val limit = signal.size - lag
        if (limit <= 1) {
            return 0.0
        }

        var numerator = 0.0
        var energyA = 0.0
        var energyB = 0.0
        for (index in 0 until limit) {
            val a = signal[index]
            val b = signal[index + lag]
            numerator += a * b
            energyA += a * a
            energyB += b * b
        }

        val denominator = sqrt(energyA * energyB)
        return if (denominator <= 1e-9) 0.0 else numerator / denominator
    }

    companion object {
        private const val PCM_FULL_SCALE = 32768.0
        private const val DEFAULT_REFINEMENT_ITERATIONS = 22
        private const val LOCAL_PEAK_RELATIVE_THRESHOLD = 0.985
    }
}

