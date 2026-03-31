// node:zlib not available in browser — MXL extraction is handled by the native Android layer
function inflateRawSync() { throw new Error("MXL not supported; pass xmlText directly"); }

import { MidiQuantizeStrength, quantizeMidiNotes } from "./midi.js";
import { pickMelodyPart } from "./parts.js";
import { buildSimplifiedTeachingReduction } from "./reduction.js";

const STEP_TO_PC = Object.freeze({
  C: 0,
  D: 2,
  E: 4,
  F: 5,
  G: 7,
  A: 9,
  B: 11,
});

function ensureString(input, name) {
  if (typeof input !== "string") throw new Error(`${name} must be a string`);
  return input;
}

function ensureBytes(input, name) {
  if (input instanceof Uint8Array) return input;
  if (input instanceof ArrayBuffer) return new Uint8Array(input);
  if (ArrayBuffer.isView(input)) return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
  throw new Error(`${name} must be bytes`);
}

function firstMatch(text, pattern) {
  const m = text.match(pattern);
  return m ? m[1] : null;
}

function parseIntOrNull(value) {
  if (value === null || value === undefined) return null;
  const n = Number.parseInt(String(value).trim(), 10);
  return Number.isInteger(n) ? n : null;
}

function parseFloatOrNull(value) {
  if (value === null || value === undefined) return null;
  const n = Number.parseFloat(String(value).trim());
  return Number.isFinite(n) ? n : null;
}

function parseAttributesFromOpenTag(openTagText) {
  const attrs = {};
  const re = /([A-Za-z_:][A-Za-z0-9_:\-\.]*)\s*=\s*"([^"]*)"/g;
  let m = re.exec(openTagText);
  while (m) {
    attrs[m[1]] = m[2];
    m = re.exec(openTagText);
  }
  return attrs;
}

function decodeXmlEntities(text) {
  if (typeof text !== "string" || text.indexOf("&") < 0) return text;
  return text.replace(/&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);/g, (full, ent) => {
    switch (ent) {
      case "amp":
        return "&";
      case "lt":
        return "<";
      case "gt":
        return ">";
      case "quot":
        return "\"";
      case "apos":
        return "'";
      default: {
        try {
          if (ent.startsWith("#x") || ent.startsWith("#X")) {
            const cp = Number.parseInt(ent.slice(2), 16);
            return Number.isFinite(cp) ? String.fromCodePoint(cp) : full;
          }
          if (ent.startsWith("#")) {
            const cp = Number.parseInt(ent.slice(1), 10);
            return Number.isFinite(cp) ? String.fromCodePoint(cp) : full;
          }
        } catch {
          // ignore
        }
        return full;
      }
    }
  });
}

function pitchToMidi({ step, alter = 0, octave }) {
  const pc = STEP_TO_PC[step];
  if (pc === undefined) return null;
  const midi = (octave + 1) * 12 + pc + alter;
  if (!Number.isInteger(midi) || midi < 0 || midi > 127) return null;
  return midi;
}

function normalizeRowsByTick(rows, fallbackAtTick0) {
  const byTick = new Map();
  for (const r of rows ?? []) {
    if (!Number.isInteger(r?.tick) || r.tick < 0) continue;
    byTick.set(r.tick, r);
  }
  const out = [...byTick.values()].sort((a, b) => a.tick - b.tick);
  if (fallbackAtTick0 && (out.length === 0 || out[0].tick !== 0)) {
    out.unshift({ ...fallbackAtTick0, tick: 0 });
  }
  return out;
}

function measureLengthTicks({ ppq, num, den }) {
  return Math.max(1, Math.round((ppq * 4 * num) / den));
}

function extendMeasuresToEnd({ measures, timeSignatures, endTick, ppq }) {
  const out = [...measures];
  const tsRows = normalizeRowsByTick(timeSignatures, { num: 4, den: 4 });
  if (out.length === 0) {
    out.push({
      measureNumber: 1,
      startTick: 0,
      lengthTicks: measureLengthTicks({ ppq, num: tsRows[0].num, den: tsRows[0].den }),
      timeSignature: { num: tsRows[0].num, den: tsRows[0].den },
    });
  }

  const tsAtTick = (tick) => {
    let cur = tsRows[0];
    for (const t of tsRows) {
      if (t.tick > tick) break;
      cur = t;
    }
    return cur;
  };

  while (true) {
    const last = out[out.length - 1];
    const lastEnd = last.startTick + last.lengthTicks;
    if (lastEnd >= endTick) break;
    const ts = tsAtTick(lastEnd);
    out.push({
      measureNumber: last.measureNumber + 1,
      startTick: lastEnd,
      lengthTicks: measureLengthTicks({ ppq, num: ts.num, den: ts.den }),
      timeSignature: { num: ts.num, den: ts.den },
    });
  }
  return out;
}

