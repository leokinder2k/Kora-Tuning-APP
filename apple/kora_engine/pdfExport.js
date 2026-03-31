import { instrumentModel, InstrumentType } from "./instrument.js";
import { buildLayoutModel } from "./layout.js";

const A4_PORTRAIT = Object.freeze({ widthPt: 595, heightPt: 842 });
const TAB_LINE_ORDER = Object.freeze(["LF", "LT", "RT", "RF"]);

export const DefaultPalette = Object.freeze({
  paletteId: "paper_classic",
  name: "Paper Classic",
  paper: {
    background: "#FFFFFF",
    staffLines: "#111111",
  },
  roles: {
    melody: "#0B2545",
    harmony: "#1E5F74",
    bass: "#13315C",
    tabTokens: "#111111",
    measureNumbers: "#444444",
    lyrics: "#111111",
    chords: "#111111",
    directions: "#111111",
    dynamics: "#111111",
    retuneMarkers: "#8B0000",
    omissions: "#8B0000",
  },
});

function ensureObject(name, value) {
  if (!value || typeof value !== "object") throw new Error(`${name} must be an object`);
  return value;
}

function ensureString(name, value) {
  if (typeof value !== "string") throw new Error(`${name} must be a string`);
  return value;
}

function ensureInt(name, value) {
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function ensureArray(name, value) {
  if (!Array.isArray(value)) throw new Error(`${name} must be an array`);
  return value;
}

function clamp01(x) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return x;
}

