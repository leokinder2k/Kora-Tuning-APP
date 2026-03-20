package com.leokinder2k.koratuningcompanion.livetuner.audio

expect class ReferenceTonePlayer(sampleRateHz: Int = 44_100, amplitude: Double = 0.35) {
    fun play(frequencyHz: Double)
    fun stop()
    fun isPlaying(): Boolean
    fun release()
}
