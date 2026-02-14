package com.leokinder2k.koratuningcompanion.scaleengine.model

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch

enum class ScaleType(
    val displayName: String,
    private val intervals: IntArray
) {
    MAJOR("Major (Heptatonic)", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("Natural Minor (Heptatonic)", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR("Harmonic Minor (Heptatonic)", intArrayOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR("Melodic Minor (Heptatonic)", intArrayOf(0, 2, 3, 5, 7, 9, 11)),

    IONIAN("Ionian (Mode 1)", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    DORIAN("Dorian (Mode 2)", intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("Phrygian (Mode 3)", intArrayOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("Lydian (Mode 4)", intArrayOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("Mixolydian (Mode 5)", intArrayOf(0, 2, 4, 5, 7, 9, 10)),
    AEOLIAN("Aeolian (Mode 6)", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    LOCRIAN("Locrian (Mode 7)", intArrayOf(0, 1, 3, 5, 6, 8, 10)),

    MAJOR_PENTATONIC("Major Pentatonic", intArrayOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC("Minor Pentatonic", intArrayOf(0, 3, 5, 7, 10)),

    MAJOR_HEXATONIC("Major Hexatonic", intArrayOf(0, 2, 4, 5, 7, 9)),
    MINOR_HEXATONIC("Minor Hexatonic", intArrayOf(0, 2, 3, 5, 7, 10)),
    WHOLE_TONE("Whole Tone (Hexatonic)", intArrayOf(0, 2, 4, 6, 8, 10)),
    MAJOR_BLUES("Major Blues (Hexatonic)", intArrayOf(0, 2, 3, 4, 7, 9)),
    MINOR_BLUES("Minor Blues (Hexatonic)", intArrayOf(0, 3, 5, 6, 7, 10)),

    BEEBOP_MAJOR("Beebop Major", intArrayOf(0, 2, 4, 5, 7, 8, 9, 11)),
    BEEBOP_DOMINANT("Beebop Dominant", intArrayOf(0, 2, 4, 5, 7, 9, 10, 11)),
    BEEBOP_DORIAN("Beebop Dorian", intArrayOf(0, 2, 3, 4, 5, 7, 9, 10)),

    DIMINISHED_WHOLE_HALF("Diminished Whole-Half (Octatonic)", intArrayOf(0, 2, 3, 5, 6, 8, 9, 11)),
    DIMINISHED_HALF_WHOLE("Diminished Half-Whole (Octatonic)", intArrayOf(0, 1, 3, 4, 6, 7, 9, 10)),

    CHROMATIC("Chromatic", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    ;

    fun notesForRoot(root: NoteName): Set<NoteName> {
        return intervals
            .map { step -> NoteName.fromSemitone(root.semitone + step) }
            .toCollection(LinkedHashSet())
    }
}

enum class StringSide(val shortLabel: String) {
    LEFT("L"),
    RIGHT("R")
}

enum class LeverState {
    OPEN,
    CLOSED
}

enum class EngineMode {
    LEVER_ONLY,
    PEG_CORRECT
}

data class ScaleStringRole(
    val side: StringSide,
    val positionFromLow: Int
) {
    fun asLabel(): String = "${side.shortLabel}$positionFromLow"
}

data class ScaleCalculationRequest(
    val instrumentProfile: InstrumentProfile,
    val rootNote: NoteName,
    val scaleType: ScaleType
)

data class LeverOnlyStringResult(
    val stringNumber: Int,
    val role: ScaleStringRole,
    val openPitch: Pitch,
    val closedPitch: Pitch,
    val selectedLeverState: LeverState?,
    val selectedPitch: Pitch?,
    val pegRetuneRequired: Boolean,
    val selectedIntonationCents: Double = 0.0
)

data class PegCorrectStringResult(
    val stringNumber: Int,
    val role: ScaleStringRole,
    val originalOpenPitch: Pitch,
    val originalClosedPitch: Pitch,
    val retunedOpenPitch: Pitch,
    val retunedClosedPitch: Pitch,
    val selectedLeverState: LeverState,
    val selectedPitch: Pitch,
    val pegRetuneSemitones: Int,
    val pegRetuneRequired: Boolean,
    val selectedIntonationCents: Double = 0.0
)

data class VoicingConflict(
    val mode: EngineMode,
    val side: StringSide,
    val lowerStringNumber: Int,
    val higherStringNumber: Int,
    val detail: String
)

data class VoicingSuggestion(
    val mode: EngineMode,
    val suggestion: String
)

data class ScaleCalculationResult(
    val request: ScaleCalculationRequest,
    val scaleNotes: Set<NoteName>,
    val leverOnlyTable: List<LeverOnlyStringResult>,
    val pegCorrectTable: List<PegCorrectStringResult>,
    val conflicts: List<VoicingConflict>,
    val suggestions: List<VoicingSuggestion>
)

