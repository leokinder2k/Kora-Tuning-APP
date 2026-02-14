package com.leokinder2k.koratuningcompanion.scaleengine.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.InstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScaleCalculationUiState(
    val rootNote: NoteName,
    val scaleType: ScaleType,
    val profileStatus: String,
    val result: ScaleCalculationResult
)

class ScaleCalculationViewModel(
    private val repository: InstrumentConfigRepository,
    private val engine: ScaleCalculationEngine
) : ViewModel() {

    private var currentProfile = defaultInstrumentProfile()
    private var currentRootNote = DEFAULT_ROOT_NOTE
    private var currentScaleType = DEFAULT_SCALE_TYPE

    private val _uiState = MutableStateFlow(
        buildUiState(
            profile = currentProfile,
            rootNote = currentRootNote,
            scaleType = currentScaleType,
            profileStatus = "No saved profile found. Using starter 21-string profile."
        )
    )
    val uiState: StateFlow<ScaleCalculationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                if (profile != null) {
                    currentProfile = profile
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        rootNote = currentRootNote,
                        scaleType = currentScaleType,
                        profileStatus = "Using saved ${profile.stringCount}-string profile from Instrument Configuration."
                    )
                } else {
                    currentProfile = defaultInstrumentProfile()
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        rootNote = currentRootNote,
                        scaleType = currentScaleType,
                        profileStatus = "No saved profile found. Using starter 21-string profile."
                    )
                }
            }
        }
    }

    fun onRootNoteSelected(rootNote: NoteName) {
        currentRootNote = rootNote
        _uiState.value = buildUiState(
            profile = currentProfile,
            rootNote = currentRootNote,
            scaleType = currentScaleType,
            profileStatus = _uiState.value.profileStatus
        )
    }

    fun onScaleTypeSelected(scaleType: ScaleType) {
        currentScaleType = scaleType
        _uiState.value = buildUiState(
            profile = currentProfile,
            rootNote = currentRootNote,
            scaleType = currentScaleType,
            profileStatus = _uiState.value.profileStatus
        )
    }

    private fun buildUiState(
        profile: InstrumentProfile,
        rootNote: NoteName,
        scaleType: ScaleType,
        profileStatus: String
    ): ScaleCalculationUiState {
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                rootNote = rootNote,
                scaleType = scaleType
            )
        )
        return ScaleCalculationUiState(
            rootNote = rootNote,
            scaleType = scaleType,
            profileStatus = profileStatus,
            result = result
        )
    }

    companion object {
        private val DEFAULT_ROOT_NOTE = NoteName.E
        private val DEFAULT_SCALE_TYPE = ScaleType.MAJOR

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScaleCalculationViewModel(
                    repository = DataStoreInstrumentConfigRepository.create(context),
                    engine = ScaleCalculationEngine()
                )
            }
        }

        private fun defaultInstrumentProfile(): InstrumentProfile {
            return InstrumentProfile(
                stringCount = 21,
                openPitches = StarterInstrumentProfiles.openPitches(21)
            )
        }
    }
}

