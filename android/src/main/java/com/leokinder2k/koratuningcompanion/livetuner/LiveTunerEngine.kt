package com.leokinder2k.koratuningcompanion.livetuner

import com.leokinder2k.koratuningcompanion.livetuner.audio.AudioFrameSource
import com.leokinder2k.koratuningcompanion.livetuner.detection.PitchDetectionResult
import com.leokinder2k.koratuningcompanion.livetuner.detection.PitchDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

class LiveTunerEngine(
    private val frameSource: AudioFrameSource,
    private val pitchDetector: PitchDetector,
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 4096,
    /**
     * Number of consecutive frames that must all report a stable pitch before
     * the engine begins emitting a smoothed reading (~100–300 ms at 4096/44100).
     */
    private val minStableFrames: Int = 3,
    /**
     * Maximum cents deviation across the stability window for a reading to be
     * considered stable. 20 cents is within ±1 semitone / 10.
     */
    private val stabilityToleranceCents: Double = 20.0,
    /**
     * Exponential moving average coefficient α (0 < α ≤ 1).
     * α = 0.35 gives a time constant of ~200 ms at 93 ms / frame (4096 @ 44100 Hz).
     * α = 0.55 gives a time constant of ~200 ms at 186 ms / frame (8192 @ 44100 Hz).
     */
    private val smoothingAlpha: Double = 0.35,
    /** Rolling history depth used for the stability check. */
    private val historyWindowFrames: Int = 5,
) {

    fun readings(): Flow<PitchDetectionResult> {
        return frameSource.frames(sampleRate, frameSize)
            .map { frame -> pitchDetector.detect(frame, sampleRate) }
            .scan(StabilityState()) { state, reading ->
                state.advance(
                    reading,
                    minStableFrames,
                    stabilityToleranceCents,
                    smoothingAlpha,
                    historyWindowFrames,
                )
            }
            .drop(1)                      // skip the initial empty seed state
            .map { state -> state.output }
            .conflate()
    }

    /**
     * Immutable accumulator for the [scan] operator.
     *
     * [history]      — sliding window of recent raw frequency readings (null = no pitch)
     * [smoothedFreq] — current EMA value; held when the signal goes silent
     * [output]       — the [PitchDetectionResult] to emit downstream
     */
    private data class StabilityState(
        val history: List<Double?> = emptyList(),
        val smoothedFreq: Double? = null,
        val output: PitchDetectionResult = PitchDetectionResult(null, 0.0, 0.0),
    ) {

        fun advance(
            reading: PitchDetectionResult,
            minStableFrames: Int,
            toleranceCents: Double,
            alpha: Double,
            windowFrames: Int,
        ): StabilityState {
            val newHistory = (history + reading.frequencyHz).takeLast(windowFrames)

            // Require minStableFrames consecutive non-null readings
            val recentValid = newHistory.takeLast(minStableFrames).filterNotNull()
            if (recentValid.size < minStableFrames) {
                return copy(
                    history = newHistory,
                    output = reading.copy(frequencyHz = smoothedFreq),
                )
            }

            // All readings within the stable window must lie within toleranceCents of
            // their mean — this rejects transient noise and double-struck notes
            val mean = recentValid.average()
            val isStable = recentValid.all { f ->
                abs(1200.0 * ln(f / mean) / LN2) < toleranceCents
            }

            if (!isStable) {
                return copy(
                    history = newHistory,
                    output = reading.copy(frequencyHz = smoothedFreq),
                )
            }

            // Exponential moving average — smooth toward the stable mean
            val newSmoothed = smoothedFreq
                ?.let { prev -> prev + alpha * (mean - prev) }
                ?: mean

            // Snap to the nearest 12-TET note for a clean, jitter-free display
            val snapped = snapToNote(newSmoothed)

            return copy(
                history = newHistory,
                smoothedFreq = newSmoothed,
                output = reading.copy(frequencyHz = snapped),
            )
        }

        /** Returns the frequency of the 12-TET note closest to [frequencyHz]. */
        private fun snapToNote(frequencyHz: Double): Double {
            val midiFloat = 69.0 + 12.0 * ln(frequencyHz / A4_HZ) / LN2
            val midiNearest = midiFloat.roundToInt().coerceIn(0, 127)
            return A4_HZ * 2.0.pow((midiNearest - 69.0) / 12.0)
        }

        companion object {
            private val LN2 = ln(2.0)
            private const val A4_HZ = 440.0
        }
    }
}
