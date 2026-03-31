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

function ensureFinitePositive(name, value) {
  if (!Number.isFinite(value) || value <= 0) throw new Error(`Invalid ${name}: ${value}`);
  return value;
}

function clamp01(x) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return x;
}

function midiToFrequencyHz(midi) {
  ensureIntInRange("pitchMidi", midi, 0, 127);
  return 440 * 2 ** ((midi - 69) / 12);
}

function buildVelocityBySourceEventId(score) {
  const map = new Map();
  for (const e of score.events ?? []) {
    if (e?.type !== "NOTE") continue;
    if (typeof e.eventId !== "string") continue;
    const v = Number.isInteger(e.velocity) ? Math.max(0, Math.min(127, e.velocity)) : null;
    if (v !== null) map.set(e.eventId, v);
  }
  return map;
}

function tempoRowsFromSchedule(schedule) {
  const byTick = new Map();
  for (const t of schedule?.tempoEvents ?? []) {
    if (!Number.isInteger(t?.tick) || t.tick < 0) continue;
    if (!Number.isFinite(t?.bpm) || t.bpm <= 0) continue;
    byTick.set(t.tick, t.bpm);
  }
  const rows = [...byTick.entries()]
    .map(([tick, bpm]) => ({ tick, bpm }))
    .sort((a, b) => a.tick - b.tick || a.bpm - b.bpm);

  if (rows.length === 0) return [{ tick: 0, bpm: 120 }];
  if (rows[0].tick !== 0) rows.unshift({ tick: 0, bpm: rows[0].bpm });
  return rows;
}

function buildTickToSeconds({ ppq, tempoRows, endTick }) {
  const segments = [];
  let secondsAt = 0;
  for (let i = 0; i < tempoRows.length; i++) {
    const cur = tempoRows[i];
    const nextTick = i + 1 < tempoRows.length ? tempoRows[i + 1].tick : endTick;
    const segEnd = Math.min(nextTick, endTick);
    if (segEnd <= cur.tick) continue;
    const ticksLen = segEnd - cur.tick;
    const segSeconds = (ticksLen / ppq) * (60 / cur.bpm);
    segments.push({
      startTick: cur.tick,
      endTick: segEnd,
      bpm: cur.bpm,
      startSeconds: secondsAt,
    });
    secondsAt += segSeconds;
    if (segEnd === endTick) break;
  }

  if (segments.length === 0) {
    segments.push({ startTick: 0, endTick, bpm: 120, startSeconds: 0 });
  }

  const segmentStarts = segments.map((s) => s.startTick);

  function findSegmentIndex(tick) {
    // Rightmost segment with startTick <= tick.
    let lo = 0;
    let hi = segmentStarts.length - 1;
    let best = 0;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      const v = segmentStarts[mid];
      if (v <= tick) {
        best = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return best;
  }

  function tickToSeconds(tick) {
    if (!Number.isInteger(tick) || tick < 0) throw new Error(`Invalid tick: ${tick}`);
    if (tick === 0) return 0;
    const idx = findSegmentIndex(tick);
    const seg = segments[idx];
    const localTicks = Math.max(0, tick - seg.startTick);
    return seg.startSeconds + (localTicks / ppq) * (60 / seg.bpm);
  }

  const totalSeconds = tickToSeconds(endTick);
  return { tickToSeconds, totalSeconds };
}

function encodeWavPcm16Mono({ pcm, sampleRate }) {
  if (!(pcm instanceof Int16Array)) throw new Error("pcm must be Int16Array");
  ensureIntInRange("sampleRate", sampleRate, 8000, 192000);

  const numChannels = 1;
  const bitsPerSample = 16;
  const blockAlign = numChannels * (bitsPerSample / 8);
  const byteRate = sampleRate * blockAlign;
  const dataBytes = pcm.length * 2;
  const chunkSize = 36 + dataBytes;

  const buf = new ArrayBuffer(44 + dataBytes);
  const view = new DataView(buf);
  const out = new Uint8Array(buf);

  const writeAscii = (offset, text) => {
    for (let i = 0; i < text.length; i++) out[offset + i] = text.charCodeAt(i);
  };

  writeAscii(0, "RIFF");
  view.setUint32(4, chunkSize, true);
  writeAscii(8, "WAVE");
  writeAscii(12, "fmt ");
  view.setUint32(16, 16, true); // PCM fmt chunk size
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, bitsPerSample, true);
  writeAscii(36, "data");
  view.setUint32(40, dataBytes, true);

  let o = 44;
  for (let i = 0; i < pcm.length; i++) {
    view.setInt16(o, pcm[i], true);
    o += 2;
  }

  return out;
}

