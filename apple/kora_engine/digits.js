import { allStringIds, parseStringId } from "./stringId.js";

export const DigitLine = Object.freeze({
  LF: "LF",
  LT: "LT",
  RT: "RT",
  RF: "RF",
});

const DIGIT_LINE_ORDER = ["LF", "LT", "RT", "RF"];

export function defaultStringToDigitAssignments(instrumentType) {
  // Default: both tab lines on each side can address all strings on that side.
  const ids = allStringIds(instrumentType);
  const left = ids.filter((id) => id.startsWith("L"));
  const right = ids.filter((id) => id.startsWith("R"));

  return {
    LF: [...left],
    LT: [...left],
    RF: [...right],
    RT: [...right],
  };
}

export function validateStringToDigitAssignments({ instrumentType, assignments }) {
  const expected = new Set(allStringIds(instrumentType));
  const missingLines = DIGIT_LINE_ORDER.filter((line) => !(line in assignments));
  if (missingLines.length > 0) {
    throw new Error(`Assignments missing lines: ${missingLines.join(", ")}`);
  }

  const perLineSets = {};

  for (const [line, list] of Object.entries(assignments)) {
    if (!Array.isArray(list)) throw new Error(`Assignments for ${line} must be an array`);
    if (!(line in DigitLine)) throw new Error(`Unknown digit line: ${line}`);

    const lineSet = new Set();
    perLineSets[line] = lineSet;

    for (const id of list) {
      if (!expected.has(id)) throw new Error(`Unknown stringId in assignments: ${id}`);
      lineSet.add(id);

      const { side } = parseStringId(id);
      if ((line === "LF" || line === "LT") && side !== "L") throw new Error(`${id} must be left side for ${line}`);
      if ((line === "RF" || line === "RT") && side !== "R") throw new Error(`${id} must be right side for ${line}`);
    }
  }

  const leftCoverage = new Set([...(perLineSets.LF ?? []), ...(perLineSets.LT ?? [])]);
  const rightCoverage = new Set([...(perLineSets.RF ?? []), ...(perLineSets.RT ?? [])]);

  const missing = [];
  for (const id of expected) {
    const isLeft = id.startsWith("L");
    const covered = isLeft ? leftCoverage.has(id) : rightCoverage.has(id);
    if (!covered) missing.push(id);
  }
  if (missing.length > 0) {
    throw new Error(`Assignments missing stringIds on their side lines: ${missing.join(", ")}`);
  }
}

export function digitLineForString({ assignments, stringId }) {
  const lines = digitLinesForString({ assignments, stringId });
  if (lines.length === 0) {
    throw new Error(`stringId not assigned to a digit line: ${stringId}`);
  }
  return lines[0];
}

export function digitLinesForString({ assignments, stringId }) {
  const lines = [];
  for (const line of DIGIT_LINE_ORDER) {
    const list = assignments[line];
    if (Array.isArray(list) && list.includes(stringId)) lines.push(line);
  }
  if (lines.length > 0) return lines;

  for (const [line, list] of Object.entries(assignments)) {
    if (Array.isArray(list) && list.includes(stringId)) return [line];
  }
  return [];
}
