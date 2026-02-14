package com.leokinder2k.koratuningcompanion.livetuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class PluckedStringPlayer(
    private val sampleRateHz: Int = 44_100,
    private val amplitude: Double = 0.24,
    private val pluckDurationMs: Int = 650
) {

    private val activeTracks = mutableMapOf<Int, AudioTrack>()

    @Synchronized
    fun play(stringNumber: Int, frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) {
            return
        }

        stop(stringNumber)

        val samples = createPluckedWave(frequencyHz = frequencyHz)
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBufferBytes, samples.size * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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

        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written <= 0) {
            track.release()
            return
        }

        activeTracks[stringNumber] = track
        track.play()

        thread(start = true, isDaemon = true, name = "pluck-release-$stringNumber") {
            Thread.sleep((pluckDurationMs + 90).toLong())
            synchronized(this@PluckedStringPlayer) {
                if (activeTracks[stringNumber] === track) {
                    releaseTrack(track)
                    activeTracks.remove(stringNumber)
                }
            }
        }
    }

    @Synchronized
    fun stop(stringNumber: Int) {
        val track = activeTracks.remove(stringNumber) ?: return
        releaseTrack(track)
    }

    @Synchronized
    fun stopAll() {
        val tracks = activeTracks.values.toList()
        activeTracks.clear()
        tracks.forEach(::releaseTrack)
    }

    @Synchronized
    fun release() {
        stopAll()
    }

    private fun releaseTrack(track: AudioTrack) {
        track.runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            flush()
            release()
        }
    }

    private fun createPluckedWave(frequencyHz: Double): ShortArray {
        val sampleCount = ((sampleRateHz * pluckDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.004).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)

        for (index in 0 until sampleCount) {
            val attackEnvelope = if (index < attackSamples) {
                index.toDouble() / attackSamples.toDouble()
            } else {
                1.0
            }
            val progress = index.toDouble() / sampleCount.toDouble()
            val decayEnvelope = exp(-6.5 * progress)
            val fundamentalAngle = (2.0 * PI * frequencyHz * index) / sampleRateHz
            val overtoneAngle = (2.0 * PI * frequencyHz * 2.0 * index) / sampleRateHz
            val tone = (sin(fundamentalAngle) + (0.35 * sin(overtoneAngle))) * 0.74
            val value = tone * amplitude * attackEnvelope * decayEnvelope * 5.0
            val clampedValue = value.coerceIn(-1.0, 1.0)
            samples[index] = (clampedValue * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}