function parsePartList(xmlText) {
  const nameByPartId = new Map();
  const re = /<score-part\b([^>]*)>([\s\S]*?)<\/score-part>/gi;
  let m = re.exec(xmlText);
  while (m) {
    const attrs = parseAttributesFromOpenTag(m[1] ?? "");
    const partId = attrs.id ?? null;
    if (typeof partId === "string") {
      const name = firstMatch(m[2] ?? "", /<part-name\b[^>]*>([\s\S]*?)<\/part-name>/i) ?? partId;
      nameByPartId.set(partId, decodeXmlEntities(name).trim());
    }
    m = re.exec(xmlText);
  }
  return nameByPartId;
}

function parseMeasureNumber(openTag) {
  const attrs = parseAttributesFromOpenTag(openTag);
  const raw = attrs.number;
  if (raw === undefined) return null;
  const n = Number.parseInt(String(raw), 10);
  return Number.isInteger(n) && n >= 1 ? n : null;
}

function parseNoteFromBlock(noteBlock, { ppq, divisions }) {
  const isRest = /<rest\b/i.test(noteBlock);
  const isChord = /<chord\b/i.test(noteBlock);
  const durationDiv = parseIntOrNull(firstMatch(noteBlock, /<duration\b[^>]*>\s*([^<]+)\s*<\/duration>/i)) ?? 0;
  const durationTicks = Math.max(1, Math.round((durationDiv * ppq) / Math.max(1, divisions)));
  const voice = parseIntOrNull(firstMatch(noteBlock, /<voice\b[^>]*>\s*([^<]+)\s*<\/voice>/i));
  const staff = parseIntOrNull(firstMatch(noteBlock, /<staff\b[^>]*>\s*([^<]+)\s*<\/staff>/i));
  const lyric = firstMatch(noteBlock, /<lyric\b[^>]*>[\s\S]*?<text\b[^>]*>([\s\S]*?)<\/text>[\s\S]*?<\/lyric>/i);
  const tieStart = /<tie\b[^>]*type="start"[^>]*\/?>/i.test(noteBlock);
  const tieStop = /<tie\b[^>]*type="stop"[^>]*\/?>/i.test(noteBlock);

  if (isRest) {
    return {
      isRest: true,
      isChord,
      durationTicks,
      voice,
      staff,
      lyric: lyric ? decodeXmlEntities(lyric).trim() : null,
      tie: { start: tieStart, stop: tieStop },
      pitchMidi: null,
    };
  }

  const pitchBlock = firstMatch(noteBlock, /<pitch\b[^>]*>([\s\S]*?)<\/pitch>/i);
  if (!pitchBlock) {
    return {
      isRest: true,
      isChord,
      durationTicks,
      voice,
      staff,
      lyric: lyric ? decodeXmlEntities(lyric).trim() : null,
      tie: { start: tieStart, stop: tieStop },
      pitchMidi: null,
    };
  }

  const step = (firstMatch(pitchBlock, /<step\b[^>]*>\s*([A-G])\s*<\/step>/i) ?? "").toUpperCase();
  const alter = parseIntOrNull(firstMatch(pitchBlock, /<alter\b[^>]*>\s*([^<]+)\s*<\/alter>/i)) ?? 0;
  const octave = parseIntOrNull(firstMatch(pitchBlock, /<octave\b[^>]*>\s*([^<]+)\s*<\/octave>/i));
  const pitchMidi = octave === null ? null : pitchToMidi({ step, alter, octave });

  return {
    isRest: false,
    isChord,
    durationTicks,
    voice,
    staff,
    lyric: lyric ? decodeXmlEntities(lyric).trim() : null,
    tie: { start: tieStart, stop: tieStop },
    pitchMidi,
  };
}

function stepAlterToText({ step, alter }) {
  const s = String(step ?? "").toUpperCase();
  const a = Number.isInteger(alter) ? alter : 0;
  if (!/^[A-G]$/.test(s)) return null;
  if (a === 0) return s;
  if (a > 0) return `${s}${"#".repeat(a)}`;
  return `${s}${"b".repeat(-a)}`;
}

