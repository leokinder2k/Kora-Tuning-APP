import { instrumentModel } from "./instrument.js";
import { digitLinesForString } from "./digits.js";
import { parseStringId, renderedNumber as computeRenderedNumber } from "./stringId.js";

export const NoteRole = Object.freeze({
  MELODY: "MELODY",
  HARMONY: "HARMONY",
  BASS: "BASS",
});

export const AccidentalSuggestion = Object.freeze({
  NONE: "NONE",
  SHARP: "SHARP",
  FLAT: "FLAT",
});

function accidentalSuggestionForDelta(delta) {
  if (delta === 0) return AccidentalSuggestion.NONE;
  if (delta === 1) return AccidentalSuggestion.SHARP;
  if (delta === -1) return AccidentalSuggestion.FLAT;
  return null;
}

function digitPriorityRank(role, digitLine) {
  // 0 is best
  if (role === NoteRole.BASS) {
    // Bass: LT → RT → LF → RF
    if (digitLine === "LT") return 0;
    if (digitLine === "RT") return 1;
    if (digitLine === "LF") return 2;
    if (digitLine === "RF") return 3;
  } else {
    // Melody/Harmony: RF → LF → RT → LT
    if (digitLine === "RF") return 0;
    if (digitLine === "LF") return 1;
    if (digitLine === "RT") return 2;
    if (digitLine === "LT") return 3;
  }
  throw new Error(`Unknown digitLine: ${digitLine}`);
}

function correspondingCounterpartLine(digitLine) {
  if (digitLine === "LF") return "LT";
  if (digitLine === "RF") return "RT";
  if (digitLine === "LT") return "LF";
  if (digitLine === "RT") return "RF";
  return null;
}

// When a digit line's rendered number exceeds 6, prefer its counterpart line
// (finger ↔ thumb) if available. This keeps all displayed numbers in the 1–6
// range wherever possible — RF>6 → RT, RT>6 → RF, LF>6 → LT, LT>6 → LF.
function shouldSwitchToCounterpart({ digitLine, renderedNumber, availableDigitLines }) {
  const counterpart = correspondingCounterpartLine(digitLine);
  if (!counterpart) return false;
  if (!Number.isInteger(renderedNumber) || renderedNumber <= 6) return false;
  return Array.isArray(availableDigitLines) && availableDigitLines.includes(counterpart);
}

function continuityPenalty(prevChoice, candidate) {
  if (!prevChoice) return 0;
  const prev = {
    ...parseStringId(prevChoice.stringId),
    digitLine: prevChoice.digitLine,
  };
  const cur = {
    ...parseStringId(candidate.stringId),
    digitLine: candidate.digitLine,
  };

  let p = 0;
  if (prev.side !== cur.side) p += 20;
  if (prev.digitLine !== cur.digitLine) p += 5;
  p += Math.abs(prev.physicalIndexFromGourd - cur.physicalIndexFromGourd);
  return p;
}

export function enumerateKoraMappings({
  instrumentType,
  tuningMidiByStringId,
  assignments,
  pitchMidi,
  role,
  prevChoice = null,
}) {
  const { leftCount, rightCount } = instrumentModel(instrumentType);

  const exact = [];
  const near = [];
  for (const [stringId, tunedMidi] of Object.entries(tuningMidiByStringId)) {
    if (tunedMidi === pitchMidi) exact.push(stringId);
    else if (Math.abs(tunedMidi - pitchMidi) === 1) near.push(stringId);
  }

  let candidates = [];
  if (exact.length > 0) candidates = exact;
  else if (near.length > 0) candidates = near;
  else return [{
    omit: true,
    stringId: null,
    digitLine: null,
    renderedNumber: null,
    accidentalSuggestion: AccidentalSuggestion.NONE,
    score: Number.POSITIVE_INFINITY,
    priorityRank: null,
    ease: null,
    continuity: null,
  }];

  const out = [];

  for (const stringId of candidates) {
    const digitLines = digitLinesForString({ assignments, stringId });
    for (const digitLine of digitLines) {
      const priorityRank = digitPriorityRank(role, digitLine);
      const renderedNumber = computeRenderedNumber({ instrumentType, stringId, digitLine });
      if (shouldSwitchToCounterpart({ digitLine, renderedNumber, availableDigitLines: digitLines })) {
        // Keep displayed numbers ≤6 by switching to the counterpart line (finger↔thumb) when available.
        continue;
      }

      const continuity = continuityPenalty(prevChoice, { stringId, digitLine });
      const ease = renderedNumber; // lower is easier

      // priority dominates, then ease, then continuity.
      const score = priorityRank * 1_000_000 + ease * 10_000 + continuity;

      const { side } = parseStringId(stringId);
      const N = side === "L" ? leftCount : rightCount;
      if (renderedNumber < 1 || renderedNumber > N) {
        throw new Error(`Rendered number out of range: ${renderedNumber} for ${stringId} (${side}${N})`);
      }

      const delta = pitchMidi - tuningMidiByStringId[stringId]; // desired - tuned
      const accidentalSuggestion = accidentalSuggestionForDelta(delta) ?? AccidentalSuggestion.NONE;
      out.push({
        omit: false,
        stringId,
        digitLine,
        renderedNumber,
        accidentalSuggestion,
        score,
        priorityRank,
        ease,
        continuity,
      });
    }
  }

  out.sort((a, b) =>
    a.score - b.score ||
    a.stringId.localeCompare(b.stringId) ||
    a.digitLine.localeCompare(b.digitLine));
  return out;
}

