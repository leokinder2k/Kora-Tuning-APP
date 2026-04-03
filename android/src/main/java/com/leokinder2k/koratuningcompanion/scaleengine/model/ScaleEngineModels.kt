package com.leokinder2k.koratuningcompanion.scaleengine.model

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch

enum class ScaleType(
    private val intervals: IntArray
) {
    MAJOR(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR(intArrayOf(0, 2, 3, 5, 7, 9, 11)),

    IONIAN(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    DORIAN(intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN(intArrayOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN(intArrayOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10)),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    LOCRIAN(intArrayOf(0, 1, 3, 5, 6, 8, 10)),

    MAJOR_PENTATONIC(intArrayOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC(intArrayOf(0, 3, 5, 7, 10)),

    MAJOR_HEXATONIC(intArrayOf(0, 2, 4, 5, 7, 9)),
    MINOR_HEXATONIC(intArrayOf(0, 2, 3, 5, 7, 10)),
    WHOLE_TONE(intArrayOf(0, 2, 4, 6, 8, 10)),
    MAJOR_BLUES(intArrayOf(0, 2, 3, 4, 7, 9)),
    MINOR_BLUES(intArrayOf(0, 3, 5, 6, 7, 10)),

    BEEBOP_MAJOR(intArrayOf(0, 2, 4, 5, 7, 8, 9, 11)),
    BEEBOP_DOMINANT(intArrayOf(0, 2, 4, 5, 7, 9, 10, 11)),
    BEEBOP_DORIAN(intArrayOf(0, 2, 3, 4, 5, 7, 9, 10)),

    DIMINISHED_WHOLE_HALF(intArrayOf(0, 2, 3, 5, 6, 8, 9, 11)),
    DIMINISHED_HALF_WHOLE(intArrayOf(0, 1, 3, 4, 6, 7, 9, 10)),

    HUNGARIAN_MINOR(intArrayOf(0, 2, 3, 6, 7, 8, 11)),
    HUNGARIAN_MAJOR(intArrayOf(0, 3, 4, 6, 7, 9, 10)),

    RAGA_BHAIRAV(intArrayOf(0, 1, 4, 5, 7, 8, 11)),
    RAGA_YAMAN(intArrayOf(0, 2, 4, 6, 7, 9, 11)),
    RAGA_KAFI(intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    RAGA_BHAIRAVI(intArrayOf(0, 1, 3, 5, 7, 8, 10)),

    NEAPOLITAN_MAJOR(intArrayOf(0, 1, 3, 5, 7, 9, 11)),
    NEAPOLITAN_MINOR(intArrayOf(0, 1, 3, 5, 7, 8, 11)),

    PHRYGIAN_DOMINANT(intArrayOf(0, 1, 4, 5, 7, 8, 10)),
    DOUBLE_HARMONIC(intArrayOf(0, 1, 4, 5, 7, 8, 11)),
    PERSIAN(intArrayOf(0, 1, 4, 5, 6, 8, 11)),

    HIRAJOSHI(intArrayOf(0, 2, 3, 7, 8)),
    JAPANESE_IN(intArrayOf(0, 1, 5, 7, 8)),
    IWATO(intArrayOf(0, 1, 5, 6, 10)),
    INSEN(intArrayOf(0, 1, 5, 7, 10)),

    PROMETHEUS(intArrayOf(0, 2, 4, 6, 9, 10)),
    ENIGMATIC(intArrayOf(0, 1, 4, 6, 8, 10, 11)),
    TRITONE(intArrayOf(0, 1, 4, 6, 7, 10)),

    AUGMENTED(intArrayOf(0, 3, 4, 7, 8, 11)),
    AUGMENTED_INVERSE(intArrayOf(0, 1, 4, 5, 8, 9)),

    CHROMATIC(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    ;

    fun notesForRoot(root: NoteName): Set<NoteName> {
        return intervals
            .map { step -> NoteName.fromSemitone(root.semitone + step) }
            .toCollection(LinkedHashSet())
    }
}

enum class ScaleRootReference {
    LEFT_1,
    LEFT_2,
    LEFT_3,
    LEFT_4,
    RIGHT_1
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
    val scaleType: ScaleType,
    val rootNote: NoteName = instrumentProfile.rootNote,
    val scaleRootReference: ScaleRootReference = ScaleRootReference.LEFT_1
)

data class LeverOnlyStringResult(
    val stringNumber: Int,
    val role: ScaleStringRole,
    val openPitch: Pitch,
    val closedPitch: Pitch,
    val selectedLeverState: LeverState?,
    val selectedPitch: Pitch?,
    val pegRetuneRequired: Boolean,
    val selectedIntonationCents: Double = 0.0,
    /** True when the selected lever state differs from the player's home/starting position. */
    val leverChangeFromHome: Boolean = false
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
    val selectedIntonationCents: Double = 0.0,
    /**
     * Signed semitones from the physical home/base tuning to the required peg position.
     * Positive = tune up from home, negative = tune down, zero = peg stays at home.
     */
    val fromBaseSemitones: Int = 0,
    /** True when the selected lever state differs from the player's home/starting position. */
    val leverChangeFromHome: Boolean = false
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

