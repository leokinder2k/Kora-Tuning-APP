package com.leokinder2k.koratuningcompanion.scaleengine.ui

import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagramStringActionStateTest {

    @Test
    fun manualSharpenOnOpenStringBecomesPegOnly() {
        assertEquals(
            DiagramStringActionState.PEG_ONLY,
            diagramStringActionState(
                selectedLeverState = LeverState.OPEN,
                pegRetuneSemitones = 0,
                manualSemitoneShift = 1
            )
        )
        assertEquals(1, effectivePegDelta(pegRetuneSemitones = 0, manualSemitoneShift = 1))
    }

    @Test
    fun manualFlattenCanCancelExistingPegRetuneAndLeaveLeverOnly() {
        assertEquals(
            DiagramStringActionState.LEVER_ONLY,
            diagramStringActionState(
                selectedLeverState = LeverState.CLOSED,
                pegRetuneSemitones = 1,
                manualSemitoneShift = -1
            )
        )
        assertEquals(0, effectivePegDelta(pegRetuneSemitones = 1, manualSemitoneShift = -1))
    }

    @Test
    fun unchangedOpenStringStaysOpen() {
        assertEquals(
            DiagramStringActionState.OPEN,
            diagramStringActionState(
                selectedLeverState = LeverState.OPEN,
                pegRetuneSemitones = 0,
                manualSemitoneShift = 0
            )
        )
    }
}
