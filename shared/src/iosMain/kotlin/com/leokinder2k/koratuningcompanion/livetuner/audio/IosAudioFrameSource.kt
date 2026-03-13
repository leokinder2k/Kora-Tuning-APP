@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.cinterop.get
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement

class IosAudioFrameSource : AudioFrameSource {
    override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = callbackFlow {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setMode(AVAudioSessionModeMeasurement, error = null)

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val format = AVAudioFormat(
            standardFormatWithSampleRate = sampleRate.toDouble(),
            channels = 1u
        )

        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = frameSize.toUInt(),
            format = format
        ) { buffer, _ ->
            val channelData = buffer?.floatChannelData?.get(0) ?: return@installTapOnBus
            val count = buffer.frameLength.toInt()
            val shorts = ShortArray(count) { i ->
                (channelData[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            trySend(shorts)
        }

        engine.prepare()
        engine.startAndReturnError(null)

        awaitClose {
            inputNode.removeTapOnBus(0u)
            engine.stop()
        }
    }
}
