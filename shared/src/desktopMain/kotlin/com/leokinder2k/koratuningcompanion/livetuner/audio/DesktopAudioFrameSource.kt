package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

class DesktopAudioFrameSource(
    private val sampleRateHz: Int = 44_100,
    private val frameSizeHint: Int = 4096
) : AudioFrameSource {

    override fun frames(): Flow<ShortArray> = callbackFlow {
        val format = AudioFormat(sampleRateHz.toFloat(), 16, 1, true, false)
        val info = AudioSystem.getTargetDataLineInfo(format).firstOrNull()
        val line: TargetDataLine = if (info != null) {
            AudioSystem.getTargetDataLine(format, info)
        } else {
            AudioSystem.getTargetDataLine(format)
        }
        line.open(format, frameSizeHint * 2)
        line.start()

        val byteBuffer = ByteArray(frameSizeHint * 2)
        try {
            while (!isClosedForSend) {
                val bytesRead = line.read(byteBuffer, 0, byteBuffer.size)
                if (bytesRead <= 0) continue
                val sampleCount = bytesRead / 2
                val shorts = ShortArray(sampleCount) { i ->
                    val lo = byteBuffer[i * 2].toInt() and 0xFF
                    val hi = byteBuffer[i * 2 + 1].toInt()
                    ((hi shl 8) or lo).toShort()
                }
                trySend(shorts)
            }
        } finally {
            line.stop()
            line.close()
        }
        awaitClose { line.stop(); line.close() }
    }
}
