package com.leokinder2k.koratuningcompanion.instrumentconfig.model

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
        val pitches = StarterInstrumentProfiles.openPitches(22)
        assertEquals(Pitch(NoteName.F, 2), pitches.first())
        assertEquals(Pitch(NoteName.A, 5), pitches.last())
    }

    @Test
    fun starter22_matchesSilabaConcertFStringNumberOrder() {
        assertEquals(
            listOf(
                "F2", "A#2", "C3", "D3", "E3", "F3",
                "G3", "A3", "A#3", "C4", "D4",
                "E4", "F4", "G4", "A4", "A#4", "C5",
                "D5", "E5", "F5", "G5", "A5"
            ),
            StarterInstrumentProfiles.openPitches(22).map(Pitch::asText)
        )
    }

    @Test
    fun starter22_matchesSilabaConcertFBridgeSides() {
        val pitches = StarterInstrumentProfiles.openPitches(22)
        assertEquals(
            listOf("F2", "C3", "D3", "E3", "G3", "A#3", "D4", "F4", "A4", "C5", "E5"),
            KoraStringLayout.leftOrder(22).map { stringNumber -> pitches[stringNumber - 1].asText() }
        )
        assertEquals(
            listOf("A#2", "F3", "A3", "C4", "E4", "G4", "A#4", "D5", "F5", "G5", "A5"),
            KoraStringLayout.rightOrder(22).map { stringNumber -> pitches[stringNumber - 1].asText() }
        )
    }
}

