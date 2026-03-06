package com.leokinder2k.koratuningcompanion.livetuner.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

actual class ReferenceTonePlayer actual constructor(
    private val sampleRateHz: Int,
    private val amplitude: Double
) {
    @Volatile private var loopThread: Thread? = null
    @Volatile private var currentFrequencyHz: Double? = null
    @Volatile private var playing = false

    @Synchronized
    actual fun play(frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        if (playing && currentFrequencyHz?.let { abs(it - frequencyHz) < 0.05 } == true) return
        stopInternal()
        playing = true
        currentFrequencyHz = frequencyHz
        val samples = createSineWave(frequencyHz)
        val byteBuffer = shortsToBytes(samples)
        loopThread = thread(start = true, isDaemon = true, name = "ref-tone-desktop") {
            val format = AudioFormat(sampleRateHz.toFloat(), 16, 1, true, false)
            val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
            line.open(format, byteBuffer.size * 2)
            line.start()
            try {
                while (playing) {
                    line.write(byteBuffer, 0, byteBuffer.size)
                }
            } finally {
                line.drain()
                line.close()
            }
        }
    }

    @Synchronized
    actual fun stop() { stopInternal() }

    actual fun isPlaying(): Boolean = playing

    @Synchronized
    actual fun release() { stopInternal() }

    private fun stopInternal() {
        playing = false
        currentFrequencyHz = null
        loopThread?.interrupt()
        loopThread = null
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val buf = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            buf[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            buf[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        return buf
    }

    private fun createSineWave(frequencyHz: Double): ShortArray {
        val sampleCount = sampleRateHz
        val samples = ShortArray(sampleCount)
        for (index in 0 until sampleCount) {
            val angle = (2.0 * PI * frequencyHz * index) / sampleRateHz
            val value = sin(angle) * amplitude
            samples[index] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}
