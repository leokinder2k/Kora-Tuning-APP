package com.leokinder2k.koratuningcompanion.notation.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StringIdTest {
    @Test
    fun allStringIds_useSilabaSideLabels() {
        assertEquals(
            listOf(
                "L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8", "L9", "L10", "L11",
                "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10"
            ),
            allStringIds(KoraInstrumentType.KORA_21)
        )
        assertEquals(
            listOf(
                "L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8", "L9", "L10", "L11",
                "R0", "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10"
            ),
            allStringIds(KoraInstrumentType.KORA_22_CHROMATIC)
        )
    }

    @Test
    fun fTuning_usesOptionalRightBassAsR0() {
        val tuning = fTuning(KoraInstrumentType.KORA_22_CHROMATIC)

        assertEquals("Bb1", tuning.stringNoteNames["R0"])
        assertEquals("F2", tuning.stringNoteNames["R1"])
        assertEquals("A4", tuning.stringNoteNames["R10"])
    }

    @Test
    fun renderedNumber_keeps22StringRightLabelsPhysical() {
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R0", "RF"))
        assertEquals(11, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R10", "RF"))
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_21, "R1", "RF"))
    }
}
