package com.leokinder2k.koratuningcompanion.synth

import android.content.Context

interface SynthAudioEngine {
    val isRunning: Boolean
    val masterVolume: Float
    val splitNote: Int
    val latencyMode: SynthLatencyMode
    val bufferFrames: Int
    val estimatedOutputLatencyMs: Float

    fun start()
    fun stop()
    fun setLatencyMode(mode: SynthLatencyMode)
    fun setMasterVolume(value: Float)
    fun setBassSplitEnabled(enabled: Boolean)
    fun setPadLayerEnabled(enabled: Boolean)
    fun setSplitNote(note: Int)
    fun setSustain(enabled: Boolean)
    fun playMetronomeClick(accent: Boolean)
    fun noteOn(note: Int, velocity: Float)
    fun noteOff(note: Int)
    fun playMomentaryChord(notes: List<Int>, velocity: Float = 0.72f)
    fun panic()
    fun loadSoundFont(displayName: String, bytes: ByteArray): Result<String>
    fun useBuiltInSound()
}

interface SynthMidiInput {
    fun availableDevices(): List<MidiDeviceSummary>
    fun usbDevices(): List<UsbDeviceSummary>
    fun startAutoConnect()
    fun refreshAndConnect()
    fun close()
}

fun interface SynthMidiInputFactory {
    fun create(
        context: Context,
        onEvent: (MidiControlEvent) -> Unit,
        onStatus: (String, String?, List<MidiDeviceSummary>, List<UsbDeviceSummary>) -> Unit,
        onDiagnostics: (MidiInputDiagnostics) -> Unit
    ): SynthMidiInput
}

class AndroidSynthMidiInputFactory : SynthMidiInputFactory {
    override fun create(
        context: Context,
        onEvent: (MidiControlEvent) -> Unit,
        onStatus: (String, String?, List<MidiDeviceSummary>, List<UsbDeviceSummary>) -> Unit,
        onDiagnostics: (MidiInputDiagnostics) -> Unit
    ): SynthMidiInput {
        return MidiControllerInput(
            context = context,
            onEvent = onEvent,
            onStatus = onStatus,
            onDiagnostics = onDiagnostics
        )
    }
}