function parseHexColor(hex) {
  if (typeof hex !== "string") return { r: 0, g: 0, b: 0 };
  const m = hex.trim().match(/^#?([0-9a-fA-F]{6})$/);
  if (!m) return { r: 0, g: 0, b: 0 };
  const n = Number.parseInt(m[1], 16);
  return {
    r: ((n >> 16) & 0xff) / 255,
    g: ((n >> 8) & 0xff) / 255,
    b: (n & 0xff) / 255,
  };
}

function fmt(n) {
  // PDF tolerates decimals; keep output stable and compact.
  if (Number.isInteger(n)) return String(n);
  return n.toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
}

function pdfLiteralString(text) {
  // Encode as bytes where codepoints 0..255 map 1:1; replace others with '?'.
  // This targets WinAnsiEncoding for the standard PDF fonts.
  const bytes = [];
  for (const ch of String(text)) {
    const cp = ch.codePointAt(0);
    if (cp <= 0xff) bytes.push(cp);
    else bytes.push(0x3f);
  }

  let out = "(";
  for (const b of bytes) {
    if (b === 0x28 || b === 0x29 || b === 0x5c) {
      out += "\\" + String.fromCharCode(b);
      continue;
    }
    if (b < 0x20 || b > 0x7e) {
      out += "\\" + b.toString(8).padStart(3, "0");
      continue;
    }
    out += String.fromCharCode(b);
  }
  out += ")";
  return out;
}

function asciiBytes(text) {
  const s = String(text);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xff;
  return out;
}

function concatBytes(chunks) {
  const total = chunks.reduce((s, c) => s + c.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

class PdfWriter {
  constructor() {
    this.objects = [];
  }

  addObject(bodyAscii) {
    const id = this.objects.length + 1;
    this.objects.push({ id, body: asciiBytes(bodyAscii) });
    return id;
  }

  addStream({ dictAscii, contentAscii }) {
    const content = asciiBytes(contentAscii);
    const dict = String(dictAscii ?? "").trim();
    const body =
      `<<\n/Length ${content.length}\n${dict.length > 0 ? dict + "\n" : ""}>>\nstream\n` +
      contentAscii +
      "\nendstream";
    return this.addObject(body);
  }

  build({ rootId }) {
    ensureInt("rootId", rootId);

    const header = asciiBytes("%PDF-1.4\n%\xE2\xE3\xCF\xD3\n");
    const parts = [header];
    const offsets = [0];
    let offset = header.length;

    for (const obj of this.objects) {
      offsets[obj.id] = offset;
      const prefix = asciiBytes(`${obj.id} 0 obj\n`);
      const suffix = asciiBytes("\nendobj\n");
      parts.push(prefix, obj.body, suffix);
      offset += prefix.length + obj.body.length + suffix.length;
    }

    const xrefStart = offset;
    const count = this.objects.length + 1;
    const xrefLines = [];
    xrefLines.push("xref");
    xrefLines.push(`0 ${count}`);
    xrefLines.push("0000000000 65535 f ");
    for (let i = 1; i < count; i++) {
      const off = offsets[i] ?? 0;
      xrefLines.push(`${String(off).padStart(10, "0")} 00000 n `);
    }

    const trailer =
      "trailer\n" +
      `<< /Size ${count} /Root ${rootId} 0 R >>\n` +
      "startxref\n" +
      `${xrefStart}\n` +
      "%%EOF\n";

    parts.push(asciiBytes(xrefLines.join("\n") + "\n"), asciiBytes(trailer));
    return concatBytes(parts);
  }
}

function roleColor(palette, role) {
  const roles = palette?.roles ?? DefaultPalette.roles;
  if (role === "BASS") return roles.bass;
  if (role === "HARMONY") return roles.harmony;
  return roles.melody;
}

function drawCirclePath({ x, y, r }) {
  // 4-bezier circle approximation.
  const k = 0.5522847498307936;
  const c = r * k;
  const x0 = x - r;
  const x1 = x - c;
  const x2 = x + c;
  const x3 = x + r;
  const y0 = y - r;
  const y1 = y - c;
  const y2 = y + c;
  const y3 = y + r;
  return [
    `${fmt(x3)} ${fmt(y)} m`,
    `${fmt(x3)} ${fmt(y2)} ${fmt(x2)} ${fmt(y3)} ${fmt(x)} ${fmt(y3)} c`,
    `${fmt(x1)} ${fmt(y3)} ${fmt(x0)} ${fmt(y2)} ${fmt(x0)} ${fmt(y)} c`,
    `${fmt(x0)} ${fmt(y1)} ${fmt(x1)} ${fmt(y0)} ${fmt(x)} ${fmt(y0)} c`,
    `${fmt(x2)} ${fmt(y0)} ${fmt(x3)} ${fmt(y1)} ${fmt(x3)} ${fmt(y)} c`,
  ].join("\n");
}

function computeSystemStringUsage({ mappedEvents, startTick, endTick }) {
  const used = new Set();
  for (const e of mappedEvents ?? []) {
    if (!e || e.omit) continue;
    if (!Number.isInteger(e.tick) || !Number.isInteger(e.durationTicks)) continue;
    if (e.tick >= endTick) continue;
    if (e.tick + e.durationTicks <= startTick) continue;
    if (typeof e.stringId === "string" && e.stringId.length > 0) used.add(e.stringId);
  }
  return used;
}

function buildMappedBySourceEventId(mappedEvents) {
  const bySource = new Map();
  for (const e of mappedEvents ?? []) {
    const sourceEventId = typeof e?.sourceEventId === "string" ? e.sourceEventId : null;
    if (!sourceEventId) continue;
    bySource.set(sourceEventId, e);
  }
  return bySource;
}

function stringIdDisplayNumber(stringId) {
  if (typeof stringId !== "string") return null;
  const m = stringId.match(/^[LR](\d{2})$/);
  if (!m) return null;
  const n = Number.parseInt(m[1], 10);
  return Number.isInteger(n) && n > 0 ? n : null;
}

function buildDiagramStringIds(instrumentType) {
  const model = instrumentModel(instrumentType);
  const left = [];
  const right = [];
  for (let i = 1; i <= model.leftCount; i++) left.push(`L${String(i).padStart(2, "0")}`);
  for (let i = 1; i <= model.rightCount; i++) right.push(`R${String(i).padStart(2, "0")}`);
  return { left, right };
}

function normalizeDifficultyText(difficulty) {
  if (difficulty === null || difficulty === undefined) return "n/a";
  if (typeof difficulty === "string" && difficulty.trim().length > 0) return difficulty.trim();
  if (typeof difficulty === "number" && Number.isFinite(difficulty)) return String(difficulty);
  if (typeof difficulty === "object") {
    const user = difficulty.userOverride ?? difficulty.user ?? null;
    if (typeof user === "string" && user.trim().length > 0) return user.trim();
    const auto = difficulty.autoScore ?? difficulty.auto ?? null;
    if (typeof auto === "number" && Number.isFinite(auto)) return auto.toFixed(2);
  }
  return "n/a";
}

export function exportLessonToPdfBytes({
  score,
  mappedEvents = [],
  retunePlan = null,
  metadata,
  palette = DefaultPalette,
  includeOmittedTokens = true,
  includeRetuneMarkers = true,
  maxMeasuresPerSystem = 1,
  includeDiagram = false,
  instrumentType = InstrumentType.KORA_21,
}) {
  ensureObject("score", score);
  ensureArray("mappedEvents", mappedEvents);
  ensureObject("metadata", metadata);

  const pieceName = ensureString("metadata.pieceName", metadata.pieceName);
  const tuningName = ensureString("metadata.tuningName", metadata.tuningName);
  const exportedAtIso = ensureString("metadata.exportedAtIso", metadata.exportedAtIso);
  const difficultyText = normalizeDifficultyText(metadata.difficulty);

  const pageSize = A4_PORTRAIT;
  const margin = 36;
  const innerW = pageSize.widthPt - margin * 2;
  const innerH = pageSize.heightPt - margin * 2;
  const headerH = 72;
  const gap = 14;
  const systemH = 250;
  const systemGap = 14;

  const diagW = includeDiagram ? 170 : 0;
  const colGap = includeDiagram ? 16 : 0;
  const leftW = innerW - diagW - colGap;

  const xLeft = margin;
  const xDiag = margin + leftW + colGap;
  const yTop = pageSize.heightPt - margin;

  const layout = buildLayoutModel({
    score,
    mappedEvents,
    maxMeasuresPerSystem,
    includeOmittedTokens,
  });
  const mappedBySourceEventId = buildMappedBySourceEventId(mappedEvents);

  const systems = layout.systems ?? [];
  const systemsPerPage = Math.max(1, Math.floor((innerH - headerH - gap) / (systemH + systemGap)));

  const pages = [];
  let cur = [];
  for (const s of systems) {
    if (cur.length >= systemsPerPage) {
      pages.push(cur);
      cur = [];
    }
    cur.push(s);
    if (s.breakAfter === "PAGE_BREAK") {
      pages.push(cur);
      cur = [];
    }
  }
  if (cur.length > 0) pages.push(cur);
  if (pages.length === 0) pages.push([]);
  const pageCount = pages.length;

  const paletteResolved = palette ?? DefaultPalette;
  const colors = {
    background: parseHexColor(paletteResolved?.paper?.background ?? DefaultPalette.paper.background),
    staffLines: parseHexColor(paletteResolved?.paper?.staffLines ?? DefaultPalette.paper.staffLines),
    tabTokens: parseHexColor(paletteResolved?.roles?.tabTokens ?? DefaultPalette.roles.tabTokens),
    omissions: parseHexColor(paletteResolved?.roles?.omissions ?? DefaultPalette.roles.omissions),
    retune: parseHexColor(paletteResolved?.roles?.retuneMarkers ?? DefaultPalette.roles.retuneMarkers),
    measureNums: parseHexColor(paletteResolved?.roles?.measureNumbers ?? DefaultPalette.roles.measureNumbers),
    chords: parseHexColor(paletteResolved?.roles?.chords ?? DefaultPalette.roles.chords),
    directions: parseHexColor(paletteResolved?.roles?.directions ?? DefaultPalette.roles.directions),
    dynamics: parseHexColor(paletteResolved?.roles?.dynamics ?? DefaultPalette.roles.dynamics),
    lyrics: parseHexColor(paletteResolved?.roles?.lyrics ?? DefaultPalette.roles.lyrics),
  };

  const strings = buildDiagramStringIds(instrumentType);

  const pdf = new PdfWriter();
  const fontRegularId = pdf.addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
  const fontBoldId = pdf.addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

  const pageIds = [];
  const contentIds = [];

  for (let pageIndex = 0; pageIndex < pageCount; pageIndex++) {
    const pageSystems = pages[pageIndex] ?? [];

    const lines = [];

    // Background.
    lines.push(`${fmt(colors.background.r)} ${fmt(colors.background.g)} ${fmt(colors.background.b)} rg`);
    lines.push(`0 0 ${pageSize.widthPt} ${pageSize.heightPt} re f`);

    // Header box.
    const headerY = yTop - headerH;
    lines.push(`${fmt(colors.staffLines.r)} ${fmt(colors.staffLines.g)} ${fmt(colors.staffLines.b)} RG`);
    lines.push("1 w");
    lines.push(`${fmt(xLeft)} ${fmt(headerY)} ${fmt(innerW)} ${fmt(headerH)} re S`);

    // Header text.
    const headerTextX = xLeft + 12;
    const titleY = yTop - 24;
    const metaY0 = yTop - 44;

    lines.push(`${fmt(colors.staffLines.r)} ${fmt(colors.staffLines.g)} ${fmt(colors.staffLines.b)} rg`);
    lines.push("BT");
    lines.push(`/F2 16 Tf`);
    lines.push(`1 0 0 1 ${fmt(headerTextX)} ${fmt(titleY)} Tm`);
    lines.push(`${pdfLiteralString(pieceName)} Tj`);
    lines.push(`/F1 10 Tf`);
    lines.push(`1 0 0 1 ${fmt(headerTextX)} ${fmt(metaY0)} Tm`);
    lines.push(`${pdfLiteralString(`Instrument: ${instrumentType}`)} Tj`);
    lines.push(`1 0 0 1 ${fmt(headerTextX)} ${fmt(metaY0 - 14)} Tm`);
    lines.push(`${pdfLiteralString(`Tuning: ${tuningName}`)} Tj`);
    lines.push(`1 0 0 1 ${fmt(headerTextX)} ${fmt(metaY0 - 28)} Tm`);
    lines.push(`${pdfLiteralString(`Difficulty: ${difficultyText}`)} Tj`);
    lines.push(`1 0 0 1 ${fmt(headerTextX)} ${fmt(metaY0 - 42)} Tm`);
    lines.push(`${pdfLiteralString(`Exported: ${exportedAtIso}`)} Tj`);
    lines.push("ET");

    // Systems area.
    let sysTop = headerY - gap;
    for (let i = 0; i < pageSystems.length; i++) {
      const s = pageSystems[i];
      const sysYTop = sysTop - i * (systemH + systemGap);
      const sysYBottom = sysYTop - systemH;

      // System boxes.
      lines.push(`${fmt(colors.staffLines.r)} ${fmt(colors.staffLines.g)} ${fmt(colors.staffLines.b)} RG`);
      lines.push("0.75 w");
      lines.push(`${fmt(xLeft)} ${fmt(sysYBottom)} ${fmt(leftW)} ${fmt(systemH)} re S`);
      if (includeDiagram) {
        lines.push(`${fmt(xDiag)} ${fmt(sysYBottom)} ${fmt(diagW)} ${fmt(systemH)} re S`);
      }

      // Measure range label.
      const label = `Bars ${s.measureStart}-${s.measureEnd}`;
      lines.push(`${fmt(colors.measureNums.r)} ${fmt(colors.measureNums.g)} ${fmt(colors.measureNums.b)} rg`);
      lines.push("BT");
      lines.push(`/F1 9 Tf`);
      lines.push(`1 0 0 1 ${fmt(xLeft + 8)} ${fmt(sysYTop - 14)} Tm`);
      lines.push(`${pdfLiteralString(label)} Tj`);
      lines.push("ET");

      // Staff + tab regions inside left column.
      const padX = 50;
      const staffTop = sysYTop - 26;

      const staffX0 = xLeft + padX;
      const staffX1 = xLeft + leftW - padX;

      // Engraving geometry constants.
      const lineSpacing = 5;
      const noteHeadR = 3.2;
      const stemLen = 30;

      // Upper (treble) staff position: top line at upperBase, 5 lines downward.
      const upperBase = staffTop - 16;
      const upperStaffBottom = upperBase - 4 * lineSpacing;
      // Lower (bass) staff: 16pt gap below upper staff bottom.
      const lowerBase = upperStaffBottom - 16;
      const lowerStaffBottom = lowerBase - 4 * lineSpacing;

      // Staff lines (2 staves: treble + bass).
      const staffLineColor = colors.staffLines;
      lines.push(`${fmt(staffLineColor.r)} ${fmt(staffLineColor.g)} ${fmt(staffLineColor.b)} RG`);
      lines.push("0.6 w");
      for (let l = 0; l < 5; l++) {
        const y = upperBase - l * lineSpacing;
        lines.push(`${fmt(staffX0)} ${fmt(y)} m ${fmt(staffX1)} ${fmt(y)} l S`);
      }
      for (let l = 0; l < 5; l++) {
        const y = lowerBase - l * lineSpacing;
        lines.push(`${fmt(staffX0)} ${fmt(y)} m ${fmt(staffX1)} ${fmt(y)} l S`);
      }

      // System barline: vertical line at left edge connecting both staves.
      lines.push("1.2 w");
      lines.push(`${fmt(staffX0)} ${fmt(lowerStaffBottom)} m ${fmt(staffX0)} ${fmt(upperBase)} l S`);

      // Key signature + time signature at system start.
      {
        // Key signature: resolve fifths from score at system startTick.
        const sysStart = s.startTick ?? 0;
        let activeFifths = 0;
        if (Array.isArray(score.keySignatures)) {
          for (const ks of score.keySignatures) {
            if (Number.isInteger(ks?.tick) && ks.tick <= sysStart && Number.isInteger(ks.fifths)) {
              activeFifths = ks.fifths;
            }
          }
        }
        if (activeFifths !== 0) {
          const count = Math.min(Math.abs(activeFifths), 7);
          const symbol = activeFifths > 0 ? "#" : "b";
          const keySigText = count === 1 ? symbol : `${count}${symbol}`;
          const keySigX = xLeft + 4;
          lines.push(`${fmt(colors.staffLines.r)} ${fmt(colors.staffLines.g)} ${fmt(colors.staffLines.b)} rg`);
          lines.push("BT");
          lines.push(`/F2 11 Tf`);
          lines.push(`1 0 0 1 ${fmt(keySigX)} ${fmt(upperBase - 2 * lineSpacing - 4)} Tm`);
          lines.push(`${pdfLiteralString(keySigText)} Tj`);
          lines.push("ET");
        }

        // Time signature: stacked numerator/denominator on upper staff.
        const firstTimeSig = s.measures?.[0]?.timeSignature ?? null;
        const tsNum = Number.isInteger(firstTimeSig?.num) ? String(firstTimeSig.num) : null;
        const tsDen = Number.isInteger(firstTimeSig?.den) ? String(firstTimeSig.den) : null;
        if (tsNum && tsDen) {
          const tsX = activeFifths !== 0 ? xLeft + 22 : xLeft + 8;
          lines.push(`${fmt(colors.staffLines.r)} ${fmt(colors.staffLines.g)} ${fmt(colors.staffLines.b)} rg`);
          lines.push("BT");
          lines.push(`/F2 18 Tf`);
          lines.push(`1 0 0 1 ${fmt(tsX)} ${fmt(upperBase - lineSpacing + 1)} Tm`);
          lines.push(`${pdfLiteralString(tsNum)} Tj`);
          lines.push(`1 0 0 1 ${fmt(tsX)} ${fmt(upperBase - 3 * lineSpacing + 1)} Tm`);
          lines.push(`${pdfLiteralString(tsDen)} Tj`);
          lines.push("ET");
        }
      }

      // Tab lines.
      const tabLineGap = 20;
      const rfY = sysYBottom + 14;
      const tabLineY = {
        RF: rfY,
        RT: rfY + tabLineGap,
        LT: rfY + tabLineGap * 2,
        LF: rfY + tabLineGap * 3,
      };
      lines.push("0.5 w");
      for (const y of Object.values(tabLineY)) {
        lines.push(`${fmt(staffX0)} ${fmt(y)} m ${fmt(staffX1)} ${fmt(y)} l S`);
      }

      // Tab line labels at left edge of each system (LF / LT / RT / RF).
      lines.push(`${fmt(colors.tabTokens.r)} ${fmt(colors.tabTokens.g)} ${fmt(colors.tabTokens.b)} rg`);
      lines.push("BT");
      lines.push(`/F2 10 Tf`);
      for (const label of TAB_LINE_ORDER) {
        const ly = tabLineY[label];
        if (ly !== undefined) {
          lines.push(`1 0 0 1 ${fmt(staffX0 - 22)} ${fmt(ly - 3)} Tm`);
          lines.push(`${pdfLiteralString(label)} Tj`);
        }
      }
      lines.push("ET");

      // Event X mapping.
      const sysTickLen = Math.max(1, (s.endTick ?? 0) - (s.startTick ?? 0));
      const xForSystemTick = (systemTick) => {
        const t = Number.isInteger(systemTick) ? systemTick : 0;
        const rel = clamp01(t / sysTickLen);
        return staffX0 + rel * (staffX1 - staffX0);
      };

      // Measure numbers (always visible).
      const measureNumY = upperBase + 8;
      lines.push(`${fmt(colors.measureNums.r)} ${fmt(colors.measureNums.g)} ${fmt(colors.measureNums.b)} rg`);
      lines.push("BT");
      lines.push(`/F1 8 Tf`);
      for (const m of s.measures ?? []) {
        const systemTick = (Number.isInteger(m?.startTick) ? m.startTick : s.startTick) - s.startTick;
        const x = xForSystemTick(systemTick);
        const num = Number.isInteger(m?.measureNumber) ? String(m.measureNumber) : "";
        if (num.length === 0) continue;
        lines.push(`1 0 0 1 ${fmt(x + 2)} ${fmt(measureNumY)} Tm`);
        lines.push(`${pdfLiteralString(num)} Tj`);
      }
      lines.push("ET");

      // Barlines at measure boundaries (and final barline at system end).
      {
        lines.push(`${fmt(staffLineColor.r)} ${fmt(staffLineColor.g)} ${fmt(staffLineColor.b)} RG`);
        lines.push("0.7 w");
        for (const m of s.measures ?? []) {
          const systemTick = (Number.isInteger(m?.startTick) ? m.startTick : s.startTick) - s.startTick;
          if (systemTick <= 0) continue; // left edge already drawn by system barline
          const bx = xForSystemTick(systemTick);
          lines.push(`${fmt(bx)} ${fmt(lowerStaffBottom)} m ${fmt(bx)} ${fmt(upperBase)} l S`);
        }
        // Final (right-edge) barline.
        const endX = xForSystemTick(sysTickLen);
        lines.push(`${fmt(endX)} ${fmt(lowerStaffBottom)} m ${fmt(endX)} ${fmt(upperBase)} l S`);
      }

      // Chord symbols.
      const chordY = upperBase + 16;
      if ((s.chordLayer?.length ?? 0) > 0) {
        lines.push(`${fmt(colors.chords.r)} ${fmt(colors.chords.g)} ${fmt(colors.chords.b)} rg`);
        lines.push("BT");
        lines.push(`/F2 9 Tf`);
        for (const c of s.chordLayer ?? []) {
          const x = xForSystemTick(c.systemTick);
          lines.push(`1 0 0 1 ${fmt(x)} ${fmt(chordY)} Tm`);
          lines.push(`${pdfLiteralString(c.text)} Tj`);
        }
        lines.push("ET");
      }

      // Directions.
      const directionY = upperBase + 28;
      if ((s.directionLayer?.length ?? 0) > 0) {
        lines.push(`${fmt(colors.directions.r)} ${fmt(colors.directions.g)} ${fmt(colors.directions.b)} rg`);
        lines.push("BT");
        lines.push(`/F1 8 Tf`);
        for (const d of s.directionLayer ?? []) {
          const x = xForSystemTick(d.systemTick);
          lines.push(`1 0 0 1 ${fmt(x)} ${fmt(directionY)} Tm`);
          lines.push(`${pdfLiteralString(d.text)} Tj`);
        }
        lines.push("ET");
      }

      // Dynamics.
      const dynamicY = lowerStaffBottom - 8;
      if ((s.dynamicLayer?.length ?? 0) > 0) {
        lines.push(`${fmt(colors.dynamics.r)} ${fmt(colors.dynamics.g)} ${fmt(colors.dynamics.b)} rg`);
        lines.push("BT");
        lines.push(`/F2 9 Tf`);
        for (const d of s.dynamicLayer ?? []) {
          const x = xForSystemTick(d.systemTick);
          lines.push(`1 0 0 1 ${fmt(x)} ${fmt(dynamicY)} Tm`);
          lines.push(`${pdfLiteralString(d.mark)} Tj`);
        }
        lines.push("ET");
      }

      // Lyrics.
      const lyricsY = lowerStaffBottom - 24;
      if ((s.lyricsLayer?.length ?? 0) > 0) {
        lines.push(`${fmt(colors.lyrics.r)} ${fmt(colors.lyrics.g)} ${fmt(colors.lyrics.b)} rg`);
        lines.push("BT");
        lines.push(`/F1 8 Tf`);
        for (const l of s.lyricsLayer ?? []) {
          const x = xForSystemTick(l.systemTick);
          lines.push(`1 0 0 1 ${fmt(x)} ${fmt(lyricsY)} Tm`);
          lines.push(`${pdfLiteralString(l.text)} Tj`);
        }
        lines.push("ET");
      }

      // Retune markers (optional input; measure-level).
      if (
        includeRetuneMarkers &&
        retunePlan &&
        Array.isArray(retunePlan.barInstructions) &&
        retunePlan.barInstructions.length > 0
      ) {
        const byMeasureStart = new Map();
        for (const m of s.measures ?? []) {
          if (!Number.isInteger(m?.measureNumber) || !Number.isInteger(m?.startTick)) continue;
          byMeasureStart.set(m.measureNumber, m.startTick);
        }

        const markers = retunePlan.barInstructions.filter((r) =>
          Number.isInteger(r?.measureNumber) &&
          r.measureNumber >= s.measureStart &&
          r.measureNumber <= s.measureEnd &&
          typeof r?.stringId === "string" &&
          Number.isInteger(r?.deltaSemitones) &&
          Number.isInteger(r?.appliesFromMeasureNumber));

        if (markers.length > 0) {
          const retuneY = tabLineY.LF + 12;
          lines.push(`${fmt(colors.retune.r)} ${fmt(colors.retune.g)} ${fmt(colors.retune.b)} rg`);
          lines.push("BT");
          lines.push(`/F1 7 Tf`);
          for (const r of markers) {
            const startTick = byMeasureStart.get(r.measureNumber);
            if (!Number.isInteger(startTick)) continue;
            const x = xForSystemTick(startTick - s.startTick);
            const delta = r.deltaSemitones >= 0 ? `+${r.deltaSemitones}` : String(r.deltaSemitones);
            const label = `${r.stringId}${delta}->${r.appliesFromMeasureNumber}`;
            lines.push(`1 0 0 1 ${fmt(x + 2)} ${fmt(retuneY)} Tm`);
            lines.push(`${pdfLiteralString(label)} Tj`);
          }
          lines.push("ET");
        }
      }

      // Staff notes (note heads + stems; rests as filled rectangle placeholder).
      // Pitch-to-Y: C5(MIDI 72) maps to middle of upper staff; C3(48) to middle of lower staff.
      // Each semitone step ≈ lineSpacing/2 vertically.
      const upperRefMidi = 72;
      const lowerRefMidi = 48;
      const upperRefY = upperBase - 2 * lineSpacing;   // C5 at treble middle line
      const lowerRefY = lowerBase - 2 * lineSpacing;   // C3 at bass middle line
      for (const e of s.staffLayer ?? []) {
        if (!Number.isInteger(e?.tick) || !Number.isInteger(e?.durationTicks) || e.durationTicks <= 0) continue;
        const x = xForSystemTick(e.systemTick);
        if (e.type === "REST") {
          // Quarter-rest placeholder: filled rectangle on the middle line.
          const ry = e.staff === "LOWER" ? lowerRefY : upperRefY;
          lines.push(`${fmt(staffLineColor.r)} ${fmt(staffLineColor.g)} ${fmt(staffLineColor.b)} rg`);
          lines.push(`${fmt(x - 4)} ${fmt(ry - 1)} 8 3 re f`);
          continue;
        }
        if (e.type !== "NOTE" || !Number.isInteger(e.pitchMidi)) continue;

        const role = e.role ?? "MELODY";
        const noteColor = parseHexColor(roleColor(paletteResolved, role));

        const yRef = e.staff === "LOWER" ? lowerRefY : upperRefY;
        const midiRef = e.staff === "LOWER" ? lowerRefMidi : upperRefMidi;
        const y = yRef + (e.pitchMidi - midiRef) * 2.0;

        // Note head.
        lines.push(`${fmt(noteColor.r)} ${fmt(noteColor.g)} ${fmt(noteColor.b)} rg`);
        lines.push(drawCirclePath({ x, y, r: noteHeadR }));
        lines.push("f");

        // Stem: up if note is below staff middle, down otherwise.
        const stemUp = y <= yRef;
        const stemX = stemUp ? x + noteHeadR : x - noteHeadR;
        const stemY1 = y;
        const stemY2 = stemUp ? y + stemLen : y - stemLen;
        lines.push(`${fmt(noteColor.r)} ${fmt(noteColor.g)} ${fmt(noteColor.b)} RG`);
        lines.push("0.7 w");
        lines.push(`${fmt(stemX)} ${fmt(stemY1)} m ${fmt(stemX)} ${fmt(stemY2)} l S`);
      }

      // Tab tokens.
      const minTabTokenGap = 18;
      const lastTabXByLine = new Map();
      for (const t of s.tabLayer ?? []) {
        if (!Number.isInteger(t?.tick) || !Number.isInteger(t?.durationTicks) || t.durationTicks <= 0) continue;
        const baseX = xForSystemTick(t.systemTick);
        const lineKey = typeof t.digitLine === "string" ? t.digitLine : "";
        const prevX = lastTabXByLine.get(lineKey);
        let x = prevX === undefined ? baseX : Math.max(baseX, prevX + minTabTokenGap);
        x = Math.min(x, staffX1 - 6);
        lastTabXByLine.set(lineKey, x);
        const y = tabLineY[t.digitLine] ?? tabLineY.LT;

        const omit = Boolean(t.omit);
        const accidental = t.accidentalSuggestion ?? "NONE";
        const suffix = accidental === "SHARP" ? "#" : accidental === "FLAT" ? "b" : "";
        // Show only the number (+ accidental); line labels are fixed at the system edge.
        const mapped = typeof t.sourceEventId === "string" ? mappedBySourceEventId.get(t.sourceEventId) : null;
        const canonicalStringNum = stringIdDisplayNumber(mapped?.stringId ?? null);
        const fallbackNum = Number.isInteger(t.renderedNumber) ? t.renderedNumber : null;
        const shownNum = canonicalStringNum ?? fallbackNum;
        const numStr = omit ? "×" : (Number.isInteger(shownNum) ? String(shownNum) : "");
        if (numStr.length === 0) continue;
        const token = omit ? numStr : `${numStr}${suffix}`;

        const color = omit ? colors.omissions : (suffix ? colors.retune : colors.tabTokens);
        lines.push(`${fmt(color.r)} ${fmt(color.g)} ${fmt(color.b)} rg`);
        lines.push("BT");
        lines.push(`/F2 13 Tf`);
        lines.push(`1 0 0 1 ${fmt(x - 4)} ${fmt(y - 3)} Tm`);
        lines.push(`${pdfLiteralString(token)} Tj`);
        lines.push("ET");
      }

      if (includeDiagram) {
        // Diagram (per-system string usage).
        const usedStrings = computeSystemStringUsage({ mappedEvents, startTick: s.startTick, endTick: s.endTick });
        const diagPad = 10;
        const diagX0 = xDiag + diagPad;
        const diagX1 = xDiag + diagW - diagPad;
        const diagY0 = sysYBottom + diagPad;
        const diagY1 = sysYTop - diagPad;

        // Title.
        lines.push("BT");
        lines.push(`/F1 8 Tf`);
        lines.push(`${fmt(colors.measureNums.r)} ${fmt(colors.measureNums.g)} ${fmt(colors.measureNums.b)} rg`);
        lines.push(`1 0 0 1 ${fmt(diagX0)} ${fmt(diagY1 - 10)} Tm`);
        lines.push(`${pdfLiteralString("Diagram")} Tj`);
        lines.push("ET");

        const listTop = diagY1 - 18;
        const listBottom = diagY0;
        const available = Math.max(1, listTop - listBottom);
        const maxRows = Math.max(strings.left.length, strings.right.length);
        const rowH = available / Math.max(1, maxRows);

        const leftX = diagX0;
        const rightX = (diagX0 + diagX1) / 2 + 6;
        const lineLen = 34;
        const labelDx = 38;

        const baseColor = parseHexColor("#777777");
        const hiColor = colors.retune;

        lines.push("0.7 w");
        for (let row = 0; row < maxRows; row++) {
          const y = listTop - row * rowH;

          const leftId = strings.left[row] ?? null;
          if (leftId) {
            const c = usedStrings.has(leftId) ? hiColor : baseColor;
            lines.push(`${fmt(c.r)} ${fmt(c.g)} ${fmt(c.b)} RG`);
            lines.push(`${fmt(leftX)} ${fmt(y)} m ${fmt(leftX + lineLen)} ${fmt(y)} l S`);

            lines.push("BT");
            lines.push(`/F1 7 Tf`);
            lines.push(`${fmt(c.r)} ${fmt(c.g)} ${fmt(c.b)} rg`);
            lines.push(`1 0 0 1 ${fmt(leftX + labelDx)} ${fmt(y - 2)} Tm`);
            lines.push(`${pdfLiteralString(leftId)} Tj`);
            lines.push("ET");
          }

          const rightId = strings.right[row] ?? null;
          if (rightId) {
            const c = usedStrings.has(rightId) ? hiColor : baseColor;
            lines.push(`${fmt(c.r)} ${fmt(c.g)} ${fmt(c.b)} RG`);
            lines.push(`${fmt(rightX)} ${fmt(y)} m ${fmt(rightX + lineLen)} ${fmt(y)} l S`);

            lines.push("BT");
            lines.push(`/F1 7 Tf`);
            lines.push(`${fmt(c.r)} ${fmt(c.g)} ${fmt(c.b)} rg`);
            lines.push(`1 0 0 1 ${fmt(rightX + labelDx)} ${fmt(y - 2)} Tm`);
            lines.push(`${pdfLiteralString(rightId)} Tj`);
            lines.push("ET");
          }
        }
      }
    }

    const contentId = pdf.addStream({
      dictAscii: "",
      contentAscii: lines.join("\n"),
    });
    contentIds.push(contentId);

    const pageId = pdf.addObject(
      `<<\n` +
      `/Type /Page\n` +
      `/Parent PAGES_ID 0 R\n` +
      `/MediaBox [0 0 ${pageSize.widthPt} ${pageSize.heightPt}]\n` +
      `/Resources << /Font << /F1 ${fontRegularId} 0 R /F2 ${fontBoldId} 0 R >> >>\n` +
      `/Contents ${contentId} 0 R\n` +
      `>>`
    );
    pageIds.push(pageId);
  }

  // Pages tree (fix up Parent references after page objects exist).
  const pagesId = pdf.addObject(
    `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pageIds.length} >>`
  );

  // Patch page objects with the real Parent reference (simple string replace).
  for (const pageId of pageIds) {
    const obj = pdf.objects.find((o) => o.id === pageId);
    if (!obj) continue;
    const text = new TextDecoder("latin1").decode(obj.body);
    const patched = text.replace("/Parent PAGES_ID 0 R", `/Parent ${pagesId} 0 R`);
    obj.body = asciiBytes(patched);
  }

  const rootId = pdf.addObject(`<< /Type /Catalog /Pages ${pagesId} 0 R >>`);
  return pdf.build({ rootId });
}
