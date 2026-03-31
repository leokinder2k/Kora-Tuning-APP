package com.leokinder2k.koratuningcompanion.scaleengine.orchestration

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPresets
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredTuningLlmOrchestratorTest {

    private val engine = ScaleCalculationEngine()
    private val orchestrator = StructuredTuningLlmOrchestrator()

    @Test
    fun transposedInstrumentKey_planClosesAllLeversWithoutPegMoves() {
        val silabaProfile = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }
        val instrumentInE = InstrumentProfile(
            stringCount = 21,
            openPitches = silabaProfile.openPitches.map { pitch -> pitch.plusSemitones(-1) },
            openIntonationCents = silabaProfile.openIntonationCents,
            closedIntonationCents = silabaProfile.closedIntonationCents,
            rootNote = NoteName.E,
            basePitches = silabaProfile.openPitches
        )

        val plan = orchestrator.orchestrate(
            engine.calculate(
                ScaleCalculationRequest(
                    instrumentProfile = instrumentInE,
                    scaleType = ScaleType.MAJOR,
                    rootNote = NoteName.F
                )
            )
        )

        assertEquals(NoteName.E, plan.instrumentKey)
        assertEquals(NoteName.F, plan.requestedRoot)
        assertEquals(1, plan.rootDeltaFromInstrumentKeySemitones)
        assertEquals(
            com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition.OPEN,
            plan.homeLeverPosition
        )
        assertEquals(0, plan.pegRaiseCount)
        assertEquals(0, plan.pegLowerCount)
        assertEquals(21, plan.pegKeepCount)
        assertEquals(21, plan.leverCloseCount)
        assertEquals(21, plan.changedStringCount)
        assertTrue(plan.stringPlans.all { stringPlan ->
            stringPlan.pegDeltaSemitones == 0 &&
                stringPlan.leverAction == TuningLeverAction.CLOSE &&
                stringPlan.needsLeverChange
        })
    }

    @Test
    fun pegRetunePlanTracksSignedSemitoneAgainstInstrumentKeyOpenPitch() {
        val plan = orchestrator.orchestrate(
            engine.calculate(
                ScaleCalculationRequest(
                    instrumentProfile = profileWithPitches(
                        overrides = mapOf(
                            1 to "C2",
                            2 to "F2"
                        ),
                        fill = "C4"
                    ),
                    scaleType = ScaleType.MAJOR_PENTATONIC
                )
            )
        )

        val stringTwo = plan.stringPlans.first { stringPlan -> stringPlan.stringNumber == 2 }
        assertEquals(Pitch(NoteName.F, 2), stringTwo.referenceOpenPitch)
        assertEquals(Pitch(NoteName.E, 2), stringTwo.targetOpenPitch)
        assertEquals(-1, stringTwo.pegDeltaSemitones)
        assertEquals(TuningLeverAction.KEEP, stringTwo.leverAction)
        assertTrue(stringTwo.needsPegChange)
    }

    @Test
    fun instrumentInC_toRootD_reportsTwoSemitoneRootShiftAndPerStringPlan() {
        val silabaProfile = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }
        val instrumentInC = InstrumentProfile(
            stringCount = 21,
            openPitches = silabaProfile.openPitches.map { pitch -> pitch.plusSemitones(-5) },
            openIntonationCents = silabaProfile.openIntonationCents,
            closedIntonationCents = silabaProfile.closedIntonationCents,
            rootNote = NoteName.C
        )

        val plan = orchestrator.orchestrate(
            engine.calculate(
                ScaleCalculationRequest(
                    instrumentProfile = instrumentInC,
                    scaleType = ScaleType.MAJOR,
                    rootNote = NoteName.D
                )
            )
        )

        assertEquals(2, plan.rootDeltaFromInstrumentKeySemitones)
        assertEquals(21, plan.pegRaiseCount)
        assertEquals(0, plan.pegLowerCount)
        assertEquals(21, plan.leverCloseCount)
        assertTrue(plan.stringPlans.all { stringPlan ->
            stringPlan.pegDeltaSemitones == 1 &&
                stringPlan.leverAction == TuningLeverAction.CLOSE
        })
    }

    @Test
    fun instrumentInC_right1RootD_reportsVisibleChangesAcrossPlan() {
        val silabaProfile = TraditionalPresets.presetsForStringCount(21)
            .first { preset -> preset.id == "silaba_21" }
        val instrumentInC = InstrumentProfile(
            stringCount = 21,
            openPitches = silabaProfile.openPitches.map { pitch -> pitch.plusSemitones(-5) },
            openIntonationCents = silabaProfile.openIntonationCents,
            closedIntonationCents = silabaProfile.closedIntonationCents,
            rootNote = NoteName.C
        )

        val plan = orchestrator.orchestrate(
            engine.calculate(
                ScaleCalculationRequest(
                    instrumentProfile = instrumentInC,
                    scaleType = ScaleType.MAJOR,
                    rootNote = NoteName.D,
                    scaleRootReference = ScaleRootReference.RIGHT_1
                )
            )
        )

        assertEquals(21, plan.changedStringCount)
        assertEquals(21, plan.leverCloseCount)
        assertEquals(21, plan.pegRaiseCount)
        assertTrue(plan.stringPlans.all { stringPlan ->
            stringPlan.pegDeltaSemitones == 1 &&
                stringPlan.leverAction == TuningLeverAction.CLOSE &&
                stringPlan.needsPegChange &&
                stringPlan.needsLeverChange
        })
    }

    private fun profileWithPitches(
        overrides: Map<Int, String>,
        fill: String,
        stringCount: Int = 21,
        rootNote: NoteName = NoteName.C
    ): InstrumentProfile {
        val openPitchTexts = MutableList(stringCount) { fill }
        overrides.forEach { (stringNumber, value) ->
            openPitchTexts[stringNumber - 1] = value
        }

        val pitches = openPitchTexts.map { input ->
            requireNotNull(Pitch.parse(input)) {
                "Invalid test pitch: $input"
            }
        }
        return InstrumentProfile(
            stringCount = stringCount,
            openPitches = pitches,
            rootNote = rootNote
        )
    }
}
