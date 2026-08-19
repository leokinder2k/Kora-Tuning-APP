package com.leokinder2k.koratuningcompanion.notation.engine

import kotlin.math.abs
import kotlin.math.roundToInt

private fun safePpq(ppq: Int): Int = ppq.coerceAtLeast(24)

private fun sixteenthTicks(ppq: Int): Int = (safePpq(ppq) / 4).coerceAtLeast(1)

private fun tripletEighthTicks(ppq: Int): Int = (safePpq(ppq) / 3).coerceAtLeast(1)

private fun nearestMultiple(value: Int, step: Int): Int {
    if (value <= 0) return 0
    return (value.toDouble() / step.toDouble()).roundToInt().coerceAtLeast(0) * step
}

internal fun quantizeRhythmStart(tick: Int, ppq: Int): Int {
    val candidates = listOf(
        nearestMultiple(tick, sixteenthTicks(ppq)),
        nearestMultiple(tick, tripletEighthTicks(ppq)),
    )
    return candidates.minWith(
        compareBy<Int> { abs(it - tick) }
            .thenBy { it }
    ).coerceAtLeast(0)
}

internal fun quantizeRhythmDuration(durationTicks: Int, ppq: Int): Int {
    val minDuration = sixteenthTicks(ppq)
    val safeDuration = durationTicks.coerceAtLeast(1)
    val candidates = listOf(
        nearestMultiple(safeDuration, minDuration).coerceAtLeast(minDuration),
        nearestMultiple(safeDuration, tripletEighthTicks(ppq)).coerceAtLeast(minDuration),
    ).distinct()
    return candidates.minWith(
        compareBy<Int> { abs(it - safeDuration) }
            .thenBy { if (it < safeDuration) 1 else 0 }
            .thenBy { it }
    )
}

internal fun quantizeNoteEventsRhythm(notes: List<NoteEvent>, ppq: Int): List<NoteEvent> {
    return notes.map { note ->
        note.copy(
            tick = quantizeRhythmStart(note.tick, ppq),
            durationTicks = quantizeRhythmDuration(note.durationTicks, ppq)
        )
    }.sortedWith(compareBy({ it.tick }, { -it.pitchMidi }, { it.eventId.orEmpty() }))
}

internal fun quantizeRestEventsRhythm(rests: List<RestEvent>, ppq: Int): List<RestEvent> {
    return rests.map { rest ->
        rest.copy(
            tick = quantizeRhythmStart(rest.tick, ppq),
            durationTicks = quantizeRhythmDuration(rest.durationTicks, ppq)
        )
    }.sortedWith(compareBy({ it.tick }, { it.staff.orEmpty() }, { it.eventId.orEmpty() }))
}

internal fun quantizeScoreRhythm(score: SimplifiedScore): SimplifiedScore {
    return score.copy(
        noteEvents = quantizeNoteEventsRhythm(score.noteEvents, score.ppq),
        restEvents = quantizeRestEventsRhythm(score.restEvents, score.ppq)
    )
}
