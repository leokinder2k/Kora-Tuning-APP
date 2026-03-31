import { InstrumentType } from "./instrument.js";
import { buildSimplifiedTeachingReduction } from "./reduction.js";
import { pickMelodyPart } from "./parts.js";

export const MidiQuantizeStrength = Object.freeze({
  OFF: "OFF",
  LIGHT: "LIGHT",
  MEDIUM: "MEDIUM",
  STRONG: "STRONG",
});

function ensureUint8Array(input) {
  if (input instanceof Uint8Array) return input;
  if (input instanceof ArrayBuffer) return new Uint8Array(input);
  if (ArrayBuffer.isView(input)) return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
  throw new Error("midiBytes must be Uint8Array, ArrayBuffer, or typed-array view");
}

function readAscii(bytes, offset, len) {
  let out = "";
  for (let i = 0; i < len; i++) out += String.fromCharCode(bytes[offset + i]);
  return out;
}

function readU16BE(bytes, offset) {
  return (bytes[offset] << 8) | bytes[offset + 1];
}

function readU32BE(bytes, offset) {
  return (bytes[offset] * 0x1000000) + ((bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3]);
}

function readVarLen(bytes, startOffset) {
  let value = 0;
  let offset = startOffset;
  for (let i = 0; i < 4; i++) {
    const b = bytes[offset++];
    value = (value << 7) | (b & 0x7f);
    if ((b & 0x80) === 0) {
      return { value, nextOffset: offset };
    }
  }
  throw new Error(`Invalid VLQ at offset ${startOffset}`);
}

function gridTicksForStrength(ppq, strength) {
  if (strength === MidiQuantizeStrength.OFF) return null;
  if (strength === MidiQuantizeStrength.LIGHT) return Math.max(1, Math.round(ppq / 8));
  if (strength === MidiQuantizeStrength.MEDIUM) return Math.max(1, Math.round(ppq / 4));
  if (strength === MidiQuantizeStrength.STRONG) return Math.max(1, Math.round(ppq / 2));
  throw new Error(`Unknown quantize strength: ${strength}`);
}

export function quantizeTick({ tick, ppq, strength = MidiQuantizeStrength.MEDIUM }) {
  if (!Number.isInteger(tick) || tick < 0) throw new Error(`Invalid tick: ${tick}`);
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);
  const grid = gridTicksForStrength(ppq, strength);
  if (grid === null) return tick;
  return Math.round(tick / grid) * grid;
}

export function quantizeMidiNotes({ notes, ppq, strength = MidiQuantizeStrength.MEDIUM }) {
  if (!Array.isArray(notes)) throw new Error("notes must be an array");
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);
  const grid = gridTicksForStrength(ppq, strength);
  if (grid === null) return notes.map((n) => ({ ...n }));

  const out = [];
  for (const n of notes) {
    const start = quantizeTick({ tick: n.tick, ppq, strength });
    const endRaw = n.tick + n.durationTicks;
    let end = quantizeTick({ tick: endRaw, ppq, strength });
    if (end <= start) end = start + grid;
    out.push({
      ...n,
      tick: start,
      durationTicks: end - start,
    });
  }
  out.sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi || (a.trackIndex ?? 0) - (b.trackIndex ?? 0));
  return out;
}

function normalizeTempoMap(rows) {
  const byTick = new Map();
  for (const r of rows) byTick.set(r.tick, r);
  const out = [...byTick.values()].sort((a, b) => a.tick - b.tick);
  if (out.length === 0 || out[0].tick !== 0) {
    out.unshift({ tick: 0, bpm: 120 });
  }
  return out;
}

function normalizeTimeSignatures(rows) {
  const byTick = new Map();
  for (const r of rows) byTick.set(r.tick, r);
  const out = [...byTick.values()].sort((a, b) => a.tick - b.tick);
  if (out.length === 0 || out[0].tick !== 0) {
    out.unshift({ tick: 0, num: 4, den: 4 });
  }
  return out;
}

function normalizeKeySignatures(rows) {
  const byTick = new Map();
  for (const r of rows) byTick.set(r.tick, r);
  return [...byTick.values()].sort((a, b) => a.tick - b.tick);
}

function measureLengthTicks({ num, den, ppq }) {
  return Math.max(1, Math.round((ppq * 4 * num) / den));
}

