package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.InstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.StarterInstrumentProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

data class InstrumentConfigurationUiState(
    val stringCount: Int,
    val rows: List<InstrumentStringRowUiState>,
    val canSave: Boolean,
    val statusMessage: String?
)

class InstrumentConfigurationViewModel(
    private val repository: InstrumentConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        buildUiState(
            stringCount = DEFAULT_STRING_COUNT,
            openPitchInputs = StarterInstrumentProfiles.openPitchTexts(DEFAULT_STRING_COUNT),
            openIntonationInputs = defaultIntonationInputs(DEFAULT_STRING_COUNT),
            closedIntonationInputs = defaultIntonationInputs(DEFAULT_STRING_COUNT),
            statusMessage = null
        )
    )
    val uiState: StateFlow<InstrumentConfigurationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                profile ?: return@collect
                _uiState.value = buildUiState(
                    stringCount = profile.stringCount,
                    openPitchInputs = profile.openPitches.map(Pitch::asText),
                    openIntonationInputs = profile.openIntonationCents.map(Double::toString),
                    closedIntonationInputs = profile.closedIntonationCents.map(Double::toString),
                    statusMessage = _uiState.value.statusMessage
                )
            }
        }
    }

    fun onStringCountSelected(stringCount: Int) {
        if (stringCount !in SUPPORTED_STRING_COUNTS) {
            return
        }
        val existingPitchInputs = _uiState.value.rows.map { row -> row.openPitchInput }
        val existingOpenIntonationInputs = _uiState.value.rows.map { row -> row.openIntonationInput }
        val existingClosedIntonationInputs = _uiState.value.rows.map { row -> row.closedIntonationInput }
        val nextState = buildUiState(
            stringCount = stringCount,
            openPitchInputs = resizePitchInputCount(existingPitchInputs, stringCount),
            openIntonationInputs = resizeIntonationInputCount(existingOpenIntonationInputs, stringCount),
            closedIntonationInputs = resizeIntonationInputCount(existingClosedIntonationInputs, stringCount),
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun loadStarterProfile() {
        val stringCount = _uiState.value.stringCount
        val nextState = buildUiState(
            stringCount = stringCount,
            openPitchInputs = StarterInstrumentProfiles.openPitchTexts(stringCount),
            openIntonationInputs = defaultIntonationInputs(stringCount),
            closedIntonationInputs = defaultIntonationInputs(stringCount),
            statusMessage = "Loaded starter profile for $stringCount strings."
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

        val nextState = buildUiState(
            stringCount = currentState.stringCount,
            openPitchInputs = updatedInputs,
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = currentState.rows.map { row -> row.closedIntonationInput },
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
            openPitchInputs = currentState.rows.map { row -> row.openPitchInput },
            openIntonationInputs = currentState.rows.map { row -> row.openIntonationInput },
            closedIntonationInputs = updatedClosedIntonationInputs,
            statusMessage = null
        )
        _uiState.value = nextState
        persistProfileIfValid(nextState)
    }

    fun saveProfile() {
        val currentState = _uiState.value
        if (!currentState.canSave) {
            _uiState.update { state ->
                state.copy(statusMessage = "Enter valid open pitches and intonation cents for all strings before saving.")
            }
            return
        }

        val openPitches = currentState.rows.mapNotNull { row ->
            Pitch.parse(row.openPitchInput)
        }
        if (openPitches.size != currentState.stringCount) {
            _uiState.update { state ->
                state.copy(statusMessage = "Unable to save. Please verify your pitch inputs.")
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
                state.copy(statusMessage = "Unable to save. Please verify your intonation inputs.")
            }
            return
        }

        val profile = InstrumentProfile(
            stringCount = currentState.stringCount,
            openPitches = openPitches,
            openIntonationCents = openIntonationCents,
            closedIntonationCents = closedIntonationCents
        )

        viewModelScope.launch {
            repository.saveInstrumentProfile(profile)
            _uiState.update { state ->
                state.copy(statusMessage = "Instrument profile saved.")
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

        val profile = InstrumentProfile(
            stringCount = state.stringCount,
            openPitches = openPitches,
            openIntonationCents = openIntonationCents,
            closedIntonationCents = closedIntonationCents
        )

        viewModelScope.launch {
            repository.saveInstrumentProfile(profile)
        }
    }

    companion object {
        private const val DEFAULT_STRING_COUNT = 21
        private val SUPPORTED_STRING_COUNTS = setOf(21, 22)

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InstrumentConfigurationViewModel(
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

        private fun buildUiState(
            stringCount: Int,
            openPitchInputs: List<String>,
            openIntonationInputs: List<String>,
            closedIntonationInputs: List<String>,
            statusMessage: String?
        ): InstrumentConfigurationUiState {
            val resizedPitchInputs = resizePitchInputCount(openPitchInputs, stringCount)
            val resizedOpenIntonationInputs = resizeIntonationInputCount(openIntonationInputs, stringCount)
            val resizedClosedIntonationInputs = resizeIntonationInputCount(closedIntonationInputs, stringCount)
            val rows = resizedPitchInputs.mapIndexed { index, input ->
                val parsedPitch = Pitch.parse(input)
                val inputError = when {
                    input.isBlank() -> null
                    parsedPitch == null -> "Use format like E3 or F#4."
                    else -> null
                }
                val openIntonationInput = resizedOpenIntonationInputs[index]
                val closedIntonationInput = resizedClosedIntonationInputs[index]
                val openIntonationValue = parseCentsInput(openIntonationInput)
                val closedIntonationValue = parseCentsInput(closedIntonationInput)
                val openIntonationError = if (openIntonationValue == null) {
                    "Use cents like -13.7 or 0.0."
                } else {
                    null
                }
                val closedIntonationError = if (closedIntonationValue == null) {
                    "Use cents like -13.7 or 0.0."
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

            return InstrumentConfigurationUiState(
                stringCount = stringCount,
                rows = rows,
                canSave = canSave,
                statusMessage = statusMessage
            )
        }

        private const val DEFAULT_INTONATION_INPUT = "0.0"
    }
}

