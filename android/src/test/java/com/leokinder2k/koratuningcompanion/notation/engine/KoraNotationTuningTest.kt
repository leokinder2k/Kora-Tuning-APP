package com.leokinder2k.koratuningcompanion.notation.engine

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import org.junit.Assert.assertEquals
import org.junit.Test

class KoraNotationTuningTest {
    @Test
    fun fTuningMapsWrittenLeftStringsToMatchingTabs() {
        val tuning = fTuning(KoraInstrumentType.KORA_21)
        val tuningMidi = tuningToMidi(tuning)

        assertEquals("F2", tuning.stringNoteNames["L1"])
        assertEquals("C3", tuning.stringNoteNames["L2"])
        assertEquals("D3", tuning.stringNoteNames["L3"])

        assertEquals(
            listOf("L1", "L2", "L3"),
            mapNotes("F2", "C3", "D3", tuningMidi = tuningMidi).map { it.stringId }
        )
    }

    @Test
    fun customETuningMapsWrittenBaseNoteToLeftOne() {
        val ePitches = StarterInstrumentProfiles.openPitches(21).map { pitch ->
            pitch.plusSemitones(-1)
        }
        val profile = InstrumentProfile(
            stringCount = 21,
            openPitches = ePitches,
            basePitches = ePitches,
            rootNote = NoteName.E,
        )
        val tuning = tuningFromInstrumentProfile(profile)
        val tuningMidi = tuningToMidi(tuning)

        assertEquals("E2", tuning.stringNoteNames["L1"])
        assertEquals("B2", tuning.stringNoteNames["L2"])
        assertEquals("C#3", tuning.stringNoteNames["L3"])

        assertEquals(
            listOf("L1", "L2", "L3"),
            mapNotes("E2", "B2", "C#3", tuningMidi = tuningMidi).map { it.stringId }
        )
    }

    private fun mapNotes(
        vararg noteNames: String,
        tuningMidi: Map<String, Int>,
    ): List<MappedEvent> {
        val score = SimplifiedScore(
            noteEvents = noteNames.mapIndexed { index, noteName ->
                NoteEvent(
                    eventId = "n$index",
                    tick = index * 960,
                    durationTicks = 960,
                    pitchMidi = noteNameToMidi(noteName),
                )
            },
            restEvents = emptyList(),
            measures = listOf(MeasureInfo(1, 0, 3840)),
            keySignatures = listOf(KeySignatureInfo(0, 0)),
            tempoMap = listOf(TempoInfo(0, 120.0)),
            timeSignatures = listOf(TimeSignatureInfo(0, 4, 4)),
        )
        return mapSimplifiedScoreToKora(
            instrumentType = KoraInstrumentType.KORA_21,
            tuningMidiByStringId = tuningMidi,
            score = score,
        ).events
    }
}
