package com.leokinder2k.koratuningcompanion.scaleengine.recommendation

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPreset
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPresets
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import kotlin.math.abs

data class LeverOnlyReachableState(
    val tuningId: String,
    val tuningName: String,
    val instrumentKey: NoteName,
    val rootNote: NoteName,
    val scaleType: ScaleType,
    val rootReference: ScaleRootReference,
    val closedLevers: List<String>
) {
    val closedLeverCount: Int = closedLevers.size
}

data class TuningVersatilitySummary(
    val rank: Int,
    val tuningId: String,
    val tuningName: String,
    val instrumentKey: NoteName,
    val reachableStateCount: Int,
    val distinctRoots: Int,
    val distinctScaleTypes: Int,
    val distinctReferences: Int,
    val exampleStates: List<LeverOnlyReachableState>
)

enum class RouteTransitionType {
    FROM_NATURAL_OPEN,
    FROM_PREVIOUS_STATE,
    SWITCH_TUNING_RESET
}

data class LeverRouteStep(
    val stepNumber: Int,
    val state: LeverOnlyReachableState,
    val transitionType: RouteTransitionType,
    val leverChangesFromPrevious: Int,
    val leversToClose: List<String>,
    val leversToOpen: List<String>
)

data class CommonKeySuggestion(
    val rank: Int,
    val state: LeverOnlyReachableState,
    val directMethod: LeverRouteStep
)

data class CommonKeyRouteOption(
    val orderNumber: Int,
    val steps: List<LeverRouteStep>
)

data class VersatilityAnalysis(
    val instrumentKey: NoteName,
    val evaluatedRoots: Int,
    val evaluatedScaleTypes: Int,
    val evaluatedReferences: Int,
    val tuningSummaries: List<TuningVersatilitySummary>,
    val commonKeySuggestions: List<CommonKeySuggestion>,
    val commonKeyRouteOptions: List<CommonKeyRouteOption>,
    val recommendedRoute: List<LeverRouteStep>
) {
    val bestTuning: TuningVersatilitySummary? = tuningSummaries.firstOrNull()
}

