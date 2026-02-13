package com.example.koratuningsystem.instrumentconfig.model

private val SUPPORTED_STRING_COUNTS = setOf(21, 22)

data class StringTuning(
    val stringNumber: Int,
    val openPitch: Pitch,
    val closedPitch: Pitch,
    val openIntonationCents: Double,
    val closedIntonationCents: Double
)

data class InstrumentProfile(
    val stringCount: Int,
    val openPitches: List<Pitch>,
    val openIntonationCents: List<Double> = List(stringCount) { 0.0 },
    val closedIntonationCents: List<Double> = List(stringCount) { 0.0 }
) {
    init {
        require(stringCount in SUPPORTED_STRING_COUNTS) {
            "Supported string counts are 21 and 22."
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
