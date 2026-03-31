package com.leokinder2k.koratuningcompanion.instrumentconfig.model

private val SUPPORTED_STRING_COUNTS = setOf(19, 21, 22)

enum class KoraTuningMode {
    LEVERED,
    PEG_TUNING
}

/**
 * The lever state the player starts from at the beginning of a piece.
 * OPEN  = all levers up   -> instrument sounds in its flat/natural key.
 * CLOSED = all levers down -> instrument sounds in its sharp key (+1 semitone per string).
 * Only meaningful for [KoraTuningMode.LEVERED] instruments.
 */
enum class HomeLeverPosition { OPEN, CLOSED }

data class StringTuning(
    val stringNumber: Int,
    val openPitch: Pitch,
    val closedPitch: Pitch,
    val openIntonationCents: Double,
    val closedIntonationCents: Double
)

data class InstrumentProfile(
    val stringCount: Int,
    val tuningMode: KoraTuningMode = KoraTuningMode.LEVERED,
    val openPitches: List<Pitch>,
    val openIntonationCents: List<Double> = List(stringCount) { 0.0 },
    val closedIntonationCents: List<Double> = List(stringCount) { 0.0 },
    val rootNote: NoteName = NoteName.F,
    /**
     * Physical home tuning — the pitch of each string when all levers are at rest (down).
     * Retune plans show peg adjustments relative to these pitches.
     * Defaults to [openPitches] when not set (backwards compatible).
     */
    val basePitches: List<Pitch> = openPitches,
    /**
     * The lever position the player starts from at the beginning of a piece.
     * Only meaningful for [KoraTuningMode.LEVERED] instruments.
     */
    val homeLeverPosition: HomeLeverPosition = HomeLeverPosition.OPEN
) {
    init {
        require(stringCount in SUPPORTED_STRING_COUNTS) {
            "Supported string counts are 19, 21, and 22."
        }
        require(openPitches.size == stringCount) {
            "Open tuning count must match selected string count."
        }
        require(openIntonationCents.size == stringCount) {
            "Open intonation count must match selected string count."
        }
        require(closedIntonationCents.size == stringCount) {
            "Closed intonation count must match selected string count."
        }
        require(basePitches.size == stringCount) {
            "Base tuning count must match selected string count."
        }
        require(openIntonationCents.all { cents -> cents.isFinite() }) {
            "Open intonation cents must be finite values."
        }
        require(closedIntonationCents.all { cents -> cents.isFinite() }) {
            "Closed intonation cents must be finite values."
        }
    }

    val strings: List<StringTuning> = openPitches.mapIndexed { index, open ->
        StringTuning(
            stringNumber = index + 1,
            openPitch = open,
            closedPitch = open.plusSemitones(1),
            openIntonationCents = openIntonationCents[index],
            closedIntonationCents = closedIntonationCents[index]
        )
    }
}

