package com.leokinder2k.koratuningcompanion.notation.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmQuantizerTest {
    @Test
    fun durationShorterThanSemiQuaverRoundsUpToSemiQuaver() {
        assertEquals(240, quantizeRhythmDuration(90, ppq = 960))
    }

    @Test
    fun tripletQuaverDurationIsPreserved() {
        assertEquals(320, quantizeRhythmDuration(318, ppq = 960))
    }

    @Test
    fun startTimeSnapsToTripletOrSixteenthGrid() {
        assertEquals(320, quantizeRhythmStart(317, ppq = 960))
        assertEquals(240, quantizeRhythmStart(238, ppq = 960))
    }

    @Test
    fun musicXmlImportQuantizesToSemiQuaversAndTripletQuavers() {
        val xml = """
            <score-partwise version="3.1">
              <part-list><score-part id="P1"><part-name>Test</part-name></score-part></part-list>
              <part id="P1">
                <measure number="1">
                  <attributes>
                    <divisions>12</divisions>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                  </attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
                  <note><pitch><step>D</step><octave>4</octave></pitch><duration>4</duration></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()

        val score = importMusicXmlToSimplifiedScore(xml)

        assertEquals(
            mapOf(noteNameToMidi("C4") to 240, noteNameToMidi("D4") to 320),
            score.noteEvents.associate { it.pitchMidi to it.durationTicks }
        )
    }
}
