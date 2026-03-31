package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.InstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPreset
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class InstrumentStringRowUiState(
    val stringNumber: Int,
    val openPitchInput: String,
    val closedPitch: String?,
    val inputError: String?,
    val openIntonationInput: String,
    val closedIntonationInput: String,
    val openIntonationError: String?,
    val closedIntonationError: String?
)

data class InstrumentPresetOptionUiState(
    val id: String,
    val displayName: String
)

data class InstrumentConfigurationUiState(
    val stringCount: Int,
    val tuningMode: KoraTuningMode,
    val rootNote: NoteName,
    val presetOptions: List<InstrumentPresetOptionUiState>,
    val selectedPresetId: String,
    val lowestLeftPitchInput: String,
    val autoCalibrateEnabled: Boolean,
    val rows: List<InstrumentStringRowUiState>,
    val canSave: Boolean,
    val statusMessage: String?,
    val basePitchInputs: List<String>,
    val basePitchErrors: List<String?>,
    val homeLeverPosition: HomeLeverPosition = HomeLeverPosition.OPEN
)

class InstrumentConfigurationViewModel(
    private val appContext: Context,
    private val repository: InstrumentConfigRepository
) : ViewModel() {

    private data class AutoCalibratedInputs(
        val openPitchInputs: List<String>,
        val openIntonationInputs: List<String>,
        val closedIntonationInputs: List<String>,
        val normalizedLowestLeftPitchInput: String
    )

    private data class PresetMatch(
        val presetId: String,
        val mismatchScore: Double
    )

    private var currentBasePitchInputs: List<String> = StarterInstrumentProfiles.openPitchTexts(DEFAULT_STRING_COUNT)
    private var currentHomeLeverPosition: HomeLeverPosition = HomeLeverPosition.OPEN

    private val _uiState = MutableStateFlow(buildDefaultUiState())
    val uiState: StateFlow<InstrumentConfigurationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                profile ?: return@collect

                val l01Index = lowestLeftStringIndex(profile.stringCount)
                val detectedPresetId = detectBestMatchingPreset(profile)?.presetId ?: MANUAL_PRESET_ID
                val lowestLeftPitchInput = profile.openPitches.getOrNull(l01Index)?.asText()
                    ?: DEFAULT_LOWEST_LEFT_PITCH_TEXT

                currentBasePitchInputs = profile.basePitches.map(Pitch::asText)
                currentHomeLeverPosition = profile.homeLeverPosition

                _uiState.value = buildUiState(
                    stringCount = profile.stringCount,
                    tuningMode = profile.tuningMode,
                    rootNote = profile.rootNote,
                    selectedPresetId = detectedPresetId,
                    lowestLeftPitchInput = lowestLeftPitchInput,
                    autoCalibrateEnabled = detectedPresetId != MANUAL_PRESET_ID,
                    openPitchInputs = profile.openPitches.map(Pitch::asText),
                    openIntonationInputs = profile.openIntonationCents.map(Double::toString),
                    closedIntonationInputs = profile.closedIntonationCents.map(Double::toString),
                    basePitchInputs = currentBasePitchInputs,
                    statusMessage = _uiState.value.statusMessage
                )
            }
        }
    }

    fun setCurrentAsBase() {
        val currentState = _uiState.value
        currentBasePitchInputs = currentState.rows.map { it.openPitchInput }
        val nextState = currentState.copy(
            basePitchInputs = currentBasePitchInputs,
            basePitchErrors = buildBasePitchErrors(currentBasePitchInputs),
            statusMessage = appContext.getString(R.string.instrument_config_status_home_tuning_set)
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun restoreToBase() {
        val currentState = _uiState.value
        val validBase = currentBasePitchInputs.mapNotNull(Pitch::parse)
        if (validBase.size != currentState.stringCount) {
            _uiState.update { it.copy(statusMessage = appContext.getString(R.string.instrument_config_status_home_not_set_error)) }
            return
        }
        val l01Index = lowestLeftStringIndex(currentState.stringCount)
        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = MANUAL_PRESET_ID,
            lowestLeftPitchInput = currentBasePitchInputs.getOrNull(l01Index) ?: currentState.lowestLeftPitchInput,
            autoCalibrateEnabled = false,
            openPitchInputs = currentBasePitchInputs,
            openIntonationInputs = currentState.rows.map { it.openIntonationInput },
            closedIntonationInputs = currentState.rows.map { it.closedIntonationInput },
            basePitchInputs = currentBasePitchInputs,
            statusMessage = appContext.getString(R.string.instrument_config_status_restored_to_home)
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onStringCountSelected(stringCount: Int) {
        if (stringCount !in SUPPORTED_STRING_COUNTS) {
            return
        }

        val currentState = _uiState.value
        val currentOpenPitchInputs = currentState.rows.map { row -> row.openPitchInput }
        val nextPresetId = mapPresetIdToStringCount(
            presetId = currentState.selectedPresetId,
            stringCount = stringCount
        )

        val nextState = if (
            currentState.autoCalibrateEnabled &&
            nextPresetId != MANUAL_PRESET_ID
        ) {
            val calibrated = autoCalibrateFromPreset(
                stringCount = stringCount,
                presetId = nextPresetId,
                lowestLeftPitchInput = currentState.lowestLeftPitchInput
            )
            updateLinkedBasePitchInputs(
                currentOpenPitchInputs = currentOpenPitchInputs,
                nextOpenPitchInputs = calibrated.openPitchInputs,
                stringCount = stringCount
            )
            buildUiState(
                stringCount = stringCount,
                tuningMode = currentState.tuningMode,
                rootNote = currentState.rootNote,
                selectedPresetId = nextPresetId,
                lowestLeftPitchInput = calibrated.normalizedLowestLeftPitchInput,
                autoCalibrateEnabled = true,
                openPitchInputs = calibrated.openPitchInputs,
                openIntonationInputs = calibrated.openIntonationInputs,
                closedIntonationInputs = calibrated.closedIntonationInputs,
                basePitchInputs = currentBasePitchInputs,
                statusMessage = null
            )
        } else {
            val existingPitchInputs = resizePitchInputCount(currentOpenPitchInputs, stringCount)
            val existingOpenIntonationInputs = currentState.rows.map { row -> row.openIntonationInput }
            val existingClosedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput }
            updateLinkedBasePitchInputs(
                currentOpenPitchInputs = currentOpenPitchInputs,
                nextOpenPitchInputs = existingPitchInputs,
                stringCount = stringCount
            )
            buildUiState(
                stringCount = stringCount,
                tuningMode = currentState.tuningMode,
                rootNote = currentState.rootNote,
                selectedPresetId = MANUAL_PRESET_ID,
                lowestLeftPitchInput = currentState.lowestLeftPitchInput,
                autoCalibrateEnabled = false,
                openPitchInputs = existingPitchInputs,
                openIntonationInputs = resizeIntonationInputCount(existingOpenIntonationInputs, stringCount),
                closedIntonationInputs = resizeIntonationInputCount(existingClosedIntonationInputs, stringCount),
                basePitchInputs = currentBasePitchInputs,
                statusMessage = null
            )
        }

        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onPresetSelected(presetId: String) {
        val currentState = _uiState.value
        val availableIds = presetOptionsForStringCount(currentState.stringCount)
            .map(InstrumentPresetOptionUiState::id)
            .toSet()
        if (presetId !in availableIds) {
            return
        }

        if (presetId == MANUAL_PRESET_ID) {
            val nextState = buildUiState(
                stringCount = currentState.stringCount,
                tuningMode = currentState.tuningMode,
                rootNote = currentState.rootNote,
                selectedPresetId = MANUAL_PRESET_ID,
                lowestLeftPitchInput = currentState.lowestLeftPitchInput,
                autoCalibrateEnabled = false,
                openPitchInputs = currentState.rows.map { row -> row.openPitchInput },
                openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
                closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
                statusMessage = null
            )
            _uiState.value = nextState
            persistProfileIfValid(nextState)
            return
        }

        val calibrated = autoCalibrateFromPreset(
            stringCount = currentState.stringCount,
            presetId = presetId,
            lowestLeftPitchInput = currentState.lowestLeftPitchInput
        )
        updateLinkedBasePitchInputs(
            currentOpenPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            nextOpenPitchInputs = calibrated.openPitchInputs,
            stringCount = currentState.stringCount
        )

        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = presetId,
            lowestLeftPitchInput = calibrated.normalizedLowestLeftPitchInput,
            autoCalibrateEnabled = true,
            openPitchInputs = calibrated.openPitchInputs,
            openIntonationInputs = calibrated.openIntonationInputs,
            closedIntonationInputs = calibrated.closedIntonationInputs,
            basePitchInputs = currentBasePitchInputs,
            statusMessage = null
        )

        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onLowestLeftPitchSelected(pitchText: String) {
        val currentState = _uiState.value
        if (currentState.selectedPresetId == MANUAL_PRESET_ID) {
            return
        }

        val calibrated = autoCalibrateFromPreset(
            stringCount = currentState.stringCount,
            presetId = currentState.selectedPresetId,
            lowestLeftPitchInput = pitchText
        )
        updateLinkedBasePitchInputs(
            currentOpenPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            nextOpenPitchInputs = calibrated.openPitchInputs,
            stringCount = currentState.stringCount
        )

        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = currentState.selectedPresetId,
            lowestLeftPitchInput = calibrated.normalizedLowestLeftPitchInput,
            autoCalibrateEnabled = true,
            openPitchInputs = calibrated.openPitchInputs,
            openIntonationInputs = calibrated.openIntonationInputs,
            closedIntonationInputs = calibrated.closedIntonationInputs,
            basePitchInputs = currentBasePitchInputs,
            statusMessage = null
        )

        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun loadStarterProfile() {
        val stringCount = _uiState.value.stringCount
        val l01Index = lowestLeftStringIndex(stringCount)
        val openPitchInputs = StarterInstrumentProfiles.openPitchTexts(stringCount)
        updateLinkedBasePitchInputs(
            currentOpenPitchInputs = _uiState.value.rows.map { row -> row.openPitchInput },
            nextOpenPitchInputs = openPitchInputs,
            stringCount = stringCount
        )
        val nextState = buildUiState(
            stringCount = stringCount,
            tuningMode = _uiState.value.tuningMode,
            rootNote = _uiState.value.rootNote,
            selectedPresetId = MANUAL_PRESET_ID,
            lowestLeftPitchInput = openPitchInputs.getOrNull(l01Index) ?: DEFAULT_LOWEST_LEFT_PITCH_TEXT,
            autoCalibrateEnabled = false,
            openPitchInputs = openPitchInputs,
            openIntonationInputs = defaultIntonationInputs(stringCount),
            closedIntonationInputs = defaultIntonationInputs(stringCount),
            basePitchInputs = currentBasePitchInputs,
            statusMessage = appContext.getString(
                R.string.instrument_config_status_loaded_starter_profile,
                stringCount
            )
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onOpenPitchChanged(rowIndex: Int, newValue: String) {
        val currentState = _uiState.value
        if (rowIndex !in currentState.rows.indices) {
            return
        }

        val updatedInputs = currentState.rows.mapIndexed { index, row ->
            if (index == rowIndex) newValue else row.openPitchInput
        }
        updateLinkedBasePitchInputs(
            currentOpenPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            nextOpenPitchInputs = updatedInputs,
            stringCount = currentState.stringCount
        )

        val l01Index = lowestLeftStringIndex(currentState.stringCount)
        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = MANUAL_PRESET_ID,
            lowestLeftPitchInput = if (rowIndex == l01Index) newValue else currentState.lowestLeftPitchInput,
            autoCalibrateEnabled = false,
            openPitchInputs = updatedInputs,
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
            basePitchInputs = currentBasePitchInputs,
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onOpenIntonationChanged(rowIndex: Int, newValue: String) {
        val currentState = _uiState.value
        if (rowIndex !in currentState.rows.indices) {
            return
        }

        val updatedOpenIntonationInputs = currentState.rows.mapIndexed { index, row ->
            if (index == rowIndex) newValue else row.openIntonationInput
        }

        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = currentState.selectedPresetId,
            lowestLeftPitchInput = currentState.lowestLeftPitchInput,
            autoCalibrateEnabled = currentState.autoCalibrateEnabled,
            openPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            openIntonationInputs = updatedOpenIntonationInputs,
            closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onClosedIntonationChanged(rowIndex: Int, newValue: String) {
        val currentState = _uiState.value
        if (rowIndex !in currentState.rows.indices) {
            return
        }

        val updatedClosedIntonationInputs = currentState.rows.mapIndexed { index, row ->
            if (index == rowIndex) newValue else row.closedIntonationInput
        }

        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = currentState.selectedPresetId,
            lowestLeftPitchInput = currentState.lowestLeftPitchInput,
            autoCalibrateEnabled = currentState.autoCalibrateEnabled,
            openPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = updatedClosedIntonationInputs,
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onTuningModeSelected(tuningMode: KoraTuningMode) {
        if (tuningMode == _uiState.value.tuningMode) {
            return
        }
        val currentState = _uiState.value
        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = tuningMode,
            rootNote = currentState.rootNote,
            selectedPresetId = currentState.selectedPresetId,
            lowestLeftPitchInput = currentState.lowestLeftPitchInput,
            autoCalibrateEnabled = currentState.autoCalibrateEnabled,
            openPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onHomeLeverPositionSelected(position: HomeLeverPosition) {
        if (position == _uiState.value.homeLeverPosition) return
        currentHomeLeverPosition = position
        val nextState = _uiState.value.copy(homeLeverPosition = position, statusMessage = null)
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun onRootNoteSelected(rootNote: NoteName) {
        if (rootNote == _uiState.value.rootNote) return
        val currentState = _uiState.value
        val rawDelta = rootNote.semitone - currentState.rootNote.semitone
        val semitones = Math.floorMod(rawDelta, 12).let { if (it > 6) it - 12 else it }
        val transposedPitchInputs = transposePitchInputs(
            inputs = currentState.rows.map { row -> row.openPitchInput },
            semitones = semitones
        )
        updateLinkedBasePitchInputs(
            currentOpenPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            nextOpenPitchInputs = transposedPitchInputs,
            stringCount = currentState.stringCount
        )
        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            rootNote = rootNote,
            selectedPresetId = currentState.selectedPresetId,
            lowestLeftPitchInput = transposePitchInput(currentState.lowestLeftPitchInput, semitones),
            autoCalibrateEnabled = currentState.autoCalibrateEnabled,
            openPitchInputs = transposedPitchInputs,
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
            basePitchInputs = currentBasePitchInputs,
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun saveProfile() {
        val currentState = _uiState.value
        if (!currentState.canSave) {
            _uiState.update { state ->
                state.copy(statusMessage = appContext.getString(R.string.instrument_config_status_enter_valid_before_saving))
            }
            return
        }

        val openPitches = currentState.rows.mapNotNull { row ->
            Pitch.parse(row.openPitchInput)
        }
        if (openPitches.size != currentState.stringCount) {
            _uiState.update { state ->
                state.copy(statusMessage = appContext.getString(R.string.instrument_config_status_unable_save_verify_pitch))
            }
            return
        }

        val openIntonationCents = currentState.rows.mapNotNull { row ->
            parseCentsInput(row.openIntonationInput)
        }
        val closedIntonationCents = currentState.rows.mapNotNull { row ->
            parseCentsInput(row.closedIntonationInput)
        }
        if (openIntonationCents.size != currentState.stringCount ||
            closedIntonationCents.size != currentState.stringCount
        ) {
            _uiState.update { state ->
                state.copy(statusMessage = appContext.getString(R.string.instrument_config_status_unable_save_verify_intonation))
            }
            return
        }

        val basePitches = currentState.basePitchInputs.mapNotNull(Pitch::parse)
            .takeIf { it.size == currentState.stringCount }
            ?: openPitches

        val profile = InstrumentProfile(
            stringCount = currentState.stringCount,
            tuningMode = currentState.tuningMode,
            openPitches = openPitches,
            openIntonationCents = openIntonationCents,
            closedIntonationCents = closedIntonationCents,
            rootNote = currentState.rootNote,
            basePitches = basePitches,
            homeLeverPosition = currentState.homeLeverPosition
        )

        viewModelScope.launch {
            repository.saveInstrumentProfile(profile)
            _uiState.update { state ->
                state.copy(statusMessage = appContext.getString(R.string.instrument_config_status_profile_saved))
            }
        }
    }

    private fun persistProfileIfValid(state: InstrumentConfigurationUiState) {
        if (!state.canSave) {
            return
        }

        val openPitches = state.rows.mapNotNull { row ->
            Pitch.parse(row.openPitchInput)
        }
        val openIntonationCents = state.rows.mapNotNull { row ->
            parseCentsInput(row.openIntonationInput)
        }
        val closedIntonationCents = state.rows.mapNotNull { row ->
            parseCentsInput(row.closedIntonationInput)
        }
        if (openPitches.size != state.stringCount ||
            openIntonationCents.size != state.stringCount ||
            closedIntonationCents.size != state.stringCount
        ) {
            return
        }

        val basePitches = state.basePitchInputs.mapNotNull(Pitch::parse)
            .takeIf { it.size == state.stringCount }
            ?: openPitches

        val profile = InstrumentProfile(
            stringCount = state.stringCount,
            tuningMode = state.tuningMode,
            openPitches = openPitches,
            openIntonationCents = openIntonationCents,
            closedIntonationCents = closedIntonationCents,
            rootNote = state.rootNote,
            basePitches = basePitches,
            homeLeverPosition = state.homeLeverPosition
        )

        viewModelScope.launch {
            repository.saveInstrumentProfile(profile)
        }
    }

    companion object {
        private const val DEFAULT_STRING_COUNT = 21
        private val SUPPORTED_STRING_COUNTS = setOf(19, 21, 22)
        private const val MANUAL_PRESET_ID = "manual"
        private const val DEFAULT_PRESET_BASE_ID = "sauta"
        private const val DEFAULT_LOWEST_LEFT_PITCH_TEXT = "F2"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InstrumentConfigurationViewModel(
                    appContext = context.applicationContext,
                    repository = DataStoreInstrumentConfigRepository.create(context)
                )
            }
        }

        private fun resizePitchInputCount(inputs: List<String>, stringCount: Int): List<String> {
            val starterValues = StarterInstrumentProfiles.openPitchTexts(stringCount)
            val resized = inputs.take(stringCount).toMutableList()
            while (resized.size < stringCount) {
                resized += starterValues[resized.size]
            }
            return resized
        }

        private fun resizeIntonationInputCount(inputs: List<String>, stringCount: Int): List<String> {
            val resized = inputs.take(stringCount).toMutableList()
            while (resized.size < stringCount) {
                resized += DEFAULT_INTONATION_INPUT
            }
            return resized
        }

        private fun defaultIntonationInputs(stringCount: Int): List<String> {
            return List(stringCount) { DEFAULT_INTONATION_INPUT }
        }

        private fun parseCentsInput(value: String): Double? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                return 0.0
            }
            return trimmed.toDoubleOrNull()
        }

        private const val DEFAULT_INTONATION_INPUT = "0.0"
    }

    private fun buildUiState(
        stringCount: Int,
        tuningMode: KoraTuningMode,
        rootNote: NoteName,
        selectedPresetId: String,
        lowestLeftPitchInput: String,
        autoCalibrateEnabled: Boolean,
        openPitchInputs: List<String>,
        openIntonationInputs: List<String>,
        closedIntonationInputs: List<String>,
        statusMessage: String?,
        basePitchInputs: List<String>? = null
    ): InstrumentConfigurationUiState {
        val resizedPitchInputs = resizePitchInputCount(openPitchInputs, stringCount)
        val resizedOpenIntonationInputs = resizeIntonationInputCount(openIntonationInputs, stringCount)
        val resizedClosedIntonationInputs = resizeIntonationInputCount(closedIntonationInputs, stringCount)

        val presetOptions = presetOptionsForStringCount(stringCount)
        val availablePresetIds = presetOptions.map(InstrumentPresetOptionUiState::id).toSet()
        val resolvedPresetId = when {
            selectedPresetId in availablePresetIds -> selectedPresetId
            else -> defaultPresetIdFor(stringCount)
        }
        val resolvedAutoCalibrateEnabled = autoCalibrateEnabled && resolvedPresetId != MANUAL_PRESET_ID

        val rows = resizedPitchInputs.mapIndexed { index, input ->
            val parsedPitch = Pitch.parse(input)
            val inputError = when {
                input.isBlank() -> null
                parsedPitch == null -> appContext.getString(R.string.instrument_config_error_pitch_format)
                else -> null
            }
            val openIntonationInput = resizedOpenIntonationInputs[index]
            val closedIntonationInput = resizedClosedIntonationInputs[index]
            val openIntonationValue = parseCentsInput(openIntonationInput)
            val closedIntonationValue = parseCentsInput(closedIntonationInput)
            val openIntonationError = if (openIntonationValue == null) {
                appContext.getString(R.string.instrument_config_error_cents_format)
            } else {
                null
            }
            val closedIntonationError = if (closedIntonationValue == null) {
                appContext.getString(R.string.instrument_config_error_cents_format)
            } else {
                null
            }

            InstrumentStringRowUiState(
                stringNumber = index + 1,
                openPitchInput = input,
                closedPitch = parsedPitch?.plusSemitones(1)?.asText(),
                inputError = inputError,
                openIntonationInput = openIntonationInput,
                closedIntonationInput = closedIntonationInput,
                openIntonationError = openIntonationError,
                closedIntonationError = closedIntonationError
            )
        }

        val canSave = rows.all { row ->
            Pitch.parse(row.openPitchInput) != null &&
                parseCentsInput(row.openIntonationInput) != null &&
                parseCentsInput(row.closedIntonationInput) != null
        }

        val resolvedBase = resizePitchInputCount(basePitchInputs ?: currentBasePitchInputs, stringCount)

        return InstrumentConfigurationUiState(
            stringCount = stringCount,
            tuningMode = tuningMode,
            rootNote = rootNote,
            presetOptions = presetOptions,
            selectedPresetId = resolvedPresetId,
            lowestLeftPitchInput = lowestLeftPitchInput,
            autoCalibrateEnabled = resolvedAutoCalibrateEnabled,
            rows = rows,
            canSave = canSave,
            statusMessage = statusMessage,
            basePitchInputs = resolvedBase,
            basePitchErrors = buildBasePitchErrors(resolvedBase),
            homeLeverPosition = currentHomeLeverPosition
        )
    }

    private fun buildBasePitchErrors(inputs: List<String>): List<String?> =
        inputs.map { input ->
            if (input.isNotBlank() && Pitch.parse(input) == null) {
                appContext.getString(R.string.instrument_config_error_pitch_format)
            } else null
        }

    private fun buildDefaultUiState(): InstrumentConfigurationUiState {
        val stringCount = DEFAULT_STRING_COUNT
        val presetId = defaultPresetIdFor(stringCount)
        val calibrated = autoCalibrateFromPreset(
            stringCount = stringCount,
            presetId = presetId,
            lowestLeftPitchInput = DEFAULT_LOWEST_LEFT_PITCH_TEXT
        )
        currentBasePitchInputs = calibrated.openPitchInputs
        currentHomeLeverPosition = HomeLeverPosition.OPEN
        return buildUiState(
            stringCount = stringCount,
            tuningMode = KoraTuningMode.LEVERED,
            rootNote = NoteName.F,
            selectedPresetId = presetId,
            lowestLeftPitchInput = calibrated.normalizedLowestLeftPitchInput,
            autoCalibrateEnabled = true,
            openPitchInputs = calibrated.openPitchInputs,
            openIntonationInputs = calibrated.openIntonationInputs,
            closedIntonationInputs = calibrated.closedIntonationInputs,
            basePitchInputs = currentBasePitchInputs,
            statusMessage = null
        )
    }

    private fun presetOptionsForStringCount(stringCount: Int): List<InstrumentPresetOptionUiState> {
        val manual = InstrumentPresetOptionUiState(
            id = MANUAL_PRESET_ID,
            displayName = appContext.getString(R.string.instrument_preset_manual)
        )
        val builtIn = TraditionalPresets.presetsForStringCount(stringCount).map { preset ->
            InstrumentPresetOptionUiState(
                id = preset.id,
                displayName = preset.displayName
            )
        }
        return listOf(manual) + builtIn
    }

    private fun defaultPresetIdFor(stringCount: Int): String {
        return "${DEFAULT_PRESET_BASE_ID}_$stringCount"
    }

    private fun mapPresetIdToStringCount(presetId: String, stringCount: Int): String {
        if (presetId == MANUAL_PRESET_ID) {
            return presetId
        }
        val base = presetId.substringBeforeLast("_", missingDelimiterValue = presetId)
        val candidate = "${base}_$stringCount"
        val builtInIds = TraditionalPresets.presetsForStringCount(stringCount)
            .map(TraditionalPreset::id)
            .toSet()
        return if (candidate in builtInIds) {
            candidate
        } else {
            defaultPresetIdFor(stringCount)
        }
    }

    private fun autoCalibrateFromPreset(
        stringCount: Int,
        presetId: String,
        lowestLeftPitchInput: String
    ): AutoCalibratedInputs {
        val presets = TraditionalPresets.presetsForStringCount(stringCount)
        val preset = presets.firstOrNull { item -> item.id == presetId }
            ?: presets.firstOrNull { item -> item.id == defaultPresetIdFor(stringCount) }
            ?: presets.first()

        val l01Index = lowestLeftStringIndex(stringCount)
        val baselineLowestLeft = preset.openPitches.getOrNull(l01Index) ?: preset.openPitches.first()
        val selectedLowestLeft = Pitch.parse(lowestLeftPitchInput) ?: baselineLowestLeft

        val deltaSemitones = totalSemitones(selectedLowestLeft) - totalSemitones(baselineLowestLeft)
        val transposedOpen = preset.openPitches.map { pitch -> pitch.plusSemitones(deltaSemitones) }

        return AutoCalibratedInputs(
            openPitchInputs = transposedOpen.map(Pitch::asText),
            openIntonationInputs = preset.openIntonationCents.map(Double::toString),
            closedIntonationInputs = preset.closedIntonationCents.map(Double::toString),
            normalizedLowestLeftPitchInput = selectedLowestLeft.asText()
        )
    }

    private fun detectBestMatchingPreset(profile: InstrumentProfile): PresetMatch? {
        val stringCount = profile.stringCount
        if (profile.openPitches.size != stringCount ||
            profile.openIntonationCents.size != stringCount ||
            profile.closedIntonationCents.size != stringCount
        ) {
            return null
        }

        val presets = TraditionalPresets.presetsForStringCount(stringCount)
        val l01Index = lowestLeftStringIndex(stringCount)
        val profileLowestLeft = profile.openPitches.getOrNull(l01Index) ?: return null

        val candidates = presets.mapNotNull { preset ->
            val presetLowestLeft = preset.openPitches.getOrNull(l01Index) ?: return@mapNotNull null
            val deltaSemitones = totalSemitones(profileLowestLeft) - totalSemitones(presetLowestLeft)
            val pitchMatches = preset.openPitches.indices.all { index ->
                val expected = preset.openPitches[index].plusSemitones(deltaSemitones)
                totalSemitones(profile.openPitches[index]) == totalSemitones(expected)
            }
            if (!pitchMatches) {
                return@mapNotNull null
            }

            val mismatchScore = preset.openPitches.indices.sumOf { index ->
                abs(profile.openIntonationCents[index] - preset.openIntonationCents[index]) +
                    abs(profile.closedIntonationCents[index] - preset.closedIntonationCents[index])
            }

            PresetMatch(presetId = preset.id, mismatchScore = mismatchScore)
        }

        return candidates.minByOrNull { match -> match.mismatchScore }
    }

    private fun lowestLeftStringIndex(stringCount: Int): Int {
        val stringNumber = KoraStringLayout.leftOrder(stringCount).firstOrNull() ?: 1
        return (stringNumber - 1).coerceIn(0, (stringCount - 1).coerceAtLeast(0))
    }

    private fun totalSemitones(pitch: Pitch): Int {
        return pitch.octave * 12 + pitch.note.semitone
    }

    private fun updateLinkedBasePitchInputs(
        currentOpenPitchInputs: List<String>,
        nextOpenPitchInputs: List<String>,
        stringCount: Int
    ) {
        currentBasePitchInputs = if (baseTracksCurrentWorkingTuning(currentOpenPitchInputs, stringCount)) {
            resizePitchInputCount(nextOpenPitchInputs, stringCount)
        } else {
            resizePitchInputCount(currentBasePitchInputs, stringCount)
        }
    }

    private fun baseTracksCurrentWorkingTuning(
        currentOpenPitchInputs: List<String>,
        stringCount: Int
    ): Boolean {
        val normalizedBase = resizePitchInputCount(currentBasePitchInputs, stringCount)
        val normalizedOpen = resizePitchInputCount(currentOpenPitchInputs, stringCount)
        return normalizedBase.indices.all { index ->
            normalizedPitchInput(normalizedBase[index]) == normalizedPitchInput(normalizedOpen[index])
        }
    }

    private fun transposePitchInputs(inputs: List<String>, semitones: Int): List<String> {
        if (semitones == 0) return inputs
        return inputs.map { input -> transposePitchInput(input, semitones) }
    }

    private fun transposePitchInput(input: String, semitones: Int): String {
        if (semitones == 0) return input
        return Pitch.parse(input)?.plusSemitones(semitones)?.asText() ?: input
    }

    private fun normalizedPitchInput(input: String): String {
        return Pitch.parse(input)?.asText() ?: input.trim()
    }
}

