package com.leokinder2k.koratuningcompanion.livetuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

actual class ReferenceTonePlayer actual constructor(
    private val sampleRateHz: Int,
    private val amplitude: Double
) {

    private var track: AudioTrack? = null
    private var currentFrequencyHz: Double? = null

    @Synchronized
    actual fun play(frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) {
            return
        }

        val activeTrack = track
        if (activeTrack != null &&
            activeTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
            currentFrequencyHz?.let { abs(it - frequencyHz) < 0.05 } == true
        ) {
            return
        }

        stopInternal()

        val samples = createSineWave(frequencyHz)
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBufferBytes, samples.size * 2)

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()

        val written = newTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written <= 0) {
            newTrack.release()
            return
        }

        newTrack.setLoopPoints(0, written, -1)
        newTrack.play()
        track = newTrack
        currentFrequencyHz = frequencyHz
    }

    @Synchronized
    actual fun stop() {
        stopInternal()
    }

    @Synchronized
    actual fun isPlaying(): Boolean {
        return track?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    @Synchronized
    actual fun release() {
        stopInternal()
    }

    private fun stopInternal() {
        track?.runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            flush()
            release()
        }
        track = null
        currentFrequencyHz = null
    }

    private fun createSineWave(frequencyHz: Double): ShortArray {
        // Choose enough complete cycles (~100 ms) so the loop seam is phase-seamless,
        // eliminating the click artifact and ensuring the detected pitch is exact.
        val cycles = (frequencyHz * 0.1).roundToInt().coerceAtLeast(1)
        val sampleCount = (cycles.toDouble() * sampleRateHz / frequencyHz).roundToInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (index in 0 until sampleCount) {
            val angle = 2.0 * PI * frequencyHz * index / sampleRateHz
            samples[index] = (sin(angle) * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}
