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
                // Left L1..L11
                "L1" to "F1", "L2" to "C2", "L3" to "D2", "L4" to "E2", "L5" to "G2",
                "L6" to "Bb2", "L7" to "D3", "L8" to "F3", "L9" to "A3", "L10" to "C4", "L11" to "E4",
                // Right R0..R10
                "R0" to "Bb1", "R1" to "F2", "R2" to "A2", "R3" to "C3", "R4" to "E3",
                "R5" to "G3", "R6" to "Bb3", "R7" to "D4", "R8" to "F4", "R9" to "G4", "R10" to "A4",
            )
        )
        KoraInstrumentType.KORA_21 -> KoraTuning(
            name = "F tuning",
            stringNoteNames = mapOf(
                // Left L1..L11
                "L1" to "F1", "L2" to "C2", "L3" to "D2", "L4" to "E2", "L5" to "G2",
                "L6" to "Bb2", "L7" to "D3", "L8" to "F3", "L9" to "A3", "L10" to "C4", "L11" to "E4",
                // Right R1..R10
                "R1" to "F2", "R2" to "A2", "R3" to "C3", "R4" to "E3", "R5" to "G3",
                "R6" to "Bb3", "R7" to "D4", "R8" to "F4", "R9" to "G4", "R10" to "A4",
            )
        )
    }
}

/** Convert a KoraTuning note-name map to MIDI integer map. */
fun tuningToMidi(tuning: KoraTuning): Map<String, Int> =
    tuning.stringNoteNames.mapValues { (_, name) -> noteNameToMidi(name) }
