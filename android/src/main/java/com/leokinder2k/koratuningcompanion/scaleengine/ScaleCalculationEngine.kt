package com.leokinder2k.koratuningcompanion.scaleengine

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringSide
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StringTuning
import com.leokinder2k.koratuningcompanion.scaleengine.model.EngineMode
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverOnlyStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleStringRole
import com.leokinder2k.koratuningcompanion.scaleengine.model.StringSide
import com.leokinder2k.koratuningcompanion.scaleengine.model.VoicingConflict
import com.leokinder2k.koratuningcompanion.scaleengine.model.VoicingSuggestion
import kotlin.math.abs

class ScaleCalculationEngine {

    fun calculate(request: ScaleCalculationRequest): ScaleCalculationResult {
        val profileTransposeSemitones = transposeSemitonesForRootAnchoredKora(request)
        val strings = request.instrumentProfile.strings.map { string ->
            transposeString(string, semitones = profileTransposeSemitones)
        }
        val scaleNotes = request.scaleType.notesForRoot(request.rootNote)
        val includeClosedLever = request.instrumentProfile.tuningMode == KoraTuningMode.LEVERED
        val homeLeverPosition = request.instrumentProfile.homeLeverPosition

        val leverRows = strings.map { string ->
            val role = roleForStringNumber(
                stringCount = request.instrumentProfile.stringCount,
                stringNumber = string.stringNumber
            )
            val options = buildLeverOptions(
                openPitch = string.openPitch,
                closedPitch = string.closedPitch,
                openIntonationCents = string.openIntonationCents,
                closedIntonationCents = string.closedIntonationCents,
                scaleNotes = scaleNotes,
                includeClosedLever = includeClosedLever
            )

            // The engine works in a virtual space shifted by profileTransposeSemitones for root
            // anchoring. Lever states and pitches in that space must be translated back to the
            // physical string the player actually hears and touches.
            val selectedOption = selectLeverOnlyOption(options, homeLeverPosition)
            val physicalOpenAbsolute = string.openPitch.absoluteSemitone() - profileTransposeSemitones
            val physicalLeverState = toPhysicalLeverState(
                targetAbsolute = selectedOption?.pitch?.absoluteSemitone(),
                physicalOpenAbsolute = physicalOpenAbsolute,
                includeClosedLever = includeClosedLever
            )
            val physicalIntonationCents = intonationForPhysicalLeverState(
                physicalLeverState = physicalLeverState,
                options = options
            )
            val leverChangeFromHome = physicalLeverState != null && isLeverChangeFromHome(
                leverState = physicalLeverState,
                homeLeverPosition = homeLeverPosition
            )
            val leverResult = LeverOnlyStringResult(
                stringNumber = string.stringNumber,
                role = role,
                openPitch = pitchFromAbsoluteSemitone(physicalOpenAbsolute),
                closedPitch = pitchFromAbsoluteSemitone(physicalOpenAbsolute + 1),
                selectedLeverState = physicalLeverState,
                selectedPitch = selectedOption?.pitch,
                pegRetuneRequired = physicalLeverState == null,
                selectedIntonationCents = physicalIntonationCents,
                leverChangeFromHome = leverChangeFromHome
            )

            InternalLeverRow(
                result = leverResult,
                options = options
            )
        }

        val pegRows = strings.map { string ->
            val role = roleForStringNumber(
                stringCount = request.instrumentProfile.stringCount,
                stringNumber = string.stringNumber
            )
            val options = buildLeverOptions(
                openPitch = string.openPitch,
                closedPitch = string.closedPitch,
                openIntonationCents = string.openIntonationCents,
                closedIntonationCents = string.closedIntonationCents,
                scaleNotes = scaleNotes,
                includeClosedLever = includeClosedLever
            )

            // Base pitch for this string: physical home tuning before any peg adjustment.
            // The engine transposes all strings by profileTransposeSemitones for root anchoring,
            // so subtract that offset to get the actual physical peg target relative to home.
            val baseAbsolute = request.instrumentProfile.basePitches
                .getOrNull(string.stringNumber - 1)?.absoluteSemitone()
                ?: (string.openPitch.absoluteSemitone() - profileTransposeSemitones)

            val physicalOpenAbsolute = string.openPitch.absoluteSemitone() - profileTransposeSemitones
            val physicalOpenPitch = pitchFromAbsoluteSemitone(physicalOpenAbsolute)
            val physicalClosedPitch = pitchFromAbsoluteSemitone(physicalOpenAbsolute + 1)

            val leverOnlyOption = selectLeverOnlyOption(options, homeLeverPosition)
            val physicalLeverStateForLeverOnly = toPhysicalLeverState(
                targetAbsolute = leverOnlyOption?.pitch?.absoluteSemitone(),
                physicalOpenAbsolute = physicalOpenAbsolute,
                includeClosedLever = includeClosedLever
            )

            if (leverOnlyOption != null && physicalLeverStateForLeverOnly != null) {
                PegCorrectStringResult(
                    stringNumber = string.stringNumber,
                    role = role,
                    originalOpenPitch = physicalOpenPitch,
                    originalClosedPitch = physicalClosedPitch,
                    retunedOpenPitch = physicalOpenPitch,
                    retunedClosedPitch = physicalClosedPitch,
                    selectedLeverState = physicalLeverStateForLeverOnly,
                    selectedPitch = leverOnlyOption.pitch,
                    pegRetuneSemitones = 0,
                    pegRetuneRequired = false,
                    selectedIntonationCents = intonationForPhysicalLeverState(
                        physicalLeverState = physicalLeverStateForLeverOnly,
                        options = options
                    ),
                    fromBaseSemitones = physicalOpenAbsolute - baseAbsolute,
                    leverChangeFromHome = isLeverChangeFromHome(physicalLeverStateForLeverOnly, homeLeverPosition)
                )
            } else {
                val retune = if (options.isNotEmpty()) {
                    selectBestRetuneForTargetPitches(
                        originalOpenPitch = physicalOpenPitch,
                        targetPitches = options.map { option -> option.pitch },
                        allowClosedLever = includeClosedLever,
                        homeLeverPosition = homeLeverPosition
                    )
                } else {
                    selectBestRetuneForScale(
                        originalOpenPitch = physicalOpenPitch,
                        scaleNotes = scaleNotes,
                        allowClosedLever = includeClosedLever,
                        homeLeverPosition = homeLeverPosition
                    )
                }
                val physicalRetunedAbsolute = retune.retunedOpenAbsolute
                val physicalRetunedOpen = pitchFromAbsoluteSemitone(physicalRetunedAbsolute)
                val physicalRetunedClosed = physicalRetunedOpen.plusSemitones(1)
                val physicalSelectedPitch = pitchFromAbsoluteSemitone(retune.targetAbsolute)

                PegCorrectStringResult(
                    stringNumber = string.stringNumber,
                    role = role,
                    originalOpenPitch = physicalOpenPitch,
                    originalClosedPitch = physicalClosedPitch,
                    retunedOpenPitch = physicalRetunedOpen,
                    retunedClosedPitch = physicalRetunedClosed,
                    selectedLeverState = retune.selectedLeverState,
                    selectedPitch = physicalSelectedPitch,
                    pegRetuneSemitones = physicalRetunedAbsolute - physicalOpenAbsolute,
                    pegRetuneRequired = physicalRetunedAbsolute != physicalOpenAbsolute,
                    selectedIntonationCents = when (retune.selectedLeverState) {
                        LeverState.OPEN -> string.openIntonationCents
                        LeverState.CLOSED -> string.closedIntonationCents
                    },
                    fromBaseSemitones = physicalRetunedAbsolute - baseAbsolute,
                    leverChangeFromHome = isLeverChangeFromHome(retune.selectedLeverState, homeLeverPosition)
                )
            }
        }

        val sortedLeverRows = sortByRole(leverRows.map { row -> row.result }) { row -> row.role }
        val sortedPegRows = sortByRole(pegRows) { row -> row.role }

        val leverConflicts = detectConflicts(
            rows = sortedLeverRows,
            mode = EngineMode.LEVER_ONLY,
            roleOf = { row -> row.role },
            pitchOf = { row -> row.selectedPitch },
            stringNumberOf = { row -> row.stringNumber }
        )
        val pegConflicts = detectConflicts(
            rows = sortedPegRows,
            mode = EngineMode.PEG_CORRECT,
            roleOf = { row -> row.role },
            pitchOf = { row -> row.selectedPitch },
            stringNumberOf = { row -> row.stringNumber }
        )

        val suggestions = buildSuggestions(
            leverConflicts = leverConflicts,
            pegConflicts = pegConflicts,
            leverRows = leverRows
        )

        return ScaleCalculationResult(
            request = request,
            scaleNotes = scaleNotes,
            leverOnlyTable = sortedLeverRows,
            pegCorrectTable = sortedPegRows,
            conflicts = leverConflicts + pegConflicts,
            suggestions = suggestions
        )
    }

