package com.leokinder2k.koratuningcompanion.notation.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.abs

// ── Page geometry ─────────────────────────────────────────────────────────────
// Margins generous enough to sit inside typical print-bleed lines (~3 mm = 8.5 pt).
private const val PAGE_W = 595f
private const val PAGE_H = 842f
private const val MARGIN_L = 52f          // ~18 mm — safe inside bleed
private const val MARGIN_R = 52f
private const val MARGIN_T = 55f          // ~19 mm
private const val MARGIN_B = 55f
private val CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R   // 491f

// ── Grand staff geometry ───────────────────────────────────────────────────────
private const val LINE_SP   = 9f          // space between staff lines
private const val STAFF_H   = LINE_SP * 4 // 36f  (5 lines, 4 spaces)
private const val GRAND_GAP = 18f         // gap between treble and bass staves
private const val GRAND_H   = STAFF_H * 2 + GRAND_GAP  // 90f
private const val NOTE_R    = LINE_SP * 0.88f           // ~7.9f
private const val STEM_LEN  = LINE_SP * 3.5f
private const val CLEF_W    = 40f         // column width: clef + key signature

// ── Gap between grand staff and kora TAB staff ────────────────────────────────
// Must be large enough that ledger lines on very low bass notes never touch the
// top TAB line.  Kora lowest string ≈ F2 / MIDI 41.  At LINE_SP=9 that sits
// roughly 22f below GRAND_H, leaving ~18f clearance at STAFF_TAB_GAP=40.
private const val STAFF_TAB_GAP = 40f

// ── Kora TAB staff geometry ───────────────────────────────────────────────────
// 4 horizontal lines representing the four digit lines: LF, LT, RT, RF (top→bottom)
private const val TAB_LINE_SP  = 16f      // spacing between the 4 TAB lines
private const val TAB_STAFF_H  = TAB_LINE_SP * 3   // 48f  (4 lines, 3 spaces)
private const val TAB_PAD_TOP  = 12f      // breathing space above top TAB line
private const val TAB_PAD_BOT  = 12f      // breathing space below bottom TAB line
private const val TAB_TOTAL_H  = TAB_PAD_TOP + TAB_STAFF_H + TAB_PAD_BOT   // 72f
private const val TAB_NUM_SIZE = 13f      // font size for numbers ON the lines

// ── System height: tuned so exactly 3 systems fit per page ───────────────────
// Available height (non-title pages) = PAGE_H − MARGIN_T − MARGIN_B = 732f
// 3 × SYSTEM_H must be ≤ 732f.  We use 730 / 3 ≈ 243f and give the surplus to
// the inter-system gap so there is generous white space between combos.
private const val SYSTEM_CONTENT_H = GRAND_H + STAFF_TAB_GAP + TAB_TOTAL_H  // 202f
private const val SYSTEM_GAP       = 40f  // space BETWEEN adjacent systems
private const val SYSTEM_H         = SYSTEM_CONTENT_H + SYSTEM_GAP           // 242f

// ── Digit line order and colours ─────────────────────────────────────────────
private val DIGIT_LINE_ORDER = listOf("LF", "LT", "RT", "RF")
private val DIGIT_LINE_COLORS = mapOf(
    "LF" to Color.rgb(0,  100, 220),   // blue
    "LT" to Color.rgb(0,   55, 160),   // dark blue
    "RT" to Color.rgb(180,  40,   0),  // dark red
    "RF" to Color.rgb(210,   0,   0),  // red
)

private const val MAX_NOTES_PER_LINE = 8

// ── Paint factory ─────────────────────────────────────────────────────────────
private fun mkPaint(
    color: Int   = Color.BLACK,
    size:  Float = 8f,
    bold:  Boolean = false,
    stroke: Boolean = false,
    sw:    Float = 1f,
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize   = size
    typeface   = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    style      = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
    if (stroke) strokeWidth = sw
}

// ── Pitch → grand staff Y position ───────────────────────────────────────────
private val PC_TO_DIATONIC  = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val PC_IS_ACCIDENTAL = booleanArrayOf(false,true,false,true,false,false,true,false,true,false,true,false)

