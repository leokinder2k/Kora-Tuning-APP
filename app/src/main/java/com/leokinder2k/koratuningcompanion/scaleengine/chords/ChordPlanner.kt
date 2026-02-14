package com.leokinder2k.koratuningcompanion.scaleengine.chords

import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import kotlin.math.abs

data class ChordTone(
    val semitoneOffset: Int,
    val label: String
)

enum class ChordQuality(
    val displayName: String,
    val tones: List<ChordTone>
) {
    MAJOR(
        "Major",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 4, label = "3"),
            ChordTone(semitoneOffset = 7, label = "5")
        )
    ),
    MINOR(
        "Minor",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 3, label = "b3"),
            ChordTone(semitoneOffset = 7, label = "5")
        )
    ),
    DIMINISHED(
        "Diminished",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 3, label = "b3"),
            ChordTone(semitoneOffset = 6, label = "b5")
        )
    ),
    HALF_DIMINISHED(
        "Half-Diminished (m7b5)",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 3, label = "b3"),
            ChordTone(semitoneOffset = 6, label = "b5"),
            ChordTone(semitoneOffset = 10, label = "b7")
        )
    ),
    SUS2(
        "Sus2",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 2, label = "2"),
            ChordTone(semitoneOffset = 7, label = "5")
        )
    ),
    SUS4(
        "Sus4",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 5, label = "4"),
            ChordTone(semitoneOffset = 7, label = "5")
        )
    ),
    DOMINANT7(
        "Dominant 7",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 4, label = "3"),
            ChordTone(semitoneOffset = 7, label = "5"),
            ChordTone(semitoneOffset = 10, label = "b7")
        )
    ),
    MAJOR7(
        "Major 7",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 4, label = "3"),
            ChordTone(semitoneOffset = 7, label = "5"),
            ChordTone(semitoneOffset = 11, label = "7")
        )
    ),
    MINOR7(
        "Minor 7",
        listOf(
            ChordTone(semitoneOffset = 0, label = "R"),
            ChordTone(semitoneOffset = 3, label = "b3"),
            ChordTone(semitoneOffset = 7, label = "5"),
            ChordTone(semitoneOffset = 10, label = "b7")
        )
    ),
    ;

    fun notesForRoot(root: NoteName): Set<NoteName> {
        return tones
            .map { tone -> NoteName.fromSemitone(root.semitone + tone.semitoneOffset) }
            .toCollection(LinkedHashSet())
    }
}

data class ChordDefinition(
    val root: NoteName,
    val quality: ChordQuality
) {
    val chordNotes: Set<NoteName> = quality.notesForRoot(root)
    val label: String = "${root.symbol} ${quality.displayName}"
}

data class ChordMatch(
    val definition: ChordDefinition,
    val playedStringNumbers: Set<Int>,
    val matchedNotes: Set<NoteName>,
    val missingNotes: Set<NoteName>,
    val usesDetunedStrings: Boolean,
    val openPlayCount: Int,
    val closedPlayCount: Int,
    val detunedPlayCount: Int,
    val score: Int
) {
    val isComplete: Boolean = missingNotes.isEmpty()
}

object ChordPlanner {
    fun analyze(
        rows: List<PegCorrectStringResult>,
        definition: ChordDefinition
    ): ChordMatch {
        val chordNotes = definition.chordNotes
        val playedRows = rows.filter { row -> row.selectedPitch.note in chordNotes }
        val matchedNotes = playedRows
            .map { row -> row.selectedPitch.note }
            .toCollection(LinkedHashSet())
        val missingNotes = chordNotes - matchedNotes
        val detunedPlayCount = playedRows.count { row -> row.pegRetuneRequired }
        val score = (matchedNotes.size * 120) -
            (missingNotes.size * 95) +
            (playedRows.size * 5) -
            (detunedPlayCount * 35) -
            abs(playedRows.size - 6)

        return ChordMatch(
            definition = definition,
            playedStringNumbers = playedRows.map { row -> row.stringNumber }.toSet(),
            matchedNotes = matchedNotes,
            missingNotes = missingNotes,
            usesDetunedStrings = detunedPlayCount > 0,
            openPlayCount = playedRows.count { row ->
                !row.pegRetuneRequired && row.selectedLeverState == LeverState.OPEN
            },
            closedPlayCount = playedRows.count { row ->
                !row.pegRetuneRequired && row.selectedLeverState == LeverState.CLOSED
            },
            detunedPlayCount = detunedPlayCount,
            score = score
        )
    }