    private fun buildLeverOptions(
        openPitch: Pitch,
        closedPitch: Pitch,
        openIntonationCents: Double,
        closedIntonationCents: Double,
        scaleNotes: Set<NoteName>,
        includeClosedLever: Boolean = true
    ): List<LeverOption> {
        val options = mutableListOf<LeverOption>()
        if (openPitch.note in scaleNotes) {
            options += LeverOption(
                leverState = LeverState.OPEN,
                pitch = openPitch,
                intonationCents = openIntonationCents
            )
        }
        if (includeClosedLever && closedPitch.note in scaleNotes) {
            options += LeverOption(
                leverState = LeverState.CLOSED,
                pitch = closedPitch,
                intonationCents = closedIntonationCents
            )
        }
        return options
    }

    private fun selectLeverOnlyOption(options: List<LeverOption>, homeLeverPosition: HomeLeverPosition): LeverOption? {
        if (options.isEmpty()) {
            return null
        }
        return options.minBy { option ->
            when (option.leverState) {
                LeverState.OPEN -> if (homeLeverPosition == HomeLeverPosition.OPEN) 0 else 1
                LeverState.CLOSED -> if (homeLeverPosition == HomeLeverPosition.CLOSED) 0 else 1
            }
        }
    }

    private fun selectBestRetuneForScale(
        originalOpenPitch: Pitch,
        scaleNotes: Set<NoteName>,
        allowClosedLever: Boolean,
        homeLeverPosition: HomeLeverPosition
    ): RetuneCandidate {
        val openAbsolute = originalOpenPitch.absoluteSemitone()
        val openLeverPenalty = if (homeLeverPosition == HomeLeverPosition.OPEN) 0 else 1
        val closedLeverPenalty = if (homeLeverPosition == HomeLeverPosition.CLOSED) 0 else 1
        var best: RetuneCandidate? = null

        for (octave in originalOpenPitch.octave - 2..originalOpenPitch.octave + 2) {
            for (note in scaleNotes) {
                val targetAbsolute = Pitch(note, octave).absoluteSemitone()

                considerRetuneCandidate(
                    candidate = RetuneCandidate(
                        targetAbsolute = targetAbsolute,
                        retunedOpenAbsolute = targetAbsolute,
                        selectedLeverState = LeverState.OPEN,
                        absoluteRetuneDistance = abs(targetAbsolute - openAbsolute),
                        leverPenalty = openLeverPenalty
                    ),
                    currentBest = best
                )?.let { better ->
                    best = better
                }

                if (allowClosedLever) {
                    val closedRetunedOpen = targetAbsolute - 1
                    considerRetuneCandidate(
                        candidate = RetuneCandidate(
                            targetAbsolute = targetAbsolute,
                            retunedOpenAbsolute = closedRetunedOpen,
                            selectedLeverState = LeverState.CLOSED,
                            absoluteRetuneDistance = abs(closedRetunedOpen - openAbsolute),
                            leverPenalty = closedLeverPenalty
                        ),
                        currentBest = best
                    )?.let { better ->
                        best = better
                    }
                }
            }
        }

        return requireNotNull(best) {
            "Scale notes must not be empty."
        }
    }

