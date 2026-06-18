package com.leokinder2k.koratuningcompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsInstrumentConfigurationScreen() {
        composeRule.onNodeWithText("Instrument Configuration")
            .assertIsDisplayed()
    }

    @Test
    fun presetsTab_opensTraditionalPresetsScreen() {
        composeRule.onNodeWithText("Presets")
            .performClick()

        composeRule.onNodeWithText("Traditional Presets")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Load Selected Preset")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tunerTab_showsPerformanceModeControls() {
        composeRule.onNodeWithText("Tuner")
            .performClick()

        composeRule.onNodeWithText("Live Tuner")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Realtime")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Precision")
            .assertIsDisplayed()
    }

    @Test
    fun defaultSetup_showsSilaba22TuningAndEnharmonicLabels() {
        composeRule.onNodeWithText("22 strings")
            .assertIsDisplayed()
            .performClick()
        assertVisibleAfterScroll("Load Starter Profile")
        composeRule.onNodeWithText("Load Starter Profile")
            .performClick()

        assertVisibleAfterScroll("22 strings")
        assertVisibleAfterScroll("Source: Silaba / Tomora Ba preset, based on low-left F2")
        assertVisibleAfterScroll("Target: F2 (+0.00 c)")

        listOf(
            "L1 F2",
            "L2 C3",
            "L3 D3",
            "L4 E3",
            "L5 G3",
            "L6 A#3",
            "L7 D4",
            "L8 F4",
            "L9 A4",
            "L10 C5",
            "L11 E5",
            "R0 A#2",
            "R1 F3",
            "R2 A3",
            "R3 C4",
            "R4 E4",
            "R5 G4",
            "R6 A#4",
            "R7 D5",
            "R8 F5",
            "R9 G5",
            "R10 A5"
        ).forEach(::assertExistsAfterScroll)

        composeRule.onNodeWithTag("enharmonic-toggle")
            .performClick()
        composeRule.onNodeWithTag("enharmonic-toggle")
            .assertTextContains("Bb")

        listOf(
            "L6 Bb3",
            "R0 Bb2",
            "R6 Bb4"
        ).forEach(::assertExistsAfterScroll)
    }

    private fun assertVisibleAfterScroll(text: String) {
        composeRule.onNodeWithTag("instrument-config-list")
            .performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text)
            .assertIsDisplayed()
    }

    private fun assertExistsAfterScroll(text: String) {
        composeRule.onNodeWithTag("instrument-config-list")
            .performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text)
            .assertExists()
    }
}

