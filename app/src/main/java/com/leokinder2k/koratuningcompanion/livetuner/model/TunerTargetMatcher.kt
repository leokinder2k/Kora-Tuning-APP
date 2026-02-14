package com.leokinder2k.koratuningcompanion.livetuner.model

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

data class TunerTarget(
    val stringNumber: Int,
    val roleLabel: String,
    val targetPitch: Pitch,
    val targetFrequencyHz: Double,
    val requiredLeverState: LeverState,
    val pegRetuneSemitones: Int,
    val targetIntonationCents: Double = 0.0
)

data class TunerMatchResult(
    val target: TunerTarget,
    val centsDeviation: Double
)

object TunerTargetMatcher {
    fun buildTargets(rows: List<PegCorrectStringResult>): List<TunerTarget> {
        return rows.map { row ->
            TunerTarget(
                stringNumber = row.stringNumber,
                roleLabel = row.role.asLabel(),
                targetPitch = row.selectedPitch,
                targetFrequencyHz = pitchToFrequencyHz(
                    pitch = row.selectedPitch,
                    centsOffset = row.selectedIntonationCents
                ),
                requiredLeverState = row.selectedLeverState,
                pegRetuneSemitones = row.pegRetuneSemitones,
                targetIntonationCents = row.selectedIntonationCents
            )
        }
    }

    fun matchNearestTarget(
        detectedFrequencyHz: Double,
        targets: List<TunerTarget>
    ): TunerMatchResult? {
        if (detectedFrequencyHz <= 0.0 || targets.isEmpty()) {
            return null
        }

        val target = targets.minByOrNull { candidate ->
            abs(centsDeviation(detectedFrequencyHz, candidate.targetFrequencyHz))
        } ?: return null

        return TunerMatchResult(
            target = target,
            centsDeviation = centsDeviation(detectedFrequencyHz, target.targetFrequencyHz)
        )
    }

    fun pitchToFrequencyHz(
        pitch: Pitch,
        referenceAHz: Double = 440.0,
        centsOffset: Double = 0.0
    ): Double {
        val midiNumber = pitchToMidiNumber(pitch)
        val equalTemperamentFrequency = referenceAHz * 2.0.pow((midiNumber - 69) / 12.0)
        return equalTemperamentFrequency * 2.0.pow(centsOffset / 1200.0)
    }

    private fun pitchToMidiNumber(pitch: Pitch): Int {
        return ((pitch.octave + 1) * 12) + pitch.note.semitone
    }

    private fun centsDeviation(
        detectedFrequencyHz: Double,
        targetFrequencyHz: Double
    ): Double {
        return 1200.0 * (ln(detectedFrequencyHz / targetFrequencyHz) / ln(2.0))
    }
}

