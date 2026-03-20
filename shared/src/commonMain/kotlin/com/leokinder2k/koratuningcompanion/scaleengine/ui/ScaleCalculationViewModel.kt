package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.InstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScaleCalculationUiState(
    val rootNote: NoteName,
    val scaleType: ScaleType,
    val scaleRootReference: ScaleRootReference,
    val isChromatic: Boolean,
    val allowRight1: Boolean,
    val profileStatus: String,
    val result: ScaleCalculationResult
)

class ScaleCalculationViewModel(
    private val repository: InstrumentConfigRepository = DataStoreInstrumentConfigRepository(),
    private val engine: ScaleCalculationEngine = ScaleCalculationEngine()
) : ViewModel() {

    private var currentProfile = defaultInstrumentProfile()
    private var currentScaleType = DEFAULT_SCALE_TYPE
    private var currentScaleRootNote: NoteName? = null  // null = follow instrument key
    private var currentScaleRootReference = DEFAULT_SCALE_ROOT_REFERENCE

    private val _uiState = MutableStateFlow(
        buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootNote = currentProfile.rootNote,
            scaleRootReference = currentScaleRootReference,
            profileStatus = "No saved profile found. Using starter ${DEFAULT_PROFILE_STRING_COUNT}-string profile."
        )
    )
    val uiState: StateFlow<ScaleCalculationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                if (profile != null) {
                    currentProfile = profile
                    if (currentScaleRootNote == null) currentScaleRootNote = profile.rootNote
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        scaleType = currentScaleType,
                        scaleRootNote = currentScaleRootNote ?: profile.rootNote,
                        scaleRootReference = currentScaleRootReference,
                        profileStatus = "Using saved ${profile.stringCount}-string profile from Instrument Configuration."
                    )
                } else {
                    currentProfile = defaultInstrumentProfile()
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        scaleType = currentScaleType,
                        scaleRootNote = currentScaleRootNote ?: currentProfile.rootNote,
                        scaleRootReference = currentScaleRootReference,
                        profileStatus = "No saved profile found. Using starter ${DEFAULT_PROFILE_STRING_COUNT}-string profile."
                    )
                }
            }
        }
    }

    fun onScaleRootNoteSelected(note: NoteName) {
        currentScaleRootNote = note
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootNote = note,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    fun onScaleTypeSelected(scaleType: ScaleType) {
        currentScaleType = scaleType
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootNote = currentScaleRootNote ?: currentProfile.rootNote,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    fun onScaleRootReferenceSelected(reference: ScaleRootReference) {
        currentScaleRootReference = reference
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootNote = currentScaleRootNote ?: currentProfile.rootNote,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    private fun buildUiState(
        profile: InstrumentProfile,
        scaleType: ScaleType,
        scaleRootNote: NoteName,
        scaleRootReference: ScaleRootReference,
        profileStatus: String
    ): ScaleCalculationUiState {
        val isChromatic = profile.tuningMode == KoraTuningMode.PEG_TUNING
        val allowRight1 = profile.stringCount >= 22
        val effectiveReference = if (!allowRight1 && scaleRootReference == ScaleRootReference.RIGHT_1) {
            currentScaleRootReference = ScaleRootReference.LEFT_1
            ScaleRootReference.LEFT_1
        } else {
            scaleRootReference
        }
        val result = engine.calculate(
            ScaleCalculationRequest(
                instrumentProfile = profile,
                scaleType = scaleType,
                rootNote = scaleRootNote,
                scaleRootReference = effectiveReference
            )
        )
        return ScaleCalculationUiState(
            rootNote = scaleRootNote,
            scaleType = scaleType,
            scaleRootReference = effectiveReference,
            isChromatic = isChromatic,
            allowRight1 = allowRight1,
            profileStatus = profileStatus,
            result = result
        )
    }

    companion object {
        private val DEFAULT_SCALE_TYPE = ScaleType.MAJOR
        private val DEFAULT_SCALE_ROOT_REFERENCE = ScaleRootReference.LEFT_1
        private const val DEFAULT_PROFILE_STRING_COUNT = 21

        private fun defaultInstrumentProfile(): InstrumentProfile {
            return InstrumentProfile(
                stringCount = DEFAULT_PROFILE_STRING_COUNT,
                openPitches = StarterInstrumentProfiles.openPitches(DEFAULT_PROFILE_STRING_COUNT)
            )
        }
    }
}
