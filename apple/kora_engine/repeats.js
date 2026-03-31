function validateMeasureNumber(name, value) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`Invalid ${name}: ${value}`);
  }
}

function normalizeRepeats(repeats) {
  const starts = new Set();
  const ends = new Set();
  for (const r of repeats ?? []) {
    if (!r || typeof r !== "object") throw new Error(`Invalid repeat entry: ${r}`);
    validateMeasureNumber("repeat.measureNumber", r.measureNumber);
    if (r.type === "START") starts.add(r.measureNumber);
    else if (r.type === "END") ends.add(r.measureNumber);
    else throw new Error(`Unknown repeat type: ${r.type}`);
  }
  return { starts, ends };
}

function normalizeEndings(endings) {
  const list = [];
  for (const e of endings ?? []) {
    if (!e || typeof e !== "object") throw new Error(`Invalid ending entry: ${e}`);
    validateMeasureNumber("ending.startMeasure", e.startMeasure);
    validateMeasureNumber("ending.endMeasure", e.endMeasure);
    validateMeasureNumber("ending.number", e.number);
    if (e.endMeasure < e.startMeasure) {
      throw new Error(`Ending range invalid: ${JSON.stringify(e)}`);
    }
    list.push(e);
  }
  return list;
}

function endingRangesAtMeasure(endings, measureNumber) {
  return endings.filter((e) => e.startMeasure <= measureNumber && measureNumber <= e.endMeasure);
}

export function linearizeMeasures({ lastMeasureNumber, repeats = [], endings = [] }) {
  validateMeasureNumber("lastMeasureNumber", lastMeasureNumber);
  const { starts, ends } = normalizeRepeats(repeats);
  const endingRanges = normalizeEndings(endings);

  const order = [];
  let i = 1;
  let activeRepeatStart = null;
  let repeatPass = 1;
  const repeatedEndMeasures = new Set();

  while (i <= lastMeasureNumber) {
    if (starts.has(i) && activeRepeatStart === null) {
      activeRepeatStart = i;
      repeatPass = 1;
    }

    const activeEndings = endingRangesAtMeasure(endingRanges, i);
    if (activeEndings.length > 0) {
      const include = activeEndings.some((e) => e.number === repeatPass);
      if (!include) {
        const maxEnd = Math.max(...activeEndings.map((e) => e.endMeasure));
        i = maxEnd + 1;
        continue;
      }
    }

    order.push(i);

    if (ends.has(i) && activeRepeatStart !== null && !repeatedEndMeasures.has(i)) {
      repeatedEndMeasures.add(i);
      repeatPass += 1;
      i = activeRepeatStart;
      continue;
    }

    if (ends.has(i)) {
      activeRepeatStart = null;
      repeatPass = 1;
    }

    i += 1;
  }

  return order;
}
