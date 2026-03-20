package com.leokinder2k.koratuningcompanion.notation.engine

// Port of notes.js

private val NOTE_OFFSETS = mapOf(
    'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11
)

private val MIDI_NAMES = arrayOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
)

/** Scientific pitch notation: C4 = 60. Supports # and b accidentals. */
fun noteNameToMidi(noteName: String): Int {
    val m = Regex("""^([A-Ga-g])([#b]?)(-?\d+)$""").matchEntire(noteName.trim())
        ?: error("Invalid note name: $noteName")
    val letter = m.groupValues[1].uppercase()[0]
    val accidental = m.groupValues[2]
    val octave = m.groupValues[3].toInt()
    var semitone = NOTE_OFFSETS[letter] ?: error("Unknown note letter: $letter")
    if (accidental == "#") semitone++
    if (accidental == "b") semitone--
    val midi = (octave + 1) * 12 + semitone
    require(midi in 0..127) { "MIDI out of range for $noteName: $midi" }
    return midi
}

fun midiToNoteName(midi: Int): String {
    require(midi in 0..127) { "Invalid MIDI: $midi" }
    val octave = midi / 12 - 1
    val pc = midi % 12
    return "${MIDI_NAMES[pc]}$octave"
}
