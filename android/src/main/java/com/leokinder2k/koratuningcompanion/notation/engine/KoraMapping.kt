package com.leokinder2k.koratuningcompanion.notation.engine

// Port of mapping.js

private fun accidentalSuggestionForDelta(delta: Int): AccidentalSuggestion = when (delta) {
    0 -> AccidentalSuggestion.NONE
    1 -> AccidentalSuggestion.SHARP
    -1 -> AccidentalSuggestion.FLAT
    else -> AccidentalSuggestion.NONE
}

private fun digitPriorityRank(role: NoteRole, digitLine: String): Int {
    return if (role == NoteRole.BASS) {
        when (digitLine) { "LT" -> 0; "RT" -> 1; "LF" -> 2; else -> 3 }
    } else {
        when (digitLine) { "RF" -> 0; "LF" -> 1; "RT" -> 2; else -> 3 }
    }
}

private fun correspondingCounterpartLine(digitLine: String): String? = when (digitLine) {
    "LF" -> "LT"; "RF" -> "RT"; "LT" -> "LF"; "RT" -> "RF"; else -> null
}

private fun shouldSwitchToCounterpart(digitLine: String, renderedNum: Int, availableLines: List<String>): Boolean {
    val counterpart = correspondingCounterpartLine(digitLine) ?: return false
    if (renderedNum <= 6) return false
    return counterpart in availableLines
}

data class PrevChoice(val stringId: String, val digitLine: String)

private fun continuityPenalty(prevChoice: PrevChoice?, candidate: Pair<String, String>): Int {
    if (prevChoice == null) return 0
    val prev = parseStringId(prevChoice.stringId)
    val cur = parseStringId(candidate.first)
    var p = 0
    if (prev.side != cur.side) p += 20
    if (prevChoice.digitLine != candidate.second) p += 5
    p += kotlin.math.abs(prev.physicalIndexFromGourd - cur.physicalIndexFromGourd)
    return p
}

data class MappingCandidate(
    val omit: Boolean,
    val stringId: String?,
    val digitLine: String?,
    val renderedNumber: Int?,
    val accidentalSuggestion: AccidentalSuggestion,
    val score: Double,
)

fun enumerateKoraMappings(
    instrumentType: KoraInstrumentType,
    tuningMidiByStringId: Map<String, Int>,
    assignments: Map<String, List<String>>,
    pitchMidi: Int,
    role: NoteRole,
    prevChoice: PrevChoice? = null,
    currentHandSums: Map<Char, Int>? = null,
): List<MappingCandidate> {
    val exact = mutableListOf<String>()
    val near = mutableListOf<String>()
    for ((stringId, tunedMidi) in tuningMidiByStringId) {
        when {
            tunedMidi == pitchMidi -> exact.add(stringId)
            kotlin.math.abs(tunedMidi - pitchMidi) == 1 -> near.add(stringId)
        }
    }

    val candidates = when {
        exact.isNotEmpty() -> exact
        near.isNotEmpty() -> near
        else -> return listOf(
            MappingCandidate(
                omit = true, stringId = null, digitLine = null,
                renderedNumber = null, accidentalSuggestion = AccidentalSuggestion.NONE,
                score = Double.POSITIVE_INFINITY
            )
        )
    }

    val out = mutableListOf<MappingCandidate>()

    for (stringId in candidates) {
        val digitLines = digitLinesForString(assignments, stringId)
        val (side, _) = parseStringId(stringId)
        val n = if (side == 'L') instrumentType.leftCount else instrumentType.rightCount

        for (digitLine in digitLines) {
            val priorityRank = digitPriorityRank(role, digitLine)
            val rNum = renderedNumber(instrumentType, stringId, digitLine)

            if (shouldSwitchToCounterpart(digitLine, rNum, digitLines)) continue

            val continuity = continuityPenalty(prevChoice, stringId to digitLine)
            val ease = rNum
            val handSum = currentHandSums?.get(side) ?: 0
            val handSumPenalty = if (handSum + rNum >= 13) 500_000.0 else 0.0

            val score = priorityRank * 1_000_000.0 + handSumPenalty + ease * 10_000.0 + continuity

            val delta = pitchMidi - (tuningMidiByStringId[stringId] ?: pitchMidi)
            val acc = accidentalSuggestionForDelta(delta)

            out.add(
                MappingCandidate(
                    omit = false, stringId = stringId, digitLine = digitLine,
                    renderedNumber = rNum, accidentalSuggestion = acc, score = score,
                )
            )
        }
    }

    out.sortWith(compareBy({ it.score }, { it.stringId }, { it.digitLine }))
    return out
}

fun mapPitchToKora(
    instrumentType: KoraInstrumentType,
    tuningMidiByStringId: Map<String, Int>,
    assignments: Map<String, List<String>>,
    pitchMidi: Int,
    role: NoteRole,
    prevChoice: PrevChoice? = null,
    currentHandSums: Map<Char, Int>? = null,
): MappingCandidate {
    return enumerateKoraMappings(
        instrumentType, tuningMidiByStringId, assignments,
        pitchMidi, role, prevChoice, currentHandSums
    ).first()
}
