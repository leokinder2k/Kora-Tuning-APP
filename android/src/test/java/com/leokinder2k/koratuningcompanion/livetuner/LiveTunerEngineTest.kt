package com.leokinder2k.koratuningcompanion.livetuner

import com.leokinder2k.koratuningcompanion.livetuner.audio.AudioFrameSource
import com.leokinder2k.koratuningcompanion.livetuner.detection.PitchDetectionResult
import com.leokinder2k.koratuningcompanion.livetuner.detection.PitchDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTunerEngineTest {

    @Test
    fun waitsForStableFramesThenEmitsSnappedFrequency() = runBlocking {
        val readings = listOf(
            PitchDetectionResult(frequencyHz = 440.0, confidence = 0.9, rms = 0.1),
            PitchDetectionResult(frequencyHz = 441.0, confidence = 0.9, rms = 0.1),
            PitchDetectionResult(frequencyHz = 439.0, confidence = 0.9, rms = 0.1)
        )
        val engine = LiveTunerEngine(
            frameSource = FakeFrameSource(frameCount = readings.size),
            pitchDetector = SequencePitchDetector(readings),
            sampleRate = 44100,
            frameSize = 4096
        )

        val output = engine.readings().take(readings.size).toList()

        assertNull(output[0].frequencyHz)
        assertNull(output[1].frequencyHz)
        assertEquals(440.0, output[2].frequencyHz ?: 0.0, 0.001)
    }

    private class FakeFrameSource(
        private val frameCount: Int
    ) : AudioFrameSource {
        override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = flow {
            repeat(frameCount) {
                emit(ShortArray(frameSize))
                delay(1)
            }
        }
    }

    private class SequencePitchDetector(
        readings: List<PitchDetectionResult>
    ) : PitchDetector {
        private val iterator = readings.iterator()

        override fun detect(frame: ShortArray, sampleRate: Int): PitchDetectionResult {
            return iterator.next()
        }
    }
}
