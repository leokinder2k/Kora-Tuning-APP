package com.leokinder2k.koratuningcompanion.livetuner.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

actual class PluckedStringPlayer actual constructor(
    private val sampleRateHz: Int,
    private val baseAmplitude: Double,
    private val pluckDurationMs: Int
) {
    private val extraVolumeFactor = 250.0
    private var amplitudeScale = 1.0

    actual fun setVolumeDb(db: Double) {
        val clampedDb = db.coerceIn(0.0, 100.0)
        amplitudeScale = 10.0.pow((clampedDb - 70.0) / 20.0).coerceAtLeast(0.15)
    }

    actual fun play(stringNumber: Int, frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        val samples = createPluckedWave(frequencyHz)
        val byteBuffer = shortsToBytes(samples)
        thread(start = true, isDaemon = true, name = "pluck-desktop-$stringNumber") {
            val format = AudioFormat(sampleRateHz.toFloat(), 16, 1, true, false)
            val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
            line.open(format, byteBuffer.size * 2)
            line.start()
            line.write(byteBuffer, 0, byteBuffer.size)
            line.drain()
            line.close()
        }
    }

    actual fun stop(stringNumber: Int) { /* desktop: fire-and-forget */ }
    actual fun stopAll() { /* desktop: fire-and-forget */ }
    actual fun release() { stopAll() }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val buf = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            buf[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            buf[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        return buf
    }

    private fun createPluckedWave(frequencyHz: Double): ShortArray {
        val sampleCount = ((sampleRateHz * pluckDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.004).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (index in 0 until sampleCount) {
            val attackEnvelope = if (index < attackSamples) index.toDouble() / attackSamples.toDouble() else 1.0
            val progress = index.toDouble() / sampleCount.toDouble()
            val decayEnvelope = exp(-6.5 * progress)
            val fundamentalAngle = (2.0 * PI * frequencyHz * index) / sampleRateHz
            val overtoneAngle = (2.0 * PI * frequencyHz * 2.0 * index) / sampleRateHz
            val tone = (sin(fundamentalAngle) + (0.35 * sin(overtoneAngle))) * 0.74
            val amplitude = baseAmplitude * amplitudeScale * extraVolumeFactor
            val value = tone * amplitude * attackEnvelope * decayEnvelope
            samples[index] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}
