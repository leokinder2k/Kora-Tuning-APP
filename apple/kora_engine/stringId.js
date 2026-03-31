import { instrumentModel } from "./instrument.js";

export const Side = Object.freeze({
  L: "L",
  R: "R",
});

/**
 * Canonical IDs:
 * - Left:  L01..L11
 * - Right: R01..R10 or R01..R11
 */
export function parseStringId(stringId) {
  if (typeof stringId !== "string" || stringId.length < 3) {
    throw new Error(`Invalid stringId: ${stringId}`);
  }
  const side = stringId[0];
  if (side !== "L" && side !== "R") throw new Error(`Invalid stringId side: ${stringId}`);
  const num = Number.parseInt(stringId.slice(1), 10);
  if (!Number.isInteger(num) || num < 1) throw new Error(`Invalid stringId index: ${stringId}`);
  return { side, physicalIndexFromGourd: num };
}

export function allStringIds(instrumentType) {
  const { leftCount, rightCount } = instrumentModel(instrumentType);
  const ids = [];
  for (let i = 1; i <= leftCount; i++) ids.push(`L${String(i).padStart(2, "0")}`);
  for (let i = 1; i <= rightCount; i++) ids.push(`R${String(i).padStart(2, "0")}`);
  return ids;
}

/**
 * Finger numbering is physical index. Thumb numbering is reversed (N+1-index).
 */
export function renderedNumber({ instrumentType, stringId, digitLine }) {
  const { side, physicalIndexFromGourd } = parseStringId(stringId);
  const { leftCount, rightCount } = instrumentModel(instrumentType);
  const N = side === "L" ? leftCount : rightCount;

  const usesFingerNumbering = digitLine === "LF" || digitLine === "RF";
  const usesThumbNumbering = digitLine === "LT" || digitLine === "RT";
  if (!usesFingerNumbering && !usesThumbNumbering) throw new Error(`Unknown digitLine: ${digitLine}`);

  if (usesFingerNumbering) return physicalIndexFromGourd;
  return N + 1 - physicalIndexFromGourd;
}

