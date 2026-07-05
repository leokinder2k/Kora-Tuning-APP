package com.leokinder2k.koratuningcompanion.synth

import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToLong

class MidiLoopRecorder {
    private val mutableEvents = mutableListOf<RecordedMidiEvent>()
    private var startedAtMs = 0L

    var isRecording: Boolean = false
        private set
    var durationMs: Long = 0L
        private set

    val events: List<RecordedMidiEvent>
        get() = mutableEvents.toList()

    fun start(nowMs: Long) {
        mutableEvents.clear()
        startedAtMs = nowMs
        durationMs = 0L
        isRecording = true
    }

    fun stop(nowMs: Long, minimumMs: Long) {
        if (isRecording) {
            durationMs = max(durationMs, nowMs - startedAtMs).coerceAtLeast(minimumMs)
        }
        isRecording = false
    }

    fun record(message: RecordedMidiMessage, nowMs: Long) {
        if (!isRecording) return
        val atMs = (nowMs - startedAtMs).coerceAtLeast(0L)
        mutableEvents += RecordedMidiEvent(atMs, message)
        durationMs = max(durationMs, atMs)
    }

    fun clear() {
        mutableEvents.clear()
        durationMs = 0L
        isRecording = false
    }

    fun toMidiFile(bpm: Int, includeClickTrack: Boolean = false): ByteArray {
        return MidiLoopFileWriter.write(
            events = events,
            durationMs = durationMs.coerceAtLeast(beatMs(bpm) * BeatsPerBar),
            bpm = bpm,
            includeClickTrack = includeClickTrack
        )
    }

    companion object {
        const val BeatsPerBar = 4

        fun beatMs(bpm: Int): Long {
            return (60_000L / bpm.coerceIn(40, 240)).coerceAtLeast(1L)
        }
    }
}

data class RecordedMidiEvent(
    val atMs: Long,
    val message: RecordedMidiMessage
)

sealed interface RecordedMidiMessage {
    data class NoteOn(val note: Int, val velocity: Float) : RecordedMidiMessage
    data class NoteOff(val note: Int) : RecordedMidiMessage
    data class Sustain(val enabled: Boolean) : RecordedMidiMessage
}

private object MidiLoopFileWriter {
    private const val TicksPerQuarter = 480
    private const val MidiChannel = 0
    private const val ClickChannel = 9
    private const val ClickVelocity = 96
    private const val NormalClickNote = 77
    private const val AccentClickNote = 76
    private const val ClickLengthTicks = 48

    fun write(
        events: List<RecordedMidiEvent>,
        durationMs: Long,
        bpm: Int,
        includeClickTrack: Boolean
    ): ByteArray {
        val tracks = buildList {
            add(tempoTrack(bpm, durationMs))
            add(recordingTrack(events, durationMs, bpm))
            if (includeClickTrack) add(clickTrack(durationMs, bpm))
        }
        return ByteArrayOutputStream().apply {
            writeAscii("MThd")
            writeInt32(6)
            writeInt16(1)
            writeInt16(tracks.size)
            writeInt16(TicksPerQuarter)
            tracks.forEach { track ->
                writeAscii("MTrk")
                writeInt32(track.size)
                write(track)
            }
        }.toByteArray()
    }

    private fun tempoTrack(bpm: Int, durationMs: Long): ByteArray {
        val microsecondsPerQuarter = (60_000_000 / bpm.coerceIn(40, 240))
        return ByteArrayOutputStream().apply {
            writeVarLen(0)
            writeMeta(0x51, byteArrayOf(
                ((microsecondsPerQuarter shr 16) and 0xff).toByte(),
                ((microsecondsPerQuarter shr 8) and 0xff).toByte(),
                (microsecondsPerQuarter and 0xff).toByte()
            ))
            writeVarLen(0)
            writeMeta(0x58, byteArrayOf(4, 2, 24, 8))
            writeVarLen(msToTicks(durationMs, bpm))
            writeMeta(0x2f, byteArrayOf())
        }.toByteArray()
    }

