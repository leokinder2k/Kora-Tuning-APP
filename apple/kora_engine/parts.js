function hasNameBoost(name) {
  if (typeof name !== "string") return false;
  return /\b(melody|voice|lead)\b/i.test(name);
}

function notePitches(noteEvents) {
  const pitches = [];
  for (const n of noteEvents ?? []) {
    if (Number.isInteger(n?.pitchMidi)) pitches.push(n.pitchMidi);
  }
  return pitches;
}

export function scorePartForMelody({ part }) {
  const pitches = notePitches(part?.noteEvents ?? []);
  if (pitches.length === 0) {
    return {
      score: Number.NEGATIVE_INFINITY,
      hasNotes: false,
      range: 0,
      avgPitch: 0,
      nameBoost: 0,
    };
  }

  let minPitch = Number.POSITIVE_INFINITY;
  let maxPitch = Number.NEGATIVE_INFINITY;
  let sum = 0;
  for (const p of pitches) {
    minPitch = Math.min(minPitch, p);
    maxPitch = Math.max(maxPitch, p);
    sum += p;
  }
  const range = maxPitch - minPitch;
  const avgPitch = sum / pitches.length;
  const nameBoost = hasNameBoost(part?.name) ? 1 : 0;
  const score = range * 10 + avgPitch + nameBoost * 1000;

  return { score, hasNotes: true, range, avgPitch, nameBoost };
}

export function pickMelodyPart({ parts }) {
  if (!Array.isArray(parts)) throw new Error("parts must be an array");

  let bestIndex = -1;
  let best = null;
  const scored = [];

  for (let i = 0; i < parts.length; i++) {
    const metrics = scorePartForMelody({ part: parts[i] });
    const row = { index: i, partId: parts[i]?.partId ?? null, ...metrics };
    scored.push(row);
    if (!metrics.hasNotes) continue;

    if (!best) {
      best = row;
      bestIndex = i;
      continue;
    }

    if (row.score > best.score) {
      best = row;
      bestIndex = i;
      continue;
    }

    // Deterministic tie-break: prefer higher average pitch, then earlier part order.
    if (row.score === best.score && row.avgPitch > best.avgPitch) {
      best = row;
      bestIndex = i;
    }
  }

  if (!best) {
    return { index: -1, part: null, scoredParts: scored };
  }
  return { index: bestIndex, part: parts[bestIndex], scoredParts: scored };
}
