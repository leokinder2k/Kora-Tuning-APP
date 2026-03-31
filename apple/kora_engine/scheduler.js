import { linearizeMeasures } from "./repeats.js";

export const PlayMode = Object.freeze({
  PLAY_AS_KORA: "PLAY_AS_KORA",
  PLAY_AS_WRITTEN: "PLAY_AS_WRITTEN",
});

export const RepeatsMode = Object.freeze({
  LINEARIZE: "LINEARIZE",
  FOLLOW_REPEATS: "FOLLOW_REPEATS",
});

function normalizeTempoMap(tempoMap) {
  const rows = [];
  for (const t of tempoMap ?? []) {
    if (!Number.isInteger(t?.tick) || t.tick < 0) continue;
    if (!Number.isFinite(t?.bpm) || t.bpm <= 0) continue;
    rows.push({ tick: t.tick, bpm: t.bpm });
  }
  rows.sort((a, b) => a.tick - b.tick || a.bpm - b.bpm);
  if (rows.length === 0 || rows[0].tick !== 0) {
    rows.unshift({ tick: 0, bpm: rows[0]?.bpm ?? 120 });
  }
  return rows;
}

function bpmAtSourceTick(tempoRows, tick) {
  let bpm = tempoRows[0].bpm;
  for (const row of tempoRows) {
    if (row.tick > tick) break;
    bpm = row.bpm;
  }
  return bpm;
}

function measureByNumber(measures) {
  const map = new Map();
  for (const m of measures ?? []) {
    if (!Number.isInteger(m?.measureNumber) || m.measureNumber < 1) continue;
    if (!Number.isInteger(m?.startTick) || m.startTick < 0) continue;
    if (!Number.isInteger(m?.lengthTicks) || m.lengthTicks <= 0) continue;
    map.set(m.measureNumber, m);
  }
  return map;
}

function measureNumberAtTick(measures, tick) {
  let best = null;
  for (const m of measures ?? []) {
    if (!Number.isInteger(m?.startTick) || !Number.isInteger(m?.lengthTicks)) continue;
    const end = m.startTick + m.lengthTicks;
    if (m.startTick <= tick && tick < end) {
      if (!best || m.startTick > best.startTick) best = m;
    }
  }
  return best?.measureNumber ?? null;
}

function buildMeasureOrder({ score, repeatsMode }) {
  const measures = Array.isArray(score?.measures) ? score.measures : [];
  const lastMeasureNumber = measures.length > 0
    ? Math.max(...measures.map((m) => (Number.isInteger(m?.measureNumber) ? m.measureNumber : 0)))
    : 0;
  if (lastMeasureNumber <= 0) return [];

  if (repeatsMode === RepeatsMode.LINEARIZE) {
    return linearizeMeasures({
      lastMeasureNumber,
      repeats: Array.isArray(score?.repeats) ? score.repeats : [],
      endings: Array.isArray(score?.endings) ? score.endings : [],
    });
  }

  const sequential = [];
  for (let m = 1; m <= lastMeasureNumber; m++) sequential.push(m);
  return sequential;
}

function mappedEventBySourceId(mappedEvents) {
  const out = new Map();
  for (const e of mappedEvents ?? []) {
    if (typeof e?.sourceEventId === "string") out.set(e.sourceEventId, e);
  }
  return out;
}

