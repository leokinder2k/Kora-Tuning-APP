const DIGIT_LINE_ORDER = Object.freeze(["LF", "LT", "RT", "RF"]);

// ---------------------------------------------------------------------------
// Beaming helpers
// ---------------------------------------------------------------------------

function beamFlagCount(durationTicks, ppq) {
  // Returns 0 for quarter/longer (no flags), 1 for 8th, 2 for 16th, 3 for 32nd, 4 for 64th.
  if (!Number.isInteger(durationTicks) || durationTicks <= 0) return 0;
  if (!Number.isInteger(ppq) || ppq <= 0) return 0;
  if (durationTicks > ppq / 2) return 0; // quarter or longer: no beam
  if (durationTicks > ppq / 4) return 1; // 8th
  if (durationTicks > ppq / 8) return 2; // 16th
  if (durationTicks > ppq / 16) return 3; // 32nd
  return 4; // 64th and shorter
}

function beatSizeTicks(timeSignature, ppq) {
  // Simple-time beat = one denominator note value.
  const den = Number.isInteger(timeSignature?.den) && timeSignature.den > 0 ? timeSignature.den : 4;
  return Math.max(1, Math.round((ppq * 4) / den));
}

function measureTimeSignatureAtTick(measures, tick) {
  let best = null;
  for (const m of measures ?? []) {
    if (!Number.isInteger(m?.startTick)) continue;
    if (m.startTick <= tick && (!best || m.startTick > best.startTick)) best = m;
  }
  return best?.timeSignature ?? { num: 4, den: 4 };
}

