import { defaultStringToDigitAssignments, validateStringToDigitAssignments } from "./digits.js";
import { mapPitchToKora, mapPitchToKoraWithOverride, NoteRole, AccidentalSuggestion } from "./mapping.js";
import { generateRetunePlan } from "./retunePlan.js";

function normalizeRole(role) {
  if (role === NoteRole.BASS) return NoteRole.BASS;
  if (role === NoteRole.HARMONY) return NoteRole.HARMONY;
  return NoteRole.MELODY;
}

function measureNumberAtTick({ measures, tick }) {
  if (!Array.isArray(measures) || measures.length === 0) return 1;
  let best = measures[0];
  for (const m of measures) {
    if (!Number.isInteger(m?.startTick)) continue;
    if (m.startTick <= tick && m.startTick >= best.startTick) best = m;
  }
  return Number.isInteger(best?.measureNumber) && best.measureNumber > 0 ? best.measureNumber : 1;
}

export function mapSimplifiedScoreToKora({
  instrumentType,
  tuningMidiByStringId,
  assignments = null,
  mappingOverrides = null,
  score,
}) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  if (!tuningMidiByStringId || typeof tuningMidiByStringId !== "object") {
    throw new Error("tuningMidiByStringId must be an object");
  }

  const resolvedAssignments = assignments ?? defaultStringToDigitAssignments(instrumentType);
  validateStringToDigitAssignments({ instrumentType, assignments: resolvedAssignments });

  const noteEvents = (Array.isArray(score.events) ? score.events : [])
    .filter((e) => e?.type === "NOTE" && Number.isInteger(e?.tick) && Number.isInteger(e?.durationTicks) && Number.isInteger(e?.pitchMidi))
    .sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi);

  const prevByRole = new Map();
  const mappedEvents = [];
  const overridesBySourceEventId = new Map();
  if (mappingOverrides && typeof mappingOverrides === "object") {
    const list = Array.isArray(mappingOverrides) ? mappingOverrides : Object.values(mappingOverrides);
    for (const o of list) {
      if (!o || typeof o !== "object") continue;
      const sourceEventId = o.sourceEventId ?? o.eventId ?? null;
      if (typeof sourceEventId !== "string" || sourceEventId.length === 0) continue;
      if (typeof o.stringId !== "string" || o.stringId.length === 0) continue;
      const digitLine = typeof o.digitLine === "string" && o.digitLine.length > 0 ? o.digitLine : null;
      overridesBySourceEventId.set(sourceEventId, { stringId: o.stringId, digitLine });
    }
  }

  for (const e of noteEvents) {
    const role = normalizeRole(e.role);
    const prevChoice = prevByRole.get(role) ?? null;
    const override = typeof e?.eventId === "string" ? overridesBySourceEventId.get(e.eventId) ?? null : null;
    const mapped = override
      ? mapPitchToKoraWithOverride({
        instrumentType,
        tuningMidiByStringId,
        assignments: resolvedAssignments,
        pitchMidi: e.pitchMidi,
        role,
        prevChoice,
        override,
      })
      : mapPitchToKora({
        instrumentType,
        tuningMidiByStringId,
        assignments: resolvedAssignments,
        pitchMidi: e.pitchMidi,
        role,
        prevChoice,
      });

    const out = {
      sourceEventId: e.eventId ?? null,
      tick: e.tick,
      durationTicks: e.durationTicks,
      measureNumber: measureNumberAtTick({ measures: score.measures, tick: e.tick }),
      role,
      pitchMidi: e.pitchMidi,
      stringId: mapped.stringId,
      digitLine: mapped.digitLine,
      renderedNumber: mapped.renderedNumber,
      accidentalSuggestion: mapped.accidentalSuggestion ?? AccidentalSuggestion.NONE,
      omit: mapped.omit,
    };
    mappedEvents.push(out);
    if (!mapped.omit) {
      prevByRole.set(role, {
        stringId: mapped.stringId,
        digitLine: mapped.digitLine,
      });
    }
  }

  const lastMeasureNumber = Array.isArray(score.measures)
    ? Math.max(1, ...score.measures.map((m) => (Number.isInteger(m?.measureNumber) ? m.measureNumber : 1)))
    : Math.max(1, ...mappedEvents.map((e) => e.measureNumber), 1);

  const retunePlan = generateRetunePlan({
    mappedEvents,
    lastMeasureNumber,
  });

  return {
    events: mappedEvents,
    retunePlan,
  };
}
