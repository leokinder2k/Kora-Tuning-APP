package com.leokinder2k.koratuningcompanion.livetuner.detection

data class PitchDetectionResult(
    val frequencyHz: Double?,
    val confidence: Double,
    val rms: Double
)

interface PitchDetector {
    fun detect(frame: ShortArray, sampleRate: Int): PitchDetectionResult
}

