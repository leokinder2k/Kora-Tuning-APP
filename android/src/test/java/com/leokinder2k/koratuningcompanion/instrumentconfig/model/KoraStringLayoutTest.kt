package com.leokinder2k.koratuningcompanion.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KoraStringLayoutTest {

    @Test
    fun layout21_matchesExpectedLeftAndRightOrders() {
        assertEquals(
            listOf(1, 3, 4, 5, 7, 9, 11, 13, 15, 17, 19),
            KoraStringLayout.leftOrder(21)
        )
        assertEquals(
            listOf(2, 6, 8, 10, 12, 14, 16, 18, 20, 21),
            KoraStringLayout.rightOrder(21)
        )
    }

    @Test
    fun layout22_includesExtraLowRightString() {
        assertEquals(
            listOf(1, 3, 4, 5, 7, 9, 11, 13, 15, 17, 19),
            KoraStringLayout.leftOrder(22)
        )
        assertEquals(
            listOf(2, 6, 8, 10, 12, 14, 16, 18, 20, 21, 22),
            KoraStringLayout.rightOrder(22)
        )

        val lowLeft = KoraStringLayout.roleFor(stringCount = 22, stringNumber = 1)
        assertEquals(KoraStringSide.LEFT, lowLeft.side)
        assertEquals(1, lowLeft.positionFromLow)

        val lowRight = KoraStringLayout.roleFor(stringCount = 22, stringNumber = 2)
        assertEquals(KoraStringSide.RIGHT, lowRight.side)
        assertEquals(1, lowRight.positionFromLow)

        assertEquals("L1", KoraStringLayout.roleLabel(stringCount = 22, stringNumber = 1))
        assertEquals("R0", KoraStringLayout.roleLabel(stringCount = 22, stringNumber = 2))
        assertEquals("L2", KoraStringLayout.roleLabel(stringCount = 22, stringNumber = 3))
        assertEquals("R10", KoraStringLayout.roleLabel(stringCount = 22, stringNumber = 22))
        assertEquals("R1", KoraStringLayout.roleLabel(stringCount = 21, stringNumber = 2))
        assertEquals("R10", KoraStringLayout.roleLabel(stringCount = 21, stringNumber = 21))
    }

    @Test
    fun tuningOrder21_matchesPhysicalAssistantSequence() {
        val openPitches = listOf(
            "F", "F", "C", "D", "E", "A", "G", "C", "Bb", "E", "D",
            "G", "F", "Bb", "A", "D", "C", "F", "E", "G", "A"
        )
        val tuningOrder = KoraStringLayout.tuningOrder(21)
        val sequence = tuningOrder.map { stringNumber -> openPitches[stringNumber - 1] }

        assertEquals((1..21).toList(), tuningOrder)
        assertEquals(
            listOf(
                "F", "F", "C", "D", "E", "A", "G", "C", "Bb", "E", "D",
                "G", "F", "Bb", "A", "D", "C", "F", "E", "G", "A"
            ),
            sequence
        )
    }

    @Test
    fun tuningOrder22_matchesPhysicalAssistantSequence() {
        val openPitches = listOf(
            "F", "Bb", "C", "D", "E", "F", "G", "A", "Bb", "C", "D",
            "E", "F", "G", "A", "Bb", "C", "D", "E", "F", "G", "A"
        )
        val tuningOrder = KoraStringLayout.tuningOrder(22)
        val sequence = tuningOrder.map { stringNumber -> openPitches[stringNumber - 1] }

        assertEquals((1..22).toList(), tuningOrder)
        assertEquals(
            listOf(
                "F", "Bb", "C", "D", "E", "F", "G", "A", "Bb", "C", "D",
                "E", "F", "G", "A", "Bb", "C", "D", "E", "F", "G", "A"
            ),
            sequence
        )
    }

    @Test
    fun layout21_matchesProvidedFPattern_lowToHighOnEachSide() {
        val openPitches = listOf(
            "F", "F", "C", "D", "E", "A", "G", "C", "Bb", "E", "D",
            "G", "F", "Bb", "A", "D", "C", "F", "E", "G", "A"
        )
        val left = KoraStringLayout.leftOrder(21).map { stringNumber ->
            openPitches[stringNumber - 1]
        }
        val right = KoraStringLayout.rightOrder(21).map { stringNumber ->
            openPitches[stringNumber - 1]
        }

        assertEquals(
            listOf("F", "C", "D", "E", "G", "Bb", "D", "F", "A", "C", "E"),
            left
        )
        assertEquals(
            listOf("F", "A", "C", "E", "G", "Bb", "D", "F", "G", "A"),
            right
        )
    }

    @Test
    fun layout22_matchesProvidedFPattern_lowToHighOnEachSide() {
        val openPitches = listOf(
            "F", "Bb", "C", "D", "E", "F", "G", "A", "Bb", "C", "D",
            "E", "F", "G", "A", "Bb", "C", "D", "E", "F", "G", "A"
        )
        val left = KoraStringLayout.leftOrder(22).map { stringNumber ->
            openPitches[stringNumber - 1]
        }
        val right = KoraStringLayout.rightOrder(22).map { stringNumber ->
            openPitches[stringNumber - 1]
        }

        assertEquals(
            listOf("F", "C", "D", "E", "G", "Bb", "D", "F", "A", "C", "E"),
            left
        )
        assertEquals(
            listOf("Bb", "F", "A", "C", "E", "G", "Bb", "D", "F", "G", "A"),
            right
        )
    }

    @Test
    fun nextOnSameSide_advancesWithinLeftAndRightOrders() {
        assertEquals(3, KoraStringLayout.nextOnSameSide(stringCount = 22, stringNumber = 1))
        assertEquals(4, KoraStringLayout.nextOnSameSide(stringCount = 22, stringNumber = 3))
        assertEquals(3, KoraStringLayout.nextOnSameSide(stringCount = 21, stringNumber = 1))
        assertEquals(6, KoraStringLayout.nextOnSameSide(stringCount = 22, stringNumber = 2))
        assertEquals(8, KoraStringLayout.nextOnSameSide(stringCount = 21, stringNumber = 6))
        assertEquals(null, KoraStringLayout.nextOnSameSide(stringCount = 22, stringNumber = 19))
        assertEquals(null, KoraStringLayout.nextOnSameSide(stringCount = 22, stringNumber = 22))
    }
}

