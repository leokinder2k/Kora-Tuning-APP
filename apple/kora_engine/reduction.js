import { InstrumentType } from "./instrument.js";

function validateNoteEvent(e) {
  if (!Number.isInteger(e?.tick) || e.tick < 0) {
    throw new Error(`Invalid note tick: ${JSON.stringify(e)}`);
  }
  if (!Number.isInteger(e?.durationTicks) || e.durationTicks <= 0) {
    throw new Error(`Invalid note durationTicks: ${JSON.stringify(e)}`);
  }
  if (!Number.isInteger(e?.pitchMidi) || e.pitchMidi < 0 || e.pitchMidi > 127) {
    throw new Error(`Invalid note pitchMidi: ${JSON.stringify(e)}`);
  }
}

function stableNoteId(note, fallbackIndex) {
  if (typeof note.eventId === "string" && note.eventId.length > 0) return note.eventId;
  return `@${fallbackIndex}`;
}

function noteSelectionCost({ note, mappingCostForMidi }) {
  return mappingCostForMidi(note.pitchMidi);
}

function isMelodyHinted(note) {
  return note?.melodyHint === true || note?.role === "MELODY";
}

function defaultMappingCostFactory({ tuningMidiByStringId, instrumentType }) {
  const tunedValues = Object.values(tuningMidiByStringId ?? {}).filter(Number.isFinite);
  const retuneCost = instrumentType === InstrumentType.KORA_22_CHROMATIC ? 1 : 5;

  return (pitchMidi) => {
    let exact = false;
    let near = false;
    for (const tuned of tunedValues) {
      if (tuned === pitchMidi) {
        exact = true;
        break;
      }
      if (Math.abs(tuned - pitchMidi) === 1) near = true;
    }
    if (exact) return 0;
    if (near) return retuneCost;
    return 100;
  };
}

export function buildTimeSlices({ noteEvents }) {
  if (!Array.isArray(noteEvents)) throw new Error("noteEvents must be an array");
  if (noteEvents.length === 0) return [];

  const notes = noteEvents.map((n) => ({ ...n }));
  for (const e of notes) validateNoteEvent(e);

  const cutSet = new Set();
  for (const e of notes) {
    cutSet.add(e.tick);
    cutSet.add(e.tick + e.durationTicks);
  }
  const cuts = [...cutSet].sort((a, b) => a - b);

  const slices = [];
  for (let i = 0; i < cuts.length - 1; i++) {
    const t0 = cuts[i];
    const t1 = cuts[i + 1];
    if (t1 <= t0) continue;
    const active = notes.filter((n) => n.tick <= t0 && t0 < n.tick + n.durationTicks);
    slices.push({
      tick: t0,
      lengthTicks: t1 - t0,
      notes: active,
    });
  }
  return slices;
}

function chooseSliceNotes({
  notes,
  cap = 4,
  mappingCostForMidi = () => 0,
  previousMelodyPitch = null,
  previousMelodySourceEventId = null,
}) {
  if (!Array.isArray(notes)) throw new Error("notes must be an array");
  if (!Number.isInteger(cap) || cap < 1) throw new Error(`Invalid cap: ${cap}`);
  if (notes.length === 0) return {
    selected: [],
    melodyId: null,
    bassId: null,
  };

  const indexed = notes.map((n, idx) => ({ ...n, __idx: idx, __id: stableNoteId(n, idx) }));

  const selected = [];
  const selectedIds = new Set();

  // Melody choice priority:
  // 1) explicit melody hint/role, 2) keep same source event when sustained across slices,
  // 3) continuity to previous melody pitch, 4) higher pitch, 5) stable id.
  const melodyCandidates = [...indexed].sort((a, b) => {
    const hintA = isMelodyHinted(a) ? 1 : 0;
    const hintB = isMelodyHinted(b) ? 1 : 0;
    if (hintA !== hintB) return hintB - hintA;

    if (typeof previousMelodySourceEventId === "string" && previousMelodySourceEventId.length > 0) {
      const sameA = (a.eventId === previousMelodySourceEventId || a.sourceEventId === previousMelodySourceEventId) ? 1 : 0;
      const sameB = (b.eventId === previousMelodySourceEventId || b.sourceEventId === previousMelodySourceEventId) ? 1 : 0;
      if (sameA !== sameB) return sameB - sameA;
    }

    if (Number.isInteger(previousMelodyPitch)) {
      const da = Math.abs(a.pitchMidi - previousMelodyPitch);
      const db = Math.abs(b.pitchMidi - previousMelodyPitch);
      if (da !== db) return da - db;
    }

    if (a.pitchMidi !== b.pitchMidi) return b.pitchMidi - a.pitchMidi;
    return a.__id.localeCompare(b.__id);
  });

  const melody = melodyCandidates[0];
  selected.push(melody);
  selectedIds.add(melody.__id);

  // Deterministic bass pick from remaining notes.
  const byPitchAsc = indexed
    .filter((n) => !selectedIds.has(n.__id))
    .sort((a, b) => a.pitchMidi - b.pitchMidi || a.__id.localeCompare(b.__id));
  const bass = byPitchAsc[0];
  if (bass && !selectedIds.has(bass.__id) && selected.length < cap) {
    selected.push(bass);
    selectedIds.add(bass.__id);
  }

  const remaining = indexed.filter((n) => !selectedIds.has(n.__id));
  remaining.sort((a, b) => {
    const ca = noteSelectionCost({ note: a, mappingCostForMidi });
    const cb = noteSelectionCost({ note: b, mappingCostForMidi });
    if (ca !== cb) return ca - cb;
    // Prefer higher harmony notes on equal cost.
    if (a.pitchMidi !== b.pitchMidi) return b.pitchMidi - a.pitchMidi;
    return a.__id.localeCompare(b.__id);
  });

  for (const n of remaining) {
    if (selected.length >= cap) break;
    selected.push(n);
  }

  return {
    selected,
    melodyId: melody.__id,
    bassId: bass?.__id ?? null,
  };
}