const KIND_VALUE_TO_SUFFIX = Object.freeze({
  "none": "",
  "major": "",
  "minor": "m",
  "dominant": "7",
  "major-seventh": "maj7",
  "minor-seventh": "m7",
  "diminished": "dim",
  "diminished-seventh": "dim7",
  "half-diminished": "m7b5",
  "augmented": "aug",
  "augmented-seventh": "aug7",
  "suspended-second": "sus2",
  "suspended-fourth": "sus4",
});

function parseHarmonyToChordText(harmonyBlock) {
  const rootStep = firstMatch(harmonyBlock, /<root-step\b[^>]*>\s*([A-G])\s*<\/root-step>/i);
  const rootAlter = parseIntOrNull(firstMatch(harmonyBlock, /<root-alter\b[^>]*>\s*([^<]+)\s*<\/root-alter>/i)) ?? 0;
  const root = stepAlterToText({ step: rootStep, alter: rootAlter });
  if (!root) return null;

  const kindAttr = firstMatch(harmonyBlock, /<kind\b[^>]*\btext="([^"]+)"/i);
  const kindValue = firstMatch(harmonyBlock, /<kind\b[^>]*>\s*([^<]+)\s*<\/kind>/i);

  let suffix = "";
  if (typeof kindAttr === "string" && kindAttr.trim().length > 0) {
    suffix = decodeXmlEntities(kindAttr).trim();
  } else if (typeof kindValue === "string" && kindValue.trim().length > 0) {
    const key = kindValue.trim().toLowerCase();
    suffix = KIND_VALUE_TO_SUFFIX[key] ?? decodeXmlEntities(kindValue).trim();
  }

  const bassStep = firstMatch(harmonyBlock, /<bass-step\b[^>]*>\s*([A-G])\s*<\/bass-step>/i);
  const bassAlter = parseIntOrNull(firstMatch(harmonyBlock, /<bass-alter\b[^>]*>\s*([^<]+)\s*<\/bass-alter>/i)) ?? 0;
  const bass = stepAlterToText({ step: bassStep, alter: bassAlter });

  return bass ? `${root}${suffix}/${bass}` : `${root}${suffix}`;
}

function directionWordsText(directionBlock) {
  const re = /<words\b[^>]*>([\s\S]*?)<\/words>/gi;
  const parts = [];
  let m = re.exec(directionBlock);
  while (m) {
    const t = decodeXmlEntities(m[1] ?? "").trim();
    if (t.length > 0) parts.push(t);
    m = re.exec(directionBlock);
  }
  if (parts.length === 0) return null;
  return parts.join(" ");
}

function parseDynamicMark(directionBlock) {
  const dynBlock = firstMatch(directionBlock, /<dynamics\b[^>]*>([\s\S]*?)<\/dynamics>/i);
  if (!dynBlock) return null;

  const other = firstMatch(dynBlock, /<other-dynamics\b[^>]*>([\s\S]*?)<\/other-dynamics>/i);
  if (other) {
    const t = decodeXmlEntities(other).trim();
    return t.length > 0 ? t : null;
  }

  const m = dynBlock.match(/<([A-Za-z][A-Za-z0-9-]*)\b[^>]*\/?>/);
  if (!m) return null;
  const tag = (m[1] ?? "").trim();
  return tag.length > 0 ? tag : null;
}

function parseEndingNumbers(rawNumber) {
  if (rawNumber === null || rawNumber === undefined) return [];
  const raw = String(rawNumber).trim();
  if (raw.length === 0) return [];

  const nums = [];
  const pieces = raw.split(/[,\s;]+/).filter(Boolean);
  for (const p of pieces) {
    const range = p.match(/^(\d+)\s*-\s*(\d+)$/);
    if (range) {
      const a = Number.parseInt(range[1], 10);
      const b = Number.parseInt(range[2], 10);
      if (Number.isInteger(a) && Number.isInteger(b)) {
        const lo = Math.min(a, b);
        const hi = Math.max(a, b);
        for (let n = lo; n <= hi; n++) nums.push(n);
      }
      continue;
    }
    const n = Number.parseInt(p, 10);
    if (Number.isInteger(n) && n >= 1) nums.push(n);
  }

  return [...new Set(nums)].sort((a, b) => a - b);
}