    private fun selectBestRetuneForTargetPitches(
        originalOpenPitch: Pitch,
        targetPitches: List<Pitch>,
        allowClosedLever: Boolean,
        homeLeverPosition: HomeLeverPosition
    ): RetuneCandidate {
        val openAbsolute = originalOpenPitch.absoluteSemitone()
        val openLeverPenalty = if (homeLeverPosition == HomeLeverPosition.OPEN) 0 else 1
        val closedLeverPenalty = if (homeLeverPosition == HomeLeverPosition.CLOSED) 0 else 1
        var best: RetuneCandidate? = null

        targetPitches
            .map { pitch -> pitch.absoluteSemitone() }
            .distinct()
            .forEach { targetAbsolute ->
                considerRetuneCandidate(
                    candidate = RetuneCandidate(
                        targetAbsolute = targetAbsolute,
                        retunedOpenAbsolute = targetAbsolute,
                        selectedLeverState = LeverState.OPEN,
                        absoluteRetuneDistance = abs(targetAbsolute - openAbsolute),
                        leverPenalty = openLeverPenalty
                    ),
                    currentBest = best
                )?.let { better ->
                    best = better
                }

                if (allowClosedLever) {
                    val closedRetunedOpen = targetAbsolute - 1
                    considerRetuneCandidate(
                        candidate = RetuneCandidate(
                            targetAbsolute = targetAbsolute,
                            retunedOpenAbsolute = closedRetunedOpen,
                            selectedLeverState = LeverState.CLOSED,
                            absoluteRetuneDistance = abs(closedRetunedOpen - openAbsolute),
                            leverPenalty = closedLeverPenalty
                        ),
                        currentBest = best
                    )?.let { better ->
                        best = better
                    }
                }
            }

        return requireNotNull(best) {
            "Target pitches must not be empty."
        }
    }

