package com.leokinder2k.koratuningcompanion.livetuner.detection

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002).
 *
 * Improvements over plain autocorrelation:
 *  - Squared-difference function removes the lag-0 bias present in cross-correlation
 *  - Cumulative mean normalisation (CMNDF) makes the threshold frequency-independent
 *  - Absolute threshold with local-minimum walk avoids sub-harmonic octave errors
 *  - Parabolic interpolation for sub-sample period accuracy
 *
 * Frequency-adaptive sensitivity:
 *  Low strings (≤ lowStringMaxHz) require a higher minimum confidence so that
 *  low-frequency room noise and handling noise do not trigger false detections.
 *  High strings accept a lower threshold to catch soft plucks.
 */
class YinPitchDetector(
    private val minFrequencyHz: Double = 60.0,
    private val maxFrequencyHz: Double = 1200.0,
    /**
     * RMS noise gate expressed as a linear amplitude ratio (full-scale = 1.0).
     *   -50 dBFS ≈ 0.0032   -55 dBFS ≈ 0.0018   -60 dBFS ≈ 0.0010
     * Default 0.003 sits just above typical ambient room noise when the
     * microphone input gain is set to 60–75 % of its maximum.
     */
    private val rmsGate: Double = 0.003,
    /**
     * YIN absolute threshold applied to the CMNDF.
     * The first τ at which CMNDF falls below this value is accepted as the
     * pitch period. Typical range 0.08–0.15.
     * Lower → stricter, fewer false detections.
     * Higher → more sensitive, catches weaker signals.
     */
    private val yinThreshold: Double = 0.12,
    /** Strings at or below this Hz are treated as "low strings". */
    private val lowStringMaxHz: Double = 200.0,
    /** Minimum confidence (= 1 − CMNDF_min) accepted for low strings. */
    private val lowStringMinConfidence: Double = 0.88,
    /** Minimum confidence accepted for high strings. */
    private val highStringMinConfidence: Double = 0.82,
) : PitchDetector {

    override fun detect(frame: ShortArray, sampleRate: Int): PitchDetectionResult {
        if (frame.size < MIN_FRAME_SIZE) {
            return PitchDetectionResult(null, 0.0, 0.0)
        }

        val n = frame.size

        // Normalize PCM-16 to [-1, 1] and remove DC offset
        val signal = DoubleArray(n) { frame[it] / PCM_FULL_SCALE }
        val dc = signal.average()
        for (i in signal.indices) signal[i] -= dc

        // RMS noise gate — ignore signals below ambient room level
        val rms = sqrt(signal.sumOf { it * it } / n)
        if (rms < rmsGate) {
            return PitchDetectionResult(null, 0.0, rms)
        }

        // Integration window = half the frame (standard YIN choice)
        val halfN = n / 2
        val minLag = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequencyHz).toInt().coerceAtMost(halfN - 1)
        if (maxLag <= minLag) {
            return PitchDetectionResult(null, 0.0, rms)
        }

        // Step 1: Squared difference function d(τ) for all τ in [1, maxLag]
        // d(τ) = Σ_{j=0}^{W-1} (x[j] − x[j+τ])²,  W = halfN
        val diff = DoubleArray(maxLag + 1)
        for (tau in 1..maxLag) {
            var sum = 0.0
            for (j in 0 until halfN) {
                val delta = signal[j] - signal[j + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        // Step 2: Cumulative mean normalised difference function (CMNDF)
        // d'[0] = 1 by convention
        // d'[τ] = d[τ] · τ / Σ_{j=1}^{τ} d[j]
        val cmndf = DoubleArray(maxLag + 1)
        cmndf[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxLag) {
            runningSum += diff[tau]
            cmndf[tau] = if (runningSum < 1e-10) 0.0 else diff[tau] * tau / runningSum
        }

        // Step 3: Absolute threshold
        // Find the first τ in [minLag, maxLag] where CMNDF < yinThreshold,
        // then walk forward to the local minimum.
        var bestTau = -1
        var tau = minLag
        while (tau <= maxLag) {
            if (cmndf[tau] < yinThreshold) {
                while (tau + 1 <= maxLag && cmndf[tau + 1] < cmndf[tau]) tau++
                bestTau = tau
                break
            }
            tau++
        }

        // Fallback: use the global minimum if the threshold was never crossed
        if (bestTau < 0) {
            bestTau = (minLag..maxLag).minByOrNull { cmndf[it] }
                ?: return PitchDetectionResult(null, 0.0, rms)
            // If even the global minimum is too high, the signal is aperiodic
            if (cmndf[bestTau] > APERIODIC_CUTOFF) {
                return PitchDetectionResult(
                    frequencyHz = null,
                    confidence = (1.0 - cmndf[bestTau]).coerceIn(0.0, 1.0),
                    rms = rms
                )
            }
        }

        // Step 4: Parabolic interpolation for sub-sample accuracy
        val refinedTau: Double = if (bestTau in (minLag + 1) until maxLag) {
            val lo = cmndf[bestTau - 1]
            val mid = cmndf[bestTau]
            val hi = cmndf[bestTau + 1]
            val denom = lo - 2.0 * mid + hi
            if (abs(denom) < 1e-10) {
                bestTau.toDouble()
            } else {
                (bestTau + 0.5 * (lo - hi) / denom)
                    .coerceIn(minLag.toDouble(), maxLag.toDouble())
            }
        } else {
            bestTau.toDouble()
        }

        if (refinedTau <= 0.0 || !refinedTau.isFinite()) {
            return PitchDetectionResult(null, 0.0, rms)
        }

        val frequencyHz = (sampleRate / refinedTau).coerceIn(minFrequencyHz, maxFrequencyHz)

        // Confidence = 1 − CMNDF(bestTau): 1.0 = perfectly periodic, 0.0 = noise
        val confidence = (1.0 - cmndf[bestTau]).coerceIn(0.0, 1.0)

        // Step 5: Frequency-adaptive confidence gate
        // Low strings live in a noisier frequency region; require a stronger signal
        // High strings are quieter when plucked softly; accept weaker signals
        val minConfidence = if (frequencyHz <= lowStringMaxHz) {
            lowStringMinConfidence
        } else {
            highStringMinConfidence
        }
        if (confidence < minConfidence) {
            return PitchDetectionResult(null, confidence, rms)
        }

        return PitchDetectionResult(frequencyHz, confidence, rms)
    }

    companion object {
        private const val PCM_FULL_SCALE = 32768.0
        private const val MIN_FRAME_SIZE = 512
        /** CMNDF fallback ceiling: above this the signal is considered aperiodic. */
        private const val APERIODIC_CUTOFF = 0.45
    }
}
