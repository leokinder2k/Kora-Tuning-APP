package com.leokinder2k.koratuningcompanion.notation.engine

// Port of tunings.js

data class KoraTuning(
    val name: String,
    val stringNoteNames: Map<String, String>,   // stringId → note name e.g. "F2"
)

fun fTuning(instrumentType: KoraInstrumentType): KoraTuning {
    return when (instrumentType) {
        KoraInstrumentType.KORA_22_CHROMATIC -> KoraTuning(
            name = "F tuning",
            stringNoteNames = mapOf(
                // Left L01..L11
                "L01" to "F1", "L02" to "C2", "L03" to "D2", "L04" to "E2", "L05" to "G2",
                "L06" to "Bb2", "L07" to "D3", "L08" to "F3", "L09" to "A3", "L10" to "C4", "L11" to "E4",
                // Right R01..R11
                "R01" to "Bb1", "R02" to "F2", "R03" to "A2", "R04" to "C3", "R05" to "E3",
                "R06" to "G3", "R07" to "Bb3", "R08" to "D4", "R09" to "F4", "R10" to "G4", "R11" to "A4",
            )
        )
        KoraInstrumentType.KORA_21 -> KoraTuning(
            name = "F tuning",
            stringNoteNames = mapOf(
                // Left L01..L11
                "L01" to "F1", "L02" to "C2", "L03" to "D2", "L04" to "E2", "L05" to "G2",
                "L06" to "Bb2", "L07" to "D3", "L08" to "F3", "L09" to "A3", "L10" to "C4", "L11" to "E4",
                // Right R01..R10
                "R01" to "F2", "R02" to "A2", "R03" to "C3", "R04" to "E3", "R05" to "G3",
                "R06" to "Bb3", "R07" to "D4", "R08" to "F4", "R09" to "G4", "R10" to "A4",
            )
        )
    }
}

/** Convert a KoraTuning note-name map to MIDI integer map. */
fun tuningToMidi(tuning: KoraTuning): Map<String, Int> =
    tuning.stringNoteNames.mapValues { (_, name) -> noteNameToMidi(name) }
