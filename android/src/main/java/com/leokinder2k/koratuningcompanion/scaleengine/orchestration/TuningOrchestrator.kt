package com.leokinder2k.koratuningcompanion.scaleengine.orchestration

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType

enum class TuningLeverAction {
    KEEP,
    OPEN,
    CLOSE,
    NOT_APPLICABLE
}

data class TuningStringPlan(
    val stringNumber: Int,
    val roleLabel: String,
    val referenceOpenPitch: Pitch,
    val targetOpenPitch: Pitch,
    val soundingPitch: Pitch,
    val pegDeltaSemitones: Int,
    val pegDeltaFromHomeSemitones: Int,
    val leverState: LeverState?,
    val leverAction: TuningLeverAction,
    val needsPegChange: Boolean,
    val needsLeverChange: Boolean
)

data class TuningOrchestrationPlan(
    val plannerId: String,
    val instrumentKey: NoteName,
    val requestedRoot: NoteName,
    val rootDeltaFromInstrumentKeySemitones: Int,
    val scaleType: ScaleType,
    val rootReference: ScaleRootReference,
    val tuningMode: KoraTuningMode,
    val homeLeverPosition: HomeLeverPosition?,
    val changedStringCount: Int,
    val pegRaiseCount: Int,
    val pegLowerCount: Int,
    val pegKeepCount: Int,
    val leverOpenCount: Int,
    val leverCloseCount: Int,
    val leverKeepCount: Int,
    val stringPlans: List<TuningStringPlan>
)

interface TuningOrchestrator {
    fun orchestrate(result: ScaleCalculationResult): TuningOrchestrationPlan
}

class StructuredTuningOrchestrator : TuningOrchestrator {

    override fun orchestrate(result: ScaleCalculationResult): TuningOrchestrationPlan {
        val request = result.request
        val profile = request.instrumentProfile
        val tuningMode = profile.tuningMode
        val homeLeverPosition = HomeLeverPosition.OPEN.takeIf { tuningMode == KoraTuningMode.LEVERED }

        val stringPlans = result.pegCorrectTable.map { row ->
            val leverAction = when {
                tuningMode != KoraTuningMode.LEVERED -> TuningLeverAction.NOT_APPLICABLE
                row.selectedLeverState == LeverState.CLOSED -> TuningLeverAction.CLOSE
                else -> TuningLeverAction.KEEP
            }

            TuningStringPlan(
                stringNumber = row.stringNumber,
                roleLabel = row.role.asLabel(),
                referenceOpenPitch = row.originalOpenPitch,
                targetOpenPitch = row.retunedOpenPitch,
                soundingPitch = row.selectedPitch,
                pegDeltaSemitones = row.pegRetuneSemitones,
                pegDeltaFromHomeSemitones = row.fromBaseSemitones,
                leverState = row.selectedLeverState.takeIf { tuningMode == KoraTuningMode.LEVERED },
                leverAction = leverAction,
                needsPegChange = row.pegRetuneSemitones != 0,
                needsLeverChange = tuningMode == KoraTuningMode.LEVERED &&
                    row.selectedLeverState == LeverState.CLOSED
            )
        }

        return TuningOrchestrationPlan(
            plannerId = PLANNER_ID,
            instrumentKey = profile.rootNote,
            requestedRoot = request.rootNote,
            rootDeltaFromInstrumentKeySemitones = signedSemitoneDelta(
                from = profile.rootNote,
                to = request.rootNote
            ),
            scaleType = request.scaleType,
            rootReference = request.scaleRootReference,
            tuningMode = tuningMode,
            homeLeverPosition = homeLeverPosition,
            changedStringCount = stringPlans.count { plan -> plan.needsPegChange || plan.needsLeverChange },
            pegRaiseCount = stringPlans.count { plan -> plan.pegDeltaSemitones > 0 },
            pegLowerCount = stringPlans.count { plan -> plan.pegDeltaSemitones < 0 },
            pegKeepCount = stringPlans.count { plan -> plan.pegDeltaSemitones == 0 },
            leverOpenCount = stringPlans.count { plan -> plan.leverAction == TuningLeverAction.OPEN },
            leverCloseCount = stringPlans.count { plan -> plan.leverAction == TuningLeverAction.CLOSE },
            leverKeepCount = stringPlans.count { plan -> plan.leverAction == TuningLeverAction.KEEP },
            stringPlans = stringPlans
        )
    }

    companion object {
        const val PLANNER_ID = "structured-tuning-plan-v1"

        private fun signedSemitoneDelta(from: NoteName, to: NoteName): Int {
            val rawDelta = to.semitone - from.semitone
            val normalized = rawDelta.mod(12)
            return if (normalized > 6) normalized - 12 else normalized
        }
    }
}