export function mapPitchToKora({
  instrumentType,
  tuningMidiByStringId,
  assignments,
  pitchMidi,
  role,
  prevChoice = null,
}) {
  const candidates = enumerateKoraMappings({
    instrumentType,
    tuningMidiByStringId,
    assignments,
    pitchMidi,
    role,
    prevChoice,
  });
  return candidates[0];
}

export function mapPitchToKoraWithOverride({
  instrumentType,
  tuningMidiByStringId,
  assignments,
  pitchMidi,
  role,
  prevChoice = null,
  override = null,
}) {
  if (!override) return mapPitchToKora({ instrumentType, tuningMidiByStringId, assignments, pitchMidi, role, prevChoice });
  if (typeof override !== "object") throw new Error("override must be an object");
  const stringId = override.stringId;
  if (typeof stringId !== "string" || stringId.length === 0) throw new Error("override.stringId must be a string");
  const tunedMidi = tuningMidiByStringId[stringId];
  if (!Number.isInteger(tunedMidi)) throw new Error(`override.stringId not found in tuning: ${stringId}`);
  const delta = pitchMidi - tunedMidi;
  const accidentalSuggestion = accidentalSuggestionForDelta(delta);
  if (!accidentalSuggestion) {
    throw new Error(`override.stringId ${stringId} cannot play pitch ${pitchMidi} (tuned=${tunedMidi})`);
  }

  const allowedLines = digitLinesForString({ assignments, stringId });
  if (!Array.isArray(allowedLines) || allowedLines.length === 0) {
    throw new Error(`No digit lines available for override stringId: ${stringId}`);
  }

  let digitLine = override.digitLine ?? null;
  if (digitLine !== null) {
    if (typeof digitLine !== "string") throw new Error("override.digitLine must be a string");
    if (!allowedLines.includes(digitLine)) {
      throw new Error(`override.digitLine ${digitLine} not allowed for ${stringId}`);
    }
  } else {
    // Pick best line for this string under the normal scoring model.
    const candidates = [];
    const fingerFallback = [];
    for (const line of allowedLines) {
      const priorityRank = digitPriorityRank(role, line);
      const renderedNumber = computeRenderedNumber({ instrumentType, stringId, digitLine: line });
      if (shouldPreferThumbForHighFingerNumber({ digitLine: line, renderedNumber, availableDigitLines: allowedLines })) {
        fingerFallback.push({ line, renderedNumber });
        continue;
      }
      const continuity = continuityPenalty(prevChoice, { stringId, digitLine: line });
      const ease = renderedNumber;
      const score = priorityRank * 1_000_000 + ease * 10_000 + continuity;
      candidates.push({ line, renderedNumber, score });
    }
    // If every candidate was filtered out (e.g. custom assignment exposes only high finger numbers), fall back.
    if (candidates.length === 0) {
      for (const c of fingerFallback) {
        const priorityRank = digitPriorityRank(role, c.line);
        const continuity = continuityPenalty(prevChoice, { stringId, digitLine: c.line });
        const ease = c.renderedNumber;
        const score = priorityRank * 1_000_000 + ease * 10_000 + continuity;
        candidates.push({ line: c.line, renderedNumber: c.renderedNumber, score });
      }
    }
    candidates.sort((a, b) => a.score - b.score || a.line.localeCompare(b.line));
    digitLine = candidates[0].line;
  }

  const renderedNumber = computeRenderedNumber({ instrumentType, stringId, digitLine });
  return {
    omit: false,
    stringId,
    digitLine,
    renderedNumber,
    accidentalSuggestion,
    overrideApplied: true,
  };
}