function normalizeMeasureNumberOrNull(n) {
  return Number.isInteger(n) && n >= 1 ? n : null;
}

function uniqueByKey(list, keyFn) {
  const seen = new Set();
  const out = [];
  for (const item of list ?? []) {
    const key = keyFn(item);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

function parseMusicPart({ partId, name, partXml, ppq, trackIndex = 0 }) {
  const notes = [];
  const rests = [];
  const chordSymbols = [];
  const directions = [];
  const dynamics = [];
  const repeats = [];
  const endings = [];
  const layoutBreaks = [];
  const openEndingStartByNumber = new Map();
  const tempoRows = [];
  const timeSignatures = [];
  const keySignatures = [];
  const measures = [];

  let cursorTick = 0;
  let currentDivisions = 1;
  let currentTs = { num: 4, den: 4 };
  let measureIndex = 0;

  const measureRe = /<measure\b([^>]*)>([\s\S]*?)<\/measure>/gi;
  let mm = measureRe.exec(partXml);
  while (mm) {
    measureIndex += 1;
    const measureAttrs = parseAttributesFromOpenTag(mm[1] ?? "");
    const measureNumber = parseMeasureNumber(mm[1] ?? "") ?? measureIndex;
    const measureBody = mm[2] ?? "";
    const measureStartTick = cursorTick;
    let measureCursor = measureStartTick;
    let lastNonChordStart = measureStartTick;

    // Forced layout breaks are usually emitted via <print new-page="yes"> / <print new-system="yes">.
    const printRe = /<print\b([^>]*)\/?>/gi;
    let pm = printRe.exec(measureBody);
    while (pm) {
      const attrs = parseAttributesFromOpenTag(pm[1] ?? "");
      if ((attrs["new-page"] ?? "").toLowerCase() === "yes") {
        layoutBreaks.push({ measureNumber, type: "PAGE_BREAK" });
      }
      if ((attrs["new-system"] ?? "").toLowerCase() === "yes") {
        layoutBreaks.push({ measureNumber, type: "LINE_BREAK" });
      }
      pm = printRe.exec(measureBody);
    }

    const eventRe = /<(attributes|direction|harmony|barline|backup|forward|note)\b[\s\S]*?<\/\1>/gi;
    let em = eventRe.exec(measureBody);
    while (em) {
      const kind = em[1].toLowerCase();
      const block = em[0];
      const offsetDiv = parseIntOrNull(firstMatch(block, /<offset\b[^>]*>\s*([^<]+)\s*<\/offset>/i)) ?? 0;
      const offsetTicks = Math.round((offsetDiv * ppq) / Math.max(1, currentDivisions));
      const eventTick = Math.max(measureStartTick, measureCursor + offsetTicks);

      if (kind === "attributes") {
        const div = parseIntOrNull(firstMatch(block, /<divisions\b[^>]*>\s*([^<]+)\s*<\/divisions>/i));
        if (div !== null && div > 0) currentDivisions = div;

        const beats = parseIntOrNull(firstMatch(block, /<time\b[^>]*>[\s\S]*?<beats\b[^>]*>\s*([^<]+)\s*<\/beats>/i));
        const beatType = parseIntOrNull(firstMatch(block, /<time\b[^>]*>[\s\S]*?<beat-type\b[^>]*>\s*([^<]+)\s*<\/beat-type>/i));
        if (beats !== null && beatType !== null && beats > 0 && beatType > 0) {
          currentTs = { num: beats, den: beatType };
          timeSignatures.push({ tick: measureStartTick, num: beats, den: beatType });
        }

        const fifths = parseIntOrNull(firstMatch(block, /<key\b[^>]*>[\s\S]*?<fifths\b[^>]*>\s*([^<]+)\s*<\/fifths>/i));
        const mode = (firstMatch(block, /<key\b[^>]*>[\s\S]*?<mode\b[^>]*>\s*([^<]+)\s*<\/mode>/i) ?? "major")
          .trim()
          .toUpperCase();
        if (fifths !== null) {
          keySignatures.push({ tick: measureStartTick, fifths, mode: mode === "MINOR" ? "MINOR" : "MAJOR" });
        }
      } else if (kind === "direction") {
        const tempoAttr = parseFloatOrNull(firstMatch(block, /<sound\b[^>]*\btempo="([^"]+)"/i));
        const tempoMetronome = parseFloatOrNull(firstMatch(block, /<per-minute\b[^>]*>\s*([^<]+)\s*<\/per-minute>/i));
        const tempo = tempoAttr ?? tempoMetronome;
        if (tempo !== null && tempo > 0) {
          tempoRows.push({ tick: eventTick, bpm: tempo });
        }

        const words = directionWordsText(block);
        if (words) directions.push({ tick: eventTick, text: words });

        const dyn = parseDynamicMark(block);
        if (dyn) dynamics.push({ tick: eventTick, mark: dyn });
      } else if (kind === "harmony") {
        const chordText = parseHarmonyToChordText(block);
        if (chordText) chordSymbols.push({ tick: eventTick, text: chordText });
      } else if (kind === "barline") {
        const repeatRe = /<repeat\b([^>]*)\/?>/gi;
        let rm = repeatRe.exec(block);
        while (rm) {
          const attrs = parseAttributesFromOpenTag(rm[1] ?? "");
          const dir = (attrs.direction ?? "").toLowerCase();
          if (dir === "forward") repeats.push({ type: "START", measureNumber });
          if (dir === "backward") repeats.push({ type: "END", measureNumber });
          rm = repeatRe.exec(block);
        }

        const endingRe = /<ending\b([^>]*)\/?>/gi;
        let en = endingRe.exec(block);
        while (en) {
          const attrs = parseAttributesFromOpenTag(en[1] ?? "");
          const type = (attrs.type ?? "").toLowerCase();
          const nums = parseEndingNumbers(attrs.number);
          for (const n of nums) {
            if (type === "start") {
              if (!openEndingStartByNumber.has(n)) openEndingStartByNumber.set(n, measureNumber);
            } else if (type === "stop" || type === "discontinue") {
              const start = normalizeMeasureNumberOrNull(openEndingStartByNumber.get(n));
              if (start !== null) {
                endings.push({ startMeasure: start, endMeasure: measureNumber, number: n });
                openEndingStartByNumber.delete(n);
              }
            }
          }
          en = endingRe.exec(block);
        }
      } else if (kind === "backup" || kind === "forward") {
        const durDiv = parseIntOrNull(firstMatch(block, /<duration\b[^>]*>\s*([^<]+)\s*<\/duration>/i)) ?? 0;
        const durTicks = Math.max(1, Math.round((durDiv * ppq) / Math.max(1, currentDivisions)));
        if (kind === "backup") measureCursor = Math.max(measureStartTick, measureCursor - durTicks);
        if (kind === "forward") measureCursor += durTicks;
      } else if (kind === "note") {
        const parsedNote = parseNoteFromBlock(block, { ppq, divisions: currentDivisions });
        const noteStart = parsedNote.isChord ? lastNonChordStart : measureCursor;
        if (!parsedNote.isChord) lastNonChordStart = noteStart;

        if (!parsedNote.isRest && Number.isInteger(parsedNote.pitchMidi)) {
          notes.push({
            eventId: `xml_${partId}_note_${notes.length + 1}`,
            tick: noteStart,
            durationTicks: parsedNote.durationTicks,
            pitchMidi: parsedNote.pitchMidi,
            velocity: null,
            voice: parsedNote.voice,
            staff: parsedNote.staff,
            lyric: parsedNote.lyric,
            tie: parsedNote.tie,
            trackIndex,
            partId,
          });
        } else if (parsedNote.isRest) {
          rests.push({
            eventId: `xml_${partId}_rest_${rests.length + 1}`,
            tick: noteStart,
            durationTicks: parsedNote.durationTicks,
            voice: parsedNote.voice,
            staff: parsedNote.staff,
            lyric: parsedNote.lyric,
            tie: parsedNote.tie,
            trackIndex,
            partId,
          });
        }

        if (!parsedNote.isChord) {
          measureCursor += parsedNote.durationTicks;
        }
      }

      em = eventRe.exec(measureBody);
    }

    const measuredLenRaw = Math.max(0, measureCursor - measureStartTick);
    const tsLen = measureLengthTicks({ ppq, num: currentTs.num, den: currentTs.den });
    const implicit = (measureAttrs.implicit ?? "").toLowerCase() === "yes";
    let measuredLen = measuredLenRaw;
    if (measuredLen <= 0) measuredLen = tsLen;
    else if (!implicit) measuredLen = Math.max(measuredLen, tsLen);
    measuredLen = Math.max(1, measuredLen);

    measures.push({
      measureNumber,
      startTick: measureStartTick,
      lengthTicks: measuredLen,
      timeSignature: { ...currentTs },
    });

    cursorTick = measureStartTick + measuredLen;
    mm = measureRe.exec(partXml);
  }

  const lastMeasureNumber = measures.length > 0 ? measures[measures.length - 1].measureNumber : measureIndex;
  for (const [n, startRaw] of openEndingStartByNumber.entries()) {
    const start = normalizeMeasureNumberOrNull(startRaw);
    if (start === null) continue;
    endings.push({ startMeasure: start, endMeasure: lastMeasureNumber, number: n });
  }

  notes.sort((a, b) => a.tick - b.tick || b.pitchMidi - a.pitchMidi);
  rests.sort((a, b) => a.tick - b.tick || (a.staff ?? 0) - (b.staff ?? 0));
  chordSymbols.sort((a, b) => a.tick - b.tick || a.text.localeCompare(b.text));
  directions.sort((a, b) => a.tick - b.tick || a.text.localeCompare(b.text));
  dynamics.sort((a, b) => a.tick - b.tick || a.mark.localeCompare(b.mark));

  return {
    partId,
    name,
    noteEvents: notes,
    restEvents: rests,
    measures,
    tempoMap: normalizeRowsByTick(tempoRows, null),
    timeSignatures: normalizeRowsByTick(timeSignatures, null),
    keySignatures: normalizeRowsByTick(keySignatures, null),
    chordSymbols,
    directions,
    dynamics,
    repeats: uniqueByKey(repeats, (r) => `${r.type}:${r.measureNumber}`).sort((a, b) => a.measureNumber - b.measureNumber || a.type.localeCompare(b.type)),
    endings: endings.sort((a, b) => a.startMeasure - b.startMeasure || a.number - b.number || a.endMeasure - b.endMeasure),
    layoutBreaks: uniqueByKey(layoutBreaks, (b) => `${b.measureNumber}:${b.type}`).sort((a, b) => a.measureNumber - b.measureNumber || a.type.localeCompare(b.type)),
  };
}

export function parseMusicXmlText({ xmlText, ppq = 960 }) {
  const xml = ensureString(xmlText, "xmlText").replace(/^\uFEFF/, "");
  if (!Number.isInteger(ppq) || ppq <= 0) throw new Error(`Invalid ppq: ${ppq}`);

  const namesById = parsePartList(xml);
  const parts = [];

  const partRe = /<part\b([^>]*)>([\s\S]*?)<\/part>/gi;
  let pm = partRe.exec(xml);
  while (pm) {
    const attrs = parseAttributesFromOpenTag(pm[1] ?? "");
    const partId = attrs.id ?? `PART_${parts.length + 1}`;
    const name = namesById.get(partId) ?? partId;
    const parsed = parseMusicPart({
      partId,
      name,
      partXml: pm[2] ?? "",
      ppq,
      trackIndex: parts.length,
    });
    parts.push(parsed);
    pm = partRe.exec(xml);
  }

  if (parts.length === 0) throw new Error("No <part> nodes found in MusicXML");

  const reference = parts.reduce((best, cur) => {
    if (!best) return cur;
    const bestScore =
      (best.repeats?.length ?? 0) * 1000 +
      (best.endings?.length ?? 0) * 1000 +
      (best.layoutBreaks?.length ?? 0) * 100 +
      (best.measures?.length ?? 0);
    const curScore =
      (cur.repeats?.length ?? 0) * 1000 +
      (cur.endings?.length ?? 0) * 1000 +
      (cur.layoutBreaks?.length ?? 0) * 100 +
      (cur.measures?.length ?? 0);
    if (curScore > bestScore) return cur;
    return best;
  }, null);
  const tempoRows = normalizeRowsByTick(parts.flatMap((p) => p.tempoMap), { bpm: 120 });
  const timeRows = normalizeRowsByTick(reference.timeSignatures, { num: 4, den: 4 });
  const keyRows = normalizeRowsByTick(reference.keySignatures, null);

  return {
    ppq,
    parts,
    tempoMap: tempoRows,
    timeSignatures: timeRows,
    keySignatures: keyRows,
    measures: reference.measures,
    repeats: reference.repeats ?? [],
    endings: reference.endings ?? [],
    layoutBreaks: reference.layoutBreaks ?? [],
    chordSymbols: reference.chordSymbols ?? [],
    directions: reference.directions ?? [],
    dynamics: reference.dynamics ?? [],
  };
}

function readU16LE(bytes, offset) {
  return bytes[offset] | (bytes[offset + 1] << 8);
}

function readU32LE(bytes, offset) {
  return (bytes[offset]) | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] * 0x1000000);
}

