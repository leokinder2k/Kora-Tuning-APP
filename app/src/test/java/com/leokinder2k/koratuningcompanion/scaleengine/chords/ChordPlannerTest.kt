package com.leokinder2k.koratuningcompanion.scaleengine.chords

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleStringRole
import com.leokinder2k.koratuningcompanion.scaleengine.model.StringSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordPlannerTest {

    @Test
    fun analyze_marksMajorChordAsCompleteWhenAllChordTonesExist() {
        val rows = listOf(
            testRow(stringNumber = 1, note = NoteName.C),
            testRow(stringNumber = 2, note = NoteName.E),
            testRow(stringNumber = 3, note = NoteName.G),
            testRow(stringNumber = 4, note = NoteName.B)
        )

        val match = ChordPlanner.analyze(
            rows = rows,
            definition = ChordDefinition(
                root = NoteName.C,
                quality = ChordQuality.MAJOR
            )
        )

        assertTrue(match.isComplete)
        assertFalse(match.usesDetunedStrings)
        assertTrue(match.playedStringNumbers.size >= 3)
        assertTrue(match.playedStringNumbers.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun suggestClosestWithoutDetune_returnsStableChordWhenDesiredUsesDetunedStrings() {
        val rows = listOf(
            testRow(stringNumber = 1, note = NoteName.C),
            testRow(stringNumber = 2, note = NoteName.E),
            testRow(stringNumber = 3, note = NoteName.G, detuned = true),
            testRow(stringNumber = 4, note = NoteName.A),
            testRow(stringNumber = 5, note = NoteName.C_SHARP),
            testRow(stringNumber = 6, note = NoteName.E)
        )

        val desired = ChordDefinition(
            root = NoteName.C,
            quality = ChordQuality.MAJOR
        )
        val desiredMatch = ChordPlanner.analyze(rows = rows, definition = desired)
        val suggestion = ChordPlanner.suggestClosestWithoutDetune(
            rows = rows,
            desired = desired
        )

        assertTrue(desiredMatch.usesDetunedStrings)
        assertTrue(suggestion != null)
        assertFalse(requireNotNull(suggestion).usesDetunedStrings)
        assertTrue(suggestion.isComplete)
        assertTrue(suggestion.playedStringNumbers.isNotEmpty())
    }

    @Test
    fun chooseChordStrings_limitsVoicingAndRespectsSelectedPositions() {
        val rows = listOf(
            testRow(stringNumber = 1, note = NoteName.C),
            testRow(stringNumber = 2, note = NoteName.E),
            testRow(stringNumber = 3, note = NoteName.G),
            testRow(stringNumber = 4, note = NoteName.B),
            testRow(stringNumber = 5, note = NoteName.C),
            testRow(stringNumber = 6, note = NoteName.E)
        )
        val definition = ChordDefinition(
            root = NoteName.C,
            quality = ChordQuality.MAJOR7
        )

        val selected = ChordPlanner.chooseChordStrings(
            rows = rows,
            definition = definition,
            toneOffsetsToInclude = setOf(4, 11), // 3rd and 7th
            maxNotes = 2
        )

        assertEquals(2, selected.size)
        val selectedNotes = selected.map { row -> row.selectedPitch.note }.toSet()
        assertTrue(selectedNotes.contains(NoteName.E))
        assertTrue(selectedNotes.contains(NoteName.B))
    }

    private fun testRow(
        stringNumber: Int,
        note: NoteName,
        detuned: Boolean = false,
        leverState: LeverState = LeverState.OPEN
    ): PegCorrectStringResult {
        val side = if (stringNumber % 2 == 1) StringSide.LEFT else StringSide.RIGHT
        val position = if (side == StringSide.LEFT) {
            (stringNumber / 2) + 1
        } else {
            stringNumber / 2
        }
        val openPitch = Pitch(note = note, octave = 4)
        return PegCorrectStringResult(
            stringNumber = stringNumber,
            role = ScaleStringRole(side = side, positionFromLow = position),
            originalOpenPitch = openPitch,
            originalClosedPitch = openPitch.plusSemitones(1),
            retunedOpenPitch = openPitch,
            retunedClosedPitch = openPitch.plusSemitones(1),
            selectedLeverState = leverState,
            selectedPitch = openPitch,
            pegRetuneSemitones = if (detuned) 1 else 0,
            pegRetuneRequired = detuned,
            selectedIntonationCents = 0.0
        )
    }
}

