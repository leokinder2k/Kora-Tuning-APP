package com.example.koratuningsystem.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StarterInstrumentProfilesTest {

    @Test
    fun openPitchTexts_returnsExpectedSizes() {
        assertEquals(21, StarterInstrumentProfiles.openPitchTexts(21).size)
        assertEquals(22, StarterInstrumentProfiles.openPitchTexts(22).size)
    }

    @Test
    fun openPitches_containsValidPitchObjects() {
        val pitches = StarterInstrumentProfiles.openPitches(21)
        assertEquals(Pitch(NoteName.E, 2), pitches.first())
        assertEquals(Pitch(NoteName.D_SHARP, 5), pitches.last())
    }
}
