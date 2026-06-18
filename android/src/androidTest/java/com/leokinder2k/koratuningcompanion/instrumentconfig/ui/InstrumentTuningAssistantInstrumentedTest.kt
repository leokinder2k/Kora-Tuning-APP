package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerPerformanceMode
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerUiState
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme
import org.junit.Rule
import org.junit.Test

class InstrumentTuningAssistantInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun preciseInTuneReading_advancesToNextLeftString() {
        setAssistantContent(detectedPitch = "F2")

        composeRule.onNodeWithText("Target: F2 (+0.00 c)")
            .assertIsDisplayed()

        composeRule.waitForText("Target: C3 (+0.00 c)")
    }

    @Test
    fun preciseInTuneReading_advancesToNextRightString() {
        setAssistantContent(detectedPitch = "Bb2")

        composeRule.onNodeWithText("R0 A#2")
            .performClick()
        composeRule.onNodeWithText("Target: A#2 (+0.00 c)")
            .assertIsDisplayed()

        composeRule.waitForText("Target: F3 (+0.00 c)")
    }

    private fun setAssistantContent(detectedPitch: String) {
        val detectedFrequencyHz = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = requireNotNull(Pitch.parse(detectedPitch)),
            centsOffset = 0.0
        )
        composeRule.setContent {
            KoraTuningSystemTheme {
                InstrumentConfigurationScreen(
                    uiState = silaba22UiState(),
                    tunerUiState = LiveTunerUiState(
                        hasAudioPermission = true,
                        performanceMode = LiveTunerPerformanceMode.PRECISION,
                        isListening = true,
                        detectedFrequencyHz = detectedFrequencyHz,
                        confidence = 1.0,
                        rms = 0.2,
                        errorMessage = null
                    ),
                    onStringCountSelected = {},
                    onTuningModeSelected = {},
                    onHomeLeverPositionSelected = {},
                    onRootNoteSelected = {},
                    onOpenPitchChanged = { _, _ -> },
                    onOpenIntonationChanged = { _, _ -> },
                    onClosedIntonationChanged = { _, _ -> },
                    onLoadStarterProfile = {},
                    onSaveProfile = {},
                    onSetCurrentAsHome = {},
                    onRestoreToHome = {},
                    onAudioPermissionChanged = {},
                    onStartListening = {},
                    onStopListening = {},
                    enharmonicPreference = EnharmonicPreference.SHARPS,
                    isMuted = false,
                    isActive = true
                )
            }
        }
    }

    private fun silaba22UiState(): InstrumentConfigurationUiState {
        val openPitches = StarterInstrumentProfiles.openPitchTexts(22)
        return InstrumentConfigurationUiState(
            stringCount = 22,
            tuningMode = KoraTuningMode.LEVERED,
            rootNote = NoteName.F,
            presetOptions = listOf(
                InstrumentPresetOptionUiState(
                    id = "silaba_22",
                    displayName = "Silaba / Tomora Ba"
                )
            ),
            selectedPresetId = "silaba_22",
            lowestLeftPitchInput = "F2",
            autoCalibrateEnabled = true,
            rows = openPitches.mapIndexed { index, pitch ->
                InstrumentStringRowUiState(
                    stringNumber = index + 1,
                    openPitchInput = pitch,
                    closedPitch = null,
                    inputError = null,
                    openIntonationInput = "0.0",
                    closedIntonationInput = "0.0",
                    openIntonationError = null,
                    closedIntonationError = null
                )
            },
            canSave = true,
            statusMessage = null,
            basePitchInputs = openPitches,
            basePitchErrors = List(openPitches.size) { null },
            homeLeverPosition = HomeLeverPosition.OPEN
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasText(text))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        onNodeWithText(text).assertIsDisplayed()
    }
}
