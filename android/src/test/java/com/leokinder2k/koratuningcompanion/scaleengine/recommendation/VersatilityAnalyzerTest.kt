package com.leokinder2k.koratuningcompanion.scaleengine.recommendation

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersatilityAnalyzerTest {

    private val analyzer = VersatilityAnalyzer(ScaleCalculationEngine())

    @Test
    fun analyze_forNaturalE_returnsRankedTuningsAndFifteenRouteSteps() {
        val analysis = analyzer.analyze(
            stringCount = 21,
            instrumentKey = NoteName.E
        )

        assertEquals(NoteName.E, analysis.instrumentKey)
        assertEquals(12, analysis.evaluatedRoots)
        assertEquals(VersatilityAnalyzer.PRACTICAL_SCALE_TYPES.size, analysis.evaluatedScaleTypes)
        assertEquals(5, analysis.evaluatedReferences)
        assertEquals(4, analysis.tuningSummaries.size)
        assertEquals(15, analysis.recommendedRoute.size)
        assertNotNull(analysis.bestTuning)
        assertTrue(analysis.tuningSummaries.all { summary ->
            summary.instrumentKey == NoteName.E &&
                summary.reachableStateCount > 0 &&
                summary.exampleStates.isNotEmpty()
        })
        assertTrue(analysis.recommendedRoute.all { step ->
            step.state.instrumentKey == NoteName.E
        })
        assertTrue(analysis.recommendedRoute.first().transitionType == RouteTransitionType.FROM_NATURAL_OPEN)
    }

    @Test
    fun sameTuningRouteStep_reportsExactLeverDelta() {
        val analysis = analyzer.analyze(
            stringCount = 21,
            instrumentKey = NoteName.E
        )

        val sameTuningStep = analysis.recommendedRoute.zipWithNext()
            .firstOrNull { (previous, current) ->
                previous.state.tuningId == current.state.tuningId &&
                    current.transitionType == RouteTransitionType.FROM_PREVIOUS_STATE
            }?.second

        assertNotNull(sameTuningStep)
        sameTuningStep!!
        assertEquals(
            sameTuningStep.leversToOpen.size + sameTuningStep.leversToClose.size,
            sameTuningStep.leverChangesFromPrevious
        )
    }
}
