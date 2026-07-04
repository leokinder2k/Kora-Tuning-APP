package com.leokinder2k.koratuningcompanion.synth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiLoopRecorderTest {
    @Test
    fun exportsRecordedNotesAndMetronomeClickTrack() {
        val recorder = MidiLoopRecorder()

        recorder.start(nowMs = 1_000)
        recorder.record(RecordedMidiMessage.NoteOn(note = 60, velocity = 0.8f), nowMs = 1_000)
        recorder.record(RecordedMidiMessage.NoteOff(note = 60), nowMs = 1_250)
        recorder.record(RecordedMidiMessage.Sustain(enabled = true), nowMs = 1_500)
        recorder.record(RecordedMidiMessage.Sustain(enabled = false), nowMs = 1_750)
        recorder.stop(nowMs = 3_000, minimumMs = 2_000)

        val midi = recorder.toMidiFile(bpm = 120, includeClickTrack = true)

        assertEquals("MThd", midi.asAscii(0, 4))
        assertEquals(1, midi.readInt16(8))
        assertEquals(3, midi.readInt16(10))
        assertEquals(480, midi.readInt16(12))
        assertEquals(3, midi.countAsciiChunk("MTrk"))
        assertTrue(midi.containsBytes(0x90, 60, 102))
        assertTrue(midi.containsBytes(0x80, 60, 0))
        assertTrue(midi.containsBytes(0xb0, 64, 127))
        assertTrue(midi.containsBytes(0x99, 76, 96))
    }

    @Test
    fun keepsMinimumLoopDurationWhenRecordingIsShort() {
        val recorder = MidiLoopRecorder()

        recorder.start(nowMs = 10_000)
        recorder.record(RecordedMidiMessage.NoteOn(note = 64, velocity = 0.5f), nowMs = 10_020)
        recorder.stop(nowMs = 10_100, minimumMs = 2_000)

        assertEquals(1, recorder.events.size)
        assertEquals(2_000, recorder.durationMs)
    }
}

private fun ByteArray.asAscii(start: Int, length: Int): String {
    return copyOfRange(start, start + length).toString(Charsets.US_ASCII)
}

private fun ByteArray.readInt16(start: Int): Int {
    return ((this[start].toInt() and 0xff) shl 8) or (this[start + 1].toInt() and 0xff)
}

private fun ByteArray.countAsciiChunk(value: String): Int {
    val needle = value.toByteArray(Charsets.US_ASCII)
    return indices.count { index ->
        index + needle.size <= size && needle.indices.all { offset -> this[index + offset] == needle[offset] }
    }
}

private fun ByteArray.containsBytes(vararg values: Int): Boolean {
    val needle = values.map { it.toByte() }.toByteArray()
    return indices.any { index ->
        index + needle.size <= size && needle.indices.all { offset -> this[index + offset] == needle[offset] }
    }
}