private fun diatonicStep(midi: Int) = (midi / 12 - 1) * 7 + PC_TO_DIATONIC[midi % 12]

/** Returns (isTreble, yFromSystemTop) for a MIDI pitch. */
private fun noteY(midi: Int): Pair<Boolean, Float> {
    val treble  = midi >= 60
    val refMidi = if (treble) 64 else 43   // E4 = treble bottom line; G2 = bass bottom line
    val stepsUp = diatonicStep(midi) - diatonicStep(refMidi)
    val yLocal  = STAFF_H - stepsUp * (LINE_SP / 2f)
    return treble to (if (treble) yLocal else STAFF_H + GRAND_GAP + yLocal)
}

// ── Grand staff drawing helpers ───────────────────────────────────────────────
private fun Canvas.staffLines(x: Float, y: Float, w: Float, p: Paint) {
    for (i in 0..4) drawLine(x, y + i * LINE_SP, x + w, y + i * LINE_SP, p)
}

private fun Canvas.grandStaff(x: Float, top: Float, w: Float) {
    val lp = mkPaint(stroke = true, sw = 0.9f)
    staffLines(x, top, w, lp)
    staffLines(x, top + STAFF_H + GRAND_GAP, w, lp)
    drawLine(x, top, x, top + GRAND_H, mkPaint(stroke = true, sw = 2f))  // brace
}

private fun Canvas.clefs(x: Float, top: Float) {
    drawText("𝄞", x + 2f, top + STAFF_H - LINE_SP * 0.3f,            mkPaint(size = 22f))
    drawText("𝄢", x + 2f, top + STAFF_H + GRAND_GAP + LINE_SP * 2.8f, mkPaint(size = 16f))
}

private fun Canvas.keySig(fifths: Int, x: Float, top: Float) {
    if (fifths == 0) return
    val sign = if (fifths > 0) "#" else "b"
    val n  = abs(fifths).coerceAtMost(7)
    val p  = mkPaint(size = 10f)
    val cw = p.measureText(sign) + 1f
    for (i in 0 until n) drawText(sign, x + i * cw, top + LINE_SP * 1.2f, p)
}

private fun Canvas.grandBarLine(x: Float, top: Float, final: Boolean = false) {
    drawLine(x, top, x, top + GRAND_H, mkPaint(stroke = true, sw = 0.9f))
    if (final) drawLine(x + 3f, top, x + 3f, top + GRAND_H, mkPaint(stroke = true, sw = 2.5f))
}

private fun Canvas.tabBarLine(x: Float, tabStaffTop: Float, final: Boolean = false) {
    drawLine(x, tabStaffTop, x, tabStaffTop + TAB_STAFF_H, mkPaint(stroke = true, sw = 0.9f))
    if (final) drawLine(x + 3f, tabStaffTop, x + 3f, tabStaffTop + TAB_STAFF_H,
        mkPaint(stroke = true, sw = 2.5f))
}

private fun Canvas.ledgers(nx: Float, ny: Float, systemTop: Float, treble: Boolean) {
    val stTop = systemTop + (if (treble) 0f else STAFF_H + GRAND_GAP)
    val stBot = stTop + STAFF_H
    val lp    = mkPaint(stroke = true, sw = 0.9f)
    val hw    = NOTE_R * 2.2f
    var y = stBot + LINE_SP
    while (y <= ny + LINE_SP * 0.4f) { drawLine(nx - hw, y, nx + hw, y, lp); y += LINE_SP }
    y = stTop - LINE_SP
    while (y >= ny - LINE_SP * 0.4f) { drawLine(nx - hw, y, nx + hw, y, lp); y -= LINE_SP }
}

private fun Canvas.noteHead(nx: Float, ny: Float, filled: Boolean) {
    val r = RectF(nx - NOTE_R, ny - NOTE_R * 0.72f, nx + NOTE_R, ny + NOTE_R * 0.72f)
    if (filled) drawOval(r, mkPaint()) else drawOval(r, mkPaint(stroke = true, sw = 1.3f))
}