    private fun considerRetuneCandidate(
        candidate: RetuneCandidate,
        currentBest: RetuneCandidate?
    ): RetuneCandidate? {
        if (currentBest == null) {
            return candidate
        }
        return when {
            candidate.absoluteRetuneDistance < currentBest.absoluteRetuneDistance -> candidate
            candidate.absoluteRetuneDistance > currentBest.absoluteRetuneDistance -> currentBest
            candidate.leverPenalty < currentBest.leverPenalty -> candidate
            candidate.leverPenalty > currentBest.leverPenalty -> currentBest
            candidate.retunedOpenAbsolute < currentBest.retunedOpenAbsolute -> candidate
            else -> currentBest
        }
    }

    private fun <T> detectConflicts(
        rows: List<T>,
        mode: EngineMode,
        roleOf: (T) -> ScaleStringRole,
        pitchOf: (T) -> Pitch?,
        stringNumberOf: (T) -> Int
    ): List<VoicingConflict> {
        val conflicts = mutableListOf<VoicingConflict>()
        StringSide.entries.forEach { side ->
            val sideRows = rows
                .filter { row -> roleOf(row).side == side }
                .sortedBy { row -> roleOf(row).positionFromLow }

            var previous: T? = null
            sideRows.forEach { current ->
                val previousRow = previous
                val previousPitch = previousRow?.let(pitchOf)
                val currentPitch = pitchOf(current)

                if (previousPitch != null && currentPitch != null) {
                    if (currentPitch.absoluteSemitone() <= previousPitch.absoluteSemitone()) {
                        conflicts += VoicingConflict(
                            mode = mode,
                            side = side,
                            lowerStringNumber = stringNumberOf(previousRow),
                            higherStringNumber = stringNumberOf(current),
                            detail = "Pitch crossing detected on ${side.shortLabel} side."
                        )
                    }
                }
                previous = current
            }
        }
        return conflicts
    }

