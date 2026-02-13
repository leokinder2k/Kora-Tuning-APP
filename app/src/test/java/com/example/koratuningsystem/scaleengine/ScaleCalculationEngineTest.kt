package com.example.koratuningsystem.scaleengine

import com.example.koratuningsystem.instrumentconfig.model.InstrumentProfile
import com.example.koratuningsystem.instrumentconfig.model.NoteName
import com.example.koratuningsystem.instrumentconfig.model.Pitch
import com.example.koratuningsystem.scaleengine.model.EngineMode
import com.example.koratuningsystem.scaleengine.model.LeverState
import com.example.koratuningsystem.scaleengine.model.ScaleCalculationRequest
import com.example.koratuningsystem.scaleengine.model.ScaleType
import com.example.koratuningsystem.scaleengine.model.StringSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleCalculationEngineTest {

    private val engine = ScaleCalculationEngine()

    @Test
    fun scaleType_buildsExpectedMajorNotes() {
        val notes = ScaleType.MAJOR.notesForRoot(NoteName.C)

        assertEquals(
            setOf(
                NoteName.C,
                NoteName.D,
                NoteName.E,
                NoteName.F,
                NoteName.G,
                NoteName.A,
                NoteName.B
            ),
            notes
        )
    }

    @Test
    fun scaleType_buildsExpectedModes() {
        val lydianNotes = ScaleType.LYDIAN.notesForRoot(NoteName.C)
        assertEquals(
            setOf(
                NoteName.C,
                NoteName.D,
                NoteName.E,
                NoteName.F_SHARP,
                NoteName.G,
                NoteName.A,
                NoteName.B
            ),
            lydianNotes
        )

        val locrianNotes = ScaleType.LOCRIAN.notesForRoot(NoteName.B)
        assertEquals(
            setOf(
                NoteName.B,
                NoteName.C,
                NoteName.D,
                NoteName.E,
                NoteName.F,
                NoteName.G,
                NoteName.A
            ),
            locrianNotes
        )
    }

    @Test
    fun scaleType_includesHexatonicOctatonicAndBeebopSets() {
        val wholeTone = ScaleType.WHOLE_TONE.notesForRoot(NoteName.C)
        assertEquals(6, wholeTone.size)

        val diminished = ScaleType.DIMINISHED_WHOLE_HALF.notesForRoot(NoteName.C)
        assertEquals(8, diminished.size)

        val beebopDominant = ScaleType.BEEBOP_DOMINANT.notesForRoot(NoteName.C)
        assertEquals(8, beebopDominant.size)
        assertTrue(beebopDominant.contains(NoteName.A_SHARP))
    }

    @Test
    fun leverOnly_marksPegRetune_whenOpenAndClosedAreOutOfScale() {
        val profile = profileWithPitches(
            overrides = mapOf(1 to "F2"),
            fill = "C4"
        )
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR_PENTATONIC
            )
        )

        val leverOnlyRow = result.leverOnlyTable.first { row -> row.stringNumber == 1 }
        assertNull(leverOnlyRow.selectedLeverState)
        assertNull(leverOnlyRow.selectedPitch)
        assertTrue(leverOnlyRow.pegRetuneRequired)

        val pegCorrectRow = result.pegCorrectTable.first { row -> row.stringNumber == 1 }
        assertEquals(Pitch(NoteName.E, 2), pegCorrectRow.retunedOpenPitch)
        assertEquals(LeverState.OPEN, pegCorrectRow.selectedLeverState)
        assertTrue(pegCorrectRow.pegRetuneRequired)
        assertEquals(-1, pegCorrectRow.pegRetuneSemitones)
    }

    @Test
    fun detectsVoicingConflict_andProducesSuggestion() {
        val profile = profileWithPitches(
            overrides = mapOf(
                1 to "C4",
                3 to "B3"
            ),
            fill = "C4"
        )

        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR
            )
        )

        assertTrue(
            result.conflicts.any { conflict ->
                conflict.mode == EngineMode.LEVER_ONLY &&
                    conflict.lowerStringNumber == 1 &&
                    conflict.higherStringNumber == 3
            }
        )
        assertFalse(result.suggestions.isEmpty())
    }

    @Test
    fun outputTable_isOrderedBySideLowToHigh() {
        val profile = profileWithPitches(overrides = emptyMap(), fill = "C4")
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR
            )
        )

        assertEquals(StringSide.LEFT, result.leverOnlyTable.first().role.side)
        assertEquals(1, result.leverOnlyTable.first().role.positionFromLow)
        assertEquals(3, result.leverOnlyTable[1].stringNumber)

        val rightStartIndex = 11
        assertEquals(StringSide.RIGHT, result.leverOnlyTable[rightStartIndex].role.side)
        assertEquals(1, result.leverOnlyTable[rightStartIndex].role.positionFromLow)
        assertEquals(2, result.leverOnlyTable[rightStartIndex].stringNumber)
    }

    private fun profileWithPitches(
        overrides: Map<Int, String>,
        fill: String,
        stringCount: Int = 21
    ): InstrumentProfile {
        val openPitchTexts = MutableList(stringCount) { fill }
        overrides.forEach { (stringNumber, value) ->
            openPitchTexts[stringNumber - 1] = value
        }

        val pitches = openPitchTexts.map { input ->
            requireNotNull(Pitch.parse(input)) {
                "Invalid test pitch: $input"
            }
        }
        return InstrumentProfile(
            stringCount = stringCount,
            openPitches = pitches
        )
    }
}
