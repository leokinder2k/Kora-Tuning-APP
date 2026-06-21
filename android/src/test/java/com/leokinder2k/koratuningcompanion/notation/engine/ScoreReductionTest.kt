package com.leokinder2k.koratuningcompanion.notation.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreReductionTest {
    @Test
    fun reductionMergesHeldSourceNoteAcrossMovingQuarterSlices() {
        val held = NoteEvent(
            eventId = "held",
            tick = 0,
            durationTicks = 3840,
            pitchMidi = 72,
            melodyHint = true,
        )
        val movingBass = listOf(0, 960, 1920, 2880).mapIndexed { index, tick ->
            NoteEvent(
                eventId = "bass_$index",
                tick = tick,
                durationTicks = 960,
                pitchMidi = 48 + index,
            )
        }

        val (notes, _) = buildSimplifiedTeachingReduction(
            noteEvents = listOf(held) + movingBass,
            cap = 2,
            splitMidi = 60,
        )

        val heldReduced = notes.filter { it.sourceEventId == "held" }
        assertEquals(1, heldReduced.size)
        assertEquals(0, heldReduced.single().tick)
        assertEquals(3840, heldReduced.single().durationTicks)
    }

    @Test
    fun reductionKeepsAdjacentRepeatedNotesSeparate() {
        val repeated = listOf(
            NoteEvent(
                eventId = "first",
                tick = 0,
                durationTicks = 960,
                pitchMidi = 72,
                melodyHint = true,
            ),
            NoteEvent(
                eventId = "second",
                tick = 960,
                durationTicks = 960,
                pitchMidi = 72,
                melodyHint = true,
            ),
        )

        val (notes, _) = buildSimplifiedTeachingReduction(
            noteEvents = repeated,
            cap = 1,
            splitMidi = 60,
        )

        assertEquals(listOf(960, 960), notes.map { it.durationTicks })
        assertEquals(listOf("first", "second"), notes.map { it.sourceEventId })
    }
}
