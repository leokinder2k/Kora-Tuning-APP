package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentTuningAssistantReferenceTest {

    @Test
    fun referenceToneUsesDisplayedPresetTargetFrequency() {
        val targetFrequency = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = Pitch(NoteName.F, 2),
            centsOffset = 0.0
        )

        assertEquals(
            targetFrequency,
            instrumentAssistantReferenceFrequencyHz(targetFrequency),
            0.0
        )
    }
}
