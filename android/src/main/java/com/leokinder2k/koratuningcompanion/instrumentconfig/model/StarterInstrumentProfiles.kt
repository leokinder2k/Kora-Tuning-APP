package com.leokinder2k.koratuningcompanion.instrumentconfig.model

object StarterInstrumentProfiles {
    private val starter21 = listOf(
        "F2", "F3", "C3", "D3", "E3", "A3",
        "G3", "C4", "Bb3", "E4", "D4",
        "G4", "F4", "Bb4", "A4", "D5", "C5",
        "F5", "E5", "G5", "A5"
    )

    private val starter22 = listOf(
        "F2", "Bb2", "C3", "D3", "E3", "F3",
        "G3", "A3", "Bb3", "C4", "D4",
        "E4", "F4", "G4", "A4", "Bb4", "C5",
        "D5", "E5", "F5", "G5", "A5"
    )

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

