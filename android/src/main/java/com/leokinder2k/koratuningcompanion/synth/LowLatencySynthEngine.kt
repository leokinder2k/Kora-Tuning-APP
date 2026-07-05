package com.leokinder2k.koratuningcompanion.synth

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import io.github.lemcoder.mikrosoundfont.MikroSoundFont
import io.github.lemcoder.mikrosoundfont.SoundFont
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LowLatencySynthEngine(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val sampleRate = preferredSampleRate(appContext)
    private val framesPerRender = preferredFramesPerBuffer(appContext)
    private val renderBuffer = FloatArray(framesPerRender * ChannelCount)
    private val fallbackVoices = mutableListOf<SynthVoice>()
    private val soundFontHeldChannels = mutableMapOf<Int, MutableSet<Int>>()
    private var clickSamplesRemaining = 0
    private var clickTotalSamples = 1
    private var clickPhase = 0.0
    private var clickPhaseStep = 0.0
    private var clickGain = 0.0

    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var renderThread: Thread? = null
    @Volatile private var running = false

    private var soundFont: SoundFont? = null

    var soundFontName: String? = null
        private set
    var masterVolume: Float = 0.74f
        private set
    var bassSplitEnabled: Boolean = false
        private set
    var padLayerEnabled: Boolean = false
        private set
    var splitNote: Int = 48
        private set
    var sustainEnabled: Boolean = false
        private set

    val isRunning: Boolean
        get() = running

    fun start() {
        if (running) return
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val frameBytes = ChannelCount * FloatBytes
        val bufferSizeBytes = max(
            if (minBufferBytes > 0) minBufferBytes else 0,
            framesPerRender * frameBytes
        )
        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = trackBuilder.build()
        runCatching { track.setBufferSizeInFrames((bufferSizeBytes / frameBytes).coerceAtLeast(framesPerRender)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { track.setStartThresholdInFrames(framesPerRender) }
        }
        audioTrack = track
        running = true
        renderThread = thread(start = true, name = "KoraSynthAudio") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            track.play()
            while (running) {
                render(renderBuffer, framesPerRender)
                track.write(renderBuffer, 0, renderBuffer.size, AudioTrack.WRITE_BLOCKING)
            }
            track.stop()
            track.release()
        }
    }

    fun stop() {
        running = false
        renderThread?.join(400)
        renderThread = null
        audioTrack = null
        panic()
    }

    fun setMasterVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
        synchronized(lock) {
            soundFont?.setVolume(masterVolume)
        }
    }

    fun setBassSplitEnabled(enabled: Boolean) {
        bassSplitEnabled = enabled
    }

    fun setPadLayerEnabled(enabled: Boolean) {
        padLayerEnabled = enabled
    }

    fun setSplitNote(note: Int) {
        splitNote = note.coerceIn(24, 84)
    }

    fun setSustain(enabled: Boolean) {
        sustainEnabled = enabled
        synchronized(lock) {
            soundFont?.channels?.take(3)?.forEach { channel ->
                runCatching { channel.setMidiControl(SustainPedalControl, if (enabled) 127 else 0) }
            }
            if (!enabled) {
                fallbackVoices
                    .filter { !it.keyDown && !it.released }
                    .forEach { it.release() }
            }
        }
    }

    fun playMetronomeClick(accent: Boolean) {
        synchronized(lock) {
            clickTotalSamples = (sampleRate * ClickSeconds).toInt().coerceAtLeast(1)
            clickSamplesRemaining = clickTotalSamples
            clickPhase = 0.0
            val frequency = if (accent) AccentClickHz else NormalClickHz
            clickPhaseStep = TwoPi * frequency / sampleRate
            clickGain = if (accent) AccentClickGain else NormalClickGain
        }
    }

    fun noteOn(note: Int, velocity: Float) {
        val midiNote = note.coerceIn(0, 127)
        val safeVelocity = velocity.coerceIn(0.04f, 1f)
        synchronized(lock) {
            val font = soundFont
            if (font != null) {
                soundFontNoteOn(font, midiNote, safeVelocity)
            } else {
                fallbackNoteOn(midiNote, safeVelocity)
            }
        }
    }

    fun noteOff(note: Int) {
        val midiNote = note.coerceIn(0, 127)
        synchronized(lock) {
            val font = soundFont
            if (font != null) {
                soundFontHeldChannels.remove(midiNote)?.forEach { channelIndex ->
                    runCatching { font.channels[channelIndex].noteOff(midiNote) }
                }
            } else {
                fallbackVoices
                    .filter { it.note == midiNote && it.keyDown }
                    .forEach { voice ->
                        voice.keyDown = false
                        if (!sustainEnabled) voice.release()
                    }
            }
        }
    }

    fun playMomentaryChord(notes: List<Int>, velocity: Float = 0.72f) {
        notes.forEach { noteOn(it, velocity) }
        thread(start = true, name = "KoraSynthChordRelease") {
            Thread.sleep(900)
            notes.forEach { noteOff(it) }
        }
    }

    fun panic() {
        synchronized(lock) {
            soundFont?.noteOffAll()
            soundFont?.channels?.take(3)?.forEach { channel ->
                runCatching { channel.soundsOffAll() }
            }
            soundFontHeldChannels.clear()
            fallbackVoices.clear()
            clickSamplesRemaining = 0
        }
    }

    fun loadSoundFont(displayName: String, bytes: ByteArray): Result<String> = runCatching {
        val loaded = MikroSoundFont.load(bytes)
        loaded.setOutput(SoundFont.OutputMode.STEREO_INTERLEAVED, sampleRate, 0f)
        loaded.setMaxVoices(96)
        loaded.setVolume(masterVolume)
        configureSoundFontPrograms(loaded)
        synchronized(lock) {
            soundFont = loaded
            soundFontName = displayName
            fallbackVoices.clear()
            soundFontHeldChannels.clear()
        }
        displayName
    }

    fun useBuiltInSound() {
        synchronized(lock) {
            soundFont?.noteOffAll()
            soundFont = null
            soundFontName = null
            soundFontHeldChannels.clear()
        }
    }

    private fun soundFontNoteOn(font: SoundFont, note: Int, velocity: Float) {
        val channels = mutableSetOf<Int>()
        val mainChannel = if (bassSplitEnabled && note < splitNote) BassChannel else PianoChannel
        runCatching { font.channels[mainChannel].noteOn(note, velocity) }
        channels += mainChannel
        if (padLayerEnabled && note >= splitNote) {
            runCatching { font.channels[PadChannel].noteOn(note, (velocity * 0.55f).coerceAtMost(1f)) }
            channels += PadChannel
        }
        soundFontHeldChannels.getOrPut(note) { mutableSetOf() }.addAll(channels)
    }

    private fun fallbackNoteOn(note: Int, velocity: Float) {
        val mainKind = if (bassSplitEnabled && note < splitNote) VoiceKind.Bass else VoiceKind.Piano
        fallbackVoices += SynthVoice(note = note, velocity = velocity, kind = mainKind, sampleRate = sampleRate)
        if (padLayerEnabled && note >= splitNote) {
            fallbackVoices += SynthVoice(note = note, velocity = velocity * 0.55f, kind = VoiceKind.Pad, sampleRate = sampleRate)
        }
        while (fallbackVoices.size > MaxFallbackVoices) {
            fallbackVoices.removeAt(0)
        }
    }

    private fun render(target: FloatArray, frames: Int) {
        val font = synchronized(lock) { soundFont }
        if (font != null) {
            val rendered = synchronized(lock) {
                font.renderFloat(frames, ChannelCount, isMixing = false)
            }
            if (rendered.size >= target.size) {
                for (index in target.indices) {
                    target[index] = softLimit(rendered[index] * SoundFontHeadroom)
                }
            } else {
                target.fill(0f)
            }
            synchronized(lock) {
                mixClick(target, frames)
            }
            return
        }

        synchronized(lock) {
            target.fill(0f)
            val activeVoiceCount = fallbackVoices.size.coerceAtLeast(1)
            val voiceMixGain = (FallbackVoiceHeadroom / sqrt(activeVoiceCount.toDouble())).toFloat()
            for (frame in 0 until frames) {
                var left = 0f
                var right = 0f
                fallbackVoices.forEach { voice ->
                    val sample = voice.render()
                    left += sample * voice.leftGain
                    right += sample * voice.rightGain
                }
                val offset = frame * ChannelCount
                target[offset] = softLimit(left * masterVolume * voiceMixGain)
                target[offset + 1] = softLimit(right * masterVolume * voiceMixGain)
            }
            fallbackVoices.removeAll { it.isDone }
            mixClick(target, frames)
        }
    }

    private fun mixClick(target: FloatArray, frames: Int) {
        if (clickSamplesRemaining <= 0) return
        for (frame in 0 until frames) {
            if (clickSamplesRemaining <= 0) break
            val age = 1.0 - (clickSamplesRemaining / clickTotalSamples.toDouble())
            val envelope = exp(-age * ClickDecay)
            val sample = (sin(clickPhase) * envelope * clickGain).toFloat()
            val offset = frame * ChannelCount
            target[offset] = softLimit(target[offset] + sample)
            target[offset + 1] = softLimit(target[offset + 1] + sample)
            clickPhase += clickPhaseStep
            if (clickPhase > TwoPi) clickPhase -= TwoPi
            clickSamplesRemaining -= 1
        }
    }

    private fun configureSoundFontPrograms(font: SoundFont) {
        setProgramIfPresent(font, PianoChannel, bank = 0, preset = 0)
        setProgramIfPresent(font, BassChannel, bank = 0, preset = 32)
        setProgramIfPresent(font, PadChannel, bank = 0, preset = 88)
        font.channels.take(3).forEach { channel ->
            runCatching { channel.setPitchRange(2f) }
        }
    }

    private fun setProgramIfPresent(font: SoundFont, channelIndex: Int, bank: Int, preset: Int) {
        val channel = font.channels.getOrNull(channelIndex) ?: return
        val presetIndex = font.getPresetIndex(bank, preset)
        if (presetIndex >= 0) {
            runCatching { channel.setBankPreset(bank, preset) }
        } else {
            runCatching { channel.setPresetIndex(0) }
        }
    }

    private class SynthVoice(
        val note: Int,
        val velocity: Float,
        val kind: VoiceKind,
        private val sampleRate: Int
    ) {
        private val frequency = 440.0 * 2.0.pow((note - 69) / 12.0)
        private val phaseStep = TwoPi * frequency / sampleRate
        private val detuneStep = TwoPi * frequency * 1.006 / sampleRate
        private var phase = 0.0
        private var detunePhase = 0.0
        private var ageSamples = 0
        private var releaseSamples = 0
        private var releaseStart = 1.0

        var keyDown = true
        var released = false
            private set

        val leftGain: Float
        val rightGain: Float

        init {
            val pan = ((note - 60) / 44.0).coerceIn(-0.35, 0.35)
            leftGain = sqrt(((1.0 - pan) * 0.5)).toFloat()
            rightGain = sqrt(((1.0 + pan) * 0.5)).toFloat()
        }

        val isDone: Boolean
            get() {
                val age = ageSamples / sampleRate.toDouble()
                val releaseAge = releaseSamples / sampleRate.toDouble()
                return when {
                    released && releaseEnvelope(releaseAge) < 0.0004 -> true
                    kind == VoiceKind.Piano && age > 18.0 -> true
                    else -> false
                }
            }

        fun release() {
            if (released) return
            releaseStart = activeEnvelope()
            releaseSamples = 0
            released = true
        }

        fun render(): Float {
            val envelope = activeEnvelope()
            val sample = when (kind) {
                VoiceKind.Piano -> pianoSample(envelope)
                VoiceKind.Bass -> bassSample(envelope)
                VoiceKind.Pad -> padSample(envelope)
            }
            advancePhase()
            ageSamples += 1
            if (released) releaseSamples += 1
            return sample.toFloat()
        }

        private fun activeEnvelope(): Double {
            val age = ageSamples / sampleRate.toDouble()
            if (released) {
                return releaseStart * releaseEnvelope(releaseSamples / sampleRate.toDouble())
            }
            return when (kind) {
                VoiceKind.Piano -> {
                    val attack = min(1.0, age / 0.006)
                    val decayRate = 0.56 + min(0.9, frequency / 2400.0)
                    attack * exp(-age * decayRate)
                }
                VoiceKind.Bass -> {
                    val attack = min(1.0, age / 0.01)
                    val body = if (keyDown) 0.82 else exp(-age * 2.2)
                    attack * body
                }
                VoiceKind.Pad -> {
                    val attack = min(1.0, age / 0.38)
                    val body = if (keyDown) 0.72 else exp(-age * 0.5)
                    attack * body
                }
            }
        }

        private fun releaseEnvelope(age: Double): Double {
            val speed = when (kind) {
                VoiceKind.Piano -> 7.8
                VoiceKind.Bass -> 8.5
                VoiceKind.Pad -> 1.25
            }
            return exp(-age * speed)
        }

        private fun pianoSample(envelope: Double): Double {
            val age = ageSamples / sampleRate.toDouble()
            val brightness = 0.22 + velocity * 0.78
            val hammer = sin(phase * 7.0) * exp(-age * 34.0) * 0.055 * velocity
            val body =
                sin(phase) * 0.66 +
                    sin(phase * 2.0) * 0.20 * brightness +
                    sin(phase * 3.0) * 0.10 * brightness +
                    sin(phase * 4.0) * 0.045 * brightness
            return (body + hammer) * envelope * (0.22 + velocity * 0.84)
        }

        private fun bassSample(envelope: Double): Double {
            val tone = sin(phase) * 0.78 + sin(phase * 2.0) * 0.16 + sin(phase * 3.0) * 0.07
            return tone * envelope * (0.38 + velocity * 0.70)
        }

        private fun padSample(envelope: Double): Double {
            val slow = sin(phase * 0.5) * 0.05
            val tone = sin(phase) * 0.44 + sin(detunePhase) * 0.40 + sin(phase * 2.0) * 0.10
            return (tone + slow) * envelope * 0.56
        }

        private fun advancePhase() {
            phase += phaseStep
            detunePhase += detuneStep
            if (phase > TwoPi) phase -= TwoPi
            if (detunePhase > TwoPi) detunePhase -= TwoPi
        }
    }

    private enum class VoiceKind {
        Piano,
        Bass,
        Pad
    }

    private companion object {
        const val ChannelCount = 2
        const val FloatBytes = 4
        const val MaxFallbackVoices = 44
        const val SustainPedalControl = 64
        const val PianoChannel = 0
        const val BassChannel = 1
        const val PadChannel = 2
        const val SoundFontHeadroom = 0.82f
        const val FallbackVoiceHeadroom = 0.78
        const val TwoPi = PI * 2.0
        const val ClickSeconds = 0.045
        const val ClickDecay = 7.0
        const val AccentClickHz = 1760.0
        const val NormalClickHz = 1320.0
        const val AccentClickGain = 0.52
        const val NormalClickGain = 0.4
        const val LimiterInputCeiling = 4f
        const val LimiterOutputCeiling = 0.98f

        fun preferredSampleRate(context: Context): Int {
            val audioManager = context.getSystemService(AudioManager::class.java)
            return audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.coerceIn(22_050, 96_000)
                ?: 48_000
        }

        fun preferredFramesPerBuffer(context: Context): Int {
            val audioManager = context.getSystemService(AudioManager::class.java)
            return audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()
                ?.coerceIn(64, 192)
                ?: 128
        }

        fun softLimit(sample: Float): Float {
            val x = sample.coerceIn(-LimiterInputCeiling, LimiterInputCeiling)
            val limited = x / sqrt(1.0 + (x * x).toDouble()).toFloat()
            return limited.coerceIn(-LimiterOutputCeiling, LimiterOutputCeiling)
        }
    }
}