function renderScheduleToPcm16Mono({
  schedule,
  score,
  sampleRate,
  defaultVelocity,
  masterGain,
  attackSeconds,
  releaseSeconds,
}) {
  ensureScore(score);
  ensureIntInRange("sampleRate", sampleRate, 8000, 192000);
  ensureIntInRange("defaultVelocity", defaultVelocity, 0, 127);
  ensureFinitePositive("attackSeconds", attackSeconds);
  ensureFinitePositive("releaseSeconds", releaseSeconds);

  const ppq = Number.isInteger(score.ppq) && score.ppq > 0 ? score.ppq : 960;
  const endTick = Number.isInteger(schedule?.totalLengthTicks) && schedule.totalLengthTicks > 0 ? schedule.totalLengthTicks : 0;
  const tempoRows = tempoRowsFromSchedule(schedule);
  const { tickToSeconds, totalSeconds } = buildTickToSeconds({ ppq, tempoRows, endTick });

  const totalSamples = Math.max(1, Math.ceil(totalSeconds * sampleRate));
  const mix = new Float32Array(totalSamples);
  const velocityById = buildVelocityBySourceEventId(score);

  const harmonics = [
    { mul: 1, gain: 1.0 },
    { mul: 2, gain: 0.35 },
    { mul: 3, gain: 0.18 },
    { mul: 4, gain: 0.10 },
  ];

  for (const n of schedule?.noteEvents ?? []) {
    if (!Number.isInteger(n?.tick) || n.tick < 0) continue;
    if (!Number.isInteger(n?.durationTicks) || n.durationTicks <= 0) continue;
    if (!Number.isInteger(n?.pitchMidi)) continue;

    const startSec = tickToSeconds(n.tick);
    const endSec = tickToSeconds(n.tick + n.durationTicks);
    const durSec = Math.max(0, endSec - startSec);
    if (durSec <= 0) continue;

    const startSample = Math.max(0, Math.min(totalSamples, Math.floor(startSec * sampleRate)));
    const endSample = Math.max(startSample, Math.min(totalSamples, Math.floor(endSec * sampleRate)));
    const durSamples = endSample - startSample;
    if (durSamples <= 0) continue;

    const freq = midiToFrequencyHz(n.pitchMidi);
    const baseInc = (2 * Math.PI * freq) / sampleRate;

    const v = velocityById.get(n.sourceEventId) ?? defaultVelocity;
    const vel = clamp01(v / 127);
    const amp = masterGain * (vel ** 1.2);

    const attackSampTarget = Math.max(1, Math.round(attackSeconds * sampleRate));
    const releaseSampTarget = Math.max(1, Math.round(releaseSeconds * sampleRate));
    const attackSamp = Math.min(attackSampTarget, Math.max(1, Math.floor(durSamples * 0.25)));
    const releaseSamp = Math.min(releaseSampTarget, Math.max(1, Math.floor(durSamples * 0.5)));
    const sustainEnd = Math.max(attackSamp, durSamples - releaseSamp);

    for (let i = 0; i < durSamples; i++) {
      let env;
      if (i < attackSamp) env = i / attackSamp;
      else if (i >= sustainEnd) env = Math.max(0, 1 - (i - sustainEnd) / Math.max(1, durSamples - sustainEnd));
      else env = 1;

      const phase = i * baseInc;
      let s = 0;
      for (const h of harmonics) {
        s += Math.sin(phase * h.mul) * h.gain;
      }
      mix[startSample + i] += s * amp * env;
    }
  }

  const pcm = new Int16Array(totalSamples);
  for (let i = 0; i < totalSamples; i++) {
    let x = mix[i];
    if (x > 1) x = 1;
    else if (x < -1) x = -1;
    pcm[i] = Math.round(x * 32767);
  }

  return pcm;
}

/**
 * Render a simplified score to raw PCM (Int16Array, mono).
 * Useful for platform-specific codec wrappers (MP3, M4A, etc.).
 */
export function exportSimplifiedScoreToPcm16Mono({
  score,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
}) {
  const s = ensureScore(score);
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureFinitePositive("masterGain", masterGain);

  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents: [],
    playMode: PlayMode.PLAY_AS_WRITTEN,
    repeatsMode,
  });

  return renderScheduleToPcm16Mono({
    schedule,
    score: s,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });
}

/**
 * Render a kora performance to raw PCM (Int16Array, mono).
 * Useful for platform-specific codec wrappers (MP3, M4A, etc.).
 */
export function exportKoraPerformanceToPcm16Mono({
  score,
  mappedEvents,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
}) {
  const s = ensureScore(score);
  if (!Array.isArray(mappedEvents)) throw new Error("mappedEvents must be an array");
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureFinitePositive("masterGain", masterGain);

  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents,
    playMode: PlayMode.PLAY_AS_KORA,
    repeatsMode,
  });

  return renderScheduleToPcm16Mono({
    schedule,
    score: s,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });
}

export function exportSimplifiedScoreToWavBytes({
  score,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
}) {
  const s = ensureScore(score);
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureFinitePositive("masterGain", masterGain);

  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents: [],
    playMode: PlayMode.PLAY_AS_WRITTEN,
    repeatsMode,
  });

  const pcm = renderScheduleToPcm16Mono({
    schedule,
    score: s,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  return encodeWavPcm16Mono({ pcm, sampleRate });
}

export function exportKoraPerformanceToWavBytes({
  score,
  mappedEvents,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
}) {
  const s = ensureScore(score);
  if (!Array.isArray(mappedEvents)) throw new Error("mappedEvents must be an array");
  if (!(repeatsMode in RepeatsMode)) throw new Error(`Unknown repeatsMode: ${repeatsMode}`);
  ensureFinitePositive("masterGain", masterGain);

  const schedule = buildPlaybackSchedule({
    score: s,
    mappedEvents,
    playMode: PlayMode.PLAY_AS_KORA,
    repeatsMode,
  });

  const pcm = renderScheduleToPcm16Mono({
    schedule,
    score: s,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  return encodeWavPcm16Mono({ pcm, sampleRate });
}

