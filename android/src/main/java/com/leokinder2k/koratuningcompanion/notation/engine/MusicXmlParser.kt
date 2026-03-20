package com.leokinder2k.koratuningcompanion.notation.engine

// Port of musicxml.js — regex-based MusicXML parser matching the JS implementation exactly

private val STEP_TO_PC = mapOf('C' to 0,'D' to 2,'E' to 4,'F' to 5,'G' to 7,'A' to 9,'B' to 11)

private fun attrs(openTag: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    val re = Regex("""([A-Za-z_:][A-Za-z0-9_:\-.]*)=""" + '"' + """([^"]*)""" + '"')
    for (m in re.findAll(openTag)) out[m.groupValues[1]] = m.groupValues[2]
    return out
}

private fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return text.replace(Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);""")) { m ->
        when (val ent = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> try {
                when {
                    ent.startsWith("#x") || ent.startsWith("#X") ->
                        ent.substring(2).toInt(16).toChar().toString()
                    ent.startsWith("#") -> ent.substring(1).toInt().toChar().toString()
                    else -> m.value
                }
            } catch (_: Exception) { m.value }
        }
    }
}

private fun first(text: String, pattern: String): String? =
    Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)

private fun parseInt(v: String?): Int? = v?.trim()?.toIntOrNull()
private fun parseFloat(v: String?): Double? = v?.trim()?.toDoubleOrNull()

private fun pitchToMidi(step: Char, alter: Int, octave: Int): Int? {
    val pc = STEP_TO_PC[step.uppercaseChar()] ?: return null
    val midi = (octave + 1) * 12 + pc + alter
    return if (midi in 0..127) midi else null
}

private fun measureLenTicks(ppq: Int, num: Int, den: Int): Int =
    maxOf(1, ((ppq * 4.0 * num) / den).toInt())

private data class ParsedMeasure(
    val measureNumber: Int,
    val startTick: Int,
    val lengthTicks: Int,
    val tsNum: Int,
    val tsDen: Int,
)

private data class ParsedPart(
    val partId: String,
    val name: String,
    val noteEvents: List<NoteEvent>,
    val measures: List<ParsedMeasure>,
    val tempoMap: List<TempoInfo>,
    val timeSignatures: List<TimeSignatureInfo>,
    val keySignatures: List<KeySignatureInfo>,
    val chordSymbols: List<ChordSymbolEvent>,
)

private fun parsePart(partId: String, name: String, partXml: String, ppq: Int, trackIndex: Int): ParsedPart {
    val notes = mutableListOf<NoteEvent>()
    val tempos = mutableListOf<TempoInfo>()
    val timeSigs = mutableListOf<TimeSignatureInfo>()
    val keySigs = mutableListOf<KeySignatureInfo>()
    val chords = mutableListOf<ChordSymbolEvent>()
    val measures = mutableListOf<ParsedMeasure>()

    var cursorTick = 0
    var divisions = 1
    var tsNum = 4; var tsDen = 4
    var measureIndex = 0

    val measureRe = Regex("""<measure\b([^>]*)>([\s\S]*?)</measure>""", RegexOption.IGNORE_CASE)
    for (mm in measureRe.findAll(partXml)) {
        measureIndex++
        val mAttrs = attrs(mm.groupValues[1])
        val measureNumber = parseInt(mAttrs["number"]) ?: measureIndex
        val body = mm.groupValues[2]
        val measureStartTick = cursorTick
        var measureCursor = measureStartTick
        var lastNonChordStart = measureStartTick

        // Parse events in order
        val eventRe = Regex(
            """<(attributes|direction|harmony|backup|forward|note)\b[\s\S]*?</\1>""",
            RegexOption.IGNORE_CASE
        )
        for (em in eventRe.findAll(body)) {
            val kind = em.groupValues[1].lowercase()
            val block = em.value

            when (kind) {
                "attributes" -> {
                    parseInt(first(block, """<divisions[^>]*>\s*([^<]+)\s*</divisions>"""))
                        ?.takeIf { it > 0 }?.let { divisions = it }
                    val beats = parseInt(first(block, """<time[^>]*>[\s\S]*?<beats[^>]*>\s*([^<]+)\s*</beats>"""))
                    val beatType = parseInt(first(block, """<time[^>]*>[\s\S]*?<beat-type[^>]*>\s*([^<]+)\s*</beat-type>"""))
                    if (beats != null && beatType != null && beats > 0 && beatType > 0) {
                        tsNum = beats; tsDen = beatType
                        timeSigs.add(TimeSignatureInfo(measureStartTick, beats, beatType))
                    }
                    val fifths = parseInt(first(block, """<key[^>]*>[\s\S]*?<fifths[^>]*>\s*([^<]+)\s*</fifths>"""))
                    val mode = (first(block, """<key[^>]*>[\s\S]*?<mode[^>]*>\s*([^<]+)\s*</mode>""") ?: "MAJOR")
                        .trim().uppercase()
                    if (fifths != null) keySigs.add(KeySignatureInfo(measureStartTick, fifths, if (mode == "MINOR") "MINOR" else "MAJOR"))
                }
                "direction" -> {
                    val tempo = parseFloat(first(block, """<sound[^>]*\btempo="([^"]+)"""")) ?:
                        parseFloat(first(block, """<per-minute[^>]*>\s*([^<]+)\s*</per-minute>"""))
                    if (tempo != null && tempo > 0) tempos.add(TempoInfo(measureCursor, tempo))
                    // Chord symbols from direction words
                    val words = Regex("""<words[^>]*>([\s\S]*?)</words>""", RegexOption.IGNORE_CASE)
                        .findAll(block).mapNotNull { decodeEntities(it.groupValues[1]).trim().takeIf { t -> t.isNotEmpty() } }
                        .joinToString(" ")
                    if (words.isNotEmpty()) chords.add(ChordSymbolEvent(eventId = null, tick = measureCursor, text = words))
                }
                "harmony" -> {
                    val rootStep = first(block, """<root-step[^>]*>\s*([A-G])\s*</root-step>""")
                    val rootAlter = parseInt(first(block, """<root-alter[^>]*>\s*([^<]+)\s*</root-alter>""")) ?: 0
                    if (rootStep != null) {
                        val root = rootStep.uppercase() + when (rootAlter) { 1 -> "#"; -1 -> "b"; else -> "" }
                        val kindVal = first(block, """<kind[^>]*>\s*([^<]+)\s*</kind>""")?.trim()?.lowercase()
                        val suffix = when (kindVal) {
                            "major", "none" -> ""; "minor" -> "m"; "dominant" -> "7"
                            "major-seventh" -> "maj7"; "minor-seventh" -> "m7"
                            "diminished" -> "dim"; "diminished-seventh" -> "dim7"
                            "half-diminished" -> "m7b5"; "augmented" -> "aug"
                            "suspended-second" -> "sus2"; "suspended-fourth" -> "sus4"
                            else -> kindVal?.let { decodeEntities(it) } ?: ""
                        }
                        chords.add(ChordSymbolEvent(
                            eventId = "xml_chord_${chords.size + 1}",
                            tick = measureCursor, text = "$root$suffix"
                        ))
                    }
                }
                "backup" -> {
                    val dTicks = parseInt(first(block, """<duration[^>]*>\s*([^<]+)\s*</duration>""")) ?: 0
                    val dt = maxOf(1, ((dTicks.toLong() * ppq) / maxOf(1, divisions)).toInt())
                    measureCursor = maxOf(measureStartTick, measureCursor - dt)
                }
                "forward" -> {
                    val dTicks = parseInt(first(block, """<duration[^>]*>\s*([^<]+)\s*</duration>""")) ?: 0
                    measureCursor += maxOf(1, ((dTicks.toLong() * ppq) / maxOf(1, divisions)).toInt())
                }
                "note" -> {
                    val isRest = Regex("""<rest\b""", RegexOption.IGNORE_CASE).containsMatchIn(block)
                    val isChord = Regex("""<chord\b""", RegexOption.IGNORE_CASE).containsMatchIn(block)
                    val durDiv = parseInt(first(block, """<duration[^>]*>\s*([^<]+)\s*</duration>""")) ?: 0
                    val durTicks = maxOf(1, ((durDiv.toLong() * ppq) / maxOf(1, divisions)).toInt())
                    val noteStart = if (isChord) lastNonChordStart else measureCursor
                    if (!isChord) lastNonChordStart = noteStart

                    if (!isRest) {
                        val pitchBlock = first(block, """<pitch[^>]*>([\s\S]*?)</pitch>""")
                        if (pitchBlock != null) {
                            val step = first(pitchBlock, """<step[^>]*>\s*([A-G])\s*</step>""")?.uppercase()?.get(0)
                            val alter = parseInt(first(pitchBlock, """<alter[^>]*>\s*([^<]+)\s*</alter>""")) ?: 0
                            val octave = parseInt(first(pitchBlock, """<octave[^>]*>\s*([^<]+)\s*</octave>"""))
                            if (step != null && octave != null) {
                                val midi = pitchToMidi(step, alter, octave)
                                if (midi != null) {
                                    val lyric = first(block, """<lyric[^>]*>[\s\S]*?<text[^>]*>([\s\S]*?)</text>[\s\S]*?</lyric>""")
                                        ?.let { decodeEntities(it).trim() }
                                    val tieStart = Regex("""<tie[^>]*type="start"""", RegexOption.IGNORE_CASE).containsMatchIn(block)
                                    val tieStop  = Regex("""<tie[^>]*type="stop"""",  RegexOption.IGNORE_CASE).containsMatchIn(block)
                                    notes.add(NoteEvent(
                                        eventId = "xml_${partId}_note_${notes.size + 1}",
                                        tick = noteStart, durationTicks = durTicks, pitchMidi = midi,
                                        tieStart = tieStart, tieStop = tieStop, lyric = lyric,
                                        partId = partId, trackIndex = trackIndex,
                                    ))
                                }
                            }
                        }
                    }

                    if (!isChord) measureCursor += durTicks
                }
            }
        }

        val measuredLen = run {
            val raw = maxOf(0, measureCursor - measureStartTick)
            val tsLen = measureLenTicks(ppq, tsNum, tsDen)
            val implicit = (mAttrs["implicit"] ?: "").lowercase() == "yes"
            val len = if (raw <= 0) tsLen else if (!implicit) maxOf(raw, tsLen) else raw
            maxOf(1, len)
        }
        measures.add(ParsedMeasure(measureNumber, measureStartTick, measuredLen, tsNum, tsDen))
        cursorTick = measureStartTick + measuredLen
    }

    // Deduplicate and normalize tempo rows
    fun <T> deduplicateByTick(rows: List<T>, tick: T.() -> Int): List<T> {
        val byTick = linkedMapOf<Int, T>()
        for (r in rows) byTick[r.tick()] = r
        return byTick.values.sortedBy { it.tick() }
    }

    return ParsedPart(
        partId = partId, name = name,
        noteEvents = notes.sortedWith(compareBy({ it.tick }, { -it.pitchMidi })),
        measures = measures,
        tempoMap = deduplicateByTick(tempos) { tick }.let { rows ->
            if (rows.isEmpty() || rows[0].tick != 0) listOf(TempoInfo(0, 120.0)) + rows else rows
        },
        timeSignatures = deduplicateByTick(timeSigs) { tick }.let { rows ->
            if (rows.isEmpty() || rows[0].tick != 0) listOf(TimeSignatureInfo(0, 4, 4)) + rows else rows
        },
        keySignatures = deduplicateByTick(keySigs) { tick },
        chordSymbols = chords.sortedBy { it.tick },
    )
}

private fun extendMeasures(
    measures: List<ParsedMeasure>,
    timeSignatures: List<TimeSignatureInfo>,
    endTick: Int,
    ppq: Int,
): List<MeasureInfo> {
    val out = measures.map { MeasureInfo(it.measureNumber, it.startTick, it.lengthTicks, it.tsNum, it.tsDen) }.toMutableList()

    fun tsAt(tick: Int): TimeSignatureInfo {
        var cur = timeSignatures.first()
        for (t in timeSignatures) { if (t.tick > tick) break; cur = t }
        return cur
    }

    while (true) {
        val last = out.last()
        val lastEnd = last.startTick + last.lengthTicks
        if (lastEnd >= endTick) break
        val ts = tsAt(lastEnd)
        out.add(MeasureInfo(last.measureNumber + 1, lastEnd, measureLenTicks(ppq, ts.num, ts.den), ts.num, ts.den))
    }
    return out
}

/** Parse a MusicXML string into a [SimplifiedScore]. */
fun importMusicXmlToSimplifiedScore(
    xmlText: String,
    ppq: Int = 960,
    reductionCap: Int = 4,
    splitMidi: Int = 60,
    tuningMidi: Set<Int> = emptySet(),
): SimplifiedScore {
    val xml = xmlText.trimStart('\uFEFF')

    // Extract part names
    val nameById = mutableMapOf<String, String>()
    val partListRe = Regex("""<score-part\b([^>]*)>([\s\S]*?)</score-part>""", RegexOption.IGNORE_CASE)
    for (m in partListRe.findAll(xml)) {
        val pid = attrs(m.groupValues[1])["id"] ?: continue
        val pname = first(m.groupValues[2], """<part-name[^>]*>([\s\S]*?)</part-name>""")
            ?.let { decodeEntities(it).trim() } ?: pid
        nameById[pid] = pname
    }

    // Parse each part
    val parts = mutableListOf<ParsedPart>()
    val partRe = Regex("""<part\b([^>]*)>([\s\S]*?)</part>""", RegexOption.IGNORE_CASE)
    for (pm in partRe.findAll(xml)) {
        val pid = attrs(pm.groupValues[1])["id"] ?: "PART_${parts.size + 1}"
        parts.add(parsePart(pid, nameById[pid] ?: pid, pm.groupValues[2], ppq, parts.size))
    }

    if (parts.isEmpty()) error("No <part> nodes found in MusicXML")

    // Pick reference part (most structural info) for measures/time/key sigs
    val reference = parts.maxByOrNull { it.measures.size } ?: parts.first()

    // Merge all note events from all parts; pick melody part
    val allNotes = parts.flatMap { it.noteEvents }
    val melodyPartId = pickMelodyPart(parts.map { it.partId to it.noteEvents })
    val notesWithHint = allNotes.map { n -> n.copy(melodyHint = n.partId == melodyPartId) }

    // Score reduction
    val (reducedNotes, reducedRests) = buildSimplifiedTeachingReduction(
        notesWithHint, reductionCap, splitMidi, tuningMidi
    )

    val endTick = (reducedNotes.map { it.tick + it.durationTicks } +
                   reducedRests.map { it.tick + it.durationTicks }).maxOrNull() ?: 0

    val tsSafe = reference.timeSignatures.let { rows ->
        if (rows.isEmpty() || rows[0].tick != 0) listOf(TimeSignatureInfo(0, 4, 4)) + rows else rows
    }
    val measures = extendMeasures(reference.measures, tsSafe, endTick, ppq)

    val chordEvents = reference.chordSymbols.mapIndexed { i, c ->
        c.copy(eventId = "xml_chord_${i + 1}")
    }

    return SimplifiedScore(
        ppq = ppq,
        noteEvents = reducedNotes,
        restEvents = reducedRests,
        chordSymbols = chordEvents,
        measures = measures,
        keySignatures = reference.keySignatures.let { ks ->
            if (ks.isEmpty() || ks[0].tick != 0) listOf(KeySignatureInfo(0, 0, "MAJOR")) + ks else ks
        },
        tempoMap = reference.tempoMap,
        timeSignatures = tsSafe,
        sourceKind = "MUSICXML",
    )
}