    fun bestMatches(
        rows: List<PegCorrectStringResult>,
        limit: Int = 12
    ): List<ChordMatch> {
        return allDefinitions()
            .map { definition -> analyze(rows, definition) }
            .sortedByDescending { match ->
                if (match.isComplete) {
                    match.score + 200
                } else {
                    match.score
                }
            }
            .take(limit)
    }

    fun suggestClosestWithoutDetune(
        rows: List<PegCorrectStringResult>,
        desired: ChordDefinition
    ): ChordMatch? {
        val desiredNotes = desired.chordNotes
        return allDefinitions()
            .asSequence()
            .map { definition -> analyze(rows, definition) }
            .filter { match ->
                match.isComplete &&
                    !match.usesDetunedStrings &&
                    match.playedStringNumbers.isNotEmpty()
            }
            .maxByOrNull { match ->
                val overlap = match.definition.chordNotes.intersect(desiredNotes).size
                val qualityBonus = if (match.definition.quality == desired.quality) 2 else 0
                val rootDistance = circularSemitoneDistance(
                    match.definition.root.semitone,
                    desired.root.semitone
                )
                (overlap * 120) +
                    (qualityBonus * 25) -
                    (rootDistance * 6) +
                    match.playedStringNumbers.size
            }
    }

    fun chooseChordStrings(
        rows: List<PegCorrectStringResult>,
        definition: ChordDefinition,
        toneOffsetsToInclude: Set<Int>,
        maxNotes: Int = 4
    ): List<PegCorrectStringResult> {
        val normalizedOffsets = toneOffsetsToInclude
            .ifEmpty { definition.quality.tones.map { tone -> tone.semitoneOffset }.toSet() }
            .map { offset -> Math.floorMod(offset, 12) }
            .toSet()
        val allowedNotes = normalizedOffsets
            .map { offset -> NoteName.fromSemitone(definition.root.semitone + offset) }
            .toSet()
        val sortedCandidates = rows
            .filter { row -> row.selectedPitch.note in allowedNotes }
            .sortedBy { row ->
                (row.selectedPitch.octave * 12) + row.selectedPitch.note.semitone
            }
        if (sortedCandidates.isEmpty()) {
            return emptyList()
        }

        val selected = mutableListOf<PegCorrectStringResult>()
        val selectedNotes = mutableSetOf<NoteName>()
        val selectedStringNumbers = mutableSetOf<Int>()

        // First pass: ensure each included tone is represented once when possible.
        sortedCandidates.forEach { row ->
            if (row.selectedPitch.note !in selectedNotes && selected.size < maxNotes) {
                selected += row
                selectedNotes += row.selectedPitch.note
                selectedStringNumbers += row.stringNumber
            }
        }
        // Second pass: fill remaining slots with the closest candidates.
        if (selected.size < maxNotes) {
            sortedCandidates.forEach { row ->
                if (selected.size >= maxNotes) {
                    return@forEach
                }
                if (row.stringNumber !in selectedStringNumbers) {
                    selected += row
                    selectedStringNumbers += row.stringNumber
                }
            }
        }

        return selected
            .sortedBy { row -> (row.selectedPitch.octave * 12) + row.selectedPitch.note.semitone }
            .take(maxNotes.coerceIn(1, 4))
    }

    private fun allDefinitions(): List<ChordDefinition> {
        return NoteName.entries.flatMap { root ->
            ChordQuality.entries.map { quality ->
                ChordDefinition(root = root, quality = quality)
            }
        }
    }

    private fun circularSemitoneDistance(first: Int, second: Int): Int {
        val delta = abs(first - second) % 12
        return minOf(delta, 12 - delta)
    }
}