function buildMeasures({ ppq, timeSignatures, endTick }) {
  const tsRows = normalizeTimeSignatures(timeSignatures);
  const lastTickTarget = Math.max(endTick, measureLengthTicks({ ...tsRows[0], ppq }));
  const measures = [];

  let tick = 0;
  let measureNumber = 1;
  let tsIdx = 0;

  while (tick < lastTickTarget) {
    while (tsIdx + 1 < tsRows.length && tsRows[tsIdx + 1].tick <= tick) tsIdx++;
    const ts = tsRows[tsIdx];
    let len = measureLengthTicks({ num: ts.num, den: ts.den, ppq });

    // If a time-signature change occurs inside this measure, split here.
    const nextTsTick = tsIdx + 1 < tsRows.length ? tsRows[tsIdx + 1].tick : null;
    if (Number.isInteger(nextTsTick) && tick < nextTsTick && nextTsTick < tick + len) {
      len = nextTsTick - tick;
    }

    measures.push({
      measureNumber,
      startTick: tick,
      lengthTicks: len,
      timeSignature: { num: ts.num, den: ts.den },
    });
    tick += len;
    measureNumber++;
  }

  return measures;
}

function trackStartsMapKey({ trackIndex, channel, note }) {
  return `${trackIndex}:${channel}:${note}`;
}

function parseTrack({
  trackBytes,
  trackIndex,
  notesOut,
  temposOut,
  timeSignaturesOut,
  keySignaturesOut,
}) {
  let offset = 0;
  let tick = 0;
  let runningStatus = null;
  const activeStarts = new Map();

  while (offset < trackBytes.length) {
    const delta = readVarLen(trackBytes, offset);
    tick += delta.value;
    offset = delta.nextOffset;
    if (offset >= trackBytes.length) break;

    let status = trackBytes[offset];
    let runningDataByte = null;
    if (status < 0x80) {
      if (runningStatus === null) throw new Error(`Running status without previous status in track ${trackIndex}`);
      status = runningStatus;
      runningDataByte = trackBytes[offset++];
    } else {
      offset++;
      if (status < 0xf0) runningStatus = status;
      else runningStatus = null;
    }

    if (status === 0xff) {
      if (offset >= trackBytes.length) break;
      const metaType = trackBytes[offset++];
      const metaLen = readVarLen(trackBytes, offset);
      offset = metaLen.nextOffset;
      const end = offset + metaLen.value;
      if (end > trackBytes.length) throw new Error(`Meta event out of bounds in track ${trackIndex}`);
      const payload = trackBytes.subarray(offset, end);
      offset = end;

      if (metaType === 0x51 && payload.length === 3) {
        const mpq = (payload[0] << 16) | (payload[1] << 8) | payload[2];
        if (mpq > 0) temposOut.push({ tick, bpm: 60000000 / mpq });
      } else if (metaType === 0x58 && payload.length >= 2) {
        const num = payload[0];
        const denPow = payload[1];
        timeSignaturesOut.push({ tick, num, den: 2 ** denPow });
      } else if (metaType === 0x59 && payload.length >= 2) {
        const fifths = payload[0] > 127 ? payload[0] - 256 : payload[0];
        const mode = payload[1] === 1 ? "MINOR" : "MAJOR";
        keySignaturesOut.push({ tick, fifths, mode });
      } else if (metaType === 0x2f) {
        break;
      }
      continue;
    }

    if (status === 0xf0 || status === 0xf7) {
      const len = readVarLen(trackBytes, offset);
      offset = len.nextOffset + len.value;
      continue;
    }

    const type = status & 0xf0;
    const channel = status & 0x0f;
    const dataLen = (type === 0xc0 || type === 0xd0) ? 1 : 2;
    let d1 = runningDataByte;
    if (d1 === null) {
      if (offset >= trackBytes.length) break;
      d1 = trackBytes[offset++];
    }
    let d2 = null;
    if (dataLen === 2) {
      if (offset >= trackBytes.length) break;
      d2 = trackBytes[offset++];
    }

    if (type === 0x90 && d2 > 0) {
      const key = trackStartsMapKey({ trackIndex, channel, note: d1 });
      const list = activeStarts.get(key) ?? [];
      list.push({ tick, velocity: d2 });
      activeStarts.set(key, list);
    } else if (type === 0x80 || (type === 0x90 && d2 === 0)) {
      const key = trackStartsMapKey({ trackIndex, channel, note: d1 });
      const list = activeStarts.get(key) ?? [];
      const start = list.length > 0 ? list.shift() : null;
      if (start) {
        notesOut.push({
          tick: start.tick,
          durationTicks: Math.max(1, tick - start.tick),
          pitchMidi: d1,
          velocity: start.velocity,
          channel,
          trackIndex,
        });
      }
      if (list.length > 0) activeStarts.set(key, list);
      else activeStarts.delete(key);
    }
  }
}

