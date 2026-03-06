package com.leokinder2k.koratuningcompanion.livetuner.model

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TunerTargetMatcherTest {

    @Test
    fun pitchToFrequency_matchesConcertA() {
        val frequency = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = Pitch(NoteName.A, 4)
        )

        assertEquals(440.0, frequency, 0.0001)
    }

    @Test
    fun pitchToFrequency_appliesCentsOffset() {
        val base = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = Pitch(NoteName.A, 4)
        )
        val raised = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = Pitch(NoteName.A, 4),
            centsOffset = 10.0
        )

        assertEquals(440.0, base, 0.0001)
        assertEquals(442.5489, raised, 0.01)
    }

    @Test
    fun matchNearestTarget_returnsClosestTargetByCents() {
        val targets = listOf(
            TunerTarget(
                stringNumber = 1,
                roleLabel = "L1",
                targetPitch = Pitch(NoteName.G, 4),
                targetFrequencyHz = TunerTargetMatcher.pitchToFrequencyHz(Pitch(NoteName.G, 4)),
                requiredLeverState = LeverState.OPEN,
                pegRetuneSemitones = 0
            ),
            TunerTarget(
                stringNumber = 2,
                roleLabel = "R1",
                targetPitch = Pitch(NoteName.A, 4),
                targetFrequencyHz = TunerTargetMatcher.pitchToFrequencyHz(Pitch(NoteName.A, 4)),
                requiredLeverState = LeverState.CLOSED,
                pegRetuneSemitones = 1
            )
        )

        val match = TunerTargetMatcher.matchNearestTarget(441.0, targets)
        assertNotNull(match)
        assertEquals(2, match!!.target.stringNumber)
    }
}

