package com.leokinder2k.koratuningcompanion.instrumentconfig.model

enum class NoteName(val semitone: Int, val symbol: String) {
    C(0, "C"),
    C_SHARP(1, "C#"),
    D(2, "D"),
    D_SHARP(3, "D#"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#"),
    G(7, "G"),
    G_SHARP(8, "G#"),
    A(9, "A"),
    A_SHARP(10, "A#"),
    B(11, "B"),
    ;

    companion object {
        fun fromSemitone(value: Int): NoteName {
            val normalized = Math.floorMod(value, SEMITONES_PER_OCTAVE)
            return entries.first { it.semitone == normalized }
        }
    }
}

data class Pitch(
    val note: NoteName,
    val octave: Int
) {
    fun plusSemitones(semitones: Int): Pitch {
        val total = octave * SEMITONES_PER_OCTAVE + note.semitone + semitones
        val normalizedNote = NoteName.fromSemitone(total)
        val normalizedOctave = Math.floorDiv(total, SEMITONES_PER_OCTAVE)
        return Pitch(note = normalizedNote, octave = normalizedOctave)
    }

    fun asText(): String = "${note.symbol}$octave"

    companion object {
        private val PITCH_PATTERN = Regex("^([A-Ga-g])([#bB]?)(-?\\d+)$")

        fun parse(value: String): Pitch? {
            val input = value.trim()
            if (input.isEmpty()) {
                return null
            }

            val match = PITCH_PATTERN.matchEntire(input) ?: return null
            val letter = match.groupValues[1].uppercase()
            val accidental = match.groupValues[2]
            val octave = match.groupValues[3].toIntOrNull() ?: return null

            val naturalSemitone = when (letter) {
                "C" -> 0
                "D" -> 2
                "E" -> 4
                "F" -> 5
                "G" -> 7
                "A" -> 9
                "B" -> 11
                else -> return null
            }

            var semitone = naturalSemitone + when (accidental) {
                "#" -> 1
                "b", "B" -> -1
                else -> 0
            }

            var normalizedOctave = octave
            if (semitone < 0) {
                semitone += SEMITONES_PER_OCTAVE
                normalizedOctave -= 1
            } else if (semitone >= SEMITONES_PER_OCTAVE) {
                semitone -= SEMITONES_PER_OCTAVE
                normalizedOctave += 1
            }

            return Pitch(
                note = NoteName.fromSemitone(semitone),
                octave = normalizedOctave
            )
        }
    }
}

private const val SEMITONES_PER_OCTAVE = 12

