package com.leokinder2k.koratuningcompanion.livetuner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leokinder2k.koratuningcompanion.livetuner.LiveTunerEngine
import com.leokinder2k.koratuningcompanion.livetuner.audio.createAudioFrameSource
import com.leokinder2k.koratuningcompanion.livetuner.detection.AutocorrelationPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LiveTunerPerformanceMode {
    REALTIME,
    PRECISION
}

data class LiveTunerUiState(
    val hasAudioPermission: Boolean,
    val performanceMode: LiveTunerPerformanceMode,
    val isListening: Boolean,
    val detectedFrequencyHz: Double?,
    val confidence: Double,
    val rms: Double,
    val errorMessage: String?
)

class LiveTunerViewModel(
    private val engineFactory: (LiveTunerPerformanceMode) -> LiveTunerEngine = ::defaultEngineFactory
) : ViewModel() {

    private var captureJob: Job? = null

    private val _uiState = MutableStateFlow(
        LiveTunerUiState(
            hasAudioPermission = false,
            performanceMode = LiveTunerPerformanceMode.PRECISION,
            isListening = false,
            detectedFrequencyHz = null,
            confidence = 0.0,
            rms = 0.0,
            errorMessage = null
        )
    )
    val uiState: StateFlow<LiveTunerUiState> = _uiState.asStateFlow()

    fun onAudioPermissionChanged(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                hasAudioPermission = granted,
                errorMessage = if (granted) null else "Microphone permission required."
            )
        }
        if (!granted) stopListening()
    }

    fun onPerformanceModeSelected(mode: LiveTunerPerformanceMode) {
        if (mode == _uiState.value.performanceMode) return

        val wasListening = _uiState.value.isListening
        stopListening()
        _uiState.update { state -> state.copy(performanceMode = mode, errorMessage = null) }
        if (wasListening) startListening()
    }

    fun startListening() {
        val state = _uiState.value
        if (!state.hasAudioPermission) {
            _uiState.update { it.copy(errorMessage = "Grant microphone permission to start tuning.") }
            return
        }
        if (captureJob != null) return

        val engine = engineFactory(state.performanceMode)

        captureJob = viewModelScope.launch {
            _uiState.update { it.copy(isListening = true, errorMessage = null) }
            runCatching {
                engine.readings().collect { reading ->
                    _uiState.update { current ->
                        current.copy(
                            detectedFrequencyHz = reading.frequencyHz,
                            confidence = reading.confidence,
                            rms = reading.rms,
                            errorMessage = null
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isListening = false,
                        errorMessage = error.message ?: "Live tuner stream failed."
                    )
                }
            }
        }
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        _uiState.update { it.copy(isListening = false) }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}

private fun defaultEngineFactory(mode: LiveTunerPerformanceMode): LiveTunerEngine {
    return when (mode) {
        LiveTunerPerformanceMode.REALTIME -> LiveTunerEngine(
            frameSource = createAudioFrameSource(),
            pitchDetector = AutocorrelationPitchDetector(
                correlationThreshold = 0.50,
                refinementSearchHz = 8.0,
                refinementIterations = 10
            ),
            frameSize = 4096
        )
        LiveTunerPerformanceMode.PRECISION -> LiveTunerEngine(
            frameSource = createAudioFrameSource(),
            pitchDetector = AutocorrelationPitchDetector(
                correlationThreshold = 0.55,
                refinementSearchHz = 4.0,
                refinementIterations = 22
            ),
            frameSize = 16384
        )
    }
}