export function simplifySlice({
  notes,
  cap = 4,
  mappingCostForMidi = () => 0,
}) {
  const out = chooseSliceNotes({ notes, cap, mappingCostForMidi });
  return out.selected.map(({ __idx, __id, ...plain }) => plain);
}

function roleForSelectedNote({ note, selected, melodyId, bassId }) {
  if (melodyId && note.__id === melodyId) return "MELODY";
  if (bassId && note.__id === bassId && note.__id !== melodyId) return "BASS";

  if (selected.length === 1) return "MELODY";

  let maxPitch = Number.NEGATIVE_INFINITY;
  let minPitch = Number.POSITIVE_INFINITY;
  for (const n of selected) {
    maxPitch = Math.max(maxPitch, n.pitchMidi);
    minPitch = Math.min(minPitch, n.pitchMidi);
  }

  if (note.pitchMidi === maxPitch) return "MELODY";
  if (note.pitchMidi === minPitch) return "BASS";
  return "HARMONY";
}

function buildSliceEvents({ slice, selected, splitMidi, melodyId, bassId }) {
  const out = [];
  const sorted = [...selected].sort((a, b) => b.pitchMidi - a.pitchMidi || a.tick - b.tick);
  for (const note of sorted) {
    out.push({
      eventId: null, // assigned after full sort for determinism
      type: "NOTE",
      tick: slice.tick,
      durationTicks: slice.lengthTicks,
      pitchMidi: note.pitchMidi,
      velocity: note.velocity ?? null,
      role: roleForSelectedNote({ note, selected, melodyId, bassId }),
      staff: note.pitchMidi >= splitMidi ? "UPPER" : "LOWER",
      tie: note.tie ?? { start: false, stop: false },
      tuplet: note.tuplet ?? null,
      lyrics: note.lyrics ?? (note.lyric ? { text: note.lyric } : null),
      chordSymbol: note.chordSymbol ?? null,
      direction: note.direction ?? null,
      dynamic: note.dynamic ?? null,
      sourceEventId: note.eventId ?? null,
    });
  }
  return out;
}

export function buildSimplifiedTeachingReduction({
  noteEvents,
  cap = 4,
  splitMidi = 60,
  mappingCostForMidi = null,
  tuningMidiByStringId = null,
  instrumentType = InstrumentType.KORA_21,
}) {
  if (!Array.isArray(noteEvents)) throw new Error("noteEvents must be an array");
  if (!Number.isInteger(splitMidi)) throw new Error(`Invalid splitMidi: ${splitMidi}`);
  const slices = buildTimeSlices({ noteEvents });
  if (slices.length === 0) return { slices: [], events: [] };

  const costFn =
    typeof mappingCostForMidi === "function"
      ? mappingCostForMidi
      : defaultMappingCostFactory({ tuningMidiByStringId, instrumentType });

  const events = [];
  let previousMelodyPitch = null;
  let previousMelodySourceEventId = null;

  for (const slice of slices) {
    if (slice.notes.length === 0) {
      events.push({
        eventId: null, // assigned after full sort for determinism
        type: "REST",
        tick: slice.tick,
        durationTicks: slice.lengthTicks,
        staff: "UPPER",
        sourceEventId: null,
      });
      continue;
    }

    const choice = chooseSliceNotes({
      notes: slice.notes,
      cap,
      mappingCostForMidi: costFn,
      previousMelodyPitch,
      previousMelodySourceEventId,
    });
    events.push(...buildSliceEvents({
      slice,
      selected: choice.selected,
      splitMidi,
      melodyId: choice.melodyId,
      bassId: choice.bassId,
    }));

    const melody = choice.selected.find((n) => n.__id === choice.melodyId) ?? null;
    if (melody && Number.isInteger(melody.pitchMidi)) {
      previousMelodyPitch = melody.pitchMidi;
      if (typeof melody.eventId === "string" && melody.eventId.length > 0) {
        previousMelodySourceEventId = melody.eventId;
      } else if (typeof melody.sourceEventId === "string" && melody.sourceEventId.length > 0) {
        previousMelodySourceEventId = melody.sourceEventId;
      }
    }

    // Keep public event shape free of reducer internals.
    for (const e of events) {
      if (e && typeof e === "object" && "__idx" in e) delete e.__idx;
      if (e && typeof e === "object" && "__id" in e) delete e.__id;
    }
  }

  // Remove internal markers from selected notes before returning slices (if referenced downstream).
  const cleanedSlices = slices.map((s) => ({
    ...s,
    notes: (s.notes ?? []).map((n) => {
      const { __idx, __id, ...rest } = n ?? {};
      return rest;
    }),
  }));

  events.sort((a, b) => a.tick - b.tick || (a.type === "NOTE" ? 0 : 1) - (b.type === "NOTE" ? 0 : 1));

  // Assign deterministic IDs after the final stable sort.
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    const kind = e.type === "REST" ? "rest" : "note";
    e.eventId = `red_${kind}_${i + 1}`;
  }
  return { slices: cleanedSlices, events };
}
