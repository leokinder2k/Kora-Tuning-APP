package com.leokinder2k.koratuningcompanion.notation.engine

// Port of transpose.js

private val PITCH_CLASS_TO_SEMITONE = mapOf(
    "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
    "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8,
    "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11,
)

private val SEMITONE_TO_SHARP = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val SEMITONE_TO_FLAT  = arrayOf("C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B")

private val MAJOR_SCALE_OFFSETS  = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private val MINOR_SCALE_OFFSETS  = intArrayOf(0, 2, 3, 5, 7, 8, 10)

private val FIFTHS_TO_TONIC_PC = mapOf(
    -7 to 11, -6 to 6, -5 to 1, -4 to 8, -3 to 3, -2 to 10, -1 to 5,
     0 to 0,   1 to 7,  2 to 2,  3 to 9,  4 to 4,  5 to 11,  6 to 6,  7 to 1
)

private fun mod12(n: Int): Int = ((n % 12) + 12) % 12
private fun modN(n: Int, m: Int): Int = ((n % m) + m) % m

fun transposeKeySignatureFifths(fifths: Int, semitones: Int): Int {
    val tonicPc = FIFTHS_TO_TONIC_PC[fifths.coerceIn(-7, 7)] ?: 0
    val targetPc = mod12(tonicPc + semitones)
    val candidates = FIFTHS_TO_TONIC_PC.entries
        .filter { it.value == targetPc }
        .map { it.key }
    if (candidates.isEmpty()) return fifths
    return candidates.minWithOrNull(
        compareBy({ kotlin.math.abs(it - fifths) }, { kotlin.math.abs(it) })
    ) ?: fifths
}

/** Semitone transpose: shift every note MIDI pitch and optionally key signatures. */
fun transposeScoreSemitone(
    score: SimplifiedScore,
    semitones: Int,
    transposeKeySignatures: Boolean = true,
): SimplifiedScore {
    val newNotes = score.noteEvents.map { e ->
        val transposed = e.pitchMidi + semitones
        require(transposed in 0..127) { "Transposed pitch out of range: ${e.pitchMidi} + $semitones" }
        e.copy(pitchMidi = transposed)
    }
    val newKeySigs = if (transposeKeySignatures) {
        score.keySignatures.map { ks ->
            ks.copy(fifths = transposeKeySignatureFifths(ks.fifths, semitones))
        }
    } else score.keySignatures

    return score.copy(noteEvents = newNotes, keySignatures = newKeySigs)
}
