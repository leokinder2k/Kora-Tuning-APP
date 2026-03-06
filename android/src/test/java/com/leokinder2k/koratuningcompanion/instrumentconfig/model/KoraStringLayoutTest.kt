package com.leokinder2k.koratuningcompanion.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KoraStringLayoutTest {

    @Test
    fun layout21_matchesExpectedLeftAndRightOrders() {
        assertEquals(
            listOf(1, 2, 3, 4, 6, 8, 10, 12, 14, 16, 18),
            KoraStringLayout.leftOrder(21)
        )
        assertEquals(
            listOf(5, 7, 9, 11, 13, 15, 17, 19, 20, 21),
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
    }

    @Test
    fun layout21_matchesProvidedFPattern_lowToHighOnEachSide() {
        val openPitches = listOf(
            "F", "C", "D", "E", "F", "G", "A", "Bb", "C", "D", "E",
            "F", "G", "A", "Bb", "C", "D", "E", "F", "G", "A"
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
}

