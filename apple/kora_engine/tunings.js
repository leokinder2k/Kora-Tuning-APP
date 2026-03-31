import { InstrumentType } from "./instrument.js";
import { noteNameToMidi } from "./notes.js";

export function fTuningNoteNames(instrumentType) {
  // "F tuning" preset.
  //
  // Confirmed by you as the "Silaba Default" / F major western tuning:
  // - 22-string:
  //   - Left  L01..L11:  F1 C2 D2 E2 G2 Bb2 D3 F3 A3 C4 E4
  //   - Right R01..R11: Bb1 F2 A2 C3 E3 G3 Bb3 D4 F4 G4 A4
  // - 21-string:
  //   - Left  L01..L11:  F1 C2 D2 E2 G2 Bb2 D3 F3 A3 C4 E4
  //   - Right R01..R10: F2 A2 C3 E3 G3 Bb3 D4 F4 G4 A4
  //
  // Note: this is only a default preset; users can edit per-string pitches in the tuning editor.
  if (instrumentType === InstrumentType.KORA_22_CHROMATIC) {
    return {
      name: "F tuning",
      strings: {
        // Left L01..L11
        L01: "F1",
        L02: "C2",
        L03: "D2",
        L04: "E2",
        L05: "G2",
        L06: "Bb2",
        L07: "D3",
        L08: "F3",
        L09: "A3",
        L10: "C4",
        L11: "E4",

        // Right R01..R11
        R01: "Bb1",
        R02: "F2",
        R03: "A2",
        R04: "C3",
        R05: "E3",
        R06: "G3",
        R07: "Bb3",
        R08: "D4",
        R09: "F4",
        R10: "G4",
        R11: "A4",
      },
    };
  }

  if (instrumentType === InstrumentType.KORA_21) {
    return {
      name: "F tuning",
      strings: {
        // Left L01..L11
        L01: "F1",
        L02: "C2",
        L03: "D2",
        L04: "E2",
        L05: "G2",
        L06: "Bb2",
        L07: "D3",
        L08: "F3",
        L09: "A3",
        L10: "C4",
        L11: "E4",

        // Right R01..R10
        R01: "F2",
        R02: "A2",
        R03: "C3",
        R04: "E3",
        R05: "G3",
        R06: "Bb3",
        R07: "D4",
        R08: "F4",
        R09: "G4",
        R10: "A4",
      },
    };
  }

  throw new Error(`No F tuning defined for instrumentType: ${instrumentType}`);
}

export function tuningNoteNamesToMidi(tuningNoteNames) {
  const out = {};
  for (const [stringId, noteName] of Object.entries(tuningNoteNames.strings)) {
    out[stringId] = noteNameToMidi(noteName);
  }
  return { name: tuningNoteNames.name, strings: out };
}
