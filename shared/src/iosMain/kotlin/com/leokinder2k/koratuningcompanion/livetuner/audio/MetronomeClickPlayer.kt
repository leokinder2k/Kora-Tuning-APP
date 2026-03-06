package com.leokinder2k.koratuningcompanion.livetuner.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

actual class MetronomeClickPlayer actual constructor(private val sampleRateHz: Int) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(
        standardFormatWithSampleRate = sampleRateHz.toDouble(),
        channels = 1u
    )!!
    private val activePlayers = mutableListOf<AVAudioPlayerNode>()

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun play(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Float) {
        val samples = createClickSamples(sound, accent, volumeScale.coerceIn(0f, 1.8f).toDouble())
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt()) ?: return
        buffer.setFrameLength(samples.size.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val player = AVAudioPlayerNode()
        activePlayers.add(player)
        engine.attachNode(player)
        engine.connect(player, to = mixer, format = format)
        player.scheduleBuffer(buffer, completionHandler = {
            engine.detachNode(player)
            activePlayers.remove(player)
        })
        player.play()
    }

    actual fun stopAll() {
        activePlayers.forEach { it.stop(); engine.detachNode(it) }
        activePlayers.clear()
    }

    actual fun release() {
        stopAll()
        engine.stop()
    }

    private fun createClickSamples(
        sound: MetronomeSoundOption,
        accent: Boolean,
        volumeScale: Double
    ): FloatArray {
        val durationMs = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 58
            MetronomeSoundOption.WOOD_BLOCK -> 46
            MetronomeSoundOption.WOOD_CLICK -> 34
        }
        val baseFreq = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 880.0
            MetronomeSoundOption.WOOD_BLOCK -> 1450.0
            MetronomeSoundOption.WOOD_CLICK -> 2250.0
        }
        val harmonicGain = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 0.16
            MetronomeSoundOption.WOOD_BLOCK -> 0.24
            MetronomeSoundOption.WOOD_CLICK -> 0.32
        }
        val amplitude = (if (accent) 0.48 else 0.34) * volumeScale
        val count = ((sampleRateHz * durationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.0018).toInt().coerceAtLeast(1)
        return FloatArray(count) { i ->
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-8.0 * i.toDouble() / count)
            val f = sin(2.0 * PI * baseFreq * i / sampleRateHz)
            val h = sin(2.0 * PI * baseFreq * 2.38 * i / sampleRateHz)
            ((f + harmonicGain * h) * attack * decay * amplitude).coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
