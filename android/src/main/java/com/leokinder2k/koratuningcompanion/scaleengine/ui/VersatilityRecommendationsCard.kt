package com.leokinder2k.koratuningcompanion.scaleengine.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.LeverOnlyReachableState
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.LeverRouteStep
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.RouteTransitionType
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.TuningVersatilitySummary
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.VersatilityAnalysis

@Composable
internal fun VersatilityRecommendationsCard(
    analysis: VersatilityAnalysis,
    modifier: Modifier = Modifier
) {
    val bestTuning = analysis.bestTuning ?: return
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.versatility_title,
                    analysis.instrumentKey.symbol
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.versatility_reference_note,
                    analysis.instrumentKey.symbol
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.versatility_scope,
                    analysis.evaluatedRoots,
                    analysis.evaluatedScaleTypes,
                    analysis.evaluatedReferences
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.versatility_best_example,
                    bestTuning.tuningName,
                    bestTuning.instrumentKey.symbol
                ),
                style = MaterialTheme.typography.bodySmall
            )

            analysis.tuningSummaries.forEach { summary ->
                Text(
                    text = tuningSummaryLabel(context, summary),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = stringResource(R.string.versatility_route_title),
                style = MaterialTheme.typography.titleSmall
            )
            analysis.recommendedRoute.forEach { step ->
                Text(
                    text = routeStepLabel(context, step),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun tuningSummaryLabel(
    context: Context,
    summary: TuningVersatilitySummary
): String {
    return context.getString(
        R.string.versatility_tuning_line,
        summary.rank,
        summary.tuningName,
        summary.reachableStateCount,
        summary.distinctRoots,
        summary.distinctScaleTypes,
        summary.distinctReferences,
        summary.exampleStates.joinToString { state ->
            stateLabel(context, state)
        }
    )
}

private fun routeStepLabel(
    context: Context,
    step: LeverRouteStep
): String {
    val transitionLabel = when (step.transitionType) {
        RouteTransitionType.FROM_NATURAL_OPEN -> context.getString(
            R.string.versatility_route_from_natural,
            leverListLabel(context, step.leversToClose)
        )
        RouteTransitionType.FROM_PREVIOUS_STATE -> context.getString(
            R.string.versatility_route_from_previous,
            step.leverChangesFromPrevious,
            leverListLabel(context, step.leversToOpen),
            leverListLabel(context, step.leversToClose)
        )
        RouteTransitionType.SWITCH_TUNING_RESET -> context.getString(
            R.string.versatility_route_switch_tuning,
            step.state.tuningName,
            leverListLabel(context, step.leversToClose)
        )
    }
    return context.getString(
        R.string.versatility_route_line,
        step.stepNumber,
        step.state.tuningName,
        step.state.rootNote.symbol,
        scaleTypeLabelText(context, step.state.scaleType),
        rootReferenceLabel(context, step.state.rootReference),
        transitionLabel
    )
}

private fun stateLabel(
    context: Context,
    state: LeverOnlyReachableState
): String {
    return context.getString(
        R.string.versatility_target_label,
        state.rootNote.symbol,
        scaleTypeLabelText(context, state.scaleType),
        rootReferenceLabel(context, state.rootReference)
    )
}

private fun leverListLabel(context: Context, levers: List<String>): String {
    return if (levers.isEmpty()) {
        context.getString(R.string.versatility_lever_none)
    } else {
        levers.joinToString(", ")
    }
}

private fun rootReferenceLabel(context: Context, reference: ScaleRootReference): String {
    return when (reference) {
        ScaleRootReference.LEFT_1 -> context.getString(R.string.scale_root_reference_left_1)
        ScaleRootReference.LEFT_2 -> context.getString(R.string.scale_root_reference_left_2)
        ScaleRootReference.LEFT_3 -> context.getString(R.string.scale_root_reference_left_3)
        ScaleRootReference.LEFT_4 -> context.getString(R.string.scale_root_reference_left_4)
        ScaleRootReference.RIGHT_1 -> context.getString(R.string.scale_root_reference_right_1)
    }
}

private fun scaleTypeLabelText(context: Context, scaleType: ScaleType): String {
    return when (scaleType) {
        ScaleType.MAJOR -> context.getString(R.string.scale_type_major)
        ScaleType.NATURAL_MINOR -> context.getString(R.string.scale_type_natural_minor)
        ScaleType.HARMONIC_MINOR -> context.getString(R.string.scale_type_harmonic_minor)
        ScaleType.MELODIC_MINOR -> context.getString(R.string.scale_type_melodic_minor)
        ScaleType.IONIAN -> context.getString(R.string.scale_type_ionian)
        ScaleType.DORIAN -> context.getString(R.string.scale_type_dorian)
        ScaleType.PHRYGIAN -> context.getString(R.string.scale_type_phrygian)
        ScaleType.LYDIAN -> context.getString(R.string.scale_type_lydian)
        ScaleType.MIXOLYDIAN -> context.getString(R.string.scale_type_mixolydian)
        ScaleType.AEOLIAN -> context.getString(R.string.scale_type_aeolian)
        ScaleType.LOCRIAN -> context.getString(R.string.scale_type_locrian)
        ScaleType.MAJOR_PENTATONIC -> context.getString(R.string.scale_type_major_pentatonic)
        ScaleType.MINOR_PENTATONIC -> context.getString(R.string.scale_type_minor_pentatonic)
        ScaleType.MAJOR_HEXATONIC -> context.getString(R.string.scale_type_major_hexatonic)
        ScaleType.MINOR_HEXATONIC -> context.getString(R.string.scale_type_minor_hexatonic)
        ScaleType.WHOLE_TONE -> context.getString(R.string.scale_type_whole_tone)
        ScaleType.MAJOR_BLUES -> context.getString(R.string.scale_type_major_blues)
        ScaleType.MINOR_BLUES -> context.getString(R.string.scale_type_minor_blues)
        ScaleType.BEEBOP_MAJOR -> context.getString(R.string.scale_type_beebop_major)
        ScaleType.BEEBOP_DOMINANT -> context.getString(R.string.scale_type_beebop_dominant)
        ScaleType.BEEBOP_DORIAN -> context.getString(R.string.scale_type_beebop_dorian)
        ScaleType.DIMINISHED_WHOLE_HALF -> context.getString(R.string.scale_type_diminished_whole_half)
        ScaleType.DIMINISHED_HALF_WHOLE -> context.getString(R.string.scale_type_diminished_half_whole)
        ScaleType.CHROMATIC -> context.getString(R.string.scale_type_chromatic)
    }
}
