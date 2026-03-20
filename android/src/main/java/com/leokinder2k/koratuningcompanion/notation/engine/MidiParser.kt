package com.leokinder2k.koratuningcompanion.notation.engine

// Port of midi.js — Standard MIDI File binary format parser

private fun readU16BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun readU32BE(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toLong() and 0xFF) shl 24) or
    ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
    ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
    (bytes[offset + 3].toLong() and 0xFF)

private data class VarLen(val value: Int, val nextOffset: Int)
private fun readVarLen(bytes: ByteArray, startOffset: Int): VarLen {
    var value = 0
    var offset = startOffset
    for (i in 0..3) {
        val b = bytes[offset++].toInt() and 0xFF
        value = (value shl 7) or (b and 0x7F)
        if ((b and 0x80) == 0) return VarLen(value, offset)
    }
    error("Invalid VLQ at offset $startOffset")
}

private data class ActiveNote(val tick: Int, val velocity: Int)

private class TrackParser(
    val trackBytes: ByteArray,
    val trackIndex: Int,
    val notesOut: MutableList<NoteEvent>,
    val temposOut: MutableList<TempoInfo>,
    val timeSigsOut: MutableList<TimeSignatureInfo>,
    val keySigsOut: MutableList<KeySignatureInfo>,
) {
    private val activeStarts = mutableMapOf<String, ArrayDeque<ActiveNote>>()
    private var runningStatus: Int? = null
    private var noteCounter = 0

    private fun key(channel: Int, note: Int) = "$trackIndex:$channel:$note"

    fun parse() {
        var offset = 0
        var tick = 0
        while (offset < trackBytes.size) {
            val delta = readVarLen(trackBytes, offset)
            tick += delta.value
            offset = delta.nextOffset
            if (offset >= trackBytes.size) break

            var statusByte = trackBytes[offset].toInt() and 0xFF
            var runningDataByte: Int? = null

            if (statusByte < 0x80) {
                // Running status
                statusByte = runningStatus ?: error("Running status without previous at track $trackIndex")
                runningDataByte = trackBytes[offset++].toInt() and 0xFF
            } else {
                offset++
                if (statusByte < 0xF0) runningStatus = statusByte else runningStatus = null
            }

            if (statusByte == 0xFF) {
                // Meta event
                if (offset >= trackBytes.size) break
                val metaType = trackBytes[offset++].toInt() and 0xFF
                val metaLen = readVarLen(trackBytes, offset)
                offset = metaLen.nextOffset
                val end = offset + metaLen.value
                if (end > trackBytes.size) error("Meta event out of bounds in track $trackIndex")
                val payload = trackBytes.copyOfRange(offset, end)
                offset = end

                when (metaType) {
                    0x51 -> if (payload.size == 3) {
                        val mpq = ((payload[0].toInt() and 0xFF) shl 16) or
                                  ((payload[1].toInt() and 0xFF) shl 8) or
                                   (payload[2].toInt() and 0xFF)
                        if (mpq > 0) temposOut.add(TempoInfo(tick, 60_000_000.0 / mpq))
                    }
                    0x58 -> if (payload.size >= 2) {
                        val num = payload[0].toInt() and 0xFF
                        val denPow = payload[1].toInt() and 0xFF
                        timeSigsOut.add(TimeSignatureInfo(tick, num, 1 shl denPow))
                    }
                    0x59 -> if (payload.size >= 2) {
                        val fifths = if (payload[0].toInt() and 0xFF > 127) (payload[0].toInt() and 0xFF) - 256
                                     else payload[0].toInt() and 0xFF
                        val mode = if ((payload[1].toInt() and 0xFF) == 1) "MINOR" else "MAJOR"
                        keySigsOut.add(KeySignatureInfo(tick, fifths, mode))
                    }
                    0x2F -> return   // End of track
                }
                continue
            }

            if (statusByte == 0xF0 || statusByte == 0xF7) {
                val len = readVarLen(trackBytes, offset)
                offset = len.nextOffset + len.value
                continue
            }

            val type = statusByte and 0xF0
            val channel = statusByte and 0x0F
            val dataLen = if (type == 0xC0 || type == 0xD0) 1 else 2
            val d1 = runningDataByte ?: run {
                if (offset >= trackBytes.size) return
                trackBytes[offset++].toInt() and 0xFF
            }
            var d2: Int? = null
            if (dataLen == 2) {
                if (offset >= trackBytes.size) break
                d2 = trackBytes[offset++].toInt() and 0xFF
            }

            val noteKey = key(channel, d1)
            when {
                type == 0x90 && (d2 ?: 0) > 0 -> {
                    val queue = activeStarts.getOrPut(noteKey) { ArrayDeque() }
                    queue.addLast(ActiveNote(tick, d2!!))
                }
                type == 0x80 || (type == 0x90 && (d2 ?: 0) == 0) -> {
                    val queue = activeStarts[noteKey]
                    val start = queue?.removeFirstOrNull()
                    if (start != null) {
                        noteCounter++
                        notesOut.add(NoteEvent(
                            eventId = "midi_${trackIndex}_note_$noteCounter",
                            tick = start.tick,
                            durationTicks = maxOf(1, tick - start.tick),
                            pitchMidi = d1,
                            velocity = start.velocity,
                            trackIndex = trackIndex,
                        ))
                    }
                }
            }
        }
    }
}

private fun measureLenTicks(ppq: Int, num: Int, den: Int): Int =
    maxOf(1, ((ppq * 4.0 * num) / den).toInt())

