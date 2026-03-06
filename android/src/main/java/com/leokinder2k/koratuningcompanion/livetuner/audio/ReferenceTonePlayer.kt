package com.leokinder2k.koratuningcompanion.livetuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ReferenceTonePlayer(
    private val sampleRateHz: Int = 44_100,
    private val amplitude: Double = 0.25
) {

    private var track: AudioTrack? = null
    private var currentFrequencyHz: Double? = null

    @Synchronized
    fun play(frequencyHz: Double) {
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
    fun stop() {
        stopInternal()
    }

    @Synchronized
    fun isPlaying(): Boolean {
        return track?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    @Synchronized
    fun release() {
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

