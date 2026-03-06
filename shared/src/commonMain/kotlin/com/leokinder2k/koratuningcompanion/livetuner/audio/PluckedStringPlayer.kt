package com.leokinder2k.koratuningcompanion.livetuner.audio

expect class PluckedStringPlayer(
    sampleRateHz: Int = 44_100,
    baseAmplitude: Double = 0.24,
    pluckDurationMs: Int = 650
) {
    fun setVolumeDb(db: Double)
    fun play(stringNumber: Int, frequencyHz: Double)
    fun stop(stringNumber: Int)
    fun stopAll()
    fun release()
}
