package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.TuningLeverAction
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.TuningOrchestrationPlan
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.TuningStringPlan

@Composable
internal fun TuningOrchestrationCard(
    plan: TuningOrchestrationPlan,
    modifier: Modifier = Modifier,
    maxInstructionLines: Int = 5
) {
    val changedPlans = plan.stringPlans.filter { stringPlan ->
        stringPlan.needsPegChange || stringPlan.needsLeverChange
    }
    val visiblePlans = changedPlans.take(maxInstructionLines)
    val hiddenChangeCount = (changedPlans.size - visiblePlans.size).coerceAtLeast(0)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.scale_engine_orchestrator_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.scale_engine_orchestrator_reference,
                    plan.instrumentKey.symbol,
                    plan.requestedRoot.symbol,
                    scaleTypeLabel(plan.scaleType),
                    scaleRootReferenceLabel(plan.rootReference)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.scale_engine_orchestrator_root_shift,
                    signedSemitone(plan.rootDeltaFromInstrumentKeySemitones)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.scale_engine_orchestrator_peg_summary,
                    plan.pegRaiseCount,
                    plan.pegLowerCount,
                    plan.pegKeepCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (plan.tuningMode == com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode.LEVERED) {
                Text(
                    text = stringResource(
                        R.string.scale_engine_orchestrator_lever_summary,
                        plan.leverOpenCount,
                        plan.leverCloseCount,
                        plan.leverKeepCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = stringResource(
                    R.string.scale_engine_orchestrator_changed_strings,
                    plan.changedStringCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.scale_engine_orchestrator_note),
                style = MaterialTheme.typography.bodySmall
            )

            if (visiblePlans.isEmpty()) {
                Text(
                    text = stringResource(R.string.scale_engine_orchestrator_no_changes),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                visiblePlans.forEach { stringPlan ->
                    Text(
                        text = tuningInstructionLabel(stringPlan),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (hiddenChangeCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.scale_engine_orchestrator_more_strings,
                            hiddenChangeCount
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun tuningInstructionLabel(plan: TuningStringPlan): String {
    val leverActionLabel = when (plan.leverAction) {
        TuningLeverAction.KEEP -> stringResource(R.string.scale_engine_orchestrator_lever_keep)
        TuningLeverAction.OPEN -> stringResource(R.string.scale_engine_orchestrator_lever_open)
        TuningLeverAction.CLOSE -> stringResource(R.string.scale_engine_orchestrator_lever_close)
        TuningLeverAction.NOT_APPLICABLE -> ""
    }
    val pegLabel = signedSemitone(plan.pegDeltaSemitones)
    return when {
        plan.needsPegChange && plan.needsLeverChange -> stringResource(
            R.string.scale_engine_orchestrator_line_both,
            plan.roleLabel,
            plan.stringNumber,
            pegLabel,
            leverActionLabel,
            plan.soundingPitch.asText()
        )
        plan.needsPegChange -> stringResource(
            R.string.scale_engine_orchestrator_line_peg,
            plan.roleLabel,
            plan.stringNumber,
            pegLabel,
            plan.soundingPitch.asText()
        )
        plan.needsLeverChange -> stringResource(
            R.string.scale_engine_orchestrator_line_lever,
            plan.roleLabel,
            plan.stringNumber,
            leverActionLabel,
            plan.soundingPitch.asText()
        )
        else -> stringResource(
            R.string.scale_engine_orchestrator_line_keep,
            plan.roleLabel,
            plan.stringNumber,
            plan.soundingPitch.asText()
        )
    }
}

internal fun compactTuningActionLabel(
    plan: TuningStringPlan?,
    manualSemitoneShift: Int = 0,
    showLeverInfo: Boolean
): String? {
    val totalPegDelta = (plan?.pegDeltaSemitones ?: 0) + manualSemitoneShift
    val pegLabel = when {
        totalPegDelta > 0 -> "+$totalPegDelta"
        totalPegDelta < 0 -> totalPegDelta.toString()
        else -> ""
    }
    val leverLabel = if (showLeverInfo && plan?.leverAction == TuningLeverAction.CLOSE) {
        "L"
    } else {
        ""
    }
    return listOf(pegLabel, leverLabel)
        .filter { label -> label.isNotBlank() }
        .joinToString(" ")
        .ifBlank { null }
}

@Composable
private fun scaleRootReferenceLabel(reference: ScaleRootReference): String {
    return when (reference) {
        ScaleRootReference.LEFT_1 -> stringResource(R.string.scale_root_reference_left_1)
        ScaleRootReference.LEFT_2 -> stringResource(R.string.scale_root_reference_left_2)
        ScaleRootReference.LEFT_3 -> stringResource(R.string.scale_root_reference_left_3)
        ScaleRootReference.LEFT_4 -> stringResource(R.string.scale_root_reference_left_4)
        ScaleRootReference.RIGHT_1 -> stringResource(R.string.scale_root_reference_right_1)
    }
}

private fun signedSemitone(value: Int): String {
    return if (value >= 0) "+$value" else value.toString()
}
