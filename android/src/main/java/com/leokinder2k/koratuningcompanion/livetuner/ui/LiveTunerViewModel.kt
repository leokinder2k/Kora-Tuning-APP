package com.leokinder2k.koratuningcompanion.livetuner.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.livetuner.LiveTunerEngine
import com.leokinder2k.koratuningcompanion.livetuner.audio.AudioRecordFrameSource
import com.leokinder2k.koratuningcompanion.livetuner.detection.YinPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LiveTunerPerformanceMode {
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
    private val appContext: Context,
    private val engineFactory: (LiveTunerPerformanceMode) -> LiveTunerEngine
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
                errorMessage = if (granted) null else appContext.getString(R.string.live_tuner_error_mic_permission_required)
            )
        }
        if (!granted) {
            stopListening()
        }
    }

    fun onPerformanceModeSelected(mode: LiveTunerPerformanceMode) {
        if (mode == _uiState.value.performanceMode) {
            return
        }

        val wasListening = _uiState.value.isListening
        stopListening()
        _uiState.update { state ->
            state.copy(
                performanceMode = mode,
                errorMessage = null
            )
        }

        if (wasListening) {
            startListening()
        }
    }

    fun startListening() {
        val state = _uiState.value
        if (!state.hasAudioPermission) {
            _uiState.update { current ->
                current.copy(errorMessage = appContext.getString(R.string.live_tuner_error_grant_permission_to_start))
            }
            return
        }
        if (captureJob != null) {
            return
        }
        val engine = engineFactory(state.performanceMode)

        captureJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isListening = true,
                    errorMessage = null
                )
            }
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
                        errorMessage = error.message ?: appContext.getString(R.string.live_tuner_error_stream_failed)
                    )
                }
            }
        }
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        _uiState.update { state ->
            state.copy(isListening = false)
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LiveTunerViewModel(
                    appContext = context.applicationContext,
                    engineFactory = { _ ->
                        // 8192 samples @ 44100 Hz ≈ 186 ms/frame (~200 ms target)
                        // 3 stable frames ≈ 558 ms lock-on; EMA α=0.55 ≈ 200 ms time constant
                        LiveTunerEngine(
                            frameSource = AudioRecordFrameSource(),
                            pitchDetector = YinPitchDetector(
                                rmsGate = 0.001,               // ≈ -60 dBFS
                                yinThreshold = 0.15,
                                lowStringMaxHz = 200.0,
                                lowStringMinConfidence = 0.65,
                                highStringMinConfidence = 0.48,
                            ),
                            frameSize = 8192,
                            minStableFrames = 3,
                            stabilityToleranceCents = 20.0,
                            smoothingAlpha = 0.55,
                            historyWindowFrames = 5,
                        )
                    }
                )
            }
        }
    }
}

