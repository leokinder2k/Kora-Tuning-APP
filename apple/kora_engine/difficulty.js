import { buildTimeSlices } from "./reduction.js";

function clamp01(x) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return x;
}

function sortedTempoMap(tempoMap) {
  const rows = Array.isArray(tempoMap) ? tempoMap : [];
  const cleaned = [];
  for (const t of rows) {
    if (!Number.isInteger(t?.tick) || t.tick < 0) continue;
    if (!Number.isFinite(t?.bpm) || t.bpm <= 0) continue;
    cleaned.push({ tick: t.tick, bpm: t.bpm });
  }
  if (cleaned.length === 0) return [{ tick: 0, bpm: 120 }];
  cleaned.sort((a, b) => a.tick - b.tick);
  if (cleaned[0].tick !== 0) {
    cleaned.unshift({ tick: 0, bpm: cleaned[0].bpm });
  }
  return cleaned;
}

function tempoSegmentsUntil({ tempoMap, endTick }) {
  const sorted = sortedTempoMap(tempoMap);
  const segments = [];
  for (let i = 0; i < sorted.length; i++) {
    const cur = sorted[i];
    if (cur.tick >= endTick) break;
    const nextTick = i + 1 < sorted.length ? sorted[i + 1].tick : endTick;
    const segEnd = Math.min(nextTick, endTick);
    const len = segEnd - cur.tick;
    if (len > 0) {
      segments.push({ bpm: cur.bpm, startTick: cur.tick, endTick: segEnd, lengthTicks: len });
    }
  }
  return segments;
}

function ticksToSeconds({ tickLength, bpm, ppq }) {
  return (tickLength / ppq) * (60 / bpm);
}

export function timeWeightedMedianBpm({ tempoMap, endTick, ppq = 960 }) {
  if (!Number.isInteger(endTick) || endTick < 0) throw new Error(`Invalid endTick: ${endTick}`);
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);
  if (endTick === 0) return sortedTempoMap(tempoMap)[0].bpm;

  const segments = tempoSegmentsUntil({ tempoMap, endTick });
  if (segments.length === 0) return sortedTempoMap(tempoMap)[0].bpm;

  const durationByBpm = new Map();
  for (const seg of segments) {
    const seconds = ticksToSeconds({ tickLength: seg.lengthTicks, bpm: seg.bpm, ppq });
    durationByBpm.set(seg.bpm, (durationByBpm.get(seg.bpm) ?? 0) + seconds);
  }

  const rows = [...durationByBpm.entries()]
    .map(([bpm, durationSeconds]) => ({ bpm, durationSeconds }))
    .sort((a, b) => a.bpm - b.bpm);
  const total = rows.reduce((s, r) => s + r.durationSeconds, 0);
  const threshold = total / 2;

  let acc = 0;
  for (const r of rows) {
    acc += r.durationSeconds;
    if (acc >= threshold) return r.bpm;
  }
  return rows[rows.length - 1].bpm;
}

export function analyzeScoreDensity({ events, tempoMap, ppq = 960 }) {
  if (!Array.isArray(events)) throw new Error("events must be an array");
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);

  const notes = events.filter((e) => e?.type === "NOTE" && Number.isInteger(e?.tick) && Number.isInteger(e?.durationTicks));
  if (notes.length === 0) {
    return {
      totalNotes: 0,
      totalSeconds: 0,
      notesPerSecond: 0,
      avgSimultaneousNotes: 0,
      endTick: 0,
    };
  }

  let endTick = 0;
  for (const n of notes) {
    endTick = Math.max(endTick, n.tick + n.durationTicks);
  }
  const segments = tempoSegmentsUntil({ tempoMap, endTick });
  let totalSeconds = 0;
  for (const seg of segments) {
    totalSeconds += ticksToSeconds({ tickLength: seg.lengthTicks, bpm: seg.bpm, ppq });
  }
  if (totalSeconds === 0) totalSeconds = Number.EPSILON;

  const slices = buildTimeSlices({
    noteEvents: notes.map((n) => ({
      eventId: n.eventId ?? null,
      tick: n.tick,
      durationTicks: n.durationTicks,
      pitchMidi: Number.isInteger(n.pitchMidi) ? n.pitchMidi : 60,
    })),
  });

  let weightedCountSum = 0;
  let weightedLengthSum = 0;
  for (const s of slices) {
    weightedCountSum += s.notes.length * s.lengthTicks;
    weightedLengthSum += s.lengthTicks;
  }
  const avgSimultaneousNotes = weightedLengthSum > 0 ? weightedCountSum / weightedLengthSum : 0;
  const notesPerSecond = notes.length / totalSeconds;

  return {
    totalNotes: notes.length,
    totalSeconds,
    notesPerSecond,
    avgSimultaneousNotes,
    endTick,
  };
}

export function estimateDifficulty({
  tempoBpm,
  notesPerSecond,
  avgSimultaneousNotes,
}) {
  if (!Number.isFinite(tempoBpm) || tempoBpm <= 0) throw new Error(`Invalid tempoBpm: ${tempoBpm}`);
  if (!Number.isFinite(notesPerSecond) || notesPerSecond < 0) throw new Error(`Invalid notesPerSecond: ${notesPerSecond}`);
  if (!Number.isFinite(avgSimultaneousNotes) || avgSimultaneousNotes < 0) {
    throw new Error(`Invalid avgSimultaneousNotes: ${avgSimultaneousNotes}`);
  }

  const chordFactor = 1 + 0.15 * Math.max(0, avgSimultaneousNotes - 1);
  const raw = (tempoBpm / 160) * 0.55 + (notesPerSecond / 8) * 0.45;
  return clamp01(raw * chordFactor);
}

export function estimateDifficultyFromScore({ score }) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  const ppq = Number.isInteger(score.ppq) && score.ppq > 0 ? score.ppq : 960;
  const events = Array.isArray(score.events) ? score.events : [];
  const tempoMap = Array.isArray(score.tempoMap) ? score.tempoMap : [{ tick: 0, bpm: 120 }];

  const density = analyzeScoreDensity({ events, tempoMap, ppq });
  if (density.totalNotes === 0) {
    return {
      score: 0,
      metrics: {
        tempoBpm: 120,
        notesPerSecond: 0,
        avgSimultaneousNotes: 0,
        totalNotes: 0,
        totalSeconds: 0,
      },
    };
  }

  const tempoBpm = timeWeightedMedianBpm({ tempoMap, endTick: density.endTick, ppq });
  const scoreOut = estimateDifficulty({
    tempoBpm,
    notesPerSecond: density.notesPerSecond,
    avgSimultaneousNotes: density.avgSimultaneousNotes,
  });

  return {
    score: scoreOut,
    metrics: {
      tempoBpm,
      notesPerSecond: density.notesPerSecond,
      avgSimultaneousNotes: density.avgSimultaneousNotes,
      totalNotes: density.totalNotes,
      totalSeconds: density.totalSeconds,
    },
  };
}
