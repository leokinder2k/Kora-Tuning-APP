import { buildPlaybackSchedule, PlayMode, RepeatsMode } from "./scheduler.js";

function ensureScore(score) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  return score;
}

function ensureIntInRange(name, value, min, max) {
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`Invalid ${name}: ${value} (expected ${min}..${max})`);
  }
  return value;
}

function ensureString(name, value) {
  if (typeof value !== "string") throw new Error(`${name} must be a string`);
  return value;
}

function u16be(n) {
  return Uint8Array.from([(n >> 8) & 0xff, n & 0xff]);
}

function u32be(n) {
  return Uint8Array.from([(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff]);
}

function asciiBytes(text) {
  const bytes = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i) & 0x7f;
  return bytes;
}

function concatBytes(chunks) {
  const total = chunks.reduce((s, c) => s + c.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

function writeVarLen(value) {
  let v = ensureIntInRange("varlen value", value, 0, 0x0fffffff);
  const out = [];
  out.push(v & 0x7f);
  v >>>= 7;
  while (v > 0) {
    out.unshift((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  return Uint8Array.from(out);
}

function metaEvent(typeByte, dataBytes) {
  return concatBytes([Uint8Array.from([0xff, typeByte]), writeVarLen(dataBytes.length), dataBytes]);
}

function trackNameEvent({ tick, name }) {
  const textBytes = new TextEncoder().encode(ensureString("trackName", name));
  return { tick, rank: 0, bytes: metaEvent(0x03, textBytes) };
}

function tempoEvent({ tick, bpm }) {
  if (!Number.isFinite(bpm) || bpm <= 0) throw new Error(`Invalid bpm: ${bpm}`);
  let mpq = Math.round(60000000 / bpm);
  mpq = Math.max(1, Math.min(0xffffff, mpq));
  const data = Uint8Array.from([(mpq >> 16) & 0xff, (mpq >> 8) & 0xff, mpq & 0xff]);
  return { tick, rank: 0, bytes: metaEvent(0x51, data) };
}

function timeSignatureEvent({ tick, num, den }) {
  ensureIntInRange("timeSignature.num", num, 1, 255);
  ensureIntInRange("timeSignature.den", den, 1, 1024);
  let denPow = 0;
  let d = den;
  while ((d & 1) === 0) {
    denPow += 1;
    d >>>= 1;
  }
  const isPowerOfTwo = d === 1;
  if (!isPowerOfTwo) {
    // Best-effort: clamp to 4/4 in weird cases.
    denPow = 2;
    den = 4;
  }
  const data = Uint8Array.from([num & 0xff, denPow & 0xff, 24, 8]);
  return { tick, rank: 0, bytes: metaEvent(0x58, data) };
}

function keySignatureEvent({ tick, fifths, mode }) {
  ensureIntInRange("keySignature.fifths", fifths, -7, 7);
  const mi = mode === "MINOR" ? 1 : 0;
  const sf = fifths < 0 ? 256 + fifths : fifths;
  const data = Uint8Array.from([sf & 0xff, mi & 0xff]);
  return { tick, rank: 0, bytes: metaEvent(0x59, data) };
}

function programChangeEvent({ tick, channel, program }) {
  ensureIntInRange("midiChannel", channel, 0, 15);
  ensureIntInRange("midiProgram", program, 0, 127);
  return { tick, rank: 1, bytes: Uint8Array.from([0xc0 | channel, program & 0x7f]) };
}

function noteOnEvent({ tick, channel, pitch, velocity }) {
  ensureIntInRange("midiChannel", channel, 0, 15);
  ensureIntInRange("pitch", pitch, 0, 127);
  ensureIntInRange("velocity", velocity, 0, 127);
  return { tick, rank: 3, bytes: Uint8Array.from([0x90 | channel, pitch & 0x7f, velocity & 0x7f]) };
}

function noteOffEvent({ tick, channel, pitch, velocity }) {
  ensureIntInRange("midiChannel", channel, 0, 15);
  ensureIntInRange("pitch", pitch, 0, 127);
  ensureIntInRange("velocity", velocity, 0, 127);
  return { tick, rank: 2, bytes: Uint8Array.from([0x80 | channel, pitch & 0x7f, velocity & 0x7f]) };
}

function encodeTrackChunk(events) {
  const sorted = [...events].sort((a, b) => a.tick - b.tick || a.rank - b.rank);
  const parts = [];
  let lastTick = 0;
  for (const e of sorted) {
    const tick = ensureIntInRange("event.tick", e.tick, 0, 0x0fffffff);
    const delta = tick - lastTick;
    if (delta < 0) throw new Error(`Events out of order: ${tick} < ${lastTick}`);
    lastTick = tick;
    parts.push(writeVarLen(delta));
    parts.push(e.bytes);
  }

  // End of track.
  parts.push(writeVarLen(0));
  parts.push(Uint8Array.from([0xff, 0x2f, 0x00]));

  const data = concatBytes(parts);
  return concatBytes([asciiBytes("MTrk"), u32be(data.length), data]);
}

function encodeHeaderChunk({ format, trackCount, ppq }) {
  ensureIntInRange("midiFormat", format, 0, 1);
  ensureIntInRange("trackCount", trackCount, 1, 0xffff);
  ensureIntInRange("ppq", ppq, 1, 0x7fff);
  return concatBytes([
    asciiBytes("MThd"),
    u32be(6),
    u16be(format),
    u16be(trackCount),
    u16be(ppq),
  ]);
}

function writeMidiFileBytes({ ppq, tracks }) {
  const header = encodeHeaderChunk({ format: 1, trackCount: tracks.length, ppq });
  const chunks = tracks.map((t) => encodeTrackChunk(t));
  return concatBytes([header, ...chunks]);
}

function compressTempoRows(tempoRows) {
  const rows = (tempoRows ?? [])
    .filter((t) => Number.isInteger(t?.tick) && t.tick >= 0 && Number.isFinite(t?.bpm) && t.bpm > 0)
    .map((t) => ({ tick: t.tick, bpm: t.bpm }))
    .sort((a, b) => a.tick - b.tick || a.bpm - b.bpm);

  if (rows.length === 0) return [{ tick: 0, bpm: 120 }];
  if (rows[0].tick !== 0) rows.unshift({ tick: 0, bpm: rows[0].bpm });

  const out = [];
  let last = null;
  for (const r of rows) {
    if (!last || r.bpm !== last.bpm) {
      out.push(r);
      last = r;
    }
  }
  return out;
}

function compressRowsByValue(rows, valueKey) {
  const sorted = [...rows].sort((a, b) => a.tick - b.tick);
  const out = [];
  let lastVal = null;
  for (const r of sorted) {
    const val = r[valueKey];
    if (lastVal === null || val !== lastVal) {
      out.push(r);
      lastVal = val;
    }
  }
  return out;
}

function occurrencesFromSchedule(schedule) {
  return Array.isArray(schedule?.occurrences) ? schedule.occurrences : [];
}

function measuresByNumber(score) {
  const map = new Map();
  for (const m of score.measures ?? []) {
    if (!Number.isInteger(m?.measureNumber) || m.measureNumber < 1) continue;
    map.set(m.measureNumber, m);
  }
  return map;
}

function expandKeySignaturesAcrossOccurrences({ keySignatures, occurrences }) {
  const rows = [];
  for (const ks of keySignatures ?? []) {
    if (!Number.isInteger(ks?.tick) || ks.tick < 0) continue;
    if (!Number.isInteger(ks?.fifths)) continue;
    const mode = ks.mode === "MINOR" ? "MINOR" : "MAJOR";
    for (const occ of occurrences) {
      const sourceStart = occ.sourceStartTick;
      const len = occ.lengthTicks;
      if (!Number.isInteger(sourceStart) || !Number.isInteger(len) || len <= 0) continue;
      if (sourceStart <= ks.tick && ks.tick < sourceStart + len) {
        rows.push({
          tick: occ.playbackStartTick + (ks.tick - sourceStart),
          fifths: ks.fifths,
          mode,
        });
      }
    }
  }
  rows.sort((a, b) => a.tick - b.tick || a.fifths - b.fifths || a.mode.localeCompare(b.mode));
  if (rows.length === 0) return [];
  if (rows[0].tick !== 0) rows.unshift({ tick: 0, fifths: rows[0].fifths, mode: rows[0].mode });
  return compressRowsByValue(rows, "fifths");
}

function buildTimeSignatureRowsFromOccurrences({ score, occurrences }) {
  const measureMap = measuresByNumber(score);
  const rows = [];
  for (const occ of occurrences) {
    const m = measureMap.get(occ.measureNumber);
    const num = Number.isInteger(m?.timeSignature?.num) && m.timeSignature.num > 0 ? m.timeSignature.num : 4;
    const den = Number.isInteger(m?.timeSignature?.den) && m.timeSignature.den > 0 ? m.timeSignature.den : 4;
    rows.push({ tick: occ.playbackStartTick, num, den, value: `${num}/${den}` });
  }
  if (rows.length === 0) return [{ tick: 0, num: 4, den: 4, value: "4/4" }];
  rows.sort((a, b) => a.tick - b.tick);
  if (rows[0].tick !== 0) rows.unshift({ tick: 0, num: rows[0].num, den: rows[0].den, value: rows[0].value });
  return compressRowsByValue(rows, "value");
}

function buildVelocityBySourceEventId(score) {
  const map = new Map();
  for (const e of score.events ?? []) {
    if (e?.type !== "NOTE") continue;
    if (typeof e.eventId !== "string") continue;
    const v = Number.isInteger(e.velocity) ? e.velocity : null;
    if (v !== null) map.set(e.eventId, Math.max(0, Math.min(127, v)));
  }
  return map;
}

function buildNoteEventsFromSchedule({ schedule, score, channel, defaultVelocity }) {
  const velocityById = buildVelocityBySourceEventId(score);
  const out = [];
  for (const n of schedule.noteEvents ?? []) {
    if (!Number.isInteger(n?.tick) || n.tick < 0) continue;
    if (!Number.isInteger(n?.durationTicks) || n.durationTicks <= 0) continue;
    if (!Number.isInteger(n?.pitchMidi) || n.pitchMidi < 0 || n.pitchMidi > 127) continue;
    const velocity = velocityById.get(n.sourceEventId) ?? defaultVelocity;
    out.push(noteOnEvent({ tick: n.tick, channel, pitch: n.pitchMidi, velocity }));
    out.push(noteOffEvent({ tick: n.tick + n.durationTicks, channel, pitch: n.pitchMidi, velocity: 64 }));
  }
  // Ensure offs come before ons at the same tick.
  out.sort((a, b) => a.tick - b.tick || a.rank - b.rank || a.bytes[1] - b.bytes[1]);
  return out;
}

function buildMetaTrackEvents({ schedule, score, name }) {
  const events = [trackNameEvent({ tick: 0, name })];

  const tempos = compressTempoRows(schedule.tempoEvents);
  for (const t of tempos) events.push(tempoEvent({ tick: t.tick, bpm: t.bpm }));

  const occurrences = occurrencesFromSchedule(schedule);
  const timeSigs = buildTimeSignatureRowsFromOccurrences({ score, occurrences });
  for (const ts of timeSigs) events.push(timeSignatureEvent({ tick: ts.tick, num: ts.num, den: ts.den }));

  const keySigRows = expandKeySignaturesAcrossOccurrences({ keySignatures: score.keySignatures, occurrences });
  for (const ks of keySigRows) events.push(keySignatureEvent({ tick: ks.tick, fifths: ks.fifths, mode: ks.mode }));

  return events;
}

export function exportSimplifiedScoreToMidiBytes({
  score,
  repeatsMode = RepeatsMode.LINEARIZE,
  channel = 0,
  program = 0,
  metaTrackName = "Kora Notation - Meta",
  noteTrackName = "Simplified Reduction",
  defaultVelocity = 80,
}) {
  const s = ensureScore(score);
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureIntInRange("defaultVelocity", defaultVelocity, 0, 127);

  const ppq = Number.isInteger(s.ppq) && s.ppq > 0 ? s.ppq : 960;
  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents: [],
    playMode: PlayMode.PLAY_AS_WRITTEN,
    repeatsMode,
  });

  const metaEvents = buildMetaTrackEvents({ schedule, score: s, name: metaTrackName });

  const noteEvents = [
    trackNameEvent({ tick: 0, name: noteTrackName }),
    programChangeEvent({ tick: 0, channel, program }),
    ...buildNoteEventsFromSchedule({ schedule, score: s, channel, defaultVelocity }),
  ];

  return writeMidiFileBytes({ ppq, tracks: [metaEvents, noteEvents] });
}

export function exportKoraPerformanceToMidiBytes({
  score,
  mappedEvents,
  repeatsMode = RepeatsMode.LINEARIZE,
  channel = 0,
  program = 46, // GM: Orchestral Harp (0-based)
  metaTrackName = "Kora Notation - Meta",
  noteTrackName = "Kora Performance",
  defaultVelocity = 80,
}) {
  const s = ensureScore(score);
  if (!Array.isArray(mappedEvents)) throw new Error("mappedEvents must be an array");
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureIntInRange("defaultVelocity", defaultVelocity, 0, 127);

  const ppq = Number.isInteger(s.ppq) && s.ppq > 0 ? s.ppq : 960;
  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents,
    playMode: PlayMode.PLAY_AS_KORA,
    repeatsMode,
  });

  const metaEvents = buildMetaTrackEvents({ schedule, score: s, name: metaTrackName });

  const noteEvents = [
    trackNameEvent({ tick: 0, name: noteTrackName }),
    programChangeEvent({ tick: 0, channel, program }),
    ...buildNoteEventsFromSchedule({ schedule, score: s, channel, defaultVelocity }),
  ];

  return writeMidiFileBytes({ ppq, tracks: [metaEvents, noteEvents] });
}

