package com.leokinder2k.koratuningcompanion.instrumentconfig.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraditionalPresetsTest {

    @Test
    fun presetsForStringCount_returnsNonEmptySetsFor21And22() {
        assertFalse(TraditionalPresets.presetsForStringCount(19).isEmpty())
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
            assertEquals(
                setOf(
                    "hardino_$stringCount",
                    "sauta_$stringCount",
                    "silaba_$stringCount",
                    "tomora_mesengo_$stringCount"
                ),
                ids.toSet()
            )
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
    fun tomoraMesengo21_matchesTraditionalConcertFLayout() {
        val tomoraMesengo = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "tomora_mesengo_21" }

        assertEquals("Tomora Mesengo", tomoraMesengo.displayName)
        assertEquals(
            listOf(
                "F2", "C3", "D3", "D#3",
                "F3", "G3", "G#3", "A#3", "C4", "D4", "D#4",
                "F4", "G4", "G#4", "A#4", "C5", "D5", "D#5",
                "F5", "G5", "G#5"
            ),
            tomoraMesengo.openPitches.map(Pitch::asText)
        )
    }

    @Test
    fun bridgeSideOrder_matchesTraditionalScaleDegreeLayout() {
        val silaba21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }
        val sauta21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "sauta_21" }
        val tomoraMesengo21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "tomora_mesengo_21" }
        val silaba22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "silaba_22" }
        val sauta22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "sauta_22" }
        val tomoraMesengo22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "tomora_mesengo_22" }

        assertEquals(
            listOf("F2", "C3", "D3", "E3", "G3", "A#3", "D4", "F4", "A4", "C5", "E5"),
            sidePitches(silaba21, KoraStringLayout.leftOrder(21))
        )
        assertEquals(
            listOf("F3", "A3", "C4", "E4", "G4", "A#4", "D5", "F5", "G5", "A5"),
            sidePitches(silaba21, KoraStringLayout.rightOrder(21))
        )

        assertEquals(
            listOf("F2", "C3", "D3", "E3", "G3", "B3", "D4", "F4", "A4", "C5", "E5"),
            sidePitches(sauta21, KoraStringLayout.leftOrder(21))
        )
        assertEquals(
            listOf("F3", "A3", "C4", "E4", "G4", "B4", "D5", "F5", "G5", "A5"),
            sidePitches(sauta21, KoraStringLayout.rightOrder(21))
        )
        assertEquals(
            listOf("F2", "C3", "D3", "D#3", "G3", "A#3", "D4", "F4", "G#4", "C5", "D#5"),
            sidePitches(tomoraMesengo21, KoraStringLayout.leftOrder(21))
        )
        assertEquals(
            listOf("F3", "G#3", "C4", "D#4", "G4", "A#4", "D5", "F5", "G5", "G#5"),
            sidePitches(tomoraMesengo21, KoraStringLayout.rightOrder(21))
        )

        assertEquals("A#2", sidePitches(silaba22, KoraStringLayout.rightOrder(22)).first())
        assertEquals("B2", sidePitches(sauta22, KoraStringLayout.rightOrder(22)).first())
        assertEquals("A#2", sidePitches(tomoraMesengo22, KoraStringLayout.rightOrder(22)).first())
    }

    @Test
    fun string22_addsLowFourthInBassRegister() {
        val silaba22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "silaba_22" }
        val sauta22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "sauta_22" }
        val tomoraMesengo22 = TraditionalPresets.presetsForStringCount(22)
            .first { preset -> preset.id == "tomora_mesengo_22" }

        assertEquals("F2", silaba22.openPitches.first().asText())
        assertEquals("A#2", silaba22.openPitches[1].asText())
        assertEquals("C3", silaba22.openPitches[2].asText())

        assertEquals("F2", sauta22.openPitches.first().asText())
        assertEquals("B2", sauta22.openPitches[1].asText())
        assertEquals("C3", sauta22.openPitches[2].asText())

        assertEquals("F2", tomoraMesengo22.openPitches.first().asText())
        assertEquals("A#2", tomoraMesengo22.openPitches[1].asText())
        assertEquals("C3", tomoraMesengo22.openPitches[2].asText())
    }

    @Test
    fun presets_encodeMicrotonalIntonationOffsets() {
        val hardino21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "hardino_21" }
        val sauta21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "sauta_21" }
        val silaba21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }
        val tomoraMesengo21 = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "tomora_mesengo_21" }

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

        assertEquals(30.0, tomoraMesengo21.openIntonationCents[5], 0.0) // G3
        assertEquals(25.0, tomoraMesengo21.openIntonationCents[6], 0.0) // G#3
        assertEquals(25.0, tomoraMesengo21.openIntonationCents[10], 0.0) // D#4
    }

    private fun sidePitches(
        preset: TraditionalPreset,
        stringNumbers: List<Int>
    ): List<String> {
        return stringNumbers.map { stringNumber ->
            preset.openPitches[stringNumber - 1].asText()
        }
    }
}

