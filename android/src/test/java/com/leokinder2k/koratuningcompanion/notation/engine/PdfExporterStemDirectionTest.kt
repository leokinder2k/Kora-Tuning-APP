package com.leokinder2k.koratuningcompanion.notation.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExporterStemDirectionTest {
    @Test
    fun notesBelowStaffMiddleLineUseUpStemOnRight() {
        assertTrue(shouldStemUpForStaffPosition(noteY = 19f, staffMiddleLineY = 18f))
    }

    @Test
    fun notesOnOrAboveStaffMiddleLineUseDownStemOnLeft() {
        assertFalse(shouldStemUpForStaffPosition(noteY = 18f, staffMiddleLineY = 18f))
        assertFalse(shouldStemUpForStaffPosition(noteY = 17f, staffMiddleLineY = 18f))
    }
}