private fun Canvas.stem(nx: Float, ny: Float, up: Boolean) {
    val sx = if (up) nx + NOTE_R - 0.5f else nx - NOTE_R + 0.5f
    drawLine(sx, ny, sx, if (up) ny - STEM_LEN else ny + STEM_LEN, mkPaint(stroke = true, sw = 1f))
}

// ── Kora TAB staff helpers ────────────────────────────────────────────────────

/** Draw 4 horizontal TAB staff lines + opening thick bar from [lineStartX] to [lineEndX]. */
private fun Canvas.koraTabLines(lineStartX: Float, tabStaffTop: Float, lineEndX: Float) {
    val lp = mkPaint(stroke = true, sw = 0.85f)
    for (i in 0..3) {
        val y = tabStaffTop + i * TAB_LINE_SP
        drawLine(lineStartX, y, lineEndX, y, lp)
    }
    drawLine(lineStartX, tabStaffTop, lineStartX, tabStaffTop + TAB_STAFF_H,
        mkPaint(stroke = true, sw = 2f))
}

/** Draw LF / LT / RT / RF labels in the left column, centred on their respective lines. */
private fun Canvas.koraTabLabels(labelX: Float, tabStaffTop: Float) {
    DIGIT_LINE_ORDER.forEachIndexed { i, line ->
        val lineY = tabStaffTop + i * TAB_LINE_SP
        val color = DIGIT_LINE_COLORS[line] ?: Color.DKGRAY
        drawText(line, labelX, lineY + 3.5f, mkPaint(size = 9f, bold = true, color = color))
    }
}

/**
 * Draw kora string number [number] centred ON staff line [lineIndex] (0=LF … 3=RF).
 * A white rectangle erases the staff line behind the digit so it reads clearly.
 */
private fun Canvas.tabNumber(
    nx: Float, tabStaffTop: Float,
    lineIndex: Int, number: Int,
    color: Int, bold: Boolean,
) {
    val lineY  = tabStaffTop + lineIndex * TAB_LINE_SP
    val text   = number.toString()
    val p      = mkPaint(size = TAB_NUM_SIZE, bold = bold, color = color)
    val tw     = p.measureText(text)
    val halfH  = TAB_NUM_SIZE * 0.46f
    val pad    = 2.5f

    // White knockout so the staff line doesn't bleed through the digit
    drawRect(RectF(nx - tw/2f - pad, lineY - halfH - pad,
                   nx + tw/2f + pad, lineY + halfH + pad),
             mkPaint(color = Color.WHITE))

    // Number centred on the line
    drawText(text, nx - tw / 2f, lineY + halfH, p)
}

// ── Layout note data class ────────────────────────────────────────────────────
private data class LayoutNote(
    val midi: Int,
    val durationTicks: Int,
    val ppq: Int,
    val measureNumber: Int,
    val tick: Int,
    val stringId: String?,
    val digitLine: String?,
    val renderedNumber: Int?,
    val accidentalSuggestion: AccidentalSuggestion,
    val role: NoteRole,
)

