package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.coroutines.flow.Flow

interface AudioFrameSource {
    fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray>
}

