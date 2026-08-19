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
    fun visibleAfterToggleAddsTabInNavigationOrder() {
        val visible = NavigationTabSettings.visibleAfterToggle(
            orderNames = defaults,
            currentVisibleNames = listOf("SETUP", "PRESETS"),
            toggledName = "SYNTH",
            isVisible = true
        )

        assertEquals(listOf("SETUP", "SYNTH", "PRESETS"), visible)
    }

    @Test
    fun visibleAfterToggleKeepsLastVisibleTab() {
        val visible = NavigationTabSettings.visibleAfterToggle(
            orderNames = defaults,
            currentVisibleNames = listOf("SYNTH"),
            toggledName = "SYNTH",
            isVisible = false
        )

        assertEquals(listOf("SYNTH"), visible)
    }

    @Test
    fun selectedAfterVisibilityChangeFallsBackToFirstVisibleTab() {
        assertEquals(
            "SCALE",
            NavigationTabSettings.selectedAfterVisibilityChange(
                selectedName = "SYNTH",
                visibleNames = listOf("SCALE", "PRESETS")
            )
        )
        assertEquals(
            "SYNTH",
            NavigationTabSettings.selectedAfterVisibilityChange(
                selectedName = "SYNTH",
                visibleNames = listOf("SYNTH", "PRESETS")
            )
        )
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
