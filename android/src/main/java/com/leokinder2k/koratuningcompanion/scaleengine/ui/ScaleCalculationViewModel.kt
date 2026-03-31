package com.leokinder2k.koratuningcompanion.scaleengine.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.InstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPresets
import com.leokinder2k.koratuningcompanion.scaleengine.ScaleCalculationEngine
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationRequest
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleCalculationResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.StructuredTuningLlmOrchestrator
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.TuningLlmOrchestrator
import com.leokinder2k.koratuningcompanion.scaleengine.orchestration.TuningOrchestrationPlan
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.VersatilityAnalysis
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.VersatilityAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScaleCalculationUiState(
    val instrumentKey: NoteName,
    val rootNote: NoteName,
    val scaleType: ScaleType,
    val scaleRootReference: ScaleRootReference,
    val isChromatic: Boolean,
    val allowRight1: Boolean,
    val profileStatus: String,
    val orchestrationPlan: TuningOrchestrationPlan,
    val versatilityAnalysis: VersatilityAnalysis,
    val result: ScaleCalculationResult
)

class ScaleCalculationViewModel(
    private val appContext: Context,
    private val repository: InstrumentConfigRepository,
    private val engine: ScaleCalculationEngine,
    private val tuningLlmOrchestrator: TuningLlmOrchestrator,
    private val versatilityAnalyzer: VersatilityAnalyzer
) : ViewModel() {

    private var currentProfile = defaultInstrumentProfile()
    private var currentScaleType = DEFAULT_SCALE_TYPE
    private var currentScaleRootNote: NoteName? = null  // null = follow instrument key
    private var currentScaleRootReference = DEFAULT_SCALE_ROOT_REFERENCE

    private val _uiState = MutableStateFlow(
        buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootReference = currentScaleRootReference,
            profileStatus = appContext.getString(
                R.string.scale_profile_status_no_saved,
                DEFAULT_PROFILE_STRING_COUNT
            )
        )
    )
    val uiState: StateFlow<ScaleCalculationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                if (profile != null) {
                    currentProfile = profile
                    if (currentScaleRootNote == profile.rootNote) {
                        currentScaleRootNote = null
                    }
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        scaleType = currentScaleType,
                        scaleRootReference = currentScaleRootReference,
                        profileStatus = appContext.getString(
                            R.string.scale_profile_status_using_saved,
                            profile.stringCount
                        )
                    )
                } else {
                    currentProfile = defaultInstrumentProfile()
                    if (currentScaleRootNote == currentProfile.rootNote) {
                        currentScaleRootNote = null
                    }
                    _uiState.value = buildUiState(
                        profile = currentProfile,
                        scaleType = currentScaleType,
                        scaleRootReference = currentScaleRootReference,
                        profileStatus = appContext.getString(
                            R.string.scale_profile_status_no_saved,
                            DEFAULT_PROFILE_STRING_COUNT
                        )
                    )
                }
            }
        }
    }

    fun onScaleRootNoteSelected(note: NoteName) {
        currentScaleRootNote = note.takeUnless { it == currentProfile.rootNote }
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    fun onScaleTypeSelected(scaleType: ScaleType) {
        currentScaleType = scaleType
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    fun onScaleRootReferenceSelected(reference: ScaleRootReference) {
        currentScaleRootReference = reference
        _uiState.value = buildUiState(
            profile = currentProfile,
            scaleType = currentScaleType,
            scaleRootReference = currentScaleRootReference,
            profileStatus = _uiState.value.profileStatus
        )
    }

    private fun buildUiState(
        profile: InstrumentProfile,
        scaleType: ScaleType,
        scaleRootReference: ScaleRootReference,
        profileStatus: String
    ): ScaleCalculationUiState {
        val scaleRootNote = effectiveScaleRootNote(profile)
        val isChromatic = profile.tuningMode == KoraTuningMode.PEG_TUNING
        val allowRight1 = profile.stringCount >= 21
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
        val orchestrationPlan = tuningLlmOrchestrator.orchestrate(result)
        val versatilityAnalysis = versatilityAnalyzer.analyze(
            stringCount = profile.stringCount,
            instrumentKey = profile.rootNote
        )
        return ScaleCalculationUiState(
            instrumentKey = profile.rootNote,
            rootNote = scaleRootNote,
            scaleType = scaleType,
            scaleRootReference = effectiveReference,
            isChromatic = isChromatic,
            allowRight1 = allowRight1,
            profileStatus = profileStatus,
            orchestrationPlan = orchestrationPlan,
            versatilityAnalysis = versatilityAnalysis,
            result = result
        )
    }

    companion object {
        private val DEFAULT_SCALE_TYPE = ScaleType.MAJOR
        private val DEFAULT_SCALE_ROOT_REFERENCE = ScaleRootReference.LEFT_1
        private const val DEFAULT_PRESET_BASE_ID = "sauta"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val engine = ScaleCalculationEngine()
                ScaleCalculationViewModel(
                    appContext = context.applicationContext,
                    repository = DataStoreInstrumentConfigRepository.create(context),
                    engine = engine,
                    tuningLlmOrchestrator = StructuredTuningLlmOrchestrator(),
                    versatilityAnalyzer = VersatilityAnalyzer(engine)
                )
            }
        }

        private const val DEFAULT_PROFILE_STRING_COUNT = 21

        private fun defaultInstrumentProfile(): InstrumentProfile {
            val defaultPresetId = "${DEFAULT_PRESET_BASE_ID}_$DEFAULT_PROFILE_STRING_COUNT"
            return TraditionalPresets.presetsForStringCount(DEFAULT_PROFILE_STRING_COUNT)
                .firstOrNull { preset -> preset.id == defaultPresetId }
                ?.toInstrumentProfile()
                ?: TraditionalPresets.presetsForStringCount(DEFAULT_PROFILE_STRING_COUNT).firstOrNull()?.toInstrumentProfile()
                ?: InstrumentProfile(
                    stringCount = DEFAULT_PROFILE_STRING_COUNT,
                    openPitches = StarterInstrumentProfiles.openPitches(DEFAULT_PROFILE_STRING_COUNT),
                    rootNote = NoteName.E
                )
        }
    }

    private fun effectiveScaleRootNote(profile: InstrumentProfile): NoteName {
        return currentScaleRootNote ?: profile.rootNote
    }
}

