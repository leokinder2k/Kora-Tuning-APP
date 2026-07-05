package com.leokinder2k.koratuningcompanion.synth

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiMessageParserTest {
    @Test
    fun keepsRunningStatusAfterActiveSensing() {
        val events = mutableListOf<MidiControlEvent>()
        val parser = MidiMessageParser(events::add)

        parser.parse(byteArrayOf(0x90.toByte(), 60, 100), offset = 0, count = 3)
        parser.parse(byteArrayOf(0xfe.toByte()), offset = 0, count = 1)
        parser.parse(byteArrayOf(64, 100), offset = 0, count = 2)

        assertEquals(
            listOf(
                MidiControlEvent.NoteOn(note = 60, velocity = 100 / 127f),
                MidiControlEvent.NoteOn(note = 64, velocity = 100 / 127f)
            ),
            events
        )
    }

    @Test
    fun ignoresRealTimeByteBetweenNoteDataBytes() {
        val events = mutableListOf<MidiControlEvent>()
        val parser = MidiMessageParser(events::add)

        parser.parse(byteArrayOf(0x90.toByte(), 60, 0xf8.toByte(), 100), offset = 0, count = 4)

        assertEquals(
            listOf(MidiControlEvent.NoteOn(note = 60, velocity = 100 / 127f)),
            events
        )
    }
}
