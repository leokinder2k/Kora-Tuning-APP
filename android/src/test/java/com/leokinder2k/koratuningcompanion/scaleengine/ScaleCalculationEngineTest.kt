package com.leokinder2k.koratuningcompanion.scaleengine

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.scaleengine.model.EngineMode
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.model.StringSide
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
            overrides = mapOf(
                1 to "C2",
                2 to "F2"
            ),
            fill = "C4"
        )
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR_PENTATONIC
            )
        )

        val leverOnlyRow = result.leverOnlyTable.first { row -> row.stringNumber == 2 }
        assertNull(leverOnlyRow.selectedLeverState)
        assertNull(leverOnlyRow.selectedPitch)
        assertTrue(leverOnlyRow.pegRetuneRequired)

        val pegCorrectRow = result.pegCorrectTable.first { row -> row.stringNumber == 2 }
        assertEquals(Pitch(NoteName.E, 2), pegCorrectRow.retunedOpenPitch)
        assertEquals(LeverState.OPEN, pegCorrectRow.selectedLeverState)
        assertTrue(pegCorrectRow.pegRetuneRequired)
        assertEquals(-1, pegCorrectRow.pegRetuneSemitones)
    }

    @Test
    fun root_isAnchoredToLeftBassString_andNamedNotesFollowKoraRoles() {
        val profile = profileWithPitches(
            overrides = mapOf(
                1 to "F2",
                2 to "C3",
                3 to "D3",
                4 to "E3",
                6 to "G3",
                8 to "A#3",
                10 to "D4",
                12 to "F4",
                14 to "A4",
                16 to "C5",
                18 to "E5"
            ),
            fill = "F3",
            stringCount = 21
        )
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.E,
                scaleType = ScaleType.MAJOR
            )
        )
        val leftRows = result.leverOnlyTable
            .filter { row -> row.role.side == StringSide.LEFT }
            .sortedBy { row -> row.role.positionFromLow }
        val leftOpenNotes = leftRows.map { row -> row.openPitch.note }

        assertEquals(NoteName.E, leftOpenNotes[0]) // root at left bass
        assertEquals(NoteName.B, leftOpenNotes[1]) // 5th
        assertEquals(NoteName.C_SHARP, leftOpenNotes[2]) // major 2nd
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
                    conflict.side == StringSide.LEFT &&
                    conflict.higherStringNumber == 3
            }
        )
        assertFalse(result.suggestions.isEmpty())
    }

    @Test
    fun outputTable_isOrderedByLeftThenRightLowToHigh() {
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
        assertEquals(1, result.leverOnlyTable.first().stringNumber)

        val rightStartIndex = 11
        assertEquals(StringSide.RIGHT, result.leverOnlyTable[rightStartIndex].role.side)
        assertEquals(1, result.leverOnlyTable[rightStartIndex].role.positionFromLow)
        assertEquals(5, result.leverOnlyTable[rightStartIndex].stringNumber)
    }

    @Test
    fun outputTable_respects21StringProfileCounts() {
        val profile = profileWithPitches(
            overrides = emptyMap(),
            fill = "C4",
            stringCount = 21
        )
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR
            )
        )

        assertEquals(21, result.leverOnlyTable.size)
        assertEquals(21, result.pegCorrectTable.size)
        assertEquals(
            11,
            result.leverOnlyTable.count { row -> row.role.side == StringSide.LEFT }
        )
        assertEquals(
            10,
            result.leverOnlyTable.count { row -> row.role.side == StringSide.RIGHT }
        )
    }

    @Test
    fun outputTable_respects22StringProfileCounts() {
        val profile = profileWithPitches(
            overrides = emptyMap(),
            fill = "C4",
            stringCount = 22
        )
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = NoteName.C,
                scaleType = ScaleType.MAJOR
            )
        )

        assertEquals(22, result.leverOnlyTable.size)
        assertEquals(22, result.pegCorrectTable.size)
        assertEquals(
            11,
            result.leverOnlyTable.count { row -> row.role.side == StringSide.LEFT }
        )
        assertEquals(
            11,
            result.leverOnlyTable.count { row -> row.role.side == StringSide.RIGHT }
        )
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

