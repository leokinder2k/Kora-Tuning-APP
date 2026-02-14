package com.leokinder2k.koratuningcompanion.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraditionalPresetsTest {

    @Test
    fun presetsForStringCount_returnsNonEmptySetsFor21And22() {
        assertFalse(TraditionalPresets.presetsForStringCount(21).isEmpty())
        assertFalse(TraditionalPresets.presetsForStringCount(22).isEmpty())
    }

    @Test
    fun presets_haveMatchingPitchCountsAndBuildProfiles() {
        listOf(21, 22).forEach { stringCount ->
            TraditionalPresets.presetsForStringCount(stringCount).forEach { preset ->
                assertEquals(stringCount, preset.openPitches.size)
                assertEquals(stringCount, preset.openIntonationCents.size)
                assertEquals(stringCount, preset.closedIntonationCents.size)
                assertEquals(stringCount, preset.toInstrumentProfile().strings.size)
            }
        }
    }

    @Test
    fun presetIds_areUniquePerStringCount() {
        listOf(21, 22).forEach { stringCount ->
            val ids = TraditionalPresets.presetsForStringCount(stringCount).map { preset -> preset.id }
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(ids.all { id -> id.endsWith("_$stringCount") })
        }
    }

    @Test
    fun silaba21_matchesTraditionalConcertFLayout() {
        val silaba = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }

        assertEquals(
            listOf(
                "F2", "C3", "D3", "E3",
                "F3", "G3", "A3", "A#3", "C4", "D4", "E4",
                "F4", "G4", "A4", "A#4", "C5", "D5", "E5",
                "F5", "G5", "A5"
            ),
            silaba.openPitches.map(Pitch::asText)
        )
    }

    @Test
    fun sauta_differsFromSilabaByRaisedFourthDegree() {
        val presets = TraditionalPresets.presetsForStringCount(21)
        val silaba = presets.first { preset -> preset.id == "silaba_21" }
        val sauta = presets.first { preset -> preset.id == "sauta_21" }

        val differingIndexes = silaba.openPitches.indices.filter { index ->
            silaba.openPitches[index] != sauta.openPitches[index]
        }

        assertEquals(listOf(7, 14), differingIndexes)
        assertEquals("A#3", silaba.openPitches[7].asText())
        assertEquals("B3", sauta.openPitches[7].asText())
        assertEquals("A#4", silaba.openPitches[14].asText())
        assertEquals("B4", sauta.openPitches[14].asText())
    }

    @Test
    fun string22_addsLowFourthInBassRegister() {
        val silaba22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "silaba_22" }
        val sauta22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "sauta_22" }

        assertEquals("F2", silaba22.openPitches.first().asText())
        assertEquals("A#2", silaba22.openPitches[1].asText())
        assertEquals("C3", silaba22.openPitches[2].asText())

        assertEquals("F2", sauta22.openPitches.first().asText())
        assertEquals("B2", sauta22.openPitches[1].asText())
        assertEquals("C3", sauta22.openPitches[2].asText())
    }

    @Test
    fun presets_encodeMicrotonalIntonationOffsets() {
        val hardino21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "hardino_21" }
        val sauta21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "sauta_21" }
        val silaba21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }

        assertTrue(hardino21.openIntonationCents.any { cents -> cents != 0.0 })
        assertEquals(hardino21.openPitches.size, hardino21.closedIntonationCents.size)
        assertNotEquals(hardino21.openIntonationCents, silaba21.openIntonationCents)

        assertEquals(-15.0, silaba21.openIntonationCents[3], 0.0) // E3
        assertEquals(-15.0, silaba21.openIntonationCents[6], 0.0) // A3
        assertEquals(0.0, silaba21.closedIntonationCents[6], 0.0) // A#3

        assertEquals(-15.0, hardino21.openIntonationCents[5], 0.0) // G3
        assertEquals(5.0, hardino21.openIntonationCents[6], 0.0) // A3
        assertEquals(5.0, hardino21.openIntonationCents[3], 0.0) // E3

        assertEquals(5.0, sauta21.openIntonationCents[7], 0.0) // B3
        assertEquals(-15.0, sauta21.openIntonationCents[5], 0.0) // G3
    }
}

