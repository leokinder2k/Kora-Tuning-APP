const NOTE_OFFSETS = Object.freeze({
  C: 0,
  D: 2,
  E: 4,
  F: 5,
  G: 7,
  A: 9,
  B: 11,
});

export function noteNameToMidi(noteName) {
  // Scientific pitch notation: C4 = 60. Flats and sharps: Bb3, F#2.
  if (typeof noteName !== "string") throw new Error(`Invalid note name: ${noteName}`);
  const m = noteName.trim().match(/^([A-Ga-g])([#b]?)(-?\d+)$/);
  if (!m) throw new Error(`Invalid note name format: ${noteName}`);

  const letter = m[1].toUpperCase();
  const accidental = m[2];
  const octave = Number.parseInt(m[3], 10);
  if (!Number.isInteger(octave)) throw new Error(`Invalid octave: ${noteName}`);

  let semitone = NOTE_OFFSETS[letter];
  if (accidental === "#") semitone += 1;
  if (accidental === "b") semitone -= 1;

  // MIDI: C-1 = 0
  const midi = (octave + 1) * 12 + semitone;
  if (midi < 0 || midi > 127) throw new Error(`Out of MIDI range: ${noteName} => ${midi}`);
  return midi;
}

export function midiToNoteName(midi) {
  // Mostly for debugging; prefers sharps for accidentals.
  if (!Number.isInteger(midi) || midi < 0 || midi > 127) throw new Error(`Invalid MIDI: ${midi}`);
  const octave = Math.floor(midi / 12) - 1;
  const pc = midi % 12;
  const NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  return `${NAMES[pc]}${octave}`;
}