    private fun buildSuggestions(
        leverConflicts: List<VoicingConflict>,
        pegConflicts: List<VoicingConflict>,
        leverRows: List<InternalLeverRow>
    ): List<VoicingSuggestion> {
        val suggestions = mutableListOf<VoicingSuggestion>()
        val leverRowsByString = leverRows.associateBy { row -> row.result.stringNumber }

        if (leverConflicts.isNotEmpty()) {
            val conflict = leverConflicts.first()
            val lower = leverRowsByString[conflict.lowerStringNumber]
            val higher = leverRowsByString[conflict.higherStringNumber]
            val customSuggestion = suggestLeverSwap(lower, higher, conflict.side)
            suggestions += customSuggestion ?: VoicingSuggestion(
                mode = EngineMode.LEVER_ONLY,
                suggestion = "Use peg-correct mode or choose a different root/scale to remove ${conflict.side.shortLabel}-side crossing."
            )
        }

        if (pegConflicts.isNotEmpty()) {
            suggestions += VoicingSuggestion(
                mode = EngineMode.PEG_CORRECT,
                suggestion = "Try transposing the musical center and recalculate to avoid peg-correct voicing crossings."
            )
        }

        return suggestions
    }

    private fun transposeSemitonesForRootAnchoredKora(
        request: ScaleCalculationRequest
    ): Int {
        val stringCount = request.instrumentProfile.stringCount
        val leftOrder = KoraStringLayout.leftOrder(stringCount)
        val referenceStringNumber = when (request.scaleRootReference) {
            ScaleRootReference.LEFT_1 -> leftOrder.getOrNull(0) ?: 1
            ScaleRootReference.LEFT_2 -> leftOrder.getOrNull(1) ?: leftOrder.firstOrNull() ?: 1
            ScaleRootReference.LEFT_3 -> leftOrder.getOrNull(2) ?: leftOrder.firstOrNull() ?: 1
            ScaleRootReference.LEFT_4 -> leftOrder.getOrNull(3) ?: leftOrder.firstOrNull() ?: 1
            ScaleRootReference.RIGHT_1 -> KoraStringLayout.rightOrder(stringCount).getOrNull(0) ?: 1
        }
        // Use the current working open pitch of the reference string so that the root-anchor
        // calculation correctly handles instruments whose openPitches have already been
        // transposed. Using basePitches here caused a double transposition.
        val referencePitch = request.instrumentProfile.openPitches
            .getOrNull(referenceStringNumber - 1)
            ?: request.instrumentProfile.openPitches.firstOrNull()
            ?: return 0

        val rawDelta = request.rootNote.semitone - referencePitch.note.semitone
        val normalizedDelta = rawDelta.mod(12)
        return if (normalizedDelta > 6) {
            normalizedDelta - 12
        } else {
            normalizedDelta
        }
    }

    private fun transposeString(
        string: StringTuning,
        semitones: Int
    ): StringTuning {
        if (semitones == 0) {
            return string
        }
        return string.copy(
            openPitch = string.openPitch.plusSemitones(semitones),
            closedPitch = string.closedPitch.plusSemitones(semitones)
        )
    }

