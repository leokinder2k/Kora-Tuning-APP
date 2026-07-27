package com.leokinder2k.koratuningcompanion.notation.engine

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.displaySymbol

// Port of tunings.js

data class KoraTuning(
    val name: String,
    val stringNoteNames: Map<String, String>,   // stringId → note name e.g. "F2"
)

fun fTuning(instrumentType: KoraInstrumentType): KoraTuning {
    val stringCount = when (instrumentType) {
        KoraInstrumentType.KORA_21 -> 21
        KoraInstrumentType.KORA_22_CHROMATIC -> 22
    }
    return tuningFromStringPitches(
        instrumentType = instrumentType,
        name = "F tuning",
        pitches = StarterInstrumentProfiles.openPitches(stringCount)
    )
}

fun tuningFromInstrumentProfile(profile: InstrumentProfile): KoraTuning {
    val instrumentType = when (profile.stringCount) {
        22 -> KoraInstrumentType.KORA_22_CHROMATIC
        21 -> KoraInstrumentType.KORA_21
        else -> error("Notation supports 21- and 22-string kora profiles.")
    }
    val root = profile.rootNote.displaySymbol(EnharmonicPreference.SHARPS)
    return tuningFromStringPitches(
        instrumentType = instrumentType,
        name = "$root tuning",
        pitches = profile.openPitches
    )
}

fun tuningFromStringNoteNames(
    instrumentType: KoraInstrumentType,
    name: String,
    stringNoteNames: Map<String, String>,
): KoraTuning {
    val expected = allStringIds(instrumentType).toSet()
    require(stringNoteNames.keys == expected) {
        "Tuning must define every string for $instrumentType."
    }
    stringNoteNames.values.forEach(::noteNameToMidi)
    return KoraTuning(
        name = name.ifBlank { "Custom tuning" },
        stringNoteNames = stringNoteNames
    )
}

private fun tuningFromStringPitches(
    instrumentType: KoraInstrumentType,
    name: String,
    pitches: List<Pitch>,
): KoraTuning {
    val stringCount = when (instrumentType) {
        KoraInstrumentType.KORA_21 -> 21
        KoraInstrumentType.KORA_22_CHROMATIC -> 22
    }
    require(pitches.size == stringCount) {
        "Tuning pitch count must match $instrumentType."
    }

    val namesByStringId = pitches.mapIndexed { index, pitch ->
        KoraStringLayout.roleLabel(stringCount = stringCount, stringNumber = index + 1) to
            pitch.asText(EnharmonicPreference.SHARPS)
    }.toMap()

    return tuningFromStringNoteNames(
        instrumentType = instrumentType,
        name = name,
        stringNoteNames = namesByStringId
    )
}

/** Convert a KoraTuning note-name map to MIDI integer map. */
fun tuningToMidi(tuning: KoraTuning): Map<String, Int> =
    tuning.stringNoteNames.mapValues { (_, name) -> noteNameToMidi(name) }