private fun buildMeasures(ppq: Int, timeSignatures: List<TimeSignatureInfo>, endTick: Int): List<MeasureInfo> {
    val tsRows = timeSignatures.let { rows ->
        if (rows.isEmpty() || rows[0].tick != 0) listOf(TimeSignatureInfo(0, 4, 4)) + rows else rows
    }
    val measures = mutableListOf<MeasureInfo>()
    var tick = 0; var measureNumber = 1; var tsIdx = 0
    val target = maxOf(endTick, measureLenTicks(ppq, tsRows[0].num, tsRows[0].den))

    while (tick < target) {
        while (tsIdx + 1 < tsRows.size && tsRows[tsIdx + 1].tick <= tick) tsIdx++
        val ts = tsRows[tsIdx]
        var len = measureLenTicks(ppq, ts.num, ts.den)
        val nextTs = if (tsIdx + 1 < tsRows.size) tsRows[tsIdx + 1].tick else null
        if (nextTs != null && tick < nextTs && nextTs < tick + len) len = nextTs - tick
        measures.add(MeasureInfo(measureNumber, tick, len, ts.num, ts.den))
        tick += len; measureNumber++
    }
    return measures
}

private fun normalizeTempoMap(rows: List<TempoInfo>): List<TempoInfo> {
    val byTick = linkedMapOf<Int, TempoInfo>()
    for (r in rows) byTick[r.tick] = r
    val out = byTick.values.sortedBy { it.tick }.toMutableList()
    if (out.isEmpty() || out[0].tick != 0) out.add(0, TempoInfo(0, 120.0))
    return out
}

private fun normalizeTimeSigs(rows: List<TimeSignatureInfo>): List<TimeSignatureInfo> {
    val byTick = linkedMapOf<Int, TimeSignatureInfo>()
    for (r in rows) byTick[r.tick] = r
    val out = byTick.values.sortedBy { it.tick }.toMutableList()
    if (out.isEmpty() || out[0].tick != 0) out.add(0, TimeSignatureInfo(0, 4, 4))
    return out
}

/** Parse a MIDI byte array into a [SimplifiedScore]. */
fun importMidiToSimplifiedScore(
    midiBytes: ByteArray,
    reductionCap: Int = 4,
    splitMidi: Int = 60,
    tuningMidi: Set<Int> = emptySet(),
): SimplifiedScore {
    var offset = 0

    // Header chunk
    val headerTag = String(midiBytes, 0, 4, Charsets.US_ASCII)
    require(headerTag == "MThd") { "Invalid MIDI header chunk" }
    offset += 4
    val headerLen = readU32BE(midiBytes, offset).toInt(); offset += 4
    require(headerLen >= 6) { "Invalid MIDI header length: $headerLen" }
    val format = readU16BE(midiBytes, offset); offset += 2
    val trackCount = readU16BE(midiBytes, offset); offset += 2
    val division = readU16BE(midiBytes, offset); offset += 2
    require((division and 0x8000) == 0) { "SMPTE time division not supported" }
    val ppq = division
    offset += headerLen - 6

    val allNotes = mutableListOf<NoteEvent>()
    val allTempos = mutableListOf<TempoInfo>()
    val allTimeSigs = mutableListOf<TimeSignatureInfo>()
    val allKeySigs = mutableListOf<KeySignatureInfo>()

    for (trackIndex in 0 until trackCount) {
        if (offset + 8 > midiBytes.size) break
        val trackTag = String(midiBytes, offset, 4, Charsets.US_ASCII); offset += 4
        require(trackTag == "MTrk") { "Invalid track chunk id: $trackTag" }
        val trackLen = readU32BE(midiBytes, offset).toInt(); offset += 4
        val trackEnd = offset + trackLen
        if (trackEnd > midiBytes.size) error("Track length out of bounds")
        val trackBytes = midiBytes.copyOfRange(offset, trackEnd)
        offset = trackEnd

        TrackParser(trackBytes, trackIndex, allNotes, allTempos, allTimeSigs, allKeySigs).parse()
    }

    allNotes.sortWith(compareBy({ it.tick }, { -it.pitchMidi }, { it.trackIndex }))

    val timeSigsNorm = normalizeTimeSigs(allTimeSigs)
    val tempoMapNorm = normalizeTempoMap(allTempos)
    val keySigsNorm = allKeySigs.distinctBy { it.tick }.sortedBy { it.tick }

    // Assign melody hints
    val trackIds = allNotes.map { it.trackIndex }.distinct().sorted()
    val parts = trackIds.map { ti -> "TRACK_$ti" to allNotes.filter { it.trackIndex == ti } }
    val melodyPartId = pickMelodyPart(parts)
    val melodyTrackIndex = trackIds.firstOrNull { "TRACK_$it" == melodyPartId }

    val notesWithHint = allNotes.map { n -> n.copy(melodyHint = n.trackIndex == melodyTrackIndex) }

    // Reduction
    val (reducedNotes, reducedRests) = buildSimplifiedTeachingReduction(
        notesWithHint, reductionCap, splitMidi, tuningMidi
    )

    val endTick = (reducedNotes.map { it.tick + it.durationTicks } +
                   reducedRests.map { it.tick + it.durationTicks }).maxOrNull() ?: 0

    val measures = buildMeasures(ppq, timeSigsNorm, endTick)

    return SimplifiedScore(
        ppq = ppq,
        noteEvents = reducedNotes,
        restEvents = reducedRests,
        chordSymbols = emptyList(),
        measures = measures,
        keySignatures = keySigsNorm.let { ks ->
            if (ks.isEmpty() || ks[0].tick != 0) listOf(KeySignatureInfo(0, 0, "MAJOR")) + ks else ks
        },
        tempoMap = tempoMapNorm,
        timeSignatures = timeSigsNorm,
        sourceKind = "MIDI",
    )
}
