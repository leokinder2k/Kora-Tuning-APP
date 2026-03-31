import { AccidentalSuggestion } from "./mapping.js";

export function deltaFromAccidentalSuggestion(acc) {
  if (acc === AccidentalSuggestion.NONE) return 0;
  if (acc === AccidentalSuggestion.SHARP) return 1;
  if (acc === AccidentalSuggestion.FLAT) return -1;
  throw new Error(`Unknown accidentalSuggestion: ${acc}`);
}

/**
 * Generate retune/lever plan from mapped events.
 *
 * mappedEvents: [{ stringId, measureNumber, accidentalSuggestion, omit }]
 */
export function generateRetunePlan({ mappedEvents, lastMeasureNumber = null }) {
  const countsByStringByMeasure = new Map(); // stringId -> Map(measureNumber -> {p1,m1})
  let maxMeasureSeen = 0;

  for (const e of mappedEvents) {
    if (!e || e.omit) continue;
    if (!e.stringId) continue;

    if (!Number.isInteger(e.measureNumber) || e.measureNumber < 1) {
      throw new Error(`mapped event missing measureNumber for retune plan: ${JSON.stringify(e)}`);
    }
    maxMeasureSeen = Math.max(maxMeasureSeen, e.measureNumber);

    const delta = deltaFromAccidentalSuggestion(e.accidentalSuggestion);
    if (delta === 0) continue;

    const byMeasure = countsByStringByMeasure.get(e.stringId) ?? new Map();
    const v = byMeasure.get(e.measureNumber) ?? { p1: 0, m1: 0 };
    if (delta === 1) v.p1++;
    if (delta === -1) v.m1++;
    byMeasure.set(e.measureNumber, v);
    countsByStringByMeasure.set(e.stringId, byMeasure);
  }

  const plannedLastMeasure = Number.isInteger(lastMeasureNumber) ? lastMeasureNumber : maxMeasureSeen;

  const perStringNetChange = {};
  const barInstructions = [];

  for (const [stringId, byMeasure] of countsByStringByMeasure.entries()) {
    const desiredAt = (measureNumber) => {
      const v = byMeasure.get(measureNumber);
      if (!v) return 0;
      if (v.p1 > 0 && v.m1 === 0) return 1;
      if (v.m1 > 0 && v.p1 === 0) return -1;
      // Both: deterministic tie-break (prefer +1).
      return v.p1 >= v.m1 ? 1 : -1;
    };

    // Net change summary is the desired state at bar 1 (how to start the piece).
    perStringNetChange[stringId] = desiredAt(1);

    let current = 0;
    for (let m = 1; m <= plannedLastMeasure; m++) {
      const target = desiredAt(m);
      if (target === current) continue;

      // User requirement: plan lever/retune changes one full measure before they must take effect.
      const instructionMeasure = Math.max(1, m - 1);
      barInstructions.push({
        measureNumber: instructionMeasure,
        appliesFromMeasureNumber: m,
        stringId,
        deltaSemitones: target,
      });
      current = target;
    }
  }

  barInstructions.sort(
    (a, b) =>
      a.measureNumber - b.measureNumber ||
      a.appliesFromMeasureNumber - b.appliesFromMeasureNumber ||
      a.stringId.localeCompare(b.stringId),
  );

  return { perStringNetChange, barInstructions };
}
