package com.leokinder2k.koratuningcompanion.livetuner.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferLoops
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

actual class ReferenceTonePlayer actual constructor(
    private val sampleRateHz: Int,
    private val amplitude: Double
) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(
        standardFormatWithSampleRate = sampleRateHz.toDouble(),
        channels = 1u
    )!!
    private var player: AVAudioPlayerNode? = null
    private var currentFreq: Double? = null

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun play(frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        if (currentFreq?.let { abs(it - frequencyHz) < 0.05 } == true) return
        stopInternal()

        val samples = FloatArray(sampleRateHz) { i ->
            (sin(2.0 * PI * frequencyHz * i / sampleRateHz) * amplitude).toFloat()
        }
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt()) ?: return
        buffer.setFrameLength(samples.size.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val p = AVAudioPlayerNode()
        player = p
        currentFreq = frequencyHz
        engine.attachNode(p)
        engine.connect(p, to = mixer, format = format)
        p.scheduleBuffer(buffer, atTime = null, options = AVAudioPlayerNodeBufferLoops, completionHandler = null)
        p.play()
    }

    actual fun stop() = stopInternal()

    actual fun isPlaying(): Boolean = player != null

    actual fun release() {
        stopInternal()
        engine.stop()
    }

    private fun stopInternal() {
        player?.stop()
        player?.let { engine.detachNode(it) }
        player = null
        currentFreq = null
    }
}
