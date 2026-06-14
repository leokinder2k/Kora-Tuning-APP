@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.cinterop.get
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate

class IosAudioFrameSource : AudioFrameSource {
    override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = callbackFlow {
        val session = AVAudioSession.sharedInstance()
        session.setCategoryError(AVAudioSessionCategoryRecord)?.let { error ->
            close(IllegalStateException(error))
            return@callbackFlow
        }
        session.setModeError(AVAudioSessionModeMeasurement)?.let { error ->
            close(IllegalStateException(error))
            return@callbackFlow
        }
        session.setPreferredSampleRate(sampleRate.toDouble(), error = null)
        session.setActiveError(true)?.let { error ->
            close(IllegalStateException(error))
            return@callbackFlow
        }

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val inputFormat = inputNode.outputFormatForBus(0u)

        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = frameSize.toUInt(),
            format = inputFormat
        ) { buffer, _ ->
            val pcmBuffer = buffer ?: return@installTapOnBus
            val channelData = pcmBuffer.floatChannelData ?: return@installTapOnBus
            val count = buffer.frameLength.toInt()
            val channelCount = pcmBuffer.format.channelCount.toInt().coerceAtLeast(1)
            val shorts = ShortArray(count) { i ->
                var mixed = 0f
                repeat(channelCount) { channel ->
                    mixed += channelData[channel]!![i]
                }
                ((mixed / channelCount).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            trySend(shorts)
        }

        engine.prepare()
        engine.startError()?.let { error ->
            runCatching { inputNode.removeTapOnBus(0u) }
            session.setActive(false, error = null)
            close(IllegalStateException(error))
            return@callbackFlow
        }

        awaitClose {
            inputNode.removeTapOnBus(0u)
            engine.stop()
            session.setActive(false, error = null)
        }
    }

    private fun AVAudioSession.setCategoryError(category: String?): String? =
        if (setCategory(category, error = null)) null else "Unable to configure audio session."

    private fun AVAudioSession.setModeError(mode: String?): String? =
        if (setMode(mode, error = null)) null else "Unable to configure audio mode."

    private fun AVAudioSession.setActiveError(active: Boolean): String? =
        if (setActive(active, error = null)) null else "Unable to activate audio session."

    private fun AVAudioEngine.startError(): String? =
        if (startAndReturnError(null)) null else "Unable to start audio engine."
}