export function parseMidiFileBytes({ midiBytes }) {
  const bytes = ensureUint8Array(midiBytes);
  let offset = 0;

  const headerId = readAscii(bytes, offset, 4);
  if (headerId !== "MThd") throw new Error("Invalid MIDI header chunk");
  offset += 4;
  const headerLen = readU32BE(bytes, offset);
  offset += 4;
  if (headerLen < 6) throw new Error(`Invalid MIDI header length: ${headerLen}`);
  const format = readU16BE(bytes, offset);
  offset += 2;
  const trackCount = readU16BE(bytes, offset);
  offset += 2;
  const division = readU16BE(bytes, offset);
  offset += 2;
  if ((division & 0x8000) !== 0) throw new Error("SMPTE time division is not supported");
  const ppq = division;
  offset += headerLen - 6;

  const notes = [];
  const tempos = [];
  const timeSignatures = [];
  const keySignatures = [];

  for (let trackIndex = 0; trackIndex < trackCount; trackIndex++) {
    if (offset + 8 > bytes.length) throw new Error("Unexpected EOF while reading MIDI tracks");
    const id = readAscii(bytes, offset, 4);
    offset += 4;
    if (id !== "MTrk") throw new Error(`Invalid track chunk id: ${id}`);
    const len = readU32BE(bytes, offset);
    offset += 4;
    const end = offset + len;
    if (end > bytes.length) throw new Error(`Track length out of bounds: ${len}`);
    const trackBytes = bytes.subarray(offset, end);
    offset = end;

    parseTrack({
      trackBytes,
      trackIndex,
      notesOut: notes,
      temposOut: tempos,
      timeSignaturesOut: timeSignatures,
      keySignaturesOut: keySignatures,
    });
  }

  notes.sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi || a.trackIndex - b.trackIndex || a.channel - b.channel);

  return {
    format,
    trackCount,
    ppq,
    notes,
    tempoMap: normalizeTempoMap(tempos),
    timeSignatures: normalizeTimeSignatures(timeSignatures),
    keySignatures: normalizeKeySignatures(keySignatures),
  };
}

export function importMidiToSimplifiedScore({
  midiBytes,
  quantizeStrength = MidiQuantizeStrength.MEDIUM,
  buildReduction = true,
  reductionCap = 4,
  splitMidi = 60,
  instrumentType = InstrumentType.KORA_21,
  tuningMidiByStringId = null,
}) {
  const parsed = parseMidiFileBytes({ midiBytes });
  const quantizedNotes = quantizeMidiNotes({
    notes: parsed.notes,
    ppq: parsed.ppq,
    strength: quantizeStrength,
  });
  const trackIds = [...new Set(quantizedNotes.map((n) => n.trackIndex).filter(Number.isInteger))].sort((a, b) => a - b);
  const parts = trackIds.map((trackIndex) => ({
    partId: `TRACK_${trackIndex}`,
    name: `Track ${trackIndex}`,
    noteEvents: quantizedNotes.filter((n) => n.trackIndex === trackIndex),
    trackIndex,
  }));
  const melodyPick = pickMelodyPart({ parts });
  const melodyTrackIndex = Number.isInteger(melodyPick?.part?.trackIndex) ? melodyPick.part.trackIndex : null;
  const notesForReduction = quantizedNotes.map((n) => ({
    ...n,
    melodyHint: melodyTrackIndex !== null && n.trackIndex === melodyTrackIndex,
  }));

  const endTick = quantizedNotes.reduce((m, n) => Math.max(m, n.tick + n.durationTicks), 0);
  const measures = buildMeasures({
    ppq: parsed.ppq,
    timeSignatures: parsed.timeSignatures,
    endTick,
  });

  let events;
  if (buildReduction) {
    const reduction = buildSimplifiedTeachingReduction({
      noteEvents: notesForReduction,
      cap: reductionCap,
      splitMidi,
      tuningMidiByStringId,
      instrumentType,
    });
    events = reduction.events;
  } else {
    events = quantizedNotes.map((n, i) => ({
      eventId: `midi_note_${i + 1}`,
      type: "NOTE",
      tick: n.tick,
      durationTicks: n.durationTicks,
      pitchMidi: n.pitchMidi,
      velocity: n.velocity,
      staff: n.pitchMidi >= 60 ? "UPPER" : "LOWER",
      role: null,
      tie: { start: false, stop: false },
      tuplet: null,
      lyrics: null,
      chordSymbol: null,
      direction: null,
      dynamic: null,
    }));
  }

  return {
    ppq: parsed.ppq,
    measures,
    tempoMap: parsed.tempoMap,
    keySignatures: parsed.keySignatures,
    timeSignatures: parsed.timeSignatures,
    repeats: [],
    endings: [],
    layoutBreaks: [],
    events,
    source: {
      kind: "MIDI",
      format: parsed.format,
      trackCount: parsed.trackCount,
      quantizeStrength,
      buildReduction: Boolean(buildReduction),
      reductionCap: buildReduction ? reductionCap : null,
      splitMidi: buildReduction ? splitMidi : null,
      selectedMelodyTrackIndex: buildReduction ? melodyTrackIndex : null,
    },
  };
}
