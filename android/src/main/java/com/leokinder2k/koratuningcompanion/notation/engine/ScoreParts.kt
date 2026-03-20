package com.leokinder2k.koratuningcompanion.notation.engine

// Port of parts.js

data class ScoredPart(
    val partId: String,
    val name: String,
    val score: Double,
    val hasNotes: Boolean,
    val avgPitch: Double,
    val range: Int,
)

/**
 * Pick the part most likely to contain the melody.
 * Prefers parts with name matching "melody/voice/lead", then highest pitch range + average pitch.
 */
fun pickMelodyPart(parts: List<Pair<String, List<NoteEvent>>>): String? {
    val nameBoostRe = Regex("""\b(melody|voice|lead)\b""", RegexOption.IGNORE_CASE)

    data class Entry(val partId: String, val name: String, val score: Double, val avgPitch: Double, val hasNotes: Boolean)

    val scored = parts.map { (partId, notes) ->
        val pitches = notes.mapNotNull { if (it.pitchMidi in 0..127) it.pitchMidi else null }
        if (pitches.isEmpty()) {
            Entry(partId, partId, Double.NEGATIVE_INFINITY, 0.0, false)
        } else {
            val minP = pitches.min()
            val maxP = pitches.max()
            val avg = pitches.average()
            val range = maxP - minP
            val nameBoost = if (nameBoostRe.containsMatchIn(partId)) 1000.0 else 0.0
            Entry(partId, partId, range * 10.0 + avg + nameBoost, avg, true)
        }
    }

    return scored
        .filter { it.hasNotes }
        .maxWithOrNull(compareBy({ it.score }, { it.avgPitch }))
        ?.partId
}
