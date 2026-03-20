package com.leokinder2k.koratuningcompanion.notation.engine

// Port of pipeline.js

private fun normalizeRole(role: String?): NoteRole = when (role?.uppercase()) {
    "BASS" -> NoteRole.BASS
    "HARMONY" -> NoteRole.HARMONY
    else -> NoteRole.MELODY
}

private fun measureNumberAtTick(measures: List<MeasureInfo>, tick: Int): Int {
    if (measures.isEmpty()) return 1
    var best = measures[0]
    for (m in measures) {
        if (m.startTick <= tick && m.startTick >= best.startTick) best = m
    }
    return best.measureNumber
}

fun mapSimplifiedScoreToKora(
    instrumentType: KoraInstrumentType,
    tuningMidiByStringId: Map<String, Int>,
    score: SimplifiedScore,
    assignments: Map<String, List<String>>? = null,
): KoraMappingResult {
    val resolvedAssignments = assignments ?: defaultStringToDigitAssignments(instrumentType)
    validateStringToDigitAssignments(instrumentType, resolvedAssignments)

    // Sort note events by tick ascending, then pitchMidi descending (higher notes first)
    val noteEvents = score.noteEvents
        .sortedWith(compareBy({ it.tick }, { -it.pitchMidi }))

    val prevByRole = mutableMapOf<NoteRole, PrevChoice>()
    val mappedEvents = mutableListOf<MappedEvent>()
    var currentTickForHandSums = -1
    val handSums = mutableMapOf('L' to 0, 'R' to 0)

    for (e in noteEvents) {
        // Reset per-tick hand sums at the start of each new tick
        if (e.tick != currentTickForHandSums) {
            currentTickForHandSums = e.tick
            handSums['L'] = 0
            handSums['R'] = 0
        }

        val role = normalizeRole(e.role)
        val prevChoice = prevByRole[role]
        val currentHandSums = handSums.toMap()

        val mapped = mapPitchToKora(
            instrumentType, tuningMidiByStringId, resolvedAssignments,
            e.pitchMidi, role, prevChoice, currentHandSums
        )

        val measureNumber = measureNumberAtTick(score.measures, e.tick)

        val out = MappedEvent(
            sourceEventId = e.eventId,
            tick = e.tick,
            durationTicks = e.durationTicks,
            measureNumber = measureNumber,
            role = role,
            pitchMidi = e.pitchMidi,
            stringId = mapped.stringId,
            digitLine = mapped.digitLine,
            renderedNumber = mapped.renderedNumber,
            accidentalSuggestion = mapped.accidentalSuggestion,
            omit = mapped.omit,
        )
        mappedEvents.add(out)

        if (!mapped.omit && mapped.stringId != null && mapped.digitLine != null) {
            prevByRole[role] = PrevChoice(mapped.stringId, mapped.digitLine)
            // Accumulate rendered number into per-tick hand sum
            if (mapped.renderedNumber != null) {
                val side = parseStringId(mapped.stringId).side
                handSums[side] = (handSums[side] ?: 0) + mapped.renderedNumber
            }
        }
    }

    val lastMeasureNumber = if (score.measures.isEmpty()) {
        mappedEvents.maxOfOrNull { it.measureNumber } ?: 1
    } else {
        score.measures.maxOf { it.measureNumber }
    }

    val retunePlan = generateRetunePlan(mappedEvents, lastMeasureNumber)

    return KoraMappingResult(events = mappedEvents, retunePlan = retunePlan)
}
