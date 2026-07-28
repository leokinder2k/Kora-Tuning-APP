package com.leokinder2k.koratuningcompanion.leverharp

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class LeverHarpCatalogTest {
    @Test
    fun standardHarpUsesEbLeverDownTuning() {
        val strings = standardLeverHarpStrings()

        assertEquals(34, strings.size)
        assertEquals(34, strings.first().highestFirstNumber)
        assertEquals("C2", strings.first().nominalName)
        assertEquals("C2", strings.first().leverDownPitch.asText(EnharmonicPreference.FLATS))
        assertEquals(1, strings.last().highestFirstNumber)
        assertEquals("A6", strings.last().nominalName)

        val e2 = strings.first { it.nominalName == "E2" }
        assertEquals("Eb2", e2.leverDownPitch.asText(EnharmonicPreference.FLATS))
        assertEquals("E2", e2.leverUpPitch().asText(EnharmonicPreference.FLATS))

        val a2 = strings.first { it.nominalName == "A2" }
        assertEquals("Ab2", a2.leverDownPitch.asText(EnharmonicPreference.FLATS))
        assertEquals("A2", a2.leverUpPitch().asText(EnharmonicPreference.FLATS))

        val b2 = strings.first { it.nominalName == "B2" }
        assertEquals("Bb2", b2.leverDownPitch.asText(EnharmonicPreference.FLATS))
        assertEquals("B2", b2.leverUpPitch().asText(EnharmonicPreference.FLATS))
    }

    @Test
    fun keySettingsRaiseCircleOfFifthsLeverLetters() {
        assertEquals("All down", LeverKeySettings.first().leverSummary)
        assertEquals(setOf("A"), LeverKeySettings[1].raisedLetters)
        assertEquals(setOf("A", "E", "B"), LeverKeySettings[3].raisedLetters)
        assertEquals(DiatonicLetters.toSet(), LeverKeySettings.last().raisedLetters)
        assertEquals("All up", LeverKeySettings.last().leverSummary)
    }

    @Test
    fun selectedKeyChangesCurrentPitch() {
        val strings = standardLeverHarpStrings()
        val ebMajor = LeverKeySettings.first { it.keyName.startsWith("Eb") }
        val bbMajor = LeverKeySettings.first { it.keyName.startsWith("Bb") }
        val fMajor = LeverKeySettings.first { it.keyName.startsWith("F") }

        val a3 = strings.first { it.nominalName == "A3" }
        assertEquals("Ab3", a3.currentPitch(ebMajor).asText(EnharmonicPreference.FLATS))
        assertEquals("A3", a3.currentPitch(bbMajor).asText(EnharmonicPreference.FLATS))

        val e3 = strings.first { it.nominalName == "E3" }
        assertEquals("Eb3", e3.currentPitch(bbMajor).asText(EnharmonicPreference.FLATS))
        assertEquals("E3", e3.currentPitch(fMajor).asText(EnharmonicPreference.FLATS))
    }
}