// Compute beam groups for all notes in a system's staffLayer.
// Returns a Map: sourceEventId -> { groupId, position, flagCount }
function computeBeamGroups({ staffLayer, measures, ppq }) {
  const pq = Number.isInteger(ppq) && ppq > 0 ? ppq : 960;

  // Collect only note tokens that can be beamed (8th or shorter).
  const beamable = staffLayer.filter((t) => {
    if (t.type !== "NOTE") return false;
    if (!Number.isInteger(t.durationTicks)) return false;
    return beamFlagCount(t.durationTicks, pq) > 0;
  });

  // Sort by tick, then by sourceEventId for determinism.
  beamable.sort((a, b) => a.tick - b.tick || String(a.sourceEventId ?? "").localeCompare(String(b.sourceEventId ?? "")));

  const result = new Map();
  if (beamable.length === 0) return result;

  // Group beamable notes into beam runs.
  // A run breaks when: rest between notes, different beat group, or tick gap.
  let runStart = 0;
  let groupCounter = 0;

  const flushRun = (from, to) => {
    const run = beamable.slice(from, to + 1);
    if (run.length === 0) return;
    const gid = `bg_${groupCounter++}`;
    for (let i = 0; i < run.length; i++) {
      const flags = beamFlagCount(run[i].durationTicks, pq);
      let position;
      if (run.length === 1) {
        position = "single";
      } else if (i === 0) {
        position = "start";
      } else if (i === run.length - 1) {
        position = "end";
      } else {
        position = "continue";
      }
      // Sub-beam position: for notes with flagCount >= 2 (16th or shorter),
      // determine how the secondary beam connects to its neighbors within the group.
      let subBeamPosition = null;
      if (flags >= 2) {
        const prevFlags = i > 0 ? beamFlagCount(run[i - 1].durationTicks, pq) : 0;
        const nextFlags = i < run.length - 1 ? beamFlagCount(run[i + 1].durationTicks, pq) : 0;
        const prevHas = prevFlags >= 2;
        const nextHas = nextFlags >= 2;
        if (prevHas && nextHas) subBeamPosition = "continue";
        else if (prevHas) subBeamPosition = "end";
        else if (nextHas) subBeamPosition = "start";
        else subBeamPosition = "single"; // isolated shorter note among longer beamed notes
      }

      result.set(run[i].sourceEventId, { groupId: gid, position, flagCount: flags, subBeamPosition });
    }
  };

  for (let i = 1; i <= beamable.length; i++) {
    if (i === beamable.length) {
      flushRun(runStart, i - 1);
      break;
    }
    const prev = beamable[i - 1];
    const cur = beamable[i];

    // Break if there is a gap (rest or space) between end of prev and start of cur.
    const prevEnd = prev.tick + prev.durationTicks;
    if (cur.tick !== prevEnd) {
      flushRun(runStart, i - 1);
      runStart = i;
      continue;
    }

    // Break if they fall in different beat groups within their measure.
    const ts = measureTimeSignatureAtTick(measures, prev.tick);
    const beat = beatSizeTicks(ts, pq);
    const measureForPrev = measures?.find(
      (m) => Number.isInteger(m?.startTick) && m.startTick <= prev.tick && prev.tick < m.startTick + m.lengthTicks
    );
    const msStart = measureForPrev?.startTick ?? 0;
    const prevBeat = Math.floor((prev.tick - msStart) / beat);
    const curBeat = Math.floor((cur.tick - msStart) / beat);
    if (prevBeat !== curBeat) {
      flushRun(runStart, i - 1);
      runStart = i;
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Stem direction
// ---------------------------------------------------------------------------

function stemMidLineMidi(staff) {
  // Returns the MIDI pitch of the middle staff line for the given clef.
  if (staff === 1 || staff === "UPPER" || staff === "treble" || staff === "Treble") return 71; // B4
  if (staff === 2 || staff === "LOWER" || staff === "bass" || staff === "Bass") return 50; // D3
  return 60; // fallback: middle C
}

function computeStemDirections(systems) {
  for (const s of systems) {
    for (const token of s.staffLayer) {
      if (token.type !== "NOTE" || !Number.isInteger(token.pitchMidi)) {
        token.stemDirection = null;
      } else {
        const mid = stemMidLineMidi(token.staff);
        // Notes strictly above the middle line get stem down; on or below get stem up.
        token.stemDirection = token.pitchMidi > mid ? "DOWN" : "UP";
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Tie arcs
// ---------------------------------------------------------------------------

function computeTieArcs(systems) {
  for (const s of systems) s.tieArcs = [];

  // Collect all NOTE tokens that participate in ties, tagged with their system index.
  const allTied = [];
  for (let si = 0; si < systems.length; si++) {
    for (const t of systems[si].staffLayer) {
      if (t.type === "NOTE" && t.tie) allTied.push({ t, si });
    }
  }

  allTied.sort((a, b) =>
    a.t.tick - b.t.tick ||
    (a.t.pitchMidi ?? -1) - (b.t.pitchMidi ?? -1) ||
    (a.t.sourceEventId ?? "").localeCompare(b.t.sourceEventId ?? "")
  );

  // Pending tie starts: { pitchMidi, t, si }
  const pending = [];

  for (const { t, si } of allTied) {
    const isTieStop = t.tie === "stop" || t.tie === "both" || t.tie === "continue";
    const isTieStart = t.tie === "start" || t.tie === "both" || t.tie === "continue";

    if (isTieStop) {
      const idx = pending.findIndex((p) => p.pitchMidi === t.pitchMidi);
      if (idx >= 0) {
        const { t: startT, si: startSi } = pending.splice(idx, 1)[0];
        const arcDir = startT.stemDirection === "UP" ? "DOWN" : "UP";

        if (startSi === si) {
          systems[si].tieArcs.push({
            startEventId: startT.sourceEventId,
            endEventId: t.sourceEventId,
            startTick: startT.tick,
            endTick: t.tick,
            pitchMidi: t.pitchMidi,
            direction: arcDir,
            crossSystem: false,
          });
        } else {
          systems[startSi].tieArcs.push({
            startEventId: startT.sourceEventId,
            endEventId: null,
            startTick: startT.tick,
            endTick: systems[startSi].endTick,
            pitchMidi: t.pitchMidi,
            direction: arcDir,
            crossSystem: true,
            crossSystemSide: "start",
          });
          systems[si].tieArcs.push({
            startEventId: null,
            endEventId: t.sourceEventId,
            startTick: systems[si].startTick,
            endTick: t.tick,
            pitchMidi: t.pitchMidi,
            direction: arcDir,
            crossSystem: true,
            crossSystemSide: "end",
          });
        }
      }
    }

    if (isTieStart) {
      pending.push({ pitchMidi: t.pitchMidi, t, si });
    }
  }
}

// ---------------------------------------------------------------------------
// Tuplet brackets
// ---------------------------------------------------------------------------

function computeTupletBrackets(systems) {
  for (const s of systems) {
    s.tupletBrackets = [];
    const groups = new Map(); // groupId -> { tokens, actual, normal }

    for (const t of s.staffLayer) {
      const tup = t.tuplet;
      if (!tup || typeof tup.groupId !== "string") continue;
      if (!groups.has(tup.groupId)) {
        groups.set(tup.groupId, {
          tokens: [],
          actual: Number.isInteger(tup.actual) ? tup.actual : 3,
          normal: Number.isInteger(tup.normal) ? tup.normal : 2,
        });
      }
      groups.get(tup.groupId).tokens.push(t);
    }

    for (const [groupId, { tokens, actual, normal }] of groups) {
      if (tokens.length === 0) continue;
      tokens.sort((a, b) => a.tick - b.tick);
      const startTick = tokens[0].tick;
      const last = tokens[tokens.length - 1];
      const endTick = last.tick + (Number.isInteger(last.durationTicks) ? last.durationTicks : 0);
      // Show bracket when any note in the group is not beamed (beamed tuplets show only the ratio number).
      const hasBracket = tokens.some((t) => !t.beamGroup);
      s.tupletBrackets.push({
        groupId,
        ratio: { actual, normal },
        startTick,
        endTick,
        measureNumber: tokens[0].measureNumber ?? null,
        hasBracket,
      });
    }

    s.tupletBrackets.sort((a, b) => a.startTick - b.startTick || a.groupId.localeCompare(b.groupId));
  }
}

function lineRank(line) {
  const idx = DIGIT_LINE_ORDER.indexOf(line);
  return idx >= 0 ? idx : DIGIT_LINE_ORDER.length;
}

function measureNumberAtTick(measures, tick) {
  let best = null;
  for (const m of measures ?? []) {
    if (!Number.isInteger(m?.startTick) || !Number.isInteger(m?.lengthTicks)) continue;
    const end = m.startTick + m.lengthTicks;
    if (m.startTick <= tick && tick < end) {
      if (!best || m.startTick > best.startTick) best = m;
    }
  }
  return best?.measureNumber ?? 1;
}

function normalizeMeasures(measures) {
  const out = (Array.isArray(measures) ? measures : [])
    .filter((m) =>
      Number.isInteger(m?.measureNumber) &&
      m.measureNumber >= 1 &&
      Number.isInteger(m?.startTick) &&
      m.startTick >= 0 &&
      Number.isInteger(m?.lengthTicks) &&
      m.lengthTicks > 0)
    .map((m) => ({
      measureNumber: m.measureNumber,
      startTick: m.startTick,
      lengthTicks: m.lengthTicks,
      timeSignature: m.timeSignature ?? null,
    }))
    .sort((a, b) => a.measureNumber - b.measureNumber || a.startTick - b.startTick);
  return out;
}

function normalizeLayoutBreaks(layoutBreaks) {
  // Break semantics: a break at measure N means "start a new system/page at measure N"
  // (i.e., a break occurs before measure N). This matches MusicXML <print new-system/new-page>.
  const strength = { BAR_BREAK: 1, LINE_BREAK: 2, PAGE_BREAK: 3 };
  const byMeasure = new Map();
  for (const b of layoutBreaks ?? []) {
    if (!b || typeof b !== "object") continue;
    if (!Number.isInteger(b.measureNumber) || b.measureNumber < 1) continue;
    const type = b.type;
    if (type !== "BAR_BREAK" && type !== "LINE_BREAK" && type !== "PAGE_BREAK") continue;
    const cur = byMeasure.get(b.measureNumber) ?? null;
    if (!cur || strength[type] > strength[cur]) byMeasure.set(b.measureNumber, type);
  }
  return byMeasure;
}

function chunkMeasures({ measures, maxMeasuresPerSystem, breakByMeasureNumber }) {
  const systems = [];
  let current = [];

  const pushSystem = ({ group, breakAfter }) => {
    if (group.length === 0) return;
    const start = group[0];
    const end = group[group.length - 1];
    systems.push({
      systemIndex: systems.length,
      measureStart: start.measureNumber,
      measureEnd: end.measureNumber,
      startTick: start.startTick,
      endTick: end.startTick + end.lengthTicks,
      measures: group,
      breakAfter: breakAfter ?? null, // BAR_BREAK | LINE_BREAK | PAGE_BREAK | null
      staffLayer: [],
      tabLayer: [],
      chordLayer: [],
      directionLayer: [],
      dynamicLayer: [],
      lyricsLayer: [],
    });
  };

  for (let i = 0; i < measures.length; i++) {
    const m = measures[i];
    const breakType = breakByMeasureNumber?.get(m.measureNumber) ?? null;

    // If a forced break begins at this measure, finalize the previous system.
    if (current.length > 0 && breakType) {
      pushSystem({ group: current, breakAfter: breakType });
      current = [];
    } else if (current.length >= maxMeasuresPerSystem) {
      pushSystem({ group: current, breakAfter: null });
      current = [];
    }

    current.push(m);
  }

  pushSystem({ group: current, breakAfter: null });
  return systems;
}

function systemIndexByMeasure(systems) {
  const out = new Map();
  for (const s of systems) {
    for (const m of s.measures) {
      out.set(m.measureNumber, s.systemIndex);
    }
  }
  return out;
}

function mappedBySourceId(mappedEvents) {
  const out = new Map();
  for (const e of mappedEvents ?? []) {
    if (typeof e?.sourceEventId === "string") out.set(e.sourceEventId, e);
  }
  return out;
}

function tokenText(mapped, includeOmittedTokens) {
  if (!mapped) return null;
  if (mapped.omit) return includeOmittedTokens ? "×" : null;
  if (!mapped.digitLine || !Number.isInteger(mapped.renderedNumber)) return null;
  return `${mapped.digitLine}${mapped.renderedNumber}`;
}

function eventTypeOrder(type) {
  // Deterministic ordering for events that share the same tick.
  if (type === "CHORD_SYMBOL") return 0;
  if (type === "DIRECTION") return 1;
  if (type === "DYNAMIC") return 2;
  if (type === "NOTE") return 3;
  if (type === "REST") return 4;
  if (type === "LYRICS") return 5;
  return 9;
}

function normalizeText(x) {
  if (x === null || x === undefined) return null;
  if (typeof x === "string") {
    const t = x.trim();
    return t.length > 0 ? t : null;
  }
  if (typeof x === "object") {
    const t = normalizeText(x.text ?? x.mark ?? null);
    return t;
  }
  return null;
}

export function buildLayoutModel({
  score,
  mappedEvents = [],
  maxMeasuresPerSystem = 4,
  includeOmittedTokens = true,
  computeBeams = true,
}) {
  if (!score || typeof score !== "object") throw new Error("score must be an object");
  if (!Number.isInteger(maxMeasuresPerSystem) || maxMeasuresPerSystem < 1) {
    throw new Error(`Invalid maxMeasuresPerSystem: ${maxMeasuresPerSystem}`);
  }

  const measures = normalizeMeasures(score.measures);
  if (measures.length === 0) {
    return {
      systems: [],
      timeline: { diagramHighlights: [] },
    };
  }

  const breakByMeasureNumber = normalizeLayoutBreaks(score.layoutBreaks);
  const systems = chunkMeasures({ measures, maxMeasuresPerSystem, breakByMeasureNumber });
  const byMeasureToSystem = systemIndexByMeasure(systems);
  const mappedBySource = mappedBySourceId(mappedEvents);
  const sourceEvents = Array.isArray(score.events) ? score.events : [];

  const sortedEvents = [...sourceEvents].sort((a, b) => {
    const ta = Number.isInteger(a?.tick) ? a.tick : -1;
    const tb = Number.isInteger(b?.tick) ? b.tick : -1;
    if (ta !== tb) return ta - tb;
    const oa = eventTypeOrder(a?.type);
    const ob = eventTypeOrder(b?.type);
    if (oa !== ob) return oa - ob;
    const pa = Number.isInteger(a?.pitchMidi) ? a.pitchMidi : -1;
    const pb = Number.isInteger(b?.pitchMidi) ? b.pitchMidi : -1;
    if (pa !== pb) return pb - pa;
    return String(a?.eventId ?? "").localeCompare(String(b?.eventId ?? ""));
  });

  for (const e of sortedEvents) {
    if (!Number.isInteger(e?.tick) || e.tick < 0) continue;
    const hasDuration = Number.isInteger(e?.durationTicks) && e.durationTicks > 0;
    const measureNumber = Number.isInteger(e?.measureNumber)
      ? e.measureNumber
      : measureNumberAtTick(measures, e.tick);
    const systemIndex = byMeasureToSystem.get(measureNumber);
    if (!Number.isInteger(systemIndex)) continue;
    const system = systems[systemIndex];

    const sourceEventId = typeof e.eventId === "string" ? e.eventId : null;
    const mapped = sourceEventId ? mappedBySource.get(sourceEventId) ?? null : null;

    if (e.type === "NOTE" || e.type === "REST") {
      if (!hasDuration) continue;
      system.staffLayer.push({
        sourceEventId,
        type: e.type,
        tick: e.tick,
        systemTick: e.tick - system.startTick,
        durationTicks: e.durationTicks,
        measureNumber,
        staff: e.staff ?? null,
        pitchMidi: Number.isInteger(e.pitchMidi) ? e.pitchMidi : null,
        role: e.role ?? null,
        tie: e.tie ?? null,
        tuplet: e.tuplet ?? null,
        importSourceEventId: e.sourceEventId ?? null,
      });
    }

    if (e.type === "NOTE") {
      if (!hasDuration) continue;
      const text = tokenText(mapped, includeOmittedTokens);
      if (text !== null) {
        system.tabLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          durationTicks: e.durationTicks,
          measureNumber,
          digitLine: mapped?.digitLine ?? null,
          renderedNumber: Number.isInteger(mapped?.renderedNumber) ? mapped.renderedNumber : null,
          accidentalSuggestion: mapped?.accidentalSuggestion ?? "NONE",
          omit: Boolean(mapped?.omit),
          tokenText: text,
          tie: e.tie ?? null,
          tuplet: e.tuplet ?? null,
          importSourceEventId: e.sourceEventId ?? null,
        });
      }
    }

    if (e.type === "CHORD_SYMBOL") {
      const text = normalizeText(e.text);
      if (text) {
        system.chordLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text,
        });
      }
    }

    if (e.type === "DIRECTION") {
      const text = normalizeText(e.text);
      if (text) {
        system.directionLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text,
        });
      }
    }

    if (e.type === "DYNAMIC") {
      const mark = normalizeText(e.mark);
      if (mark) {
        system.dynamicLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          mark,
        });
      }
    }

    if (e.type === "LYRICS") {
      const text = normalizeText(e.text);
      if (text) {
        system.lyricsLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text,
        });
      }
    } else if (e.type === "NOTE") {
      const lyricText = normalizeText(e.lyrics ?? e.lyric ?? null);
      if (lyricText) {
        system.lyricsLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text: lyricText,
        });
      }

      const chordInline = normalizeText(e.chordSymbol ?? null);
      if (chordInline) {
        system.chordLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text: chordInline,
        });
      }

      const directionInline = normalizeText(e.direction ?? null);
      if (directionInline) {
        system.directionLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          text: directionInline,
        });
      }

      const dynamicInline = normalizeText(e.dynamic ?? null);
      if (dynamicInline) {
        system.dynamicLayer.push({
          sourceEventId,
          tick: e.tick,
          systemTick: e.tick - system.startTick,
          measureNumber,
          mark: dynamicInline,
        });
      }
    }
  }

  // Annotate staffLayer with beam group info per system.
  const ppq = Number.isInteger(score.ppq) && score.ppq > 0 ? score.ppq : 960;
  if (computeBeams) {
    for (const s of systems) {
      const beamMap = computeBeamGroups({ staffLayer: s.staffLayer, measures: s.measures, ppq });
      for (const token of s.staffLayer) {
        token.beamGroup = beamMap.get(token.sourceEventId) ?? null;
      }
    }
  }

  for (const s of systems) {
    s.staffLayer.sort((a, b) =>
      a.tick - b.tick ||
      (a.type === "NOTE" ? 0 : 1) - (b.type === "NOTE" ? 0 : 1) ||
      (b.pitchMidi ?? -1) - (a.pitchMidi ?? -1) ||
      (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));

    s.tabLayer.sort((a, b) =>
      a.tick - b.tick ||
      lineRank(a.digitLine) - lineRank(b.digitLine) ||
      (a.renderedNumber ?? Number.POSITIVE_INFINITY) - (b.renderedNumber ?? Number.POSITIVE_INFINITY) ||
      (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));

    s.chordLayer.sort((a, b) =>
      a.tick - b.tick || a.text.localeCompare(b.text) || (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));
    s.directionLayer.sort((a, b) =>
      a.tick - b.tick || a.text.localeCompare(b.text) || (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));
    s.dynamicLayer.sort((a, b) =>
      a.tick - b.tick || a.mark.localeCompare(b.mark) || (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));
    s.lyricsLayer.sort((a, b) =>
      a.tick - b.tick || a.text.localeCompare(b.text) || (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));
  }

  // Compute engraving annotations: stem directions, tie arcs, tuplet brackets.
  computeStemDirections(systems);
  computeTieArcs(systems);
  computeTupletBrackets(systems);

  const diagramHighlights = [];
  for (const m of mappedEvents ?? []) {
    if (!m || m.omit) continue;
    if (!Number.isInteger(m.tick) || !Number.isInteger(m.durationTicks) || m.durationTicks <= 0) continue;
    if (typeof m.stringId !== "string" || m.stringId.length === 0) continue;
    diagramHighlights.push({
      sourceEventId: m.sourceEventId ?? null,
      tick: m.tick,
      durationTicks: m.durationTicks,
      stringId: m.stringId,
      measureNumber: Number.isInteger(m.measureNumber) ? m.measureNumber : null,
    });
  }
  diagramHighlights.sort((a, b) =>
    a.tick - b.tick ||
    a.stringId.localeCompare(b.stringId) ||
    (a.sourceEventId ?? "").localeCompare(b.sourceEventId ?? ""));

  return {
    systems,
    timeline: {
      diagramHighlights,
    },
  };
}
