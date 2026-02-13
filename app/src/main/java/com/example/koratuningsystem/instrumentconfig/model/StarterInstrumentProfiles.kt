package com.example.koratuningsystem.instrumentconfig.model

object StarterInstrumentProfiles {
    private val starter21 = listOf(
        "E2", "F#2", "G#2", "A2", "B2", "C#3", "D#3",
        "E3", "F#3", "G#3", "A3", "B3", "C#4", "D#4",
        "E4", "F#4", "G#4", "A4", "B4", "C#5", "D#5"
    )

    private val starter22 = starter21 + "E5"

    fun openPitchTexts(stringCount: Int): List<String> = when (stringCount) {
        21 -> starter21
        22 -> starter22
        else -> throw IllegalArgumentException("Supported string counts are 21 and 22.")
    }

    fun openPitches(stringCount: Int): List<Pitch> {
        return openPitchTexts(stringCount).map { input ->
            requireNotNull(Pitch.parse(input)) {
                "Invalid starter pitch: $input"
            }
        }
    }
}
