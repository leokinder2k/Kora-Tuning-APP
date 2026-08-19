package com.leokinder2k.koratuningcompanion.synth

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import com.leokinder2k.koratuningcompanion.BuildConfig
import io.github.lemcoder.mikrosoundfont.MikroSoundFont
import io.github.lemcoder.mikrosoundfont.SoundFont
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LowLatencySynthEngine(
    context: Context,
    private val audioManager: AudioManager? = context.applicationContext.getSystemService(AudioManager::class.java),
    private val sampleRate: Int = preferredSampleRate(audioManager),
    preferredFramesPerRender: Int = preferredFramesPerBuffer(audioManager)
) : SynthAudioEngine {
    private val synthAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            panic()
        }
    }
    private val lock = Any()
    private val preferredFramesPerRender = preferredFramesPerRender
    private val fallbackVoices = mutableListOf<SynthVoice>()
    private val soundFontHeldChannels = mutableMapOf<Int, MutableSet<Int>>()
    private var clickSamplesRemaining = 0
    private var clickTotalSamples = 1
    private var clickPhase = 0.0
    private var clickPhaseStep = 0.0
    private var clickGain = 0.0
    private val outputLimiter = SynthOutputLimiter(
        peakCeiling = LimiterPeakCeiling,
        hardCeiling = LimiterHardCeiling,
        release = LimiterRelease
    )

    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var renderThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var activeBufferFrames = SynthLatencyMode.Low.renderFramesFor(preferredFramesPerRender)
    private var audioFocusRequest: AudioFocusRequest? = null

    private var soundFont: SoundFont? = null

    var soundFontName: String? = null
        private set
    override var masterVolume: Float = 0.74f
        private set
    var bassSplitEnabled: Boolean = false
        private set
    var padLayerEnabled: Boolean = false
        private set
    override var splitNote: Int = 48
        private set
    var sustainEnabled: Boolean = false
        private set
    override var latencyMode: SynthLatencyMode = SynthLatencyMode.Low
        private set

    override val isRunning: Boolean
        get() = running &&
            renderThread?.isAlive == true &&
            audioTrack?.state == AudioTrack.STATE_INITIALIZED
    override val bufferFrames: Int
        get() = activeBufferFrames
    override val estimatedOutputLatencyMs: Float
        get() = activeBufferFrames * 1000f / sampleRate

    override fun start() {
        if (isRunning) return
        stop()
        val activeFramesPerRender = latencyMode.renderFramesFor(preferredFramesPerRender)
        val activeRenderBuffer = FloatArray(activeFramesPerRender * ChannelCount)
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val frameBytes = ChannelCount * FloatBytes
        val minBufferFrames = if (minBufferBytes > 0) {
            (minBufferBytes + frameBytes - 1) / frameBytes
        } else {
            0
        }
        val targetBufferFrames = latencyMode.bufferFramesFor(minBufferFrames, activeFramesPerRender)
        val bufferSizeBytes = targetBufferFrames * frameBytes
        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(
                synthAudioAttributes
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

        val track = runCatching { trackBuilder.build() }.getOrElse { throwable ->
            if (BuildConfig.DEBUG) Log.w(LogTag, "AudioTrack build failed", throwable)
            audioTrack = null
            running = false
            return
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            if (BuildConfig.DEBUG) Log.w(LogTag, "AudioTrack failed to initialize")
            runCatching { track.release() }
            audioTrack = null
            running = false
            return
        }
        val appliedBufferFrames = runCatching {
            track.setBufferSizeInFrames(targetBufferFrames)
        }.getOrDefault(targetBufferFrames)
        activeBufferFrames = if (appliedBufferFrames > 0) appliedBufferFrames else targetBufferFrames
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { track.setStartThresholdInFrames(activeFramesPerRender.coerceAtMost(activeBufferFrames)) }
        }
        audioTrack = track
        running = true
        renderThread = thread(start = true, name = "KoraSynthAudio") {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                track.play()
                while (running) {
                    render(activeRenderBuffer, activeFramesPerRender)
                    val written = track.write(activeRenderBuffer, 0, activeRenderBuffer.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) error("AudioTrack write failed: $written")
                }
            } catch (throwable: Throwable) {
                if (BuildConfig.DEBUG) Log.w(LogTag, "Synth audio thread stopped", throwable)
            } finally {
                running = false
                if (audioTrack === track) audioTrack = null
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                }
                runCatching { track.release() }
            }
        }
    }

    override fun stop() {
        val track = audioTrack
        running = false
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        renderThread?.join(400)
        if (renderThread?.isAlive == true) {
            runCatching { track?.release() }
        }
        renderThread = null
        audioTrack = null
        panic()
        abandonAudioFocus()
    }

    override fun setLatencyMode(mode: SynthLatencyMode) {
        if (latencyMode == mode) return
        val shouldRestart = isRunning
        stop()
        latencyMode = mode
        activeBufferFrames = mode.renderFramesFor(preferredFramesPerRender)
        if (shouldRestart) start()
    }

    override fun setMasterVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
        synchronized(lock) {
            soundFont?.setVolume(soundFontRenderVolume())
        }
    }

    override fun setBassSplitEnabled(enabled: Boolean) {
        bassSplitEnabled = enabled
    }

    override fun setPadLayerEnabled(enabled: Boolean) {
        padLayerEnabled = enabled
    }

    override fun setSplitNote(note: Int) {
        splitNote = note.coerceIn(24, 84)
    }

    override fun setSustain(enabled: Boolean) {
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

    override fun playMetronomeClick(accent: Boolean) {
        start()
        requestAudioFocus()
        synchronized(lock) {
            clickTotalSamples = (sampleRate * ClickSeconds).toInt().coerceAtLeast(1)
            clickSamplesRemaining = clickTotalSamples
            clickPhase = 0.0
            val frequency = if (accent) AccentClickHz else NormalClickHz
            clickPhaseStep = TwoPi * frequency / sampleRate
            clickGain = if (accent) AccentClickGain else NormalClickGain
        }
    }

    override fun noteOn(note: Int, velocity: Float) {
        start()
        requestAudioFocus()
        val midiNote = note.coerceIn(0, 127)
        val safeVelocity = velocity.coerceIn(MinNoteVelocity, MaxNoteVelocity)
        synchronized(lock) {
            val font = soundFont
            if (font != null) {
                soundFontNoteOn(font, midiNote, safeVelocity)
            } else {
                fallbackNoteOn(midiNote, safeVelocity)
            }
        }
    }

    override fun noteOff(note: Int) {
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

    override fun playMomentaryChord(notes: List<Int>, velocity: Float) {
        notes.forEach { noteOn(it, velocity) }
        thread(start = true, name = "KoraSynthChordRelease") {
            Thread.sleep(900)
            notes.forEach { noteOff(it) }
        }
    }

    override fun panic() {
        synchronized(lock) {
            soundFont?.noteOffAll()
            soundFont?.channels?.take(3)?.forEach { channel ->
                runCatching { channel.soundsOffAll() }
            }
            soundFontHeldChannels.clear()
            fallbackVoices.clear()
            clickSamplesRemaining = 0
            outputLimiter.reset()
        }
    }

    override fun loadSoundFont(displayName: String, bytes: ByteArray): Result<String> = runCatching {
        val loaded = MikroSoundFont.load(bytes)
        loaded.setOutput(SoundFont.OutputMode.STEREO_INTERLEAVED, sampleRate, 0f)
        loaded.setMaxVoices(96)
        loaded.setVolume(soundFontRenderVolume())
        configureSoundFontPrograms(loaded)
        synchronized(lock) {
            soundFont = loaded
            soundFontName = displayName
            fallbackVoices.clear()
            soundFontHeldChannels.clear()
        }
        displayName
    }

    override fun useBuiltInSound() {
        synchronized(lock) {
            soundFont?.noteOffAll()
            soundFont = null
            soundFontName = null
            soundFontHeldChannels.clear()
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(synthAudioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { audioFocusRequest = it }
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        if (BuildConfig.DEBUG && result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(LogTag, "Audio focus not granted: $result")
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun soundFontNoteOn(font: SoundFont, note: Int, velocity: Float) {
        soundFontHeldChannels.remove(note)?.forEach { channelIndex ->
            runCatching { font.channels[channelIndex].noteOff(note) }
        }
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
        removeFallbackVoice(note, mainKind)
        fallbackVoices += SynthVoice(note = note, velocity = velocity, kind = mainKind, sampleRate = sampleRate)
        if (padLayerEnabled && note >= splitNote) {
            removeFallbackVoice(note, VoiceKind.Pad)
            fallbackVoices += SynthVoice(note = note, velocity = velocity * 0.55f, kind = VoiceKind.Pad, sampleRate = sampleRate)
        }
        while (fallbackVoices.size > MaxFallbackVoices) {
            fallbackVoices.removeAt(0)
        }
    }

    private fun removeFallbackVoice(note: Int, kind: VoiceKind) {
        fallbackVoices.removeAll { voice -> voice.note == note && voice.kind == kind }
    }

    private fun render(target: FloatArray, frames: Int) {
        val font = synchronized(lock) { soundFont }
        if (font != null) {
            val rendered = synchronized(lock) {
                font.renderFloat(frames, ChannelCount, isMixing = false)
            }
            if (rendered.size >= target.size) {
                for (index in target.indices) {
                    target[index] = rendered[index]
                }
            } else {
                target.fill(0f)
            }
            synchronized(lock) {
                mixClick(target, frames)
                applyOutputGainAndLimiter(target, frames, requestedGain = 1f)
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
                target[offset] = left
                target[offset + 1] = right
            }
            fallbackVoices.removeAll { it.isDone }
            val hadClick = clickSamplesRemaining > 0
            mixClick(target, frames)
            val requestedGain = if (hadClick && fallbackVoices.isEmpty()) {
                masterVolume
            } else {
                masterVolume * voiceMixGain
            }
            applyOutputGainAndLimiter(target, frames, requestedGain)
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
            target[offset] += sample
            target[offset + 1] += sample
            clickPhase += clickPhaseStep
            if (clickPhase > TwoPi) clickPhase -= TwoPi
            clickSamplesRemaining -= 1
        }
    }

    private fun applyOutputGainAndLimiter(target: FloatArray, frames: Int, requestedGain: Float) {
        outputLimiter.applyInterleaved(target, frames * ChannelCount, requestedGain)
    }

    private fun soundFontRenderVolume(): Float {
        return masterVolume * SoundFontRenderHeadroom
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
        const val LogTag = "KoraSynth"
        const val ChannelCount = 2
        const val FloatBytes = 4
        const val MaxFallbackVoices = 28
        const val SustainPedalControl = 64
        const val PianoChannel = 0
        const val BassChannel = 1
        const val PadChannel = 2
        const val MinNoteVelocity = 0.04f
        const val MaxNoteVelocity = 0.78f
        const val SoundFontRenderHeadroom = 0.55f
        const val FallbackVoiceHeadroom = 0.5
        const val TwoPi = PI * 2.0
        const val ClickSeconds = 0.045
        const val ClickDecay = 7.0
        const val AccentClickHz = 1760.0
        const val NormalClickHz = 1320.0
        const val AccentClickGain = 0.52
        const val NormalClickGain = 0.4
        const val LimiterPeakCeiling = 0.72f
        const val LimiterHardCeiling = 0.88f
        const val LimiterRelease = 0.06f

        fun preferredSampleRate(audioManager: AudioManager?): Int {
            return audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.coerceIn(22_050, 96_000)
                ?: 48_000
        }

        fun preferredFramesPerBuffer(audioManager: AudioManager?): Int {
            return audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()
                ?.coerceIn(64, 192)
                ?: 128
        }

    }
}

enum class SynthLatencyMode(
    val label: String,
    val detail: String,
    private val fixedRenderFrames: Int?,
    private val preferredMultiplier: Int,
    private val bufferPeriods: Int
) {
    Minimum(
        label = "Min",
        detail = "Smallest buffer; use if the tablet is clean and close to the controller.",
        fixedRenderFrames = 64,
        preferredMultiplier = 1,
        bufferPeriods = 1
    ),
    Low(
        label = "Low",
        detail = "Current low-lag setting.",
        fixedRenderFrames = null,
        preferredMultiplier = 1,
        bufferPeriods = 1
    ),
    Balanced(
        label = "Balanced",
        detail = "More buffer for fewer pops.",
        fixedRenderFrames = null,
        preferredMultiplier = 2,
        bufferPeriods = 2
    ),
    Stable(
        label = "Stable",
        detail = "Largest buffer for difficult hubs, speakers, or busy tablets.",
        fixedRenderFrames = null,
        preferredMultiplier = 4,
        bufferPeriods = 3
    );

    fun renderFramesFor(preferredFrames: Int): Int {
        return (fixedRenderFrames ?: (preferredFrames * preferredMultiplier)).coerceIn(64, 768)
    }

    fun bufferFramesFor(minBufferFrames: Int, renderFrames: Int): Int {
        return max(minBufferFrames, renderFrames * bufferPeriods).coerceAtLeast(renderFrames)
    }
}

internal class SynthOutputLimiter(
    private val peakCeiling: Float,
    private val hardCeiling: Float,
    private val release: Float
) {
    private var gain = 1f

    fun reset() {
        gain = 1f
    }

    fun applyInterleaved(target: FloatArray, requestedSampleCount: Int, requestedGain: Float) {
        val sampleCount = requestedSampleCount.coerceAtMost(target.size).coerceAtLeast(0)
        var peak = 0f
        for (index in 0 until sampleCount) {
            peak = max(peak, abs(target[index]))
        }

        var desiredGain = requestedGain.coerceAtLeast(0f)
        if (peak > 0f) {
            desiredGain = min(desiredGain, peakCeiling / peak)
        } else {
            desiredGain = 1f
        }
        gain = if (desiredGain < gain) {
            desiredGain
        } else {
            gain + ((desiredGain - gain) * release)
        }

        for (index in 0 until sampleCount) {
            target[index] = (target[index] * gain).coerceIn(-hardCeiling, hardCeiling)
        }
    }
}
