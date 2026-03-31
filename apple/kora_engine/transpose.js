const PITCH_CLASS_TO_SEMITONE = Object.freeze({
  C: 0,
  "C#": 1,
  Db: 1,
  D: 2,
  "D#": 3,
  Eb: 3,
  E: 4,
  F: 5,
  "F#": 6,
  Gb: 6,
  G: 7,
  "G#": 8,
  Ab: 8,
  A: 9,
  "A#": 10,
  Bb: 10,
  B: 11,
});

const SEMITONE_TO_SHARP = Object.freeze(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]);
const SEMITONE_TO_FLAT = Object.freeze(["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"]);

const MAJOR_SCALE_OFFSETS = Object.freeze([0, 2, 4, 5, 7, 9, 11]);
const NATURAL_MINOR_SCALE_OFFSETS = Object.freeze([0, 2, 3, 5, 7, 8, 10]);

// Major-key tonic pitch class for each key-signature fifths value.
const FIFTHS_TO_TONIC_PC = Object.freeze({
  "-7": 11, // Cb
  "-6": 6, // Gb
  "-5": 1, // Db
  "-4": 8, // Ab
  "-3": 3, // Eb
  "-2": 10, // Bb
  "-1": 5, // F
  "0": 0, // C
  "1": 7, // G
  "2": 2, // D
  "3": 9, // A
  "4": 4, // E
  "5": 11, // B
  "6": 6, // F#
  "7": 1, // C#
});

function mod12(n) {
  return ((n % 12) + 12) % 12;
}

function modN(n, m) {
  return ((n % m) + m) % m;
}

function floorDiv(n, d) {
  return Math.floor(n / d);
}

function deepClone(value) {
  return JSON.parse(JSON.stringify(value));
}

function parsePitchClass(name) {
  return PITCH_CLASS_TO_SEMITONE[name] ?? null;
}

function semitoneToPitchClassName(semitone, preferFlats) {
  const idx = mod12(semitone);
  return preferFlats ? SEMITONE_TO_FLAT[idx] : SEMITONE_TO_SHARP[idx];
}

function transposePitchClassName(name, semitones) {
  const src = parsePitchClass(name);
  if (src === null) return name;
  const preferFlats = name.includes("b");
  const out = mod12(src + semitones);
  return semitoneToPitchClassName(out, preferFlats);
}

export function transposeMidiPitch({ midi, semitones }) {
  if (!Number.isInteger(midi) || midi < 0 || midi > 127) throw new Error(`Invalid MIDI pitch: ${midi}`);
  if (!Number.isInteger(semitones)) throw new Error(`Invalid semitones: ${semitones}`);
  const out = midi + semitones;
  if (out < 0 || out > 127) throw new Error(`Transposed MIDI out of range: ${midi} + ${semitones} => ${out}`);
  return out;
}

