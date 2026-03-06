package com.leokinder2k.koratuningcompanion.scaleengine

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringSide
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StringTuning
import com.leokinder2k.koratuningcompanion.scaleengine.model.EngineMode
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverOnlyStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
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

            val selectedOption = selectLeverOnlyOption(options)
            val leverResult = LeverOnlyStringResult(
                stringNumber = string.stringNumber,
                role = role,
                openPitch = string.openPitch,
                closedPitch = string.closedPitch,
                selectedLeverState = selectedOption?.leverState,
                selectedPitch = selectedOption?.pitch,
                pegRetuneRequired = selectedOption == null,
                selectedIntonationCents = selectedOption?.intonationCents ?: 0.0
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

            val leverOnlyOption = selectLeverOnlyOption(options)
            if (leverOnlyOption != null) {
                PegCorrectStringResult(
                    stringNumber = string.stringNumber,
                    role = role,
                    originalOpenPitch = string.openPitch,
                    originalClosedPitch = string.closedPitch,
                    retunedOpenPitch = string.openPitch,
                    retunedClosedPitch = string.closedPitch,
                    selectedLeverState = leverOnlyOption.leverState,
                    selectedPitch = leverOnlyOption.pitch,
                    pegRetuneSemitones = 0,
                    pegRetuneRequired = false,
                    selectedIntonationCents = leverOnlyOption.intonationCents
                )
            } else {
                val retune = selectBestRetune(
                    originalOpenPitch = string.openPitch,
                    scaleNotes = scaleNotes,
                    allowClosedLever = includeClosedLever
                )
                val retunedOpen = pitchFromAbsoluteSemitone(retune.retunedOpenAbsolute)
                val retunedClosed = retunedOpen.plusSemitones(1)
                val selectedPitch = if (retune.selectedLeverState == LeverState.OPEN) {
                    retunedOpen
                } else {
                    retunedClosed
                }

                PegCorrectStringResult(
                    stringNumber = string.stringNumber,
                    role = role,
                    originalOpenPitch = string.openPitch,
                    originalClosedPitch = string.closedPitch,
                    retunedOpenPitch = retunedOpen,
                    retunedClosedPitch = retunedClosed,
                    selectedLeverState = retune.selectedLeverState,
                    selectedPitch = selectedPitch,
                    pegRetuneSemitones = retune.retunedOpenAbsolute - string.openPitch.absoluteSemitone(),
                    pegRetuneRequired = retune.retunedOpenAbsolute != string.openPitch.absoluteSemitone(),
                    selectedIntonationCents = 0.0
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

    private fun selectLeverOnlyOption(options: List<LeverOption>): LeverOption? {
        if (options.isEmpty()) {
            return null
        }
        return options.minBy { option ->
            when (option.leverState) {
                LeverState.OPEN -> 0
                LeverState.CLOSED -> 1
            }
        }
    }

    private fun selectBestRetune(
        originalOpenPitch: Pitch,
        scaleNotes: Set<NoteName>,
        allowClosedLever: Boolean
    ): RetuneCandidate {
        val openAbsolute = originalOpenPitch.absoluteSemitone()
        var best: RetuneCandidate? = null

        for (octave in originalOpenPitch.octave - 2..originalOpenPitch.octave + 2) {
            for (note in scaleNotes) {
                val targetAbsolute = Pitch(note, octave).absoluteSemitone()

                considerRetuneCandidate(
                    candidate = RetuneCandidate(
                        retunedOpenAbsolute = targetAbsolute,
                        selectedLeverState = LeverState.OPEN,
                        absoluteRetuneDistance = abs(targetAbsolute - openAbsolute),
                        leverPenalty = 0
                    ),
                    currentBest = best
                )?.let { better ->
                    best = better
                }

                if (allowClosedLever) {
                    val closedRetunedOpen = targetAbsolute - 1
                    considerRetuneCandidate(
                        candidate = RetuneCandidate(
                            retunedOpenAbsolute = closedRetunedOpen,
                            selectedLeverState = LeverState.CLOSED,
                            absoluteRetuneDistance = abs(closedRetunedOpen - openAbsolute),
                            leverPenalty = 1
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
        val leftBassStringNumber = KoraStringLayout.leftOrder(request.instrumentProfile.stringCount)
            .firstOrNull()
            ?: 1
        val leftBassPitch = request.instrumentProfile.strings
            .firstOrNull { string -> string.stringNumber == leftBassStringNumber }
            ?.openPitch
            ?: request.instrumentProfile.strings.firstOrNull()?.openPitch
            ?: return 0

        val rawDelta = request.rootNote.semitone - leftBassPitch.note.semitone
        val normalizedDelta = Math.floorMod(rawDelta, 12)
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

    private fun pitchFromAbsoluteSemitone(value: Int): Pitch {
        val note = NoteName.fromSemitone(value)
        val octave = Math.floorDiv(value, 12)
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
        val retunedOpenAbsolute: Int,
        val selectedLeverState: LeverState,
        val absoluteRetuneDistance: Int,
        val leverPenalty: Int
    )
}