    private fun suggestLeverSwap(
        lower: InternalLeverRow?,
        higher: InternalLeverRow?,
        side: StringSide
    ): VoicingSuggestion? {
        if (lower == null || higher == null) {
            return null
        }

        val lowerSelected = lower.result.selectedPitch ?: return null
        val higherSelected = higher.result.selectedPitch ?: return null

        val lowerAlternative = lower.options.firstOrNull { option ->
            option.leverState != lower.result.selectedLeverState &&
                option.pitch.absoluteSemitone() < higherSelected.absoluteSemitone()
        }
        if (lowerAlternative != null) {
            return VoicingSuggestion(
                mode = EngineMode.LEVER_ONLY,
                suggestion = "Set string ${lower.result.stringNumber} to ${lowerAlternative.leverState} to preserve ${side.shortLabel}-side ascending order."
            )
        }

        val higherAlternative = higher.options.firstOrNull { option ->
            option.leverState != higher.result.selectedLeverState &&
                option.pitch.absoluteSemitone() > lowerSelected.absoluteSemitone()
        }
        if (higherAlternative != null) {
            return VoicingSuggestion(
                mode = EngineMode.LEVER_ONLY,
                suggestion = "Set string ${higher.result.stringNumber} to ${higherAlternative.leverState} to preserve ${side.shortLabel}-side ascending order."
            )
        }

        return null
    }

    private fun roleForStringNumber(stringCount: Int, stringNumber: Int): ScaleStringRole {
        val layoutRole = KoraStringLayout.roleFor(
            stringCount = stringCount,
            stringNumber = stringNumber
        )
        val side = when (layoutRole.side) {
            KoraStringSide.LEFT -> StringSide.LEFT
            KoraStringSide.RIGHT -> StringSide.RIGHT
        }
        return ScaleStringRole(
            side = side,
            positionFromLow = layoutRole.positionFromLow
        )
    }

    private fun toPhysicalLeverState(
        targetAbsolute: Int?,
        physicalOpenAbsolute: Int,
        includeClosedLever: Boolean
    ): LeverState? {
        targetAbsolute ?: return null
        return when (targetAbsolute) {
            physicalOpenAbsolute -> LeverState.OPEN
            physicalOpenAbsolute + 1 -> if (includeClosedLever) LeverState.CLOSED else null
            else -> null
        }
    }

    private fun intonationForPhysicalLeverState(
        physicalLeverState: LeverState?,
        options: List<LeverOption>
    ): Double = when (physicalLeverState) {
        LeverState.OPEN -> options.firstOrNull { it.leverState == LeverState.OPEN }?.intonationCents ?: 0.0
        LeverState.CLOSED -> options.firstOrNull { it.leverState == LeverState.CLOSED }?.intonationCents ?: 0.0
        null -> 0.0
    }

    private fun isLeverChangeFromHome(leverState: LeverState, homeLeverPosition: HomeLeverPosition): Boolean {
        return when (homeLeverPosition) {
            HomeLeverPosition.OPEN -> leverState == LeverState.CLOSED
            HomeLeverPosition.CLOSED -> leverState == LeverState.OPEN
        }
    }

    private fun pitchFromAbsoluteSemitone(value: Int): Pitch {
        val note = NoteName.fromSemitone(value)
        val octave = value.floorDiv(12)
        return Pitch(note = note, octave = octave)
    }

    private fun Pitch.absoluteSemitone(): Int {
        return octave * 12 + note.semitone
    }

    private fun <T> sortByRole(
        rows: List<T>,
        roleOf: (T) -> ScaleStringRole
    ): List<T> {
        return rows.sortedWith(
            compareBy<T>(
                { row -> sideOrder(roleOf(row).side) },
                { row -> roleOf(row).positionFromLow }
            )
        )
    }

    private fun sideOrder(side: StringSide): Int {
        return when (side) {
            StringSide.LEFT -> 0
            StringSide.RIGHT -> 1
        }
    }

    private data class LeverOption(
        val leverState: LeverState,
        val pitch: Pitch,
        val intonationCents: Double
    )

    private data class InternalLeverRow(
        val result: LeverOnlyStringResult,
        val options: List<LeverOption>
    )

    private data class RetuneCandidate(
        val targetAbsolute: Int,
        val retunedOpenAbsolute: Int,
        val selectedLeverState: LeverState,
        val absoluteRetuneDistance: Int,
        val leverPenalty: Int
    )
}
