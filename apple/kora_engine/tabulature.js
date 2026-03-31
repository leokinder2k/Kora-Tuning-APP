import { DigitLine } from "./digits.js";
import { instrumentModel } from "./instrument.js";
import { midiToNoteName } from "./notes.js";
import { renderedNumber } from "./stringId.js";

function sideForDigitLine(digitLine) {
  if (digitLine === DigitLine.LF || digitLine === DigitLine.LT) return "L";
  if (digitLine === DigitLine.RF || digitLine === DigitLine.RT) return "R";
  throw new Error(`Unknown digitLine: ${digitLine}`);
}

function noteNameForString({ tuningByStringId, stringId }) {
  const value = tuningByStringId[stringId];
  if (value === undefined) throw new Error(`Missing tuning for stringId: ${stringId}`);
  if (typeof value === "string") return value;
  if (typeof value === "number") return midiToNoteName(value);
  throw new Error(`Unsupported tuning value for ${stringId}: ${value}`);
}

function idsForSide(side, count) {
  const ids = [];
  for (let i = 1; i <= count; i++) ids.push(`${side}${String(i).padStart(2, "0")}`);
  return ids;
}

export function buildTabulatureReference({ instrumentType, tuningByStringId }) {
  const { leftCount, rightCount } = instrumentModel(instrumentType);
  const byLine = {
    LF: idsForSide("L", leftCount),
    LT: idsForSide("L", leftCount),
    RF: idsForSide("R", rightCount),
    RT: idsForSide("R", rightCount),
  };

  const out = {};
  for (const [digitLine, ids] of Object.entries(byLine)) {
    out[digitLine] = ids
      .map((stringId) => ({
        digitLine,
        side: sideForDigitLine(digitLine),
        stringId,
        number: renderedNumber({ instrumentType, stringId, digitLine }),
        noteName: noteNameForString({ tuningByStringId, stringId }),
      }))
      .sort((a, b) => a.number - b.number);
  }
  return out;
}

export function lookupTabToken({ instrumentType, tuningByStringId, digitLine, number }) {
  if (!Number.isInteger(number) || number < 1) {
    throw new Error(`Invalid tab number: ${number}`);
  }
  const reference = buildTabulatureReference({ instrumentType, tuningByStringId });
  const line = reference[digitLine];
  if (!line) throw new Error(`Unknown digitLine: ${digitLine}`);

  const token = line.find((entry) => entry.number === number);
  if (!token) {
    throw new Error(`Tab number out of range for ${digitLine}: ${number}`);
  }
  return token;
}
