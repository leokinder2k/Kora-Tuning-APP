package com.example.koratuningsystem.livetuner

import com.example.koratuningsystem.livetuner.audio.AudioFrameSource
import com.example.koratuningsystem.livetuner.detection.PitchDetectionResult
import com.example.koratuningsystem.livetuner.detection.PitchDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

class LiveTunerEngine(
    private val frameSource: AudioFrameSource,
    private val pitchDetector: PitchDetector,
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 16384
) {

    fun readings(): Flow<PitchDetectionResult> {
        return frameSource.frames(sampleRate, frameSize)
            .map { frame -> pitchDetector.detect(frame, sampleRate) }
            .conflate()
    }
}
