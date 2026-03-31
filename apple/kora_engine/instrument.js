export const InstrumentType = Object.freeze({
  KORA_21: "KORA_21",
  KORA_22_CHROMATIC: "KORA_22_CHROMATIC",
});

export function instrumentModel(instrumentType) {
  switch (instrumentType) {
    case InstrumentType.KORA_21:
      return { leftCount: 11, rightCount: 10 };
    case InstrumentType.KORA_22_CHROMATIC:
      return { leftCount: 11, rightCount: 11 };
    default:
      throw new Error(`Unknown instrumentType: ${instrumentType}`);
  }
}