function findEocdOffset(bytes) {
  const min = Math.max(0, bytes.length - 0xffff - 22);
  for (let i = bytes.length - 22; i >= min; i--) {
    if (bytes[i] === 0x50 && bytes[i + 1] === 0x4b && bytes[i + 2] === 0x05 && bytes[i + 3] === 0x06) {
      return i;
    }
  }
  return -1;
}

function decodeUtf8(bytes) {
  return new TextDecoder("utf-8").decode(bytes);
}

function unzipEntries(zipBytes) {
  const bytes = ensureBytes(zipBytes, "zipBytes");
  const eocdOffset = findEocdOffset(bytes);
  if (eocdOffset < 0) throw new Error("Invalid ZIP: end of central directory not found");

  const totalEntries = readU16LE(bytes, eocdOffset + 10);
  const centralDirOffset = readU32LE(bytes, eocdOffset + 16);
  let offset = centralDirOffset;

  const entries = new Map();
  for (let i = 0; i < totalEntries; i++) {
    if (!(bytes[offset] === 0x50 && bytes[offset + 1] === 0x4b && bytes[offset + 2] === 0x01 && bytes[offset + 3] === 0x02)) {
      throw new Error("Invalid ZIP: central directory entry signature");
    }
    const compressionMethod = readU16LE(bytes, offset + 10);
    const compressedSize = readU32LE(bytes, offset + 20);
    const fileNameLen = readU16LE(bytes, offset + 28);
    const extraLen = readU16LE(bytes, offset + 30);
    const commentLen = readU16LE(bytes, offset + 32);
    const localHeaderOffset = readU32LE(bytes, offset + 42);
    const fileNameBytes = bytes.subarray(offset + 46, offset + 46 + fileNameLen);
    const fileName = decodeUtf8(fileNameBytes);
    offset += 46 + fileNameLen + extraLen + commentLen;

    if (!(bytes[localHeaderOffset] === 0x50 && bytes[localHeaderOffset + 1] === 0x4b && bytes[localHeaderOffset + 2] === 0x03 && bytes[localHeaderOffset + 3] === 0x04)) {
      throw new Error(`Invalid ZIP: local file header for ${fileName}`);
    }
    const localNameLen = readU16LE(bytes, localHeaderOffset + 26);
    const localExtraLen = readU16LE(bytes, localHeaderOffset + 28);
    const dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen;
    const dataEnd = dataStart + compressedSize;
    const compressed = bytes.subarray(dataStart, dataEnd);

    let contentBytes;
    if (compressionMethod === 0) {
      contentBytes = compressed;
    } else if (compressionMethod === 8) {
      contentBytes = inflateRawSync(compressed);
    } else {
      throw new Error(`Unsupported ZIP compression method ${compressionMethod} for ${fileName}`);
    }

    entries.set(fileName, contentBytes);
  }

  return entries;
}