export function buildPlaybackSchedule({
  score,
  mappedEvents = [],
  playMode = PlayMode.PLAY_AS_KORA,
  repeatsMode = RepeatsMode.LINEARIZE,
}) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  if (!(playMode in PlayMode)) throw new Error(`Unknown playMode: ${playMode}`);
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);

  const measures = Array.isArray(score.measures) ? score.measures : [];
  const ppq = Number.isInteger(score.ppq) && score.ppq > 0 ? score.ppq : 960;
  const tempoRows = normalizeTempoMap(score.tempoMap);
  const measureMap = measureByNumber(measures);
  const order = buildMeasureOrder({ score, repeatsMode });
  const sourceNotes = (Array.isArray(score.events) ? score.events : [])
    .filter((e) => e?.type === "NOTE" && Number.isInteger(e?.tick) && Number.isInteger(e?.durationTicks) && Number.isInteger(e?.pitchMidi))
    .sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi);

  // Group note events by source measure for fast expansion.
  const notesByMeasure = new Map();
  for (const n of sourceNotes) {
    const m = measureNumberAtTick(measures, n.tick);
    if (!Number.isInteger(m)) continue;
    const list = notesByMeasure.get(m) ?? [];
    list.push(n);
    notesByMeasure.set(m, list);
  }

  const mappedBySource = mappedEventBySourceId(mappedEvents);
  const scheduledNotes = [];
  const occurrences = [];
  const tempoEvents = [];
  const metronomeEvents = [];
  const cursorEvents = [];
  const highlightEvents = [];
  let playbackTickOffset = 0;

  for (let i = 0; i < order.length; i++) {
    const measureNumber = order[i];
    const m = measureMap.get(measureNumber);
    if (!m) continue;

    occurrences.push({
      occurrenceIndex: i,
      measureNumber,
      playbackStartTick: playbackTickOffset,
      sourceStartTick: m.startTick,
      lengthTicks: m.lengthTicks,
    });

    cursorEvents.push({
      type: "MEASURE",
      occurrenceIndex: i,
      measureNumber,
      tick: playbackTickOffset,
      durationTicks: m.lengthTicks,
    });

    // Tempo timeline for this occurrence: start tempo + in-measure tempo changes.
    const sourceEndTick = m.startTick + m.lengthTicks;
    const bpmStart = bpmAtSourceTick(tempoRows, m.startTick);
    tempoEvents.push({
      occurrenceIndex: i,
      measureNumber,
      sourceTick: m.startTick,
      tick: playbackTickOffset,
      bpm: bpmStart,
    });
    for (const tr of tempoRows) {
      if (tr.tick <= m.startTick || tr.tick >= sourceEndTick) continue;
      tempoEvents.push({
        occurrenceIndex: i,
        measureNumber,
        sourceTick: tr.tick,
        tick: playbackTickOffset + (tr.tick - m.startTick),
        bpm: tr.bpm,
      });
    }

    // Metronome events based on per-measure time signature or 4/4 fallback.
    const tsNum = Number.isInteger(m.timeSignature?.num) && m.timeSignature.num > 0 ? m.timeSignature.num : 4;
    const tsDen = Number.isInteger(m.timeSignature?.den) && m.timeSignature.den > 0 ? m.timeSignature.den : 4;
    const beatTicks = Math.max(1, Math.round((ppq * 4) / tsDen));
    for (let beat = 0; beat < tsNum; beat++) {
      const rel = beat * beatTicks;
      if (rel >= m.lengthTicks) break;
      metronomeEvents.push({
        occurrenceIndex: i,
        measureNumber,
        beatIndex: beat,
        isDownbeat: beat === 0,
        tick: playbackTickOffset + rel,
      });
    }

    const measureNotes = notesByMeasure.get(measureNumber) ?? [];
    for (const n of measureNotes) {
      const relTick = n.tick - m.startTick;
      if (relTick < 0 || relTick >= m.lengthTicks) continue;

      const mapped = typeof n.eventId === "string" ? mappedBySource.get(n.eventId) : null;
      const omit = Boolean(mapped?.omit);

      cursorEvents.push({
        type: "NOTE",
        sourceEventId: n.eventId ?? null,
        occurrenceIndex: i,
        measureNumber,
        tick: playbackTickOffset + relTick,
        durationTicks: n.durationTicks,
        omit,
      });

      if (!omit && typeof mapped?.stringId === "string") {
        highlightEvents.push({
          sourceEventId: n.eventId ?? null,
          occurrenceIndex: i,
          measureNumber,
          tick: playbackTickOffset + relTick,
          durationTicks: n.durationTicks,
          stringId: mapped.stringId,
        });
      }

      if (playMode === PlayMode.PLAY_AS_KORA && omit) continue;

      scheduledNotes.push({
        sourceEventId: n.eventId ?? null,
        occurrenceIndex: i,
        measureNumber,
        tick: playbackTickOffset + relTick,
        durationTicks: n.durationTicks,
        pitchMidi: n.pitchMidi,
        stringId: mapped?.stringId ?? null,
        digitLine: mapped?.digitLine ?? null,
        renderedNumber: mapped?.renderedNumber ?? null,
      });
    }

    playbackTickOffset += m.lengthTicks;
  }

  scheduledNotes.sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi);
  tempoEvents.sort((a, b) => a.tick - b.tick || a.bpm - b.bpm || a.occurrenceIndex - b.occurrenceIndex);
  metronomeEvents.sort((a, b) => a.tick - b.tick || a.beatIndex - b.beatIndex || a.occurrenceIndex - b.occurrenceIndex);
  cursorEvents.sort((a, b) =>
    a.tick - b.tick ||
    (a.type === "MEASURE" ? 0 : 1) - (b.type === "MEASURE" ? 0 : 1) ||
    (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));
  highlightEvents.sort((a, b) =>
    a.tick - b.tick || a.stringId.localeCompare(b.stringId) || (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));

  return {
    measureOrder: order,
    occurrences,
    noteEvents: scheduledNotes,
    tempoEvents,
    metronomeEvents,
    cursorEvents,
    highlightEvents,
    totalLengthTicks: playbackTickOffset,
  };
}
