package com.leokinder2k.koratuningcompanion.instrumentconfig.model

data class TraditionalPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val stringCount: Int,
    val openPitches: List<Pitch>,
    val openIntonationCents: List<Double>,
    val closedIntonationCents: List<Double>
) {
    init {
        require(openPitches.size == stringCount) {
            "Preset pitch count must match selected string count."
        }
        require(openIntonationCents.size == stringCount) {
            "Preset open intonation count must match selected string count."
        }
        require(closedIntonationCents.size == stringCount) {
            "Preset closed intonation count must match selected string count."
        }
    }

    fun toInstrumentProfile(): InstrumentProfile {
        return InstrumentProfile(
            stringCount = stringCount,
            openPitches = openPitches,
            openIntonationCents = openIntonationCents,
            closedIntonationCents = closedIntonationCents
        )
    }
}

object TraditionalPresets {
    private data class PresetDefinition(
        val id: String,
        val displayName: String,
        val description: String,
        val openPitches21: List<String>,
        val openPitches22: List<String>,
        val intonationTemplate: IntonationTemplate
    )

    private data class IntonationTemplate(
        val centsByNoteSymbol: Map<String, Double>
    ) {
        fun centsForPitch(pitch: Pitch): Double {
            return centsByNoteSymbol[pitch.note.symbol] ?: 0.0
        }
    }

    /*
     * Source-derived intonation maps (concert F reference):
     * - Tomora Ba / Silaba: F G A Bb C D E with cents deviations [0, 0, -15, 0, 0, 0, -15]
     * - Sauta: F G A B C D E with cents deviations [0, -15, +5, +5, 0, -15, +5]
     * - Hardino: F G A Bb C D E with cents deviations [0, -15, +5, 0, 0, -15, +5]
     */
    private val hardinoIntonationTemplate = IntonationTemplate(
        centsByNoteSymbol = mapOf(
            "F" to 0.0,
            "G" to -15.0,
            "A" to 5.0,
            "A#" to 0.0,
            "C" to 0.0,
            "D" to -15.0,
            "E" to 5.0
        )
    )

    private val sautaIntonationTemplate = IntonationTemplate(
        centsByNoteSymbol = mapOf(
            "F" to 0.0,
            "G" to -15.0,
            "A" to 5.0,
            "B" to 5.0,
            "C" to 0.0,
            "D" to -15.0,
            "E" to 5.0
        )
    )

    private val silabaIntonationTemplate = IntonationTemplate(
        centsByNoteSymbol = mapOf(
            "F" to 0.0,
            "G" to 0.0,
            "A" to -15.0,
            "A#" to 0.0,
            "C" to 0.0,
            "D" to 0.0,
            "E" to -15.0
        )
    )

    private val tomoraBaIntonationTemplate = silabaIntonationTemplate

    private val definitions = listOf(
        PresetDefinition(
            id = "hardino",
            displayName = "Hardino",
            description = "Traditional Hardino map in concert-F reference (equal-tempered nominal notes).",
            openPitches21 = listOf(
                "F2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            openPitches22 = listOf(
                "F2", "Bb2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            intonationTemplate = hardinoIntonationTemplate
        ),
        PresetDefinition(
            id = "sauta",
            displayName = "Sauta",
            description = "Traditional Sauta map (raised 4th degree) in concert-F reference.",
            openPitches21 = listOf(
                "F2", "C3", "D3", "E3",
                "F3", "G3", "A3", "B3", "C4", "D4", "E4",
                "F4", "G4", "A4", "B4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            openPitches22 = listOf(
                "F2", "B2", "C3", "D3", "E3",
                "F3", "G3", "A3", "B3", "C4", "D4", "E4",
                "F4", "G4", "A4", "B4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            intonationTemplate = sautaIntonationTemplate
        ),
        PresetDefinition(
            id = "silaba",
            displayName = "Silaba",
            description = "Traditional Silaba map (Tomora Ba family) in concert-F reference.",
            openPitches21 = listOf(
                "F2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            openPitches22 = listOf(
                "F2", "Bb2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            intonationTemplate = silabaIntonationTemplate
        ),
        PresetDefinition(
            id = "tomora_ba",
            displayName = "Tomora Ba",
            description = "Traditional Tomora Ba map in concert-F reference.",
            openPitches21 = listOf(
                "F2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            openPitches22 = listOf(
                "F2", "Bb2", "C3", "D3", "E3",
                "F3", "G3", "A3", "Bb3", "C4", "D4", "E4",
                "F4", "G4", "A4", "Bb4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            intonationTemplate = tomoraBaIntonationTemplate
        )
    )

    fun presetsForStringCount(stringCount: Int): List<TraditionalPreset> {
        require(stringCount in SUPPORTED_STRING_COUNTS) {
            "Supported string counts are 21 and 22."
        }
        return definitions.map { definition ->
            val parsedOpenPitches = parsePitchList(
                if (stringCount == 21) definition.openPitches21 else definition.openPitches22
            )
            TraditionalPreset(
                id = "${definition.id}_$stringCount",
                displayName = definition.displayName,
                description = definition.description,
                stringCount = stringCount,
                openPitches = parsedOpenPitches,
                openIntonationCents = parsedOpenPitches.map { pitch ->
                    definition.intonationTemplate.centsForPitch(pitch)
                },
                closedIntonationCents = parsedOpenPitches.map { openPitch ->
                    definition.intonationTemplate.centsForPitch(openPitch.plusSemitones(1))
                }
            )
        }
    }

    private val SUPPORTED_STRING_COUNTS = setOf(21, 22)

    private fun parsePitchList(pitches: List<String>): List<Pitch> {
        return pitches.map { input ->
            requireNotNull(Pitch.parse(input)) {
                "Invalid traditional preset pitch: $input"
            }
        }
    }
}