function pickRootfilePathFromContainerXml(containerXmlText) {
  const m = containerXmlText.match(/<rootfile\b[^>]*\bfull-path="([^"]+)"/i);
  return m ? m[1] : null;
}

export function extractMusicXmlTextFromMxlBytes({ mxlBytes }) {
  const entries = unzipEntries(ensureBytes(mxlBytes, "mxlBytes"));
  const containerBytes = entries.get("META-INF/container.xml");
  if (!containerBytes) {
    // Fallback: first .xml entry
    for (const [name, bytes] of entries.entries()) {
      if (name.toLowerCase().endsWith(".xml")) return decodeUtf8(bytes);
    }
    throw new Error("MXL container.xml not found");
  }

  const containerXml = decodeUtf8(containerBytes);
  const rootPath = pickRootfilePathFromContainerXml(containerXml);
  if (rootPath && entries.has(rootPath)) {
    return decodeUtf8(entries.get(rootPath));
  }

  for (const [name, bytes] of entries.entries()) {
    if (name.toLowerCase().endsWith(".xml") && name !== "META-INF/container.xml") {
      return decodeUtf8(bytes);
    }
  }
  throw new Error("No score XML found in MXL");
}

function buildEventsFromNotes(notes) {
  return notes.map((n, i) => ({
    eventId: `xml_note_${i + 1}`,
    type: "NOTE",
    tick: n.tick,
    durationTicks: n.durationTicks,
    pitchMidi: n.pitchMidi,
    velocity: n.velocity,
    staff: n.pitchMidi >= 60 ? "UPPER" : "LOWER",
    role: null,
    tie: n.tie ?? { start: false, stop: false },
    tuplet: null,
    lyrics: n.lyric ? { text: n.lyric } : null,
    chordSymbol: null,
    direction: null,
    dynamic: null,
  }));
}

