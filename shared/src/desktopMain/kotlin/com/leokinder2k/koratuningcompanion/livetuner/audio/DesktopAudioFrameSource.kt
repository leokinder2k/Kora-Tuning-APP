package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class DesktopAudioFrameSource : AudioFrameSource {

    override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = flow {
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as TargetDataLine
        val byteBuffer = ByteArray(frameSize.coerceAtLeast(1) * BYTES_PER_SAMPLE)

        try {
            line.open(format, byteBuffer.size)
            line.start()

            while (currentCoroutineContext().isActive) {
                val bytesRead = line.read(byteBuffer, 0, byteBuffer.size)
                if (bytesRead <= 0) {
                    continue
                }

                val sampleCount = bytesRead / BYTES_PER_SAMPLE
                val shorts = ShortArray(sampleCount) { index ->
                    val lo = byteBuffer[index * BYTES_PER_SAMPLE].toInt() and 0xFF
                    val hi = byteBuffer[index * BYTES_PER_SAMPLE + 1].toInt()
                    ((hi shl 8) or lo).toShort()
                }
                emit(shorts)
            }
        } finally {
            runCatching { line.stop() }
            runCatching { line.close() }
        }
    }

    private companion object {
        private const val BYTES_PER_SAMPLE = 2
    }
}
