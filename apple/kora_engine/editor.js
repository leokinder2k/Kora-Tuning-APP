import { transposeScoreDiatonic, transposeScoreSemitone } from "./transpose.js";

function clone(value) {
  if (typeof globalThis.structuredClone === "function") return globalThis.structuredClone(value);
  return JSON.parse(JSON.stringify(value));
}

function ensureObject(name, value) {
  if (!value || typeof value !== "object") throw new Error(`${name} must be an object`);
  return value;
}

function ensureInt(name, value) {
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function ensureNonNegInt(name, value) {
  ensureInt(name, value);
  if (value < 0) throw new Error(`${name} must be >= 0`);
  return value;
}

function normalizeMode(mode) {
  return mode === "MINOR" ? "MINOR" : "MAJOR";
}

function normalizeTempoMap(rows) {
  const byTick = new Map();
  for (const r of rows ?? []) {
    if (!Number.isInteger(r?.tick) || r.tick < 0) continue;
    if (!Number.isFinite(r?.bpm) || r.bpm <= 0) continue;
    byTick.set(r.tick, { tick: r.tick, bpm: r.bpm });
  }
  const out = [...byTick.values()].sort((a, b) => a.tick - b.tick || a.bpm - b.bpm);
  if (out.length === 0 || out[0].tick !== 0) out.unshift({ tick: 0, bpm: out[0]?.bpm ?? 120 });
  return out;
}

function normalizeTimeSignatures(rows) {
  const byTick = new Map();
  for (const r of rows ?? []) {
    if (!Number.isInteger(r?.tick) || r.tick < 0) continue;
    if (!Number.isInteger(r?.num) || r.num <= 0) continue;
    if (!Number.isInteger(r?.den) || r.den <= 0) continue;
    byTick.set(r.tick, { tick: r.tick, num: r.num, den: r.den });
  }
  const out = [...byTick.values()].sort((a, b) => a.tick - b.tick || a.num - b.num || a.den - b.den);
  if (out.length === 0 || out[0].tick !== 0) out.unshift({ tick: 0, num: 4, den: 4 });
  return out;
}

function normalizeKeySignatures(rows) {
  const byTick = new Map();
  for (const r of rows ?? []) {
    if (!Number.isInteger(r?.tick) || r.tick < 0) continue;
    if (!Number.isInteger(r?.fifths) || r.fifths < -7 || r.fifths > 7) continue;
    byTick.set(r.tick, { tick: r.tick, fifths: r.fifths, mode: normalizeMode(r.mode) });
  }
  return [...byTick.values()].sort((a, b) => a.tick - b.tick || a.fifths - b.fifths || a.mode.localeCompare(b.mode));
}

function measureLengthTicks({ ppq, num, den }) {
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

function maxTickToCover(score) {
  let endTick = 0;
  for (const e of score?.events ?? []) {
    if (!Number.isInteger(e?.tick) || e.tick < 0) continue;
    if (Number.isInteger(e?.durationTicks) && e.durationTicks > 0) {
      endTick = Math.max(endTick, e.tick + e.durationTicks);
    } else {
      endTick = Math.max(endTick, e.tick + 1);
    }
  }
  for (const t of score?.tempoMap ?? []) {
    if (!Number.isInteger(t?.tick) || t.tick < 0) continue;
    endTick = Math.max(endTick, t.tick + 1);
  }
  for (const t of score?.timeSignatures ?? []) {
    if (!Number.isInteger(t?.tick) || t.tick < 0) continue;
    endTick = Math.max(endTick, t.tick + 1);
  }
  for (const k of score?.keySignatures ?? []) {
    if (!Number.isInteger(k?.tick) || k.tick < 0) continue;
    endTick = Math.max(endTick, k.tick + 1);
  }
  return endTick;
}

function normalizeRepeats(repeats) {
  const seen = new Set();
  const out = [];
  for (const r of repeats ?? []) {
    if (!r || typeof r !== "object") continue;
    const type = r.type === "END" ? "END" : (r.type === "START" ? "START" : null);
    const measureNumber = Number.isInteger(r.measureNumber) && r.measureNumber >= 1 ? r.measureNumber : null;
    if (!type || measureNumber === null) continue;
    const key = `${type}:${measureNumber}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ type, measureNumber });
  }
  out.sort((a, b) => a.measureNumber - b.measureNumber || a.type.localeCompare(b.type));
  return out;
}

function normalizeEndings(endings) {
  const out = [];
  for (const e of endings ?? []) {
    if (!e || typeof e !== "object") continue;
    const startMeasure = Number.isInteger(e.startMeasure) && e.startMeasure >= 1 ? e.startMeasure : null;
    const endMeasure = Number.isInteger(e.endMeasure) && e.endMeasure >= 1 ? e.endMeasure : null;
    const number = Number.isInteger(e.number) && e.number >= 1 ? e.number : null;
    if (startMeasure === null || endMeasure === null || number === null) continue;
    out.push({ startMeasure, endMeasure, number });
  }
  out.sort((a, b) => a.startMeasure - b.startMeasure || a.number - b.number || a.endMeasure - b.endMeasure);
  return out;
}

function normalizeLayoutBreaks(layoutBreaks) {
  const out = [];
  const strength = { BAR_BREAK: 1, LINE_BREAK: 2, PAGE_BREAK: 3 };
  const byMeasure = new Map();
  for (const b of layoutBreaks ?? []) {
    if (!b || typeof b !== "object") continue;
    const measureNumber = Number.isInteger(b.measureNumber) && b.measureNumber >= 1 ? b.measureNumber : null;
    const type = b.type;
    if (measureNumber === null) continue;
    if (type !== "BAR_BREAK" && type !== "LINE_BREAK" && type !== "PAGE_BREAK") continue;
    const cur = byMeasure.get(measureNumber) ?? null;
    if (!cur || strength[type] > strength[cur]) byMeasure.set(measureNumber, type);
  }
  for (const [measureNumber, type] of byMeasure.entries()) out.push({ measureNumber, type });
  out.sort((a, b) => a.measureNumber - b.measureNumber || a.type.localeCompare(b.type));
  return out;
}

function normalizeBarlines(barlines) {
  const out = [];
  const byMeasure = new Map();
  for (const b of barlines ?? []) {
    if (!b || typeof b !== "object") continue;
    const measureNumber = Number.isInteger(b.measureNumber) && b.measureNumber >= 1 ? b.measureNumber : null;
    if (measureNumber === null) continue;
    const barlineType = typeof b.barlineType === "string" && b.barlineType.length > 0 ? b.barlineType : null;
    if (!barlineType) continue;
    byMeasure.set(measureNumber, { measureNumber, barlineType });
  }
  for (const v of byMeasure.values()) out.push(v);
  out.sort((a, b) => a.measureNumber - b.measureNumber || a.barlineType.localeCompare(b.barlineType));
  return out;
}

function normalizeEvents(events) {
  const out = [];
  const seen = new Set();
  for (const e of events ?? []) {
    if (!e || typeof e !== "object") continue;
    if (typeof e.eventId !== "string" || e.eventId.length === 0) continue;
    if (seen.has(e.eventId)) throw new Error(`Duplicate eventId: ${e.eventId}`);
    seen.add(e.eventId);
    out.push({ ...e });
  }
  out.sort((a, b) => {
    const ta = Number.isInteger(a.tick) ? a.tick : -1;
    const tb = Number.isInteger(b.tick) ? b.tick : -1;
    if (ta !== tb) return ta - tb;
    const typeA = String(a.type ?? "");
    const typeB = String(b.type ?? "");
    if (typeA !== typeB) return typeA.localeCompare(typeB);
    const pa = Number.isInteger(a.pitchMidi) ? a.pitchMidi : -1;
    const pb = Number.isInteger(b.pitchMidi) ? b.pitchMidi : -1;
    if (pa !== pb) return pb - pa;
    return a.eventId.localeCompare(b.eventId);
  });
  return out;
}

function nextEventId({ events, prefix }) {
  let max = 0;
  for (const e of events ?? []) {
    const id = typeof e?.eventId === "string" ? e.eventId : "";
    if (!id.startsWith(prefix)) continue;
    const tail = id.slice(prefix.length);
    const n = Number.parseInt(tail, 10);
    if (Number.isInteger(n)) max = Math.max(max, n);
  }
  return `${prefix}${max + 1}`;
}

function validateEventShape(e) {
  if (!e || typeof e !== "object") throw new Error("event must be an object");
  if (typeof e.type !== "string" || e.type.length === 0) throw new Error("event.type must be a string");
  ensureNonNegInt("event.tick", e.tick);

  if (e.type === "NOTE") {
    ensureInt("event.pitchMidi", e.pitchMidi);
    if (e.pitchMidi < 0 || e.pitchMidi > 127) throw new Error("event.pitchMidi must be 0..127");
    ensureInt("event.durationTicks", e.durationTicks);
    if (e.durationTicks <= 0) throw new Error("event.durationTicks must be > 0");
  } else if (e.type === "REST") {
    ensureInt("event.durationTicks", e.durationTicks);
    if (e.durationTicks <= 0) throw new Error("event.durationTicks must be > 0");
  } else if (e.type === "CHORD_SYMBOL" || e.type === "DIRECTION" || e.type === "LYRICS") {
    if (typeof e.text !== "string" || e.text.trim().length === 0) throw new Error("event.text must be a non-empty string");
  } else if (e.type === "DYNAMIC") {
    if (typeof e.mark !== "string" || e.mark.trim().length === 0) throw new Error("event.mark must be a non-empty string");
  }

  if (e.tie !== undefined && e.tie !== null) {
    if (e.type !== "NOTE") throw new Error("event.tie is only allowed on NOTE events");
    if (!e.tie || typeof e.tie !== "object") throw new Error("event.tie must be an object");
    if ("start" in e.tie && typeof e.tie.start !== "boolean") throw new Error("event.tie.start must be a boolean");
    if ("stop" in e.tie && typeof e.tie.stop !== "boolean") throw new Error("event.tie.stop must be a boolean");
  }

  if (e.tuplet !== undefined && e.tuplet !== null) {
    if (e.type !== "NOTE" && e.type !== "REST") throw new Error("event.tuplet is only allowed on NOTE or REST events");
    if (!e.tuplet || typeof e.tuplet !== "object") throw new Error("event.tuplet must be an object");
    const groupId = String(e.tuplet.groupId ?? "");
    if (groupId.length === 0) throw new Error("event.tuplet.groupId must be a non-empty string");
    const actual = ensureInt("event.tuplet.actual", e.tuplet.actual);
    const normal = ensureInt("event.tuplet.normal", e.tuplet.normal);
    if (actual <= 0) throw new Error("event.tuplet.actual must be > 0");
    if (normal <= 0) throw new Error("event.tuplet.normal must be > 0");
  }
}

function rhythmVoiceKey(e) {
  const staff = typeof e.staff === "string" ? e.staff : "";
  const pitch = Number.isInteger(e.pitchMidi) ? e.pitchMidi : "";
  return `${staff}|${pitch}`;
}

function validateTupletCompleteness(events) {
  const groups = new Map();

  for (const e of events ?? []) {
    const t = e?.tuplet;
    if (t === null || t === undefined) continue;
    const groupId = String(t.groupId);
    const actual = t.actual;
    const normal = t.normal;
    const entry = groups.get(groupId);
    if (!entry) {
      groups.set(groupId, { actual, normal, eventIds: [e.eventId] });
      continue;
    }
    if (entry.actual !== actual || entry.normal !== normal) {
      throw new Error(`Tuplet group ${groupId} has inconsistent ratio metadata`);
    }
    entry.eventIds.push(e.eventId);
  }

  for (const [groupId, entry] of groups.entries()) {
    if (entry.eventIds.length !== entry.actual) {
      throw new Error(`Tuplet group ${groupId} is incomplete: expected ${entry.actual} events, found ${entry.eventIds.length}`);
    }
  }
}

function validateTieChains(events) {
  const byStart = new Map();
  const byEnd = new Map();
  const notes = [];

  for (const e of events ?? []) {
    if (e?.type !== "NOTE") continue;
    const duration = Number.isInteger(e.durationTicks) ? e.durationTicks : null;
    const tick = Number.isInteger(e.tick) ? e.tick : null;
    if (duration === null || duration <= 0 || tick === null || tick < 0) continue;
    const endTick = tick + duration;
    const key = rhythmVoiceKey(e);
    notes.push({ e, key, tick, endTick });

    const startMapKey = `${key}@${tick}`;
    const endMapKey = `${key}@${endTick}`;
    if (!byStart.has(startMapKey)) byStart.set(startMapKey, []);
    if (!byEnd.has(endMapKey)) byEnd.set(endMapKey, []);
    byStart.get(startMapKey).push(e);
    byEnd.get(endMapKey).push(e);
  }

  for (const row of notes) {
    const tieStart = row.e.tie?.start === true;
    const tieStop = row.e.tie?.stop === true;

    if (tieStart) {
      const prev = byEnd.get(`${row.key}@${row.tick}`) ?? [];
      const hasLinkedPrev = prev.some((p) => p.tie?.stop === true);
      if (!hasLinkedPrev) {
        throw new Error(`Tie chain inconsistency: NOTE ${row.e.eventId} has tie.start=true without matching previous tied note`);
      }
    }

    if (tieStop) {
      const next = byStart.get(`${row.key}@${row.endTick}`) ?? [];
      const hasLinkedNext = next.some((n) => n.tie?.start === true);
      if (!hasLinkedNext) {
        throw new Error(`Tie chain inconsistency: NOTE ${row.e.eventId} has tie.stop=true without matching next tied note`);
      }
    }
  }
}

function validateRhythmIntegrity(events) {
  validateTieChains(events);
  validateTupletCompleteness(events);
}

export function normalizeSimplifiedScore(score) {
  ensureObject("score", score);
  const out = clone(score);
  out.ppq = Number.isInteger(out.ppq) && out.ppq > 0 ? out.ppq : 960;
  out.tempoMap = normalizeTempoMap(out.tempoMap);
  out.timeSignatures = normalizeTimeSignatures(out.timeSignatures);
  out.keySignatures = normalizeKeySignatures(out.keySignatures);
  out.repeats = normalizeRepeats(out.repeats);
  out.endings = normalizeEndings(out.endings);
  out.layoutBreaks = normalizeLayoutBreaks(out.layoutBreaks);
  out.barlines = normalizeBarlines(out.barlines);
  out.events = normalizeEvents(out.events);
  for (const e of out.events) validateEventShape(e);
  validateRhythmIntegrity(out.events);

  // Always ensure measures exist and cover the current end tick.
  const endTick = maxTickToCover(out);
  out.measures = buildMeasures({ ppq: out.ppq, timeSignatures: out.timeSignatures, endTick });
  return out;
}

export function createEmptySimplifiedScore({
  ppq = 960,
  bpm = 120,
  timeSignature = { num: 4, den: 4 },
  keySignature = { fifths: 0, mode: "MAJOR" },
} = {}) {
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);
  if (!Number.isFinite(bpm) || bpm <= 0) throw new Error(`Invalid bpm: ${bpm}`);
  const ts = {
    num: Number.isInteger(timeSignature?.num) && timeSignature.num > 0 ? timeSignature.num : 4,
    den: Number.isInteger(timeSignature?.den) && timeSignature.den > 0 ? timeSignature.den : 4,
  };
  const ks = {
    fifths: Number.isInteger(keySignature?.fifths) ? keySignature.fifths : 0,
    mode: normalizeMode(keySignature?.mode),
  };

  const score = {
    ppq,
    tempoMap: [{ tick: 0, bpm }],
    timeSignatures: [{ tick: 0, num: ts.num, den: ts.den }],
    keySignatures: [{ tick: 0, fifths: ks.fifths, mode: ks.mode }],
    repeats: [],
    endings: [],
    layoutBreaks: [],
    barlines: [],
    events: [],
    measures: [],
  };
  return normalizeSimplifiedScore(score);
}

export function applyScoreEdit({ score, edit }) {
  ensureObject("score", score);
  ensureObject("edit", edit);
  if (typeof edit.type !== "string" || edit.type.length === 0) throw new Error("edit.type must be a string");

  // Start from a normalized clone to keep downstream behavior deterministic.
  let next = normalizeSimplifiedScore(score);
  const warnings = [];

  const findEventIndex = (eventId) => next.events.findIndex((e) => e.eventId === eventId);

  switch (edit.type) {
    case "INSERT_EVENT": {
      const event = ensureObject("edit.event", edit.event);
      const eventId = typeof event.eventId === "string" && event.eventId.length > 0
        ? event.eventId
        : (() => {
          const type = String(event.type ?? "");
          if (type === "NOTE") return nextEventId({ events: next.events, prefix: "ed_note_" });
          if (type === "REST") return nextEventId({ events: next.events, prefix: "ed_rest_" });
          if (type === "CHORD_SYMBOL") return nextEventId({ events: next.events, prefix: "ed_chord_" });
          if (type === "DIRECTION") return nextEventId({ events: next.events, prefix: "ed_dir_" });
          if (type === "DYNAMIC") return nextEventId({ events: next.events, prefix: "ed_dyn_" });
          if (type === "LYRICS") return nextEventId({ events: next.events, prefix: "ed_lyr_" });
          return nextEventId({ events: next.events, prefix: "ed_evt_" });
        })();

      if (findEventIndex(eventId) >= 0) throw new Error(`Event already exists: ${eventId}`);
      const full = { ...event, eventId };
      validateEventShape(full);
      next.events.push(full);
      next.events = normalizeEvents(next.events);
      break;
    }
    case "PATCH_EVENT": {
      const eventId = String(edit.eventId ?? "");
      if (eventId.length === 0) throw new Error("edit.eventId must be a string");
      const idx = findEventIndex(eventId);
      if (idx < 0) throw new Error(`Event not found: ${eventId}`);
      const patch = ensureObject("edit.patch", edit.patch);
      if ("eventId" in patch) throw new Error("edit.patch must not include eventId");
      const updated = { ...next.events[idx], ...patch };
      validateEventShape(updated);
      next.events[idx] = updated;
      next.events = normalizeEvents(next.events);
      break;
    }
    case "DELETE_EVENT": {
      const eventId = String(edit.eventId ?? "");
      if (eventId.length === 0) throw new Error("edit.eventId must be a string");
      const idx = findEventIndex(eventId);
      if (idx < 0) throw new Error(`Event not found: ${eventId}`);
      next.events.splice(idx, 1);
      next.events = normalizeEvents(next.events);
      break;
    }
    case "SET_TEMPO": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      const bpm = edit.bpm;
      if (!Number.isFinite(bpm) || bpm <= 0) throw new Error("edit.bpm must be a positive number");
      const rows = next.tempoMap.filter((r) => r.tick !== tick);
      rows.push({ tick, bpm });
      next.tempoMap = normalizeTempoMap(rows);
      break;
    }
    case "DELETE_TEMPO": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      next.tempoMap = normalizeTempoMap(next.tempoMap.filter((r) => r.tick !== tick));
      break;
    }
    case "SET_TIME_SIGNATURE": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      const num = ensureInt("edit.num", edit.num);
      const den = ensureInt("edit.den", edit.den);
      if (num <= 0 || den <= 0) throw new Error("Time signature must be > 0");
      const rows = next.timeSignatures.filter((r) => r.tick !== tick);
      rows.push({ tick, num, den });
      next.timeSignatures = normalizeTimeSignatures(rows);
      break;
    }
    case "DELETE_TIME_SIGNATURE": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      next.timeSignatures = normalizeTimeSignatures(next.timeSignatures.filter((r) => r.tick !== tick));
      break;
    }
    case "SET_KEY_SIGNATURE": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      const fifths = ensureInt("edit.fifths", edit.fifths);
      const mode = normalizeMode(edit.mode);
      if (fifths < -7 || fifths > 7) throw new Error("Key signature fifths must be -7..7");
      const rows = next.keySignatures.filter((r) => r.tick !== tick);
      rows.push({ tick, fifths, mode });
      next.keySignatures = normalizeKeySignatures(rows);
      break;
    }
    case "DELETE_KEY_SIGNATURE": {
      const tick = ensureNonNegInt("edit.tick", edit.tick);
      next.keySignatures = normalizeKeySignatures(next.keySignatures.filter((r) => r.tick !== tick));
      break;
    }
    case "ADD_REPEAT": {
      const repeatType = edit.repeatType === "END" ? "END" : "START";
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      next.repeats = normalizeRepeats([...next.repeats, { type: repeatType, measureNumber }]);
      break;
    }
    case "DELETE_REPEAT": {
      const repeatType = edit.repeatType === "END" ? "END" : "START";
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      next.repeats = normalizeRepeats(next.repeats.filter((r) => !(r.type === repeatType && r.measureNumber === measureNumber)));
      break;
    }
    case "ADD_ENDING": {
      const startMeasure = ensureInt("edit.startMeasure", edit.startMeasure);
      const endMeasure = ensureInt("edit.endMeasure", edit.endMeasure);
      const number = ensureInt("edit.number", edit.number);
      if (startMeasure < 1 || endMeasure < 1 || number < 1) throw new Error("Ending measures/number must be >= 1");
      next.endings = normalizeEndings([...next.endings, { startMeasure, endMeasure, number }]);
      break;
    }
    case "DELETE_ENDING": {
      const startMeasure = ensureInt("edit.startMeasure", edit.startMeasure);
      const endMeasure = ensureInt("edit.endMeasure", edit.endMeasure);
      const number = ensureInt("edit.number", edit.number);
      next.endings = normalizeEndings(next.endings.filter((e) =>
        !(e.startMeasure === startMeasure && e.endMeasure === endMeasure && e.number === number)));
      break;
    }
    case "SET_LAYOUT_BREAK": {
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      const type = edit.breakType;
      if (type !== "BAR_BREAK" && type !== "LINE_BREAK" && type !== "PAGE_BREAK") {
        throw new Error(`Unknown breakType: ${type}`);
      }
      next.layoutBreaks = normalizeLayoutBreaks([
        ...next.layoutBreaks.filter((b) => b.measureNumber !== measureNumber),
        { measureNumber, type },
      ]);
      break;
    }
    case "DELETE_LAYOUT_BREAK": {
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      next.layoutBreaks = normalizeLayoutBreaks(next.layoutBreaks.filter((b) => b.measureNumber !== measureNumber));
      break;
    }
    case "SET_BARLINE": {
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      const barlineType = String(edit.barlineType ?? "").trim();
      if (barlineType.length === 0) throw new Error("edit.barlineType must be a string");
      next.barlines = normalizeBarlines([
        ...next.barlines.filter((b) => b.measureNumber !== measureNumber),
        { measureNumber, barlineType },
      ]);
      break;
    }
    case "DELETE_BARLINE": {
      const measureNumber = ensureInt("edit.measureNumber", edit.measureNumber);
      if (measureNumber < 1) throw new Error("edit.measureNumber must be >= 1");
      next.barlines = normalizeBarlines(next.barlines.filter((b) => b.measureNumber !== measureNumber));
      break;
    }
    case "TRANSPOSE_SEMITONE": {
      const semitones = ensureInt("edit.semitones", edit.semitones);
      next = transposeScoreSemitone({ score: next, semitones });
      break;
    }
    case "TRANSPOSE_DIATONIC": {
      const steps = ensureInt("edit.steps", edit.steps);
      const out = transposeScoreDiatonic({ score: next, steps });
      next = out.score;
      warnings.push(...(out.warnings ?? []));
      break;
    }
    case "SPLIT_NOTE_AT_TICK": {
      const eventId = String(edit.eventId ?? "");
      if (eventId.length === 0) throw new Error("edit.eventId must be a string");
      const splitTick = ensureNonNegInt("edit.splitTick", edit.splitTick);
      const idx = findEventIndex(eventId);
      if (idx < 0) throw new Error(`Event not found: ${eventId}`);
      const e = next.events[idx];
      if (e.type !== "NOTE" && e.type !== "REST") {
        throw new Error("SPLIT_NOTE_AT_TICK only applies to NOTE or REST events");
      }
      if (!Number.isInteger(e.tick) || !Number.isInteger(e.durationTicks) || e.durationTicks <= 0) {
        throw new Error("Event missing valid tick/durationTicks");
      }
      if (splitTick <= e.tick || splitTick >= e.tick + e.durationTicks) {
        throw new Error(
          `edit.splitTick (${splitTick}) must be strictly between event.tick (${e.tick}) and event end (${e.tick + e.durationTicks})`
        );
      }
      const firstDuration = splitTick - e.tick;
      const secondDuration = e.tick + e.durationTicks - splitTick;
      const prefix = e.type === "NOTE" ? "ed_note_" : "ed_rest_";
      const firstId = nextEventId({ events: next.events.filter((ev) => ev.eventId !== eventId), prefix });
      const firstEvent = { ...e, eventId: firstId, durationTicks: firstDuration };
      if (e.type === "NOTE") {
        firstEvent.tie = { start: e.tie?.start ?? false, stop: false };
      }
      const tempEvents = [...next.events.filter((ev) => ev.eventId !== eventId), firstEvent];
      const secondId = nextEventId({ events: tempEvents, prefix });
      const secondEvent = { ...e, eventId: secondId, tick: splitTick, durationTicks: secondDuration };
      if (e.type === "NOTE") {
        secondEvent.tie = { start: false, stop: e.tie?.stop ?? false };
      }
      next.events = next.events.filter((ev) => ev.eventId !== eventId);
      next.events.push(firstEvent, secondEvent);
      next.events = normalizeEvents(next.events);
      break;
    }
    case "JOIN_NOTES_WITH_TIE": {
      const firstId = String(edit.firstEventId ?? "");
      const secondId = String(edit.secondEventId ?? "");
      if (firstId.length === 0) throw new Error("edit.firstEventId must be a string");
      if (secondId.length === 0) throw new Error("edit.secondEventId must be a string");
      if (firstId === secondId) throw new Error("edit.firstEventId and edit.secondEventId must be different");
      const firstIdx = findEventIndex(firstId);
      const secondIdx = findEventIndex(secondId);
      if (firstIdx < 0) throw new Error(`Event not found: ${firstId}`);
      if (secondIdx < 0) throw new Error(`Event not found: ${secondId}`);
      const first = next.events[firstIdx];
      const second = next.events[secondIdx];
      if (first.type !== "NOTE") throw new Error("JOIN_NOTES_WITH_TIE: first event must be a NOTE");
      if (second.type !== "NOTE") throw new Error("JOIN_NOTES_WITH_TIE: second event must be a NOTE");
      if (first.pitchMidi !== second.pitchMidi) {
        throw new Error("JOIN_NOTES_WITH_TIE: both notes must have the same pitchMidi");
      }
      if (second.tick !== first.tick + first.durationTicks) {
        throw new Error(
          `JOIN_NOTES_WITH_TIE: second note must start exactly where first ends (expected tick ${first.tick + first.durationTicks}, got ${second.tick})`
        );
      }
      next.events[firstIdx] = { ...first, tie: { start: first.tie?.start ?? false, stop: true } };
      next.events[secondIdx] = { ...second, tie: { start: true, stop: second.tie?.stop ?? false } };
      next.events = normalizeEvents(next.events);
      break;
    }
    case "SET_TUPLET_GROUP": {
      const groupId = String(edit.groupId ?? "");
      if (groupId.length === 0) throw new Error("edit.groupId must be a string");
      const actual = ensureInt("edit.actual", edit.actual);
      const normal = ensureInt("edit.normal", edit.normal);
      if (actual <= 0) throw new Error("edit.actual must be > 0");
      if (normal <= 0) throw new Error("edit.normal must be > 0");
      const eventIds = Array.isArray(edit.eventIds) ? edit.eventIds.map(String) : [];
      if (eventIds.length === 0) throw new Error("edit.eventIds must be a non-empty array");
      if (eventIds.length !== actual) {
        throw new Error(`SET_TUPLET_GROUP expects exactly ${actual} eventIds (got ${eventIds.length})`);
      }
      if (new Set(eventIds).size !== eventIds.length) {
        throw new Error("edit.eventIds must not contain duplicates");
      }
      for (const eid of eventIds) {
        const idx = findEventIndex(eid);
        if (idx < 0) throw new Error(`Event not found: ${eid}`);
        const ev = next.events[idx];
        if (ev.type !== "NOTE" && ev.type !== "REST") {
          throw new Error("SET_TUPLET_GROUP only applies to NOTE or REST events");
        }
        next.events[idx] = { ...next.events[idx], tuplet: { groupId, actual, normal } };
      }
      next.events = normalizeEvents(next.events);
      break;
    }
    case "CLEAR_TUPLET_GROUP": {
      const groupId = String(edit.groupId ?? "");
      if (groupId.length === 0) throw new Error("edit.groupId must be a string");
      for (let i = 0; i < next.events.length; i++) {
        if (next.events[i].tuplet?.groupId === groupId) {
          const { tuplet, ...rest } = next.events[i];
          next.events[i] = { ...rest, tuplet: null };
        }
      }
      next.events = normalizeEvents(next.events);
      break;
    }
    case "SET_MAPPING_OVERRIDE": {
      const eventId = String(edit.eventId ?? "");
      if (eventId.length === 0) throw new Error("edit.eventId must be a string");
      const idx = findEventIndex(eventId);
      if (idx < 0) throw new Error(`Event not found: ${eventId}`);
      if (next.events[idx].type !== "NOTE") throw new Error("SET_MAPPING_OVERRIDE only applies to NOTE events");
      const stringId = String(edit.stringId ?? "");
      if (stringId.length === 0) throw new Error("edit.stringId must be a string");
      const digitLine = typeof edit.digitLine === "string" && edit.digitLine.length > 0 ? edit.digitLine : null;
      next.events[idx] = { ...next.events[idx], mappingOverride: { stringId, digitLine } };
      next.events = normalizeEvents(next.events);
      break;
    }
    case "CLEAR_MAPPING_OVERRIDE": {
      const eventId = String(edit.eventId ?? "");
      if (eventId.length === 0) throw new Error("edit.eventId must be a string");
      const idx = findEventIndex(eventId);
      if (idx < 0) throw new Error(`Event not found: ${eventId}`);
      const { mappingOverride, ...rest } = next.events[idx];
      next.events[idx] = rest;
      next.events = normalizeEvents(next.events);
      break;
    }
    default:
      throw new Error(`Unknown edit.type: ${edit.type}`);
  }

  // Measures depend on the time model; always ensure coverage after edits.
  const endTick = maxTickToCover(next);
  next.measures = buildMeasures({ ppq: next.ppq, timeSignatures: next.timeSignatures, endTick });
  next.tempoMap = normalizeTempoMap(next.tempoMap);
  next.timeSignatures = normalizeTimeSignatures(next.timeSignatures);
  next.keySignatures = normalizeKeySignatures(next.keySignatures);
  next.repeats = normalizeRepeats(next.repeats);
  next.endings = normalizeEndings(next.endings);
  next.layoutBreaks = normalizeLayoutBreaks(next.layoutBreaks);
  next.barlines = normalizeBarlines(next.barlines);
  next.events = normalizeEvents(next.events);
  for (const e of next.events) validateEventShape(e);
  validateRhythmIntegrity(next.events);

  return { score: next, warnings };
}

export function createEditorState({ score }) {
  return {
    score: normalizeSimplifiedScore(score),
    undoStack: [],
    redoStack: [],
  };
}

export function applyEditorEdit({ state, edit }) {
  ensureObject("state", state);
  const prevScore = state.score;
  const out = applyScoreEdit({ score: prevScore, edit });
  return {
    state: {
      score: out.score,
      undoStack: [...(state.undoStack ?? []), prevScore],
      redoStack: [],
    },
    warnings: out.warnings ?? [],
  };
}

export function undoEditorEdit({ state }) {
  ensureObject("state", state);
  const undoStack = Array.isArray(state.undoStack) ? state.undoStack : [];
  if (undoStack.length === 0) return state;
  const prev = undoStack[undoStack.length - 1];
  return {
    score: prev,
    undoStack: undoStack.slice(0, -1),
    redoStack: [...(state.redoStack ?? []), state.score],
  };
}

export function redoEditorEdit({ state }) {
  ensureObject("state", state);
  const redoStack = Array.isArray(state.redoStack) ? state.redoStack : [];
  if (redoStack.length === 0) return state;
  const next = redoStack[redoStack.length - 1];
  return {
    score: next,
    undoStack: [...(state.undoStack ?? []), state.score],
    redoStack: redoStack.slice(0, -1),
  };
}
