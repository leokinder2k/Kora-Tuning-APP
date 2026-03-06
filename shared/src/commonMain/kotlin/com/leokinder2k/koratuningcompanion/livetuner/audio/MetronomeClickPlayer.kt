package com.leokinder2k.koratuningcompanion.livetuner.audio

enum class MetronomeSoundOption {
    WOOD_SOFT,
    WOOD_BLOCK,
    WOOD_CLICK
}

expect class MetronomeClickPlayer(sampleRateHz: Int = 44_100) {
    fun play(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Float = 1.0f)
    fun stopAll()
    fun release()
}
