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

        assertEquals("A#2", tuning.stringNoteNames["R0"])
        assertEquals("F3", tuning.stringNoteNames["R1"])
        assertEquals("A5", tuning.stringNoteNames["R10"])
    }

    @Test
    fun defaultAssignmentsUseSixStringThumbAndFingerZones() {
        assertEquals(
            listOf("L1", "L2", "L3", "L4", "L5", "L6"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_21).getValue("LT")
        )
        assertEquals(
            listOf("L6", "L7", "L8", "L9", "L10", "L11"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_21).getValue("LF")
        )
        assertEquals(
            listOf("R1", "R2", "R3", "R4", "R5", "R6"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_21).getValue("RT")
        )
        assertEquals(
            listOf("R5", "R6", "R7", "R8", "R9", "R10"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_21).getValue("RF")
        )
        assertEquals(
            listOf("R0", "R1", "R2", "R3", "R4", "R5"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_22_CHROMATIC).getValue("RT")
        )
        assertEquals(
            listOf("R5", "R6", "R7", "R8", "R9", "R10"),
            defaultStringToDigitAssignments(KoraInstrumentType.KORA_22_CHROMATIC).getValue("RF")
        )
    }

    @Test
    fun renderedNumberCountsThumbFromBassAndFingerFromTreble() {
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_21, "L1", "LT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_21, "L6", "LT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_21, "L6", "LF"))
        assertEquals(2, renderedNumber(KoraInstrumentType.KORA_21, "L10", "LF"))
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_21, "L11", "LF"))

        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_21, "R1", "RT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_21, "R6", "RT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_21, "R5", "RF"))
        assertEquals(2, renderedNumber(KoraInstrumentType.KORA_21, "R9", "RF"))
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_21, "R10", "RF"))

        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R0", "RT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R5", "RT"))
        assertEquals(6, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R5", "RF"))
        assertEquals(1, renderedNumber(KoraInstrumentType.KORA_22_CHROMATIC, "R10", "RF"))
    }
}
