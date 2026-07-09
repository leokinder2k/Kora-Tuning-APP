package com.leokinder2k.koratuningcompanion.synth

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

class SynthViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = LowLatencySynthEngine(application)
    private val recorder = MidiLoopRecorder()
    private val recorderLock = Any()
    private var loopJob: Job? = null
    private var recordClickJob: Job? = null
    private var recordingSessionId = 0L

    private val midiInput = MidiControllerInput(
        context = application,
        onEvent = ::handleMidiEvent,
        onStatus = { status, connectedName, midiDevices, usbDevices ->
            _uiState.update {
                it.copy(
                    midiStatus = status,
                    connectedMidiDevice = connectedName,
                    availableMidiDevices = midiDevices,
                    visibleUsbDevices = usbDevices
                )
            }
        },
        onDiagnostics = { diagnostics ->
            _uiState.update {
                it.copy(
                    midiByteCount = diagnostics.byteCount,
                    midiEventCount = diagnostics.eventCount,
                    midiReconnectCount = diagnostics.reconnectCount,
                    midiInputMode = diagnostics.mode,
                    midiIdleMs = diagnostics.idleMs
                )
            }
        }
    )

    private val _uiState = MutableStateFlow(
        SynthUiState(
            midiStatus = "Connect A-49 by USB or Bluetooth MIDI",
            availableMidiDevices = emptyList(),
            visibleUsbDevices = emptyList()
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
                availableMidiDevices = midiInput.availableDevices(),
                visibleUsbDevices = midiInput.usbDevices()
            )
        }
    }

    fun refreshMidi() {
        midiInput.refreshAndConnect()
        _uiState.update {
            it.copy(
                availableMidiDevices = midiInput.availableDevices(),
                visibleUsbDevices = midiInput.usbDevices()
            )
        }
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

    fun setOctaveShift(shift: Int) {
        _uiState.update { it.copy(octaveShift = shift.coerceIn(-2, 2)) }
    }

    fun noteOn(note: Int, velocity: Float) {
        start()
        val shifted = note + (_uiState.value.octaveShift * 12)
        engine.noteOn(shifted, velocity)
        recordEvent(RecordedMidiMessage.NoteOn(shifted, velocity))
        _uiState.update { it.copy(lastNote = midiNoteName(shifted), lastVelocity = velocity) }
    }

    fun noteOff(note: Int) {
        val shifted = note + (_uiState.value.octaveShift * 12)
        engine.noteOff(shifted)
        recordEvent(RecordedMidiMessage.NoteOff(shifted))
    }

    fun playPad(rootNote: Int, quality: PadQuality) {
        val shiftedRoot = rootNote + (_uiState.value.octaveShift * 12)
        val notes = quality.notesFrom(shiftedRoot)
        engine.playMomentaryChord(notes, velocity = 0.76f)
        val recordingSession = synchronized(recorderLock) {
            if (recorder.isRecording) recordingSessionId else null
        }
        notes.forEach { note -> recordEvent(RecordedMidiMessage.NoteOn(note, 0.76f)) }
        viewModelScope.launch {
            delay(PadReleaseMs)
            val shouldRecordRelease = synchronized(recorderLock) {
                recorder.isRecording && recordingSession != null && recordingSession == recordingSessionId
            }
            if (shouldRecordRelease) {
                notes.forEach { note -> recordEvent(RecordedMidiMessage.NoteOff(note)) }
            }
        }
        _uiState.update { it.copy(lastNote = "${midiNoteName(shiftedRoot)} ${quality.label}", lastVelocity = 0.76f) }
    }

    fun allNotesOff() {
        engine.panic()
        engine.setSustain(false)
        _uiState.update { it.copy(sustainEnabled = false) }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    fun toggleLoopPlayback() {
        if (_uiState.value.isLooping) {
            stopLoopPlayback()
        } else {
            startLoopPlayback()
        }
    }

    fun setMetronomeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(metronomeEnabled = enabled) }
        restartRecordClick()
    }

    fun setMetronomeBpm(value: Float) {
        val bpm = value.roundToInt().coerceIn(MinBpm, MaxBpm)
        _uiState.update { it.copy(metronomeBpm = bpm) }
        restartRecordClick()
    }

    fun saveRecording(uri: Uri) {
        val bpm = _uiState.value.metronomeBpm
        val bytes = synchronized(recorderLock) {
            if (recorder.events.isEmpty()) null else recorder.toMidiFile(bpm)
        }
        if (bytes == null) {
            _uiState.update { it.copy(recordingStatus = "Record a loop before saving") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(recordingStatus = "Saving MIDI...") }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                    } ?: error("Could not open MIDI file")
                }
            }
            _uiState.update { state ->
                state.copy(
                    recordingStatus = result.fold(
                        onSuccess = { "Saved MIDI loop" },
                        onFailure = { it.localizedMessage ?: "MIDI save failed" }
                    )
                )
            }
        }
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
        recordClickJob?.cancel()
        loopJob?.cancel()
        midiInput.close()
        engine.stop()
        super.onCleared()
    }

    private fun startRecording() {
        stopLoopPlayback()
        allNotesOff()
        synchronized(recorderLock) {
            recordingSessionId += 1
            recorder.start(nowMs())
        }
        _uiState.update {
            it.copy(
                isRecording = true,
                isLooping = false,
                recordedEventCount = 0,
                recordedDurationMs = 0L,
                recordingStatus = "Recording"
            )
        }
        restartRecordClick()
    }

    private fun stopRecording() {
        synchronized(recorderLock) {
            recorder.stop(nowMs(), minimumMs = barMs(_uiState.value.metronomeBpm))
        }
        recordClickJob?.cancel()
        recordClickJob = null
        allNotesOff()
        updateRecordingSummary(statusPrefix = "Recorded")
    }

    private fun startLoopPlayback() {
        if (_uiState.value.isRecording) {
            stopRecording()
        }
        val snapshot = synchronized(recorderLock) {
            recorder.events to recorder.durationMs
        }
        val events = snapshot.first
        val duration = snapshot.second
        if (events.isEmpty() || duration <= 0L) {
            _uiState.update { it.copy(recordingStatus = "Record a loop first") }
            return
        }
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            _uiState.update { it.copy(isLooping = true, recordingStatus = "Looping") }
            while (isActive) {
                playLoopOnce(events, duration)
            }
        }
    }

    private fun stopLoopPlayback() {
        loopJob?.cancel()
        loopJob = null
        allNotesOff()
        _uiState.update {
            it.copy(
                isLooping = false,
                recordingStatus = if (it.recordedEventCount > 0) recordingSummary("Ready", it.recordedEventCount, it.recordedDurationMs) else it.recordingStatus
            )
        }
    }

    private suspend fun playLoopOnce(events: List<RecordedMidiEvent>, durationMs: Long) {
        val startedAt = nowMs()
        var eventIndex = 0
        var beatIndex = 0
        while (coroutineContext.isActive) {
            val elapsed = nowMs() - startedAt
            if (elapsed >= durationMs) break

            val beatMs = MidiLoopRecorder.beatMs(_uiState.value.metronomeBpm)
            val nextEventAt = events.getOrNull(eventIndex)?.atMs ?: Long.MAX_VALUE
            val nextBeatAt = if (_uiState.value.metronomeEnabled) beatIndex * beatMs else Long.MAX_VALUE
            val nextAt = min(min(nextEventAt, nextBeatAt), durationMs)
            delay((nextAt - elapsed).coerceAtLeast(0L))

            val dueAt = nowMs() - startedAt
            while (_uiState.value.metronomeEnabled && beatIndex * beatMs <= dueAt && beatIndex * beatMs < durationMs) {
                engine.playMetronomeClick(accent = beatIndex % MidiLoopRecorder.BeatsPerBar == 0)
                beatIndex += 1
            }
            while (eventIndex < events.size && events[eventIndex].atMs <= dueAt) {
                playRecordedEvent(events[eventIndex].message)
                eventIndex += 1
            }
        }
        allNotesOff()
    }

    private fun playRecordedEvent(message: RecordedMidiMessage) {
        when (message) {
            is RecordedMidiMessage.NoteOn -> {
                engine.noteOn(message.note, message.velocity)
                _uiState.update {
                    it.copy(
                        lastNote = midiNoteName(message.note),
                        lastVelocity = message.velocity
                    )
                }
            }
            is RecordedMidiMessage.NoteOff -> engine.noteOff(message.note)
            is RecordedMidiMessage.Sustain -> {
                engine.setSustain(message.enabled)
                _uiState.update { it.copy(sustainEnabled = message.enabled) }
            }
        }
    }

    private fun restartRecordClick() {
        recordClickJob?.cancel()
        recordClickJob = null
        if (!_uiState.value.isRecording || !_uiState.value.metronomeEnabled) return
        recordClickJob = viewModelScope.launch {
            var beat = 0
            while (isActive && _uiState.value.isRecording && _uiState.value.metronomeEnabled) {
                engine.playMetronomeClick(accent = beat % MidiLoopRecorder.BeatsPerBar == 0)
                beat += 1
                delay(MidiLoopRecorder.beatMs(_uiState.value.metronomeBpm))
            }
        }
    }

    private fun recordEvent(message: RecordedMidiMessage) {
        val summary = synchronized(recorderLock) {
            recorder.record(message, nowMs())
            Triple(recorder.events.size, recorder.durationMs, recorder.isRecording)
        }
        if (summary.third) {
            _uiState.update {
                it.copy(
                    recordedEventCount = summary.first,
                    recordedDurationMs = summary.second,
                    recordingStatus = recordingSummary("Recording", summary.first, summary.second)
                )
            }
        }
    }

    private fun updateRecordingSummary(statusPrefix: String) {
        val summary = synchronized(recorderLock) {
            Triple(recorder.events.size, recorder.durationMs, recorder.isRecording)
        }
        _uiState.update {
            it.copy(
                isRecording = summary.third,
                recordedEventCount = summary.first,
                recordedDurationMs = summary.second,
                recordingStatus = recordingSummary(statusPrefix, summary.first, summary.second)
            )
        }
    }

    private fun handleMidiEvent(event: MidiControlEvent) {
        when (event) {
            is MidiControlEvent.NoteOn -> {
                engine.noteOn(event.note, event.velocity)
                recordEvent(RecordedMidiMessage.NoteOn(event.note, event.velocity))
                _uiState.update {
                    it.copy(
                        lastNote = midiNoteName(event.note),
                        lastVelocity = event.velocity
                    )
                }
            }
            is MidiControlEvent.NoteOff -> {
                engine.noteOff(event.note)
                recordEvent(RecordedMidiMessage.NoteOff(event.note))
            }
            is MidiControlEvent.Sustain -> {
                engine.setSustain(event.enabled)
                recordEvent(RecordedMidiMessage.Sustain(event.enabled))
                _uiState.update { it.copy(sustainEnabled = event.enabled) }
            }
            MidiControlEvent.AllNotesOff -> {
                allNotesOff()
            }
        }
    }

    private fun nowMs(): Long = SystemClock.uptimeMillis()

    private fun barMs(bpm: Int): Long = MidiLoopRecorder.beatMs(bpm) * MidiLoopRecorder.BeatsPerBar

    private fun recordingSummary(prefix: String, eventCount: Int, durationMs: Long): String {
        return "$prefix: $eventCount events • ${durationSeconds(durationMs)}"
    }

    private fun durationSeconds(durationMs: Long): String {
        return "%.1fs".format(durationMs / 1000f)
    }

    private companion object {
        const val MinBpm = 60
        const val MaxBpm = 200
        const val PadReleaseMs = 900L
    }
}

data class SynthUiState(
    val audioRunning: Boolean = false,
    val midiStatus: String,
    val connectedMidiDevice: String? = null,
    val availableMidiDevices: List<MidiDeviceSummary>,
    val visibleUsbDevices: List<UsbDeviceSummary>,
    val soundFontName: String? = null,
    val soundFontStatus: String = "Built-in low-latency piano",
    val volume: Float = 0.74f,
    val bassSplitEnabled: Boolean = false,
    val padLayerEnabled: Boolean = false,
    val sustainEnabled: Boolean = false,
    val splitNote: Int = 48,
    val octaveShift: Int = 0,
    val lastNote: String = "--",
    val lastVelocity: Float = 0f,
    val midiByteCount: Long = 0L,
    val midiEventCount: Long = 0L,
    val midiReconnectCount: Int = 0,
    val midiInputMode: String = "Idle",
    val midiIdleMs: Long? = null,
    val isRecording: Boolean = false,
    val isLooping: Boolean = false,
    val metronomeEnabled: Boolean = true,
    val metronomeBpm: Int = 120,
    val recordedEventCount: Int = 0,
    val recordedDurationMs: Long = 0L,
    val recordingStatus: String = "Recorder ready"
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
