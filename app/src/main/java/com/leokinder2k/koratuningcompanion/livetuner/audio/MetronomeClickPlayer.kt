package com.leokinder2k.koratuningcompanion.livetuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class MetronomeSoundOption(val label: String) {
    WOOD_SOFT("Soft Wood"),
    WOOD_BLOCK("Wood Block"),
    WOOD_CLICK("Bright Click")
}

class MetronomeClickPlayer(
    private val sampleRateHz: Int = 44_100
) {

    private val activeTracks = mutableSetOf<AudioTrack>()

    @Synchronized
    fun play(sound: MetronomeSoundOption, accent: Boolean) {
        val samples = createClickWave(
            sound = sound,
            accent = accent
        )
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
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

        activeTracks += track
        track.play()

        val releaseDelayMs = ((samples.size.toDouble() / sampleRateHz) * 1000.0).toLong() + 40L
        thread(start = true, isDaemon = true, name = "metronome-release") {
            Thread.sleep(releaseDelayMs)
            synchronized(this@MetronomeClickPlayer) {
                if (track in activeTracks) {
                    releaseTrack(track)
                    activeTracks -= track
                }
            }
        }
    }

    @Synchronized
    fun stopAll() {
        val tracks = activeTracks.toList()
        activeTracks.clear()
        tracks.forEach { track -> releaseTrack(track) }
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

    private fun createClickWave(
        sound: MetronomeSoundOption,
        accent: Boolean
    ): ShortArray {
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
        val amplitude = if (accent) 0.36 else 0.26
        val attackSamples = (sampleRateHz * 0.0018).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)

        for (i in 0 until sampleCount) {
            val attack = if (i < attackSamples) {
                i.toDouble() / attackSamples.toDouble()
            } else {
                1.0
            }
            val progress = i.toDouble() / sampleCount.toDouble()
            val decay = exp(-8.0 * progress)
            val fundamental = sin((2.0 * PI * baseFrequencyHz * i) / sampleRateHz)
            val harmonic = sin((2.0 * PI * baseFrequencyHz * 2.38 * i) / sampleRateHz)
            val tone = (fundamental + (harmonicGain * harmonic)) * attack * decay
            samples[i] = (tone * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}