// ── Render one system (grand staff + kora TAB combo) ─────────────────────────
private fun Canvas.renderSystem(
    notes: List<LayoutNote>,
    systemTop: Float,
    fifths: Int,
    retunePlan: RetunePlan,
    isLast: Boolean,
) {
    val staffX    = MARGIN_L
    val staffW    = CONTENT_W
    val rightEdge = staffX + staffW

    // Note columns — same X positions used for both grand staff and TAB staff
    val noteAreaX = staffX + CLEF_W
    val noteAreaW = staffW - CLEF_W
    val slotW     = if (notes.isEmpty()) noteAreaW else noteAreaW / notes.size.toFloat()

    // TAB staff Y coordinates
    val tabAreaTop   = systemTop + GRAND_H + STAFF_TAB_GAP   // top of the TAB area
    val tabStaffTop  = tabAreaTop + TAB_PAD_TOP               // top of the first TAB line

    // ── Draw grand staff ─────────────────────────────────────────────────────
    grandStaff(staffX, systemTop, staffW)
    clefs(staffX + 2f, systemTop)
    keySig(fifths, staffX + CLEF_W - 14f, systemTop)

    // ── Draw kora TAB staff ───────────────────────────────────────────────────
    // Lines span from noteAreaX (aligned with grand-staff note columns) to rightEdge
    koraTabLines(noteAreaX, tabStaffTop, rightEdge)
    koraTabLabels(staffX + 3f, tabStaffTop)   // labels in the CLEF_W column

    // ── Notes and tab numbers ─────────────────────────────────────────────────
    var prevMeasure = notes.firstOrNull()?.measureNumber ?: 1

    notes.forEachIndexed { idx, n ->
        val nx = noteAreaX + (idx + 0.5f) * slotW

        // Bar line at measure boundary
        if (idx > 0 && n.measureNumber != prevMeasure) {
            val blX = noteAreaX + idx * slotW - slotW * 0.12f
            grandBarLine(blX, systemTop)
            tabBarLine(blX, tabStaffTop)
            prevMeasure = n.measureNumber
        }

        // Bar number above grand staff + optional retune annotation
        if (idx == 0 || n.measureNumber != notes[idx - 1].measureNumber) {
            drawText(n.measureNumber.toString(), nx - 5f, systemTop - 5f,
                mkPaint(size = 7.5f, color = Color.GRAY))
            val retuneText = retunePlan.barInstructions
                .filter { it.measureNumber == n.measureNumber }
                .joinToString(" ") { "${it.stringId}${if (it.deltaSemitones > 0) "↑" else "↓"}" }
            if (retuneText.isNotEmpty())
                drawText(retuneText, nx - 8f, systemTop - 15f,
                    mkPaint(size = 6.5f, color = Color.rgb(180, 0, 0)))
        }

        // Grand staff: ledger lines, accidental, note head, stem
        val (isTreble, nyRel) = noteY(n.midi)
        val ny = systemTop + nyRel
        ledgers(nx, ny, systemTop, isTreble)

        val showAcc = when (n.accidentalSuggestion) {
            AccidentalSuggestion.SHARP -> "#"
            AccidentalSuggestion.FLAT  -> "b"
            AccidentalSuggestion.NONE  -> if (PC_IS_ACCIDENTAL[n.midi % 12]) "#" else null
        }
        showAcc?.let { drawText(it, nx - NOTE_R * 2.8f, ny + 3f, mkPaint(size = 9f)) }

        val beats = n.durationTicks.toFloat() / n.ppq.toFloat()
        noteHead(nx, ny, beats < 3.5f)
        if (beats < 1.8f) stem(nx, ny, up = !isTreble)

        // Kora TAB: number ON the correct digit line
        val lineIndex = DIGIT_LINE_ORDER.indexOf(n.digitLine ?: "")
        if (lineIndex >= 0 && n.renderedNumber != null) {
            val numColor = when (n.role) {
                NoteRole.MELODY  -> Color.rgb(0, 90, 200)
                NoteRole.BASS    -> Color.rgb(180, 0, 0)
                NoteRole.HARMONY -> Color.rgb(80, 80, 80)
            }
            tabNumber(nx, tabStaffTop, lineIndex, n.renderedNumber, numColor,
                bold = n.role == NoteRole.MELODY)
        }

        prevMeasure = n.measureNumber
    }

    // Closing bar lines (single or double at end of piece)
    grandBarLine(rightEdge, systemTop, final = isLast)
    tabBarLine(rightEdge, tabStaffTop, final = isLast)
}

// ── Public export entry point ─────────────────────────────────────────────────

data class PdfExportMeta(
    val pieceName: String,
    val tuningName: String,
    val exportedAtIso: String,
    val difficulty: Double,
)

