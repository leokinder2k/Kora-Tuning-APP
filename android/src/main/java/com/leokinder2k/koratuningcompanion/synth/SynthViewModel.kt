package com.leokinder2k.koratuningcompanion.synth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SynthViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = LowLatencySynthEngine(application)
    private val midiInput = MidiControllerInput(
        context = application,
        onEvent = ::handleMidiEvent,
        onStatus = { status, connectedName ->
            _uiState.update {
                it.copy(
                    midiStatus = status,
                    connectedMidiDevice = connectedName
                )
            }
        }
    )

    private val _uiState = MutableStateFlow(
        SynthUiState(
            midiStatus = "Connect A-49 by USB or Bluetooth MIDI",
            availableMidiDevices = emptyList()
        )
    )
    val uiState: StateFlow<SynthUiState> = _uiState

    init {
        start()
    }

    fun start() {
        engine.start()
        midiInput.startAutoConnect()
        _uiState.update {
            it.copy(
                audioRunning = engine.isRunning,
                availableMidiDevices = midiInput.availableDevices()
            )
        }
    }

    fun refreshMidi() {
        midiInput.refreshAndConnect()
        _uiState.update { it.copy(availableMidiDevices = midiInput.availableDevices()) }
    }

    fun setVolume(volume: Float) {
        engine.setMasterVolume(volume)
        _uiState.update { it.copy(volume = engine.masterVolume) }
    }

    fun setBassSplit(enabled: Boolean) {
        engine.setBassSplitEnabled(enabled)
        _uiState.update { it.copy(bassSplitEnabled = enabled) }
    }

    fun setPadLayer(enabled: Boolean) {
        engine.setPadLayerEnabled(enabled)
        _uiState.update { it.copy(padLayerEnabled = enabled) }
    }

    fun setSplitNote(note: Int) {
        engine.setSplitNote(note)
        _uiState.update { it.copy(splitNote = engine.splitNote) }
    }

    fun setSustain(enabled: Boolean) {
        engine.setSustain(enabled)
        _uiState.update { it.copy(sustainEnabled = enabled) }
    }

    fun setOctaveShift(shift: Int) {
        _uiState.update { it.copy(octaveShift = shift.coerceIn(-2, 2)) }
    }

    fun noteOn(note: Int, velocity: Float) {
        start()
        val shifted = note + (_uiState.value.octaveShift * 12)
        engine.noteOn(shifted, velocity)
        _uiState.update { it.copy(lastNote = midiNoteName(shifted), lastVelocity = velocity) }
    }

    fun noteOff(note: Int) {
        val shifted = note + (_uiState.value.octaveShift * 12)
        engine.noteOff(shifted)
    }

    fun playPad(rootNote: Int, quality: PadQuality) {
        val shiftedRoot = rootNote + (_uiState.value.octaveShift * 12)
        engine.playMomentaryChord(quality.notesFrom(shiftedRoot), velocity = 0.76f)
        _uiState.update { it.copy(lastNote = "${midiNoteName(shiftedRoot)} ${quality.label}", lastVelocity = 0.76f) }
    }

    fun allNotesOff() {
        engine.panic()
        engine.setSustain(false)
        _uiState.update { it.copy(sustainEnabled = false) }
    }

    fun loadSoundFont(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(soundFontStatus = "Loading SoundFont...") }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "SoundFont"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not open SoundFont")
                    engine.loadSoundFont(name, bytes).getOrThrow()
                }
            }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { name ->
                        state.copy(
                            soundFontName = name,
                            soundFontStatus = "Loaded $name"
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            soundFontStatus = error.localizedMessage ?: "SoundFont failed to load"
                        )
                    }
                )
            }
        }
    }

    fun useBuiltInSound() {
        engine.useBuiltInSound()
        _uiState.update {
            it.copy(
                soundFontName = null,
                soundFontStatus = "Built-in low-latency piano"
            )
        }
    }

    override fun onCleared() {
        midiInput.close()
        engine.stop()
        super.onCleared()
    }

    private fun handleMidiEvent(event: MidiControlEvent) {
        when (event) {
            is MidiControlEvent.NoteOn -> {
                engine.noteOn(event.note, event.velocity)
                _uiState.update {
                    it.copy(
                        lastNote = midiNoteName(event.note),
                        lastVelocity = event.velocity
                    )
                }
            }
            is MidiControlEvent.NoteOff -> engine.noteOff(event.note)
            is MidiControlEvent.Sustain -> {
                engine.setSustain(event.enabled)
                _uiState.update { it.copy(sustainEnabled = event.enabled) }
            }
        }
    }
}

data class SynthUiState(
    val audioRunning: Boolean = false,
    val midiStatus: String,
    val connectedMidiDevice: String? = null,
    val availableMidiDevices: List<MidiDeviceSummary>,
    val soundFontName: String? = null,
    val soundFontStatus: String = "Built-in low-latency piano",
    val volume: Float = 0.74f,
    val bassSplitEnabled: Boolean = false,
    val padLayerEnabled: Boolean = false,
    val sustainEnabled: Boolean = false,
    val splitNote: Int = 48,
    val octaveShift: Int = 0,
    val lastNote: String = "--",
    val lastVelocity: Float = 0f
)

enum class PadQuality(val label: String, private val intervals: List<Int>) {
    Major("maj", listOf(0, 4, 7, 12)),
    Minor("min", listOf(0, 3, 7, 12)),
    Sus("sus", listOf(0, 5, 7, 12)),
    Seven("7", listOf(0, 4, 7, 10))
    ;

    fun notesFrom(root: Int): List<Int> = intervals.map { (root + it).coerceIn(0, 127) }
}

fun midiNoteName(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val clamped = note.coerceIn(0, 127)
    return "${names[clamped % 12]}${(clamped / 12) - 1}"
}
