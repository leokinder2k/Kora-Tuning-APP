package com.leokinder2k.koratuningcompanion.notation.engine

import java.io.ByteArrayOutputStream

// Standard MIDI File format export (Format 0 — single track)

private fun writeU32BE(out: ByteArrayOutputStream, v: Long) {
    out.write(((v shr 24) and 0xFF).toInt())
    out.write(((v shr 16) and 0xFF).toInt())
    out.write(((v shr 8) and 0xFF).toInt())
    out.write((v and 0xFF).toInt())
}

private fun writeU16BE(out: ByteArrayOutputStream, v: Int) {
    out.write((v shr 8) and 0xFF)
    out.write(v and 0xFF)
}

private fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
    var v = value
    val buf = mutableListOf<Int>()
    buf.add(v and 0x7F)
    v = v ushr 7
    while (v > 0) {
        buf.add(0x80 or (v and 0x7F))
        v = v ushr 7
    }
    for (b in buf.reversed()) out.write(b)
}

/** Export a SimplifiedScore (or mapped events) to Standard MIDI File bytes, Format 0. */
fun exportKoraPerformanceToMidiBytes(
    score: SimplifiedScore,
    mappedEvents: List<MappedEvent>? = null,
    tempoMapOverride: List<TempoInfo>? = null,
): ByteArray {
    val ppq = score.ppq
    val tempoMap = tempoMapOverride ?: score.tempoMap
    val notes = score.noteEvents

    // Build a flat list of MIDI events (tick, message bytes)
    data class MidiMsg(val tick: Int, val bytes: List<Int>, val sortKey: Int = 0)
    val msgs = mutableListOf<MidiMsg>()

    // Tempo changes (meta 0x51)
    for (t in tempoMap) {
        val mpq = (60_000_000.0 / t.bpm).toLong().coerceIn(1, 0xFFFFFF)
        msgs.add(MidiMsg(t.tick, listOf(
            0xFF, 0x51, 0x03,
            ((mpq shr 16) and 0xFF).toInt(),
            ((mpq shr 8) and 0xFF).toInt(),
            (mpq and 0xFF).toInt()
        ), sortKey = -1))
    }

    // Key signatures (meta 0x59)
    for (ks in score.keySignatures) {
        val fifthsByte = if (ks.fifths < 0) ks.fifths + 256 else ks.fifths
        val modeByte = if (ks.mode == "MINOR") 1 else 0
        msgs.add(MidiMsg(ks.tick, listOf(0xFF, 0x59, 0x02, fifthsByte and 0xFF, modeByte), sortKey = -1))
    }

    // Time signatures (meta 0x58)
    for (ts in score.timeSignatures) {
        val denLog = (0..7).firstOrNull { 1 shl it == ts.den } ?: 2
        msgs.add(MidiMsg(ts.tick, listOf(0xFF, 0x58, 0x04, ts.num, denLog, 24, 8), sortKey = -1))
    }

    // Note on/off events (channel 0)
    for (n in notes) {
        val velocity = (n.velocity ?: 80).coerceIn(1, 127)
        msgs.add(MidiMsg(n.tick, listOf(0x90, n.pitchMidi, velocity), sortKey = 0))
        msgs.add(MidiMsg(n.tick + n.durationTicks, listOf(0x80, n.pitchMidi, 0), sortKey = 1))
    }

    // End of track (meta 0x2F)
    val lastTick = msgs.maxOfOrNull { it.tick } ?: 0
    msgs.add(MidiMsg(lastTick, listOf(0xFF, 0x2F, 0x00), sortKey = 2))

    msgs.sortWith(compareBy({ it.tick }, { it.sortKey }))

    // Write track data
    val track = ByteArrayOutputStream()
    var prevTick = 0
    for (msg in msgs) {
        writeVarLen(track, maxOf(0, msg.tick - prevTick))
        prevTick = msg.tick
        for (b in msg.bytes) track.write(b)
    }

    // Assemble SMF
    val smf = ByteArrayOutputStream()
    smf.write("MThd".toByteArray(Charsets.US_ASCII))
    writeU32BE(smf, 6)
    writeU16BE(smf, 0)   // format 0
    writeU16BE(smf, 1)   // 1 track
    writeU16BE(smf, ppq)
    smf.write("MTrk".toByteArray(Charsets.US_ASCII))
    writeU32BE(smf, track.size().toLong())
    smf.write(track.toByteArray())
    return smf.toByteArray()
}

/** Export original (non-kora-mapped) score to MIDI. */
fun exportSimplifiedScoreToMidiBytes(score: SimplifiedScore): ByteArray =
    exportKoraPerformanceToMidiBytes(score, null)
