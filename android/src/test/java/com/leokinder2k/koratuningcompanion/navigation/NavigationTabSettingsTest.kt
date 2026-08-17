package com.leokinder2k.koratuningcompanion.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTabSettingsTest {
    private val defaults = listOf("SETUP", "SCALE", "SYNTH", "PRESETS")

    @Test
    fun parseOrderIgnoresUnknownDuplicatesAndAppendsMissingDefaults() {
        val order = NavigationTabSettings.parseOrder(
            savedNames = "SYNTH,BOGUS,SCALE,SYNTH",
            defaultNames = defaults
        )

        assertEquals(listOf("SYNTH", "SCALE", "SETUP", "PRESETS"), order)
    }

    @Test
    fun parseVisibleDefaultsToAllTabsWhenNothingWasSaved() {
        assertEquals(defaults, NavigationTabSettings.parseVisible(defaults, ""))
    }

    @Test
    fun parseVisibleKeepsAtLeastOneValidTab() {
        assertEquals(listOf("SETUP"), NavigationTabSettings.parseVisible(defaults, "MISSING"))
    }

    @Test
    fun moveByReordersAndClampsAtEdges() {
        assertEquals(
            listOf("SETUP", "SYNTH", "SCALE", "PRESETS"),
            NavigationTabSettings.moveBy(defaults, "SYNTH", -1)
        )
        assertEquals(defaults, NavigationTabSettings.moveBy(defaults, "SETUP", -1))
        assertEquals(defaults, NavigationTabSettings.moveBy(defaults, "PRESETS", 1))
    }
}