    private fun recordingTrack(
        events: List<RecordedMidiEvent>,
        durationMs: Long,
        bpm: Int
    ): ByteArray {
        val midiEvents = events
            .map { event ->
                MidiFileEvent(
                    tick = msToTicks(event.atMs, bpm),
                    order = event.message.order,
                    bytes = event.message.toMidiBytes()
                )
            }
            .sortedWith(compareBy<MidiFileEvent> { it.tick }.thenBy { it.order })

        var previousTick = 0L
        return ByteArrayOutputStream().apply {
            midiEvents.forEach { event ->
                writeVarLen((event.tick - previousTick).coerceAtLeast(0L))
                write(event.bytes)
                previousTick = event.tick
            }
            val endTick = msToTicks(durationMs, bpm).coerceAtLeast(previousTick)
            writeVarLen(endTick - previousTick)
            writeMeta(0x2f, byteArrayOf())
        }.toByteArray()
    }

    private fun clickTrack(durationMs: Long, bpm: Int): ByteArray {
        val beatCount = max(1, ((durationMs + MidiLoopRecorder.beatMs(bpm) - 1) / MidiLoopRecorder.beatMs(bpm)).toInt())
        val events = mutableListOf<MidiFileEvent>()
        repeat(beatCount) { beat ->
            val tick = beat.toLong() * TicksPerQuarter
            val note = if (beat % MidiLoopRecorder.BeatsPerBar == 0) AccentClickNote else NormalClickNote
            events += MidiFileEvent(tick, 0, byteArrayOf((0x90 or ClickChannel).toByte(), note.toByte(), ClickVelocity.toByte()))
            events += MidiFileEvent(tick + ClickLengthTicks, 1, byteArrayOf((0x80 or ClickChannel).toByte(), note.toByte(), 0))
        }

        var previousTick = 0L
        return ByteArrayOutputStream().apply {
            events.sortedWith(compareBy<MidiFileEvent> { it.tick }.thenBy { it.order }).forEach { event ->
                writeVarLen((event.tick - previousTick).coerceAtLeast(0L))
                write(event.bytes)
                previousTick = event.tick
            }
            val endTick = msToTicks(durationMs, bpm).coerceAtLeast(previousTick)
            writeVarLen(endTick - previousTick)
            writeMeta(0x2f, byteArrayOf())
        }.toByteArray()
    }

    private fun RecordedMidiMessage.toMidiBytes(): ByteArray {
        return when (this) {
            is RecordedMidiMessage.NoteOn -> byteArrayOf(
                (0x90 or MidiChannel).toByte(),
                note.coerceIn(0, 127).toByte(),
                (velocity.coerceIn(0f, 1f) * 127f).roundToLong().coerceIn(1L, 127L).toByte()
            )
            is RecordedMidiMessage.NoteOff -> byteArrayOf(
                (0x80 or MidiChannel).toByte(),
                note.coerceIn(0, 127).toByte(),
                0
            )
            is RecordedMidiMessage.Sustain -> byteArrayOf(
                (0xb0 or MidiChannel).toByte(),
                64,
                if (enabled) 127 else 0
            )
        }
    }

    private val RecordedMidiMessage.order: Int
        get() = when (this) {
            is RecordedMidiMessage.NoteOff -> 0
            is RecordedMidiMessage.Sustain -> 1
            is RecordedMidiMessage.NoteOn -> 2
        }

    private fun msToTicks(ms: Long, bpm: Int): Long {
        return (ms * bpm.coerceIn(40, 240) * TicksPerQuarter / 60_000.0).roundToLong()
    }

    private fun ByteArrayOutputStream.writeMeta(type: Int, data: ByteArray) {
        write(0xff)
        write(type and 0xff)
        writeVarLen(data.size.toLong())
        write(data)
    }

    private fun ByteArrayOutputStream.writeVarLen(value: Long) {
        var remaining = value.coerceAtLeast(0L)
        val bytes = mutableListOf((remaining and 0x7f).toInt())
        remaining = remaining shr 7
        while (remaining > 0) {
            bytes += ((remaining and 0x7f) or 0x80).toInt()
            remaining = remaining shr 7
        }
        bytes.asReversed().forEachIndexed { index, byte ->
            val valueWithContinuation = if (index == bytes.lastIndex) byte and 0x7f else byte or 0x80
            write(valueWithContinuation)
        }
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeInt16(value: Int) {
        write((value shr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeInt32(value: Int) {
        write((value shr 24) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 8) and 0xff)
        write(value and 0xff)
    }

    private data class MidiFileEvent(
        val tick: Long,
        val order: Int,
        val bytes: ByteArray
    )
}