export function transposeChordSymbolText({ text, semitones }) {
  if (typeof text !== "string") throw new Error(`Invalid chord symbol text: ${text}`);
  if (!Number.isInteger(semitones)) throw new Error(`Invalid semitones: ${semitones}`);

  const m = text.match(/^(\s*)([A-G](?:#|b)?)(.*)$/);
  if (!m) return text;

  const leadWs = m[1];
  const root = m[2];
  let tail = m[3];

  const transposedRoot = transposePitchClassName(root, semitones);
  tail = tail.replace(/\/([A-G](?:#|b)?)/g, (_, bass) => `/${transposePitchClassName(bass, semitones)}`);

  return `${leadWs}${transposedRoot}${tail}`;
}

export function transposeKeySignatureFifths({ fifths, semitones }) {
  if (!Number.isInteger(fifths) || fifths < -7 || fifths > 7) throw new Error(`Invalid key-signature fifths: ${fifths}`);
  if (!Number.isInteger(semitones)) throw new Error(`Invalid semitones: ${semitones}`);

  const tonicPc = FIFTHS_TO_TONIC_PC[String(fifths)];
  const targetPc = mod12(tonicPc + semitones);
  const candidates = [];
  for (let f = -7; f <= 7; f++) {
    if (FIFTHS_TO_TONIC_PC[String(f)] === targetPc) candidates.push(f);
  }
  if (candidates.length === 0) return fifths;

  // Best-effort deterministic pick: nearest to source fifths, then smallest abs(fifths).
  candidates.sort((a, b) => Math.abs(a - fifths) - Math.abs(b - fifths) || Math.abs(a) - Math.abs(b));
  return candidates[0];
}

function normalizeKeySignatureRows(keySignatures) {
  const rows = [];
  for (const ks of keySignatures ?? []) {
    if (!Number.isInteger(ks?.tick) || ks.tick < 0) continue;
    if (!Number.isInteger(ks?.fifths) || ks.fifths < -7 || ks.fifths > 7) continue;
    rows.push({
      tick: ks.tick,
      fifths: ks.fifths,
      mode: ks.mode === "MINOR" ? "MINOR" : "MAJOR",
    });
  }
  rows.sort((a, b) => a.tick - b.tick || a.fifths - b.fifths || a.mode.localeCompare(b.mode));
  if (rows.length === 0 || rows[0].tick !== 0) {
    rows.unshift({ tick: 0, fifths: 0, mode: "MAJOR" });
  }
  return rows;
}

function keySignatureAtTick(rows, tick) {
  // rows are normalized with a fallback at tick 0.
  let cur = rows[0];
  for (const r of rows) {
    if (r.tick > tick) break;
    cur = r;
  }
  return cur;
}

function tonicPitchClassForKey({ fifths, mode }) {
  const majorTonic = FIFTHS_TO_TONIC_PC[String(fifths)] ?? 0;
  if (mode === "MINOR") {
    // Relative minor is +9 semitones from major tonic (e.g., C major -> A minor).
    return mod12(majorTonic + 9);
  }
  return mod12(majorTonic);
}

function scaleOffsetsForMode(mode) {
  return mode === "MINOR" ? NATURAL_MINOR_SCALE_OFFSETS : MAJOR_SCALE_OFFSETS;
}

function signedPcDelta(targetPc, basePc) {
  const raw = mod12(targetPc - basePc); // 0..11
  return raw > 6 ? raw - 12 : raw; // -5..6 (and -6 if raw==6? raw==6 -> 6, fine)
}

function bestEffortDegreeForPitchClass({ pitchClass, tonicPc, scaleOffsets }) {
  const candidates = [];
  for (let deg = 0; deg < 7; deg++) {
    const scalePc = mod12(tonicPc + scaleOffsets[deg]);
    const offset = signedPcDelta(pitchClass, scalePc);
    candidates.push({ deg, scalePc, offset, abs: Math.abs(offset) });
  }

  candidates.sort((a, b) =>
    a.abs - b.abs ||
    // Tie-break: prefer non-negative offsets (e.g., F# prefers F+1 over G-1).
    (b.offset >= 0 ? 1 : 0) - (a.offset >= 0 ? 1 : 0) ||
    a.deg - b.deg);

  const bestAbs = candidates[0].abs;
  const tied = candidates.filter((c) => c.abs === bestAbs);
  return { best: candidates[0], tied };
}

export function transposeScoreDiatonic({
  score,
  steps,
}) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  if (!Number.isInteger(steps)) throw new Error(`Invalid steps: ${steps}`);

  const out = deepClone(score);
  const warnings = [];
  const ksRows = normalizeKeySignatureRows(out.keySignatures);

  if (Array.isArray(out.events)) {
    for (const e of out.events) {
      if (e?.type !== "NOTE" || !Number.isInteger(e.pitchMidi)) continue;
      if (!Number.isInteger(e.tick) || e.tick < 0) continue;

      const midi = e.pitchMidi;
      if (midi < 0 || midi > 127) throw new Error(`Invalid NOTE pitchMidi: ${midi}`);

      const ks = keySignatureAtTick(ksRows, e.tick);
      const tonicPc = tonicPitchClassForKey(ks);
      const scaleOffsets = scaleOffsetsForMode(ks.mode);
      const pitchClass = mod12(midi);

      const { best, tied } = bestEffortDegreeForPitchClass({ pitchClass, tonicPc, scaleOffsets });
      if (tied.length > 1) {
        warnings.push({
          type: "AMBIGUOUS_DEGREE",
          eventId: typeof e.eventId === "string" ? e.eventId : null,
          tick: e.tick,
          pitchMidi: midi,
          key: { fifths: ks.fifths, mode: ks.mode },
          candidates: tied.map((c) => ({ degree: c.deg, chromaticOffset: c.offset })),
        });
      }

      const baseMidi = midi - best.offset;
      const sum = best.deg + steps;
      const octaveShift = floorDiv(sum, 7);
      const deg2 = modN(sum, 7);
      const delta = (scaleOffsets[deg2] - scaleOffsets[best.deg]) + octaveShift * 12;
      const transposed = baseMidi + delta + best.offset;

      if (!Number.isInteger(transposed) || transposed < 0 || transposed > 127) {
        throw new Error(`Transposed MIDI out of range: ${midi} (steps=${steps}) => ${transposed}`);
      }
      e.pitchMidi = transposed;
    }
  }

  return { score: out, warnings };
}

export function transposeScoreSemitone({
  score,
  semitones,
  transposeKeySignatures = true,
  transposeChordSymbols = true,
}) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  if (!Number.isInteger(semitones)) throw new Error(`Invalid semitones: ${semitones}`);

  const out = deepClone(score);

  if (Array.isArray(out.events)) {
    for (const e of out.events) {
      if (e?.type === "NOTE" && Number.isInteger(e.pitchMidi)) {
        e.pitchMidi = transposeMidiPitch({ midi: e.pitchMidi, semitones });
      }
      if (transposeChordSymbols) {
        if (e?.type === "CHORD_SYMBOL" && typeof e.text === "string") {
          e.text = transposeChordSymbolText({ text: e.text, semitones });
        }
        if (e?.chordSymbol && typeof e.chordSymbol.text === "string") {
          e.chordSymbol.text = transposeChordSymbolText({ text: e.chordSymbol.text, semitones });
        } else if (typeof e?.chordSymbol === "string") {
          e.chordSymbol = transposeChordSymbolText({ text: e.chordSymbol, semitones });
        }
      }
    }
  }

  if (transposeKeySignatures && Array.isArray(out.keySignatures)) {
    for (const ks of out.keySignatures) {
      if (Number.isInteger(ks?.fifths)) {
        ks.fifths = transposeKeySignatureFifths({ fifths: ks.fifths, semitones });
      }
    }
  }

  return out;
}