export function importMusicXmlToSimplifiedScore({
  xmlText,
  ppq = 960,
  quantizeStrength = MidiQuantizeStrength.OFF,
  reductionCap = 4,
  splitMidi = 60,
  instrumentType = null,
  tuningMidiByStringId = null,
}) {
  const parsed = parseMusicXmlText({ xmlText, ppq });
  const pick = pickMelodyPart({ parts: parsed.parts });
  const melodyPart = pick.part;
  const melodyPartId = typeof melodyPart?.partId === "string" ? melodyPart.partId : null;

  const notes = parsed.parts.flatMap((p) =>
    p.noteEvents.map((n) => ({
      ...n,
      melodyHint: melodyPartId !== null && p.partId === melodyPartId,
    })));
  const quantizedNotes = quantizeMidiNotes({
    notes,
    ppq: parsed.ppq,
    strength: quantizeStrength,
  });

  const reduction = buildSimplifiedTeachingReduction({
    noteEvents: quantizedNotes,
    cap: reductionCap,
    splitMidi,
    tuningMidiByStringId,
    instrumentType: instrumentType ?? undefined,
  });

  const reductionEvents = reduction.events ?? [];
  const endTick = reductionEvents.reduce((m, e) => {
    if (!Number.isInteger(e?.tick) || !Number.isInteger(e?.durationTicks)) return m;
    return Math.max(m, e.tick + e.durationTicks);
  }, 0);
  const measures = extendMeasuresToEnd({
    measures: parsed.measures,
    timeSignatures: parsed.timeSignatures,
    endTick,
    ppq: parsed.ppq,
  });

  const chordEvents = (parsed.chordSymbols ?? []).map((c, i) => ({
    eventId: `xml_chord_${i + 1}`,
    type: "CHORD_SYMBOL",
    tick: c.tick,
    text: c.text,
  }));

  const directionEvents = (parsed.directions ?? []).map((d, i) => ({
    eventId: `xml_direction_${i + 1}`,
    type: "DIRECTION",
    tick: d.tick,
    text: d.text,
  }));

  const dynamicEvents = (parsed.dynamics ?? []).map((d, i) => ({
    eventId: `xml_dynamic_${i + 1}`,
    type: "DYNAMIC",
    tick: d.tick,
    mark: d.mark,
  }));

  return {
    ppq: parsed.ppq,
    measures,
    tempoMap: parsed.tempoMap,
    keySignatures: parsed.keySignatures,
    timeSignatures: parsed.timeSignatures,
    repeats: parsed.repeats ?? [],
    endings: parsed.endings ?? [],
    layoutBreaks: parsed.layoutBreaks ?? [],
    events: [...reductionEvents, ...chordEvents, ...directionEvents, ...dynamicEvents],
    source: {
      kind: "MUSICXML",
      quantizeStrength,
      reductionCap,
      selectedMelodyPartId: melodyPart?.partId ?? null,
      selectedMelodyPartName: melodyPart?.name ?? null,
      partCount: parsed.parts.length,
    },
  };
}

export function importMxlToSimplifiedScore({
  mxlBytes,
  ppq = 960,
  quantizeStrength = MidiQuantizeStrength.OFF,
  reductionCap = 4,
  splitMidi = 60,
  instrumentType = null,
  tuningMidiByStringId = null,
}) {
  const xmlText = extractMusicXmlTextFromMxlBytes({ mxlBytes });
  return importMusicXmlToSimplifiedScore({
    xmlText,
    ppq,
    quantizeStrength,
    reductionCap,
    splitMidi,
    instrumentType,
    tuningMidiByStringId,
  });
}
