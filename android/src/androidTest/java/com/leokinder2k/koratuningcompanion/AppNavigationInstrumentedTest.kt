package com.leokinder2k.koratuningcompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
}

