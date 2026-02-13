package com.example.koratuningsystem.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchTest {

    @Test
    fun parse_acceptsSharpAndFlatInputs() {
        assertEquals(Pitch(NoteName.C_SHARP, 4), Pitch.parse("C#4"))
        assertEquals(Pitch(NoteName.C_SHARP, 4), Pitch.parse("Db4"))
        assertEquals(Pitch(NoteName.B, 3), Pitch.parse("Cb4"))
    }

    @Test
    fun parse_returnsNullForInvalidPitch() {
        assertNull(Pitch.parse("H2"))
        assertNull(Pitch.parse("A#"))
        assertNull(Pitch.parse(""))
    }

    @Test
    fun plusSemitones_rollsOverOctave() {
        val openPitch = Pitch(NoteName.B, 3)
        assertEquals(Pitch(NoteName.C, 4), openPitch.plusSemitones(1))
    }
}
