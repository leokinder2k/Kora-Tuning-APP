package com.leokinder2k.koratuningcompanion.livetuner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningFeedbackTest {

    @Test
    fun classify_returnsFlat_whenBelowNegativeThreshold() {
        val state = TuningFeedbackClassifier.classify(centsDeviation = -7.2)

        assertEquals(TuningFeedbackState.FLAT, state)
    }

    @Test
    fun classify_returnsInTune_withinThreshold() {
        val state = TuningFeedbackClassifier.classify(centsDeviation = 2.4)

        assertEquals(TuningFeedbackState.IN_TUNE, state)
    }

    @Test
    fun classify_returnsSharp_whenAboveThreshold() {
        val state = TuningFeedbackClassifier.classify(centsDeviation = 6.1)

        assertEquals(TuningFeedbackState.SHARP, state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun classify_requiresPositiveThreshold() {
        TuningFeedbackClassifier.classify(
            centsDeviation = 0.0,
            inTuneThresholdCents = 0.0
        )
    }
}

