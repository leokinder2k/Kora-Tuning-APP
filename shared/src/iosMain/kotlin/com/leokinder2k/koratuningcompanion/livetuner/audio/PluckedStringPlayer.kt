@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

actual class PluckedStringPlayer actual constructor(
    private val sampleRateHz: Int,
    private val baseAmplitude: Double,
    private val pluckDurationMs: Int
) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(
        standardFormatWithSampleRate = sampleRateHz.toDouble(),
        channels = 1u
    )!!
    private val activePlayers = mutableMapOf<Int, AVAudioPlayerNode>()
    private val extraVolumeFactor = 250.0
    private var amplitudeScale = 1.0

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun setVolumeDb(db: Double) {
        val clamped = db.coerceIn(0.0, 100.0)
        amplitudeScale = 10.0.pow((clamped - 70.0) / 20.0).coerceAtLeast(0.15)
    }

    actual fun play(stringNumber: Int, frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        stop(stringNumber)

        val count = ((sampleRateHz * pluckDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.004).toInt().coerceAtLeast(1)
        val amp = baseAmplitude * amplitudeScale * extraVolumeFactor

        val samples = FloatArray(count) { i ->
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-6.5 * i.toDouble() / count)
            val f = sin(2.0 * PI * frequencyHz * i / sampleRateHz)
            val o = sin(2.0 * PI * frequencyHz * 2.0 * i / sampleRateHz)
            ((f + 0.35 * o) * 0.74 * amp * attack * decay).coerceIn(-1.0, 1.0).toFloat()
        }

        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = count.toUInt()) ?: return
        buffer.setFrameLength(count.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val player = AVAudioPlayerNode()
        activePlayers[stringNumber] = player
        engine.attachNode(player)
        engine.connect(player, to = mixer, format = format)
        player.scheduleBuffer(buffer, completionHandler = {
            engine.detachNode(player)
            activePlayers.remove(stringNumber)
        })
        player.play()
    }

    actual fun stop(stringNumber: Int) {
        activePlayers.remove(stringNumber)?.let {
            it.stop()
            engine.detachNode(it)
        }
    }

    actual fun stopAll() {
        activePlayers.values.forEach { it.stop(); engine.detachNode(it) }
        activePlayers.clear()
    }

    actual fun release() {
        stopAll()
        engine.stop()
    }
}