fun exportLessonToPdfBytes(
    score: SimplifiedScore,
    mappedEvents: List<MappedEvent>,
    retunePlan: RetunePlan,
    meta: PdfExportMeta,
): ByteArray {
    val document = PdfDocument()
    var pageNum  = 0
    var page: PdfDocument.Page? = null
    var cv: Canvas? = null
    var curY = MARGIN_T

    fun newPage() {
        page?.let { document.finishPage(it) }
        pageNum++
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNum).create()
        page = document.startPage(info)
        cv   = page!!.canvas
        cv!!.drawColor(Color.WHITE)
        curY = MARGIN_T
    }

    fun need(h: Float) { if (page == null || curY + h > PAGE_H - MARGIN_B) newPage() }

    newPage()

    // ── Title block ───────────────────────────────────────────────────────────
    cv!!.drawText(meta.pieceName.ifBlank { "Untitled" }, MARGIN_L, curY + 14f,
        mkPaint(size = 16f, bold = true))
    curY += 22f
    cv!!.drawText(
        "${meta.tuningName}  ·  ${score.sourceKind}  ·  Difficulty ${(meta.difficulty * 100).toInt()}%",
        MARGIN_L, curY, mkPaint(size = 8.5f, color = Color.GRAY))
    curY += 20f

    // ── Opening lever/tuning summary ──────────────────────────────────────────
    val netChanges = retunePlan.perStringNetChange.filter { it.value != 0 }
    if (netChanges.isNotEmpty()) {
        cv!!.drawText("Opening lever/tuning adjustments:", MARGIN_L, curY,
            mkPaint(size = 8.5f, bold = true))
        curY += 13f
        val cols  = 3
        val colW  = CONTENT_W / cols
        netChanges.entries.sortedBy { it.key }.forEachIndexed { i, (sid, delta) ->
            cv!!.drawText(
                if (delta > 0) "$sid ↑ raise" else "$sid ↓ lower",
                MARGIN_L + (i % cols) * colW, curY + (i / cols) * 12f,
                mkPaint(size = 8f))
        }
        curY += ((netChanges.size + cols - 1) / cols) * 12f + 10f
    }

    // ── Build ordered note list ───────────────────────────────────────────────
    val allNotes: List<LayoutNote> = mappedEvents
        .filter { !it.omit }
        .sortedWith(compareBy({ it.tick }, { it.measureNumber }))
        .map { me ->
            LayoutNote(
                midi              = me.pitchMidi,
                durationTicks     = me.durationTicks,
                ppq               = score.ppq,
                measureNumber     = me.measureNumber,
                tick              = me.tick,
                stringId          = me.stringId,
                digitLine         = me.digitLine,
                renderedNumber    = me.renderedNumber,
                accidentalSuggestion = me.accidentalSuggestion,
                role              = me.role,
            )
        }

    if (allNotes.isEmpty()) {
        cv!!.drawText("No notes to display.", MARGIN_L, curY + 20f, mkPaint(size = 10f))
        page?.let { document.finishPage(it) }
        val out = ByteArrayOutputStream()
        document.writeTo(out); document.close()
        return out.toByteArray()
    }

    val fifths = score.keySignatures.firstOrNull()?.fifths ?: 0

    // ── Render systems (one system = grand staff + kora TAB combo) ────────────
    val lines = allNotes.chunked(MAX_NOTES_PER_LINE)
    lines.forEachIndexed { lineIdx, lineNotes ->
        need(SYSTEM_H)
        cv!!.renderSystem(
            notes       = lineNotes,
            systemTop   = curY,
            fifths      = fifths,
            retunePlan  = retunePlan,
            isLast      = lineIdx == lines.lastIndex,
        )
        curY += SYSTEM_H
    }

    // ── Legend ────────────────────────────────────────────────────────────────
    need(50f)
    curY += 10f
    val legendItems = listOf(
        "LF = Left Forefinger"  to Color.rgb(0, 100, 220),
        "LT = Left Thumb"       to Color.rgb(0,  55, 160),
        "RT = Right Thumb"      to Color.rgb(180, 40,   0),
        "RF = Right Forefinger" to Color.rgb(210,  0,   0),
    )
    val legColW = CONTENT_W / 2f
    legendItems.forEachIndexed { i, (text, col) ->
        cv!!.drawText(text,
            MARGIN_L + (i % 2) * legColW, curY + (i / 2) * 14f,
            mkPaint(size = 8f, bold = true, color = col))
    }
    curY += 32f
    cv!!.drawText(
        "Number colour: Blue = melody  ·  Red = bass  ·  Grey = harmony  ·  Number = kora string position",
        MARGIN_L, curY, mkPaint(size = 7f, color = Color.GRAY))

    page?.let { document.finishPage(it) }
    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
}
