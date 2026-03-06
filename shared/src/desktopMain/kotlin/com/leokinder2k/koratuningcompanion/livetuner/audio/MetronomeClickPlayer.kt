package com.leokinder2k.koratuningcompanion.livetuner.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

actual class MetronomeClickPlayer actual constructor(
    private val sampleRateHz: Int
) {
    @Synchronized
    actual fun play(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Float) {
        val samples = createClickWave(sound, accent, volumeScale.coerceIn(0f, 1.8f).toDouble())
        thread(start = true, isDaemon = true, name = "metro-desktop") {
            val format = AudioFormat(sampleRateHz.toFloat(), 16, 1, true, false)
            val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
            line.open(format, samples.size * 2)
            line.start()
            val byteBuffer = shortsToBytes(samples)
            line.write(byteBuffer, 0, byteBuffer.size)
            line.drain()
            line.close()
        }
    }

    @Synchronized
    actual fun stopAll() { /* desktop: fire-and-forget, cannot cancel */ }

    @Synchronized
    actual fun release() { stopAll() }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val buf = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            buf[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            buf[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        return buf
    }

    private fun createClickWave(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Double): ShortArray {
        val baseDurationMs = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 58
            MetronomeSoundOption.WOOD_BLOCK -> 46
            MetronomeSoundOption.WOOD_CLICK -> 34
        }
        val sampleCount = ((sampleRateHz * baseDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        val baseFrequencyHz = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 880.0
            MetronomeSoundOption.WOOD_BLOCK -> 1_450.0
            MetronomeSoundOption.WOOD_CLICK -> 2_250.0
        }
        val harmonicGain = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 0.16
            MetronomeSoundOption.WOOD_BLOCK -> 0.24
            MetronomeSoundOption.WOOD_CLICK -> 0.32
        }
        val amplitude = (if (accent) 0.48 else 0.34) * volumeScale
        val attackSamples = (sampleRateHz * 0.0018).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val attack = if (i < attackSamples) i.toDouble() / attackSamples.toDouble() else 1.0
            val progress = i.toDouble() / sampleCount.toDouble()
            val decay = exp(-8.0 * progress)
            val fundamental = sin((2.0 * PI * baseFrequencyHz * i) / sampleRateHz)
            val harmonic = sin((2.0 * PI * baseFrequencyHz * 2.38 * i) / sampleRateHz)
            val tone = (fundamental + (harmonicGain * harmonic)) * attack * decay
            val scaled = (tone * amplitude).coerceIn(-1.0, 1.0)
            samples[i] = (scaled * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}