class VersatilityAnalyzer(
    private val engine: ScaleCalculationEngine
) {
    private val cache = mutableMapOf<Pair<Int, NoteName>, VersatilityAnalysis>()

    fun analyze(stringCount: Int, instrumentKey: NoteName): VersatilityAnalysis {
        return cache.getOrPut(stringCount to instrumentKey) {
            buildAnalysis(
                stringCount = stringCount,
                instrumentKey = instrumentKey
            )
        }
    }

    private fun buildAnalysis(
        stringCount: Int,
        instrumentKey: NoteName
    ): VersatilityAnalysis {
        statesByTuning.clear()
        val rootReferences = supportedRootReferences(stringCount)
        val tuningEvaluations = TraditionalPresets.presetsForStringCount(stringCount)
            .map { preset ->
                analyzePresetInKey(
                    preset = preset,
                    instrumentKey = instrumentKey,
                    rootReferences = rootReferences
                )
            }
            .sortedWith(
                compareByDescending<TuningVersatilitySummary> { summary ->
                    summary.reachableStateCount
                }
                    .thenByDescending { summary -> summary.distinctRoots }
                    .thenByDescending { summary -> summary.distinctScaleTypes }
                    .thenBy { summary -> summary.tuningName }
            )
            .mapIndexed { index, summary ->
                summary.copy(rank = index + 1)
            }

        val commonKeySuggestions = buildCommonKeySuggestions(
            bestTuning = tuningEvaluations.firstOrNull()
        )
        val commonKeyRouteOptions = buildCommonKeyRouteOptions(commonKeySuggestions)
        val recommendedRoute = buildRecommendedRoute(tuningEvaluations)

        return VersatilityAnalysis(
            instrumentKey = instrumentKey,
            evaluatedRoots = NoteName.entries.size,
            evaluatedScaleTypes = PRACTICAL_SCALE_TYPES.size,
            evaluatedReferences = rootReferences.size,
            tuningSummaries = tuningEvaluations,
            commonKeySuggestions = commonKeySuggestions,
            commonKeyRouteOptions = commonKeyRouteOptions,
            recommendedRoute = recommendedRoute
        )
    }

    private fun analyzePresetInKey(
        preset: TraditionalPreset,
        instrumentKey: NoteName,
        rootReferences: List<ScaleRootReference>
    ): TuningVersatilitySummary {
        val profile = buildNaturalOpenProfile(
            preset = preset,
            instrumentKey = instrumentKey
        )
        val states = rootReferences.flatMap { rootReference ->
            NoteName.entries.flatMap { rootNote ->
                PRACTICAL_SCALE_TYPES.mapNotNull { scaleType ->
                    val result = engine.calculate(
                        ScaleCalculationRequest(
                            instrumentProfile = profile,
                            scaleType = scaleType,
                            rootNote = rootNote,
                            scaleRootReference = rootReference
                        )
                    )
                    if (result.pegCorrectTable.any { row -> row.pegRetuneRequired || row.pegRetuneSemitones != 0 }) {
                        null
                    } else {
                        LeverOnlyReachableState(
                            tuningId = preset.id,
                            tuningName = preset.displayName,
                            instrumentKey = instrumentKey,
                            rootNote = rootNote,
                            scaleType = scaleType,
                            rootReference = rootReference,
                            closedLevers = result.pegCorrectTable
                                .filter { row -> row.selectedLeverState == LeverState.CLOSED }
                                .map { row -> row.role.asLabel() }
                        )
                    }
                }
            }
        }
            .distinctBy { state ->
                listOf(
                    state.tuningId,
                    state.rootReference,
                    state.rootNote,
                    state.scaleType,
                    state.closedLevers.joinToString(",")
                )
            }
            .sortedWith(stateComparator(instrumentKey))

        return TuningVersatilitySummary(
            rank = 0,
            tuningId = preset.id,
            tuningName = preset.displayName,
            instrumentKey = instrumentKey,
            reachableStateCount = states.size,
            distinctRoots = states.map { state -> state.rootNote }.distinct().size,
            distinctScaleTypes = states.map { state -> state.scaleType }.distinct().size,
            distinctReferences = states.map { state -> state.rootReference }.distinct().size,
            exampleStates = states.take(EXAMPLE_STATE_COUNT)
        ).also { summary ->
            statesByTuning[summary.tuningId] = states
        }
    }

    private fun buildNaturalOpenProfile(
        preset: TraditionalPreset,
        instrumentKey: NoteName
    ): InstrumentProfile {
        val transposition = signedSemitoneDelta(
            from = NoteName.F,
            to = instrumentKey
        )
        val openPitches = preset.openPitches.map { pitch ->
            pitch.plusSemitones(transposition)
        }
        return InstrumentProfile(
            stringCount = preset.stringCount,
            tuningMode = KoraTuningMode.LEVERED,
            openPitches = openPitches,
            openIntonationCents = preset.openIntonationCents,
            closedIntonationCents = preset.closedIntonationCents,
            rootNote = instrumentKey,
            basePitches = openPitches,
            homeLeverPosition = HomeLeverPosition.OPEN
        )
    }

    private fun buildRecommendedRoute(
        tuningSummaries: List<TuningVersatilitySummary>
    ): List<LeverRouteStep> {
        if (tuningSummaries.isEmpty()) {
            return emptyList()
        }

        val selectedStates = selectRouteStates(tuningSummaries)
        if (selectedStates.isEmpty()) {
            return emptyList()
        }

        return buildRouteSteps(
            orderedStates = orderStatesGreedy(selectedStates)
        )
    }

    private fun selectRouteStates(
        tuningSummaries: List<TuningVersatilitySummary>
    ): List<LeverOnlyReachableState> {
        val selected = mutableListOf<LeverOnlyReachableState>()
        val statesBySummary = tuningSummaries.associateWith { summary ->
            statesByTuning[summary.tuningId].orEmpty()
        }

        tuningSummaries.forEach { summary ->
            val firstState = statesBySummary[summary].orEmpty().firstOrNull()
            if (firstState != null && firstState !in selected && selected.size < MAX_ROUTE_STEPS) {
                selected += firstState
            }
        }

        var cursor = 1
        while (selected.size < MAX_ROUTE_STEPS) {
            var addedInPass = false
            tuningSummaries.forEach { summary ->
                val state = statesBySummary[summary].orEmpty().getOrNull(cursor)
                if (state != null && state !in selected && selected.size < MAX_ROUTE_STEPS) {
                    selected += state
                    addedInPass = true
                }
            }
            if (!addedInPass) {
                break
            }
            cursor += 1
        }

        return selected
    }

    private fun buildCommonKeySuggestions(
        bestTuning: TuningVersatilitySummary?
    ): List<CommonKeySuggestion> {
        val bestTuningId = bestTuning?.tuningId ?: return emptyList()
        val states = statesByTuning[bestTuningId].orEmpty()
        if (states.isEmpty()) {
            return emptyList()
        }

        val selected = mutableListOf<LeverOnlyReachableState>()
        val usedRoots = mutableSetOf<NoteName>()

        COMMON_KEY_SCALE_PRIORITY.forEach { scaleType ->
            if (selected.size >= COMMON_KEY_ROUTE_COUNT) {
                return@forEach
            }
            val candidate = states.firstOrNull { state ->
                state.scaleType == scaleType &&
                    state !in selected &&
                    state.rootNote !in usedRoots
            } ?: states.firstOrNull { state ->
                state.scaleType == scaleType &&
                    state !in selected
            }
            if (candidate != null) {
                selected += candidate
                usedRoots += candidate.rootNote
            }
        }

        states.asSequence()
            .filter { state -> state !in selected && state.rootNote !in usedRoots }
            .take(COMMON_KEY_ROUTE_COUNT - selected.size)
            .forEach { state ->
                selected += state
                usedRoots += state.rootNote
            }

        states.asSequence()
            .filter { state -> state !in selected }
            .take(COMMON_KEY_ROUTE_COUNT - selected.size)
            .forEach { state ->
                selected += state
            }

        return selected.take(COMMON_KEY_ROUTE_COUNT).mapIndexed { index, state ->
            CommonKeySuggestion(
                rank = index + 1,
                state = state,
                directMethod = buildRouteStep(
                    stepNumber = 1,
                    previous = null,
                    state = state
                )
            )
        }
    }

    private fun buildCommonKeyRouteOptions(
        commonKeySuggestions: List<CommonKeySuggestion>
    ): List<CommonKeyRouteOption> {
        val selectedStates = commonKeySuggestions
            .map { suggestion -> suggestion.state }
            .distinct()
        if (selectedStates.isEmpty()) {
            return emptyList()
        }

        return selectedStates.take(COMMON_KEY_ROUTE_COUNT).mapIndexed { index, startState ->
            CommonKeyRouteOption(
                orderNumber = index + 1,
                steps = buildRouteSteps(
                    orderedStates = orderStatesGreedy(
                        selectedStates = selectedStates,
                        startState = startState
                    )
                )
            )
        }
    }

    private fun orderStatesGreedy(
        selectedStates: List<LeverOnlyReachableState>,
        startState: LeverOnlyReachableState? = null
    ): List<LeverOnlyReachableState> {
        if (selectedStates.isEmpty()) {
            return emptyList()
        }

        val orderedStates = mutableListOf<LeverOnlyReachableState>()
        val remaining = selectedStates.toMutableList()
        val initialState = startState?.takeIf { state -> state in remaining } ?: remaining.first()
        orderedStates += initialState
        remaining.remove(initialState)

        while (remaining.isNotEmpty()) {
            val current = orderedStates.last()
            val nextIndex = remaining.indices.minByOrNull { index ->
                transitionCost(
                    previous = current,
                    next = remaining[index]
                )
            } ?: 0
            orderedStates += remaining.removeAt(nextIndex)
        }

        return orderedStates
    }

    private fun buildRouteSteps(
        orderedStates: List<LeverOnlyReachableState>
    ): List<LeverRouteStep> {
        return orderedStates.mapIndexed { index, state ->
            val previous = orderedStates.getOrNull(index - 1)
            buildRouteStep(
                stepNumber = index + 1,
                previous = previous,
                state = state
            )
        }
    }

    private fun buildRouteStep(
        stepNumber: Int,
        previous: LeverOnlyReachableState?,
        state: LeverOnlyReachableState
    ): LeverRouteStep {
        if (previous == null) {
            return LeverRouteStep(
                stepNumber = stepNumber,
                state = state,
                transitionType = RouteTransitionType.FROM_NATURAL_OPEN,
                leverChangesFromPrevious = state.closedLevers.size,
                leversToClose = state.closedLevers,
                leversToOpen = emptyList()
            )
        }
        if (previous.tuningId != state.tuningId) {
            return LeverRouteStep(
                stepNumber = stepNumber,
                state = state,
                transitionType = RouteTransitionType.SWITCH_TUNING_RESET,
                leverChangesFromPrevious = state.closedLevers.size,
                leversToClose = state.closedLevers,
                leversToOpen = emptyList()
            )
        }

        val previousClosed = previous.closedLevers.toSet()
        val currentClosed = state.closedLevers.toSet()
        val toClose = (currentClosed - previousClosed).sorted()
        val toOpen = (previousClosed - currentClosed).sorted()
        return LeverRouteStep(
            stepNumber = stepNumber,
            state = state,
            transitionType = RouteTransitionType.FROM_PREVIOUS_STATE,
            leverChangesFromPrevious = toClose.size + toOpen.size,
            leversToClose = toClose,
            leversToOpen = toOpen
        )
    }

    private fun transitionCost(
        previous: LeverOnlyReachableState,
        next: LeverOnlyReachableState
    ): Int {
        if (previous.tuningId != next.tuningId) {
            return TUNING_SWITCH_COST + next.closedLeverCount
        }
        val previousClosed = previous.closedLevers.toSet()
        val nextClosed = next.closedLevers.toSet()
        return (previousClosed - nextClosed).size + (nextClosed - previousClosed).size
    }

    private fun supportedRootReferences(stringCount: Int): List<ScaleRootReference> {
        return buildList {
            add(ScaleRootReference.LEFT_1)
            add(ScaleRootReference.LEFT_2)
            add(ScaleRootReference.LEFT_3)
            add(ScaleRootReference.LEFT_4)
            if (stringCount >= 21) {
                add(ScaleRootReference.RIGHT_1)
            }
        }
    }

    private fun stateComparator(instrumentKey: NoteName): Comparator<LeverOnlyReachableState> {
        return compareBy<LeverOnlyReachableState>(
            { state -> state.closedLeverCount },
            { state -> abs(signedSemitoneDelta(from = instrumentKey, to = state.rootNote)) },
            { state -> scalePriority(state.scaleType) },
            { state -> rootReferencePriority(state.rootReference) },
            { state -> state.rootNote.semitone }
        )
    }

    private fun scalePriority(scaleType: ScaleType): Int {
        return PRACTICAL_SCALE_TYPES.indexOf(scaleType).coerceAtLeast(PRACTICAL_SCALE_TYPES.size)
    }

    private fun rootReferencePriority(rootReference: ScaleRootReference): Int {
        return when (rootReference) {
            ScaleRootReference.LEFT_1 -> 0
            ScaleRootReference.LEFT_2 -> 1
            ScaleRootReference.LEFT_3 -> 2
            ScaleRootReference.LEFT_4 -> 3
            ScaleRootReference.RIGHT_1 -> 4
        }
    }

    private fun signedSemitoneDelta(from: NoteName, to: NoteName): Int {
        val rawDelta = to.semitone - from.semitone
        val normalized = rawDelta.mod(12)
        return if (normalized > 6) normalized - 12 else normalized
    }

    companion object {
        private const val EXAMPLE_STATE_COUNT = 4
        private const val COMMON_KEY_ROUTE_COUNT = 7
        private const val MAX_ROUTE_STEPS = 15
        private const val TUNING_SWITCH_COST = 50
        private val COMMON_KEY_SCALE_PRIORITY = listOf(
            ScaleType.MAJOR,
            ScaleType.NATURAL_MINOR,
            ScaleType.MIXOLYDIAN,
            ScaleType.DORIAN,
            ScaleType.MAJOR_PENTATONIC,
            ScaleType.MINOR_PENTATONIC,
            ScaleType.HARMONIC_MINOR
        )
        internal val PRACTICAL_SCALE_TYPES = listOf(
            ScaleType.MAJOR,
            ScaleType.NATURAL_MINOR,
            ScaleType.HARMONIC_MINOR,
            ScaleType.MELODIC_MINOR,
            ScaleType.DORIAN,
            ScaleType.PHRYGIAN,
            ScaleType.LYDIAN,
            ScaleType.MIXOLYDIAN,
            ScaleType.AEOLIAN,
            ScaleType.LOCRIAN,
            ScaleType.MAJOR_PENTATONIC,
            ScaleType.MINOR_PENTATONIC,
            ScaleType.MAJOR_BLUES,
            ScaleType.MINOR_BLUES
        )
    }

    private val statesByTuning = mutableMapOf<String, List<LeverOnlyReachableState>>()
}
