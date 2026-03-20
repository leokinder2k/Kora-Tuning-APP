package com.leokinder2k.koratuningcompanion.notation.engine

// Port of reduction.js — simplifies polyphonic input to at most [cap] simultaneous notes

private data class TimeSlice(
    val tick: Int,
    val lengthTicks: Int,
    val notes: List<NoteEvent>,
)

private fun buildTimeSlices(noteEvents: List<NoteEvent>): List<TimeSlice> {
    if (noteEvents.isEmpty()) return emptyList()
    val cutSet = sortedSetOf<Int>()
    for (e in noteEvents) {
        cutSet.add(e.tick)
        cutSet.add(e.tick + e.durationTicks)
    }
    val cuts = cutSet.toList()
    val slices = mutableListOf<TimeSlice>()
    for (i in 0 until cuts.size - 1) {
        val t0 = cuts[i]; val t1 = cuts[i + 1]
        if (t1 <= t0) continue
        val active = noteEvents.filter { n -> n.tick <= t0 && t0 < n.tick + n.durationTicks }
        slices.add(TimeSlice(t0, t1 - t0, active))
    }
    return slices
}

private fun mappingCost(pitchMidi: Int, tuningMidi: Set<Int>): Int = when {
    pitchMidi in tuningMidi -> 0
    tuningMidi.any { kotlin.math.abs(it - pitchMidi) == 1 } -> 5
    else -> 100
}

/**
 * For a set of simultaneously sounding notes, select up to [cap] to keep:
 * always include the highest (melody) and lowest (bass), then fill with cheapest-to-play notes.
 */
private fun chooseSliceNotes(
    notes: List<NoteEvent>,
    cap: Int,
    tuningMidi: Set<Int>,
    previousMelodyPitch: Int?,
    previousMelodyEventId: String?,
): Triple<List<NoteEvent>, NoteEvent?, NoteEvent?> {
    if (notes.isEmpty()) return Triple(emptyList(), null, null)

    // Melody: prefer explicit hint, then continuity, then highest pitch
    val melodyCandidates = notes.sortedWith(
        compareByDescending<NoteEvent> { it.melodyHint }
            .thenByDescending {
                if (it.eventId != null && it.eventId == previousMelodyEventId) 1 else 0
            }
            .thenBy { if (previousMelodyPitch != null) kotlin.math.abs(it.pitchMidi - previousMelodyPitch) else 0 }
            .thenByDescending { it.pitchMidi }
            .thenBy { it.eventId ?: "" }
    )
    val melody = melodyCandidates.first()
    val selected = mutableListOf(melody)
    val selectedIds = mutableSetOf(melody.eventId ?: "@${notes.indexOf(melody)}")

    // Bass: lowest pitch from remaining
    val remaining = notes.filter { (it.eventId ?: "@${notes.indexOf(it)}") !in selectedIds }
        .sortedWith(compareBy({ it.pitchMidi }, { it.eventId ?: "" }))
    val bass = remaining.firstOrNull()
    if (bass != null && selected.size < cap) {
        selected.add(bass)
        selectedIds.add(bass.eventId ?: "@${notes.indexOf(bass)}")
    }

    // Fill remaining slots sorted by playability cost, then higher pitch
    val rest = notes.filter { (it.eventId ?: "@${notes.indexOf(it)}") !in selectedIds }
        .sortedWith(
            compareBy<NoteEvent> { mappingCost(it.pitchMidi, tuningMidi) }
                .thenByDescending { it.pitchMidi }
                .thenBy { it.eventId ?: "" }
        )
    for (n in rest) {
        if (selected.size >= cap) break
        selected.add(n)
    }

    return Triple(selected, melody, bass)
}

private fun roleForNote(note: NoteEvent, selected: List<NoteEvent>, melody: NoteEvent?, bass: NoteEvent?): String {
    if (note.eventId != null && note.eventId == melody?.eventId) return "MELODY"
    if (note.eventId != null && note.eventId == bass?.eventId && note.eventId != melody?.eventId) return "BASS"
    if (selected.size == 1) return "MELODY"
    val maxPitch = selected.maxOf { it.pitchMidi }
    val minPitch = selected.minOf { it.pitchMidi }
    return when (note.pitchMidi) {
        maxPitch -> "MELODY"
        minPitch -> "BASS"
        else -> "HARMONY"
    }
}

/**
 * Build a simplified teaching reduction from note events.
 * Returns note and rest events in the simplified score format.
 */
fun buildSimplifiedTeachingReduction(
    noteEvents: List<NoteEvent>,
    cap: Int = 4,
    splitMidi: Int = 60,
    tuningMidi: Set<Int> = emptySet(),
): Pair<List<NoteEvent>, List<RestEvent>> {
    val slices = buildTimeSlices(noteEvents)
    if (slices.isEmpty()) return Pair(emptyList(), emptyList())

    val outNotes = mutableListOf<NoteEvent>()
    val outRests = mutableListOf<RestEvent>()
    var prevMelodyPitch: Int? = null
    var prevMelodyEventId: String? = null
    var counter = 0

    for (slice in slices) {
        if (slice.notes.isEmpty()) {
            counter++
            outRests.add(
                RestEvent(
                    eventId = "red_rest_$counter",
                    tick = slice.tick,
                    durationTicks = slice.lengthTicks,
                    staff = "UPPER",
                )
            )
            continue
        }

        val (selected, melody, bass) = chooseSliceNotes(
            slice.notes, cap, tuningMidi, prevMelodyPitch, prevMelodyEventId
        )

        // Emit selected notes sorted highest to lowest
        val sorted = selected.sortedByDescending { it.pitchMidi }
        for (n in sorted) {
            counter++
            outNotes.add(
                NoteEvent(
                    eventId = "red_note_$counter",
                    tick = slice.tick,
                    durationTicks = slice.lengthTicks,
                    pitchMidi = n.pitchMidi,
                    velocity = n.velocity,
                    role = roleForNote(n, selected, melody, bass),
                    staff = if (n.pitchMidi >= splitMidi) "UPPER" else "LOWER",
                    tieStart = n.tieStart,
                    tieStop = n.tieStop,
                    lyric = n.lyric,
                    chordSymbol = n.chordSymbol,
                    sourceEventId = n.eventId,
                    melodyHint = n.melodyHint,
                )
            )
        }

        if (melody != null) {
            prevMelodyPitch = melody.pitchMidi
            prevMelodyEventId = melody.eventId
        }
    }

    outNotes.sortWith(compareBy({ it.tick }, { -it.pitchMidi }))
    return Pair(outNotes, outRests)
}
