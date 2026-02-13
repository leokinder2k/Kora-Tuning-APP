package com.example.koratuningsystem.instrumentconfig.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.koratuningsystem.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.example.koratuningsystem.instrumentconfig.data.InstrumentConfigRepository
import com.example.koratuningsystem.instrumentconfig.model.TraditionalPreset
import com.example.koratuningsystem.instrumentconfig.model.TraditionalPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TraditionalPresetUiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val openPitchPreview: String
)

data class TraditionalPresetStringRowUiState(
    val stringNumber: Int,
    val openPitch: String,
    val closedPitch: String,
    val openIntonationCents: Double,
    val closedIntonationCents: Double
)

data class TraditionalPresetsUiState(
    val stringCount: Int,
    val presets: List<TraditionalPresetUiModel>,
    val selectedPresetId: String?,
    val previewRows: List<TraditionalPresetStringRowUiState>,
    val canApply: Boolean,
    val statusMessage: String?
)

class TraditionalPresetsViewModel(
    private val repository: InstrumentConfigRepository
) : ViewModel() {

    private var selectedStringCount: Int = DEFAULT_STRING_COUNT
    private var selectedPresetId: String? = TraditionalPresets.presetsForStringCount(DEFAULT_STRING_COUNT)
        .firstOrNull()
        ?.id
    private var statusMessage: String? = null

    private val _uiState = MutableStateFlow(
        buildUiState(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId,
            statusMessage = statusMessage
        )
    )
    val uiState: StateFlow<TraditionalPresetsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.instrumentProfile.collect { profile ->
                val profileStringCount = profile?.stringCount ?: return@collect
                if (profileStringCount != selectedStringCount) {
                    selectedStringCount = profileStringCount
                    selectedPresetId = adjustedPresetId(
                        stringCount = selectedStringCount,
                        requestedPresetId = selectedPresetId
                    )
                    _uiState.value = buildUiState(
                        stringCount = selectedStringCount,
                        requestedPresetId = selectedPresetId,
                        statusMessage = statusMessage
                    )
                }
            }
        }
    }

    fun onStringCountSelected(stringCount: Int) {
        if (stringCount !in SUPPORTED_STRING_COUNTS) {
            return
        }
        selectedStringCount = stringCount
        selectedPresetId = adjustedPresetId(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId
        )
        statusMessage = null
        _uiState.value = buildUiState(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId,
            statusMessage = statusMessage
        )
    }

    fun onPresetSelected(presetId: String) {
        val availablePresetIds = TraditionalPresets.presetsForStringCount(selectedStringCount)
            .map(TraditionalPreset::id)
            .toSet()
        if (presetId !in availablePresetIds) {
            return
        }
        selectedPresetId = presetId
        statusMessage = null
        _uiState.value = buildUiState(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId,
            statusMessage = statusMessage
        )
    }

    fun applySelectedPreset() {
        val preset = resolvedSelectedPreset(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId
        ) ?: run {
            statusMessage = "Select a preset before loading."
            _uiState.value = buildUiState(
                stringCount = selectedStringCount,
                requestedPresetId = selectedPresetId,
                statusMessage = statusMessage
            )
            return
        }

        viewModelScope.launch {
            repository.saveInstrumentProfile(preset.toInstrumentProfile())
            statusMessage = "Loaded ${preset.displayName} preset for ${preset.stringCount} strings."
            _uiState.value = buildUiState(
                stringCount = selectedStringCount,
                requestedPresetId = preset.id,
                statusMessage = statusMessage
            )
        }
    }

    private fun buildUiState(
        stringCount: Int,
        requestedPresetId: String?,
        statusMessage: String?
    ): TraditionalPresetsUiState {
        val presets = TraditionalPresets.presetsForStringCount(stringCount)
        val resolvedPreset = resolvedSelectedPreset(stringCount, requestedPresetId)
        selectedPresetId = resolvedPreset?.id

        val presetUiModels = presets.map { preset ->
            TraditionalPresetUiModel(
                id = preset.id,
                displayName = preset.displayName,
                description = preset.description,
                openPitchPreview = previewText(preset)
            )
        }

        val previewRows = resolvedPreset?.openPitches?.mapIndexed { index, openPitch ->
            TraditionalPresetStringRowUiState(
                stringNumber = index + 1,
                openPitch = openPitch.asText(),
                closedPitch = openPitch.plusSemitones(1).asText(),
                openIntonationCents = resolvedPreset.openIntonationCents[index],
                closedIntonationCents = resolvedPreset.closedIntonationCents[index]
            )
        }.orEmpty()

        return TraditionalPresetsUiState(
            stringCount = stringCount,
            presets = presetUiModels,
            selectedPresetId = selectedPresetId,
            previewRows = previewRows,
            canApply = resolvedPreset != null,
            statusMessage = statusMessage
        )
    }

    private fun resolvedSelectedPreset(
        stringCount: Int,
        requestedPresetId: String?
    ): TraditionalPreset? {
        val presets = TraditionalPresets.presetsForStringCount(stringCount)
        return presets.firstOrNull { preset -> preset.id == requestedPresetId }
            ?: presets.firstOrNull()
    }

    private fun adjustedPresetId(
        stringCount: Int,
        requestedPresetId: String?
    ): String? {
        return resolvedSelectedPreset(stringCount, requestedPresetId)?.id
    }

    private fun previewText(preset: TraditionalPreset): String {
        val prefix = preset.openPitches.take(PREVIEW_LIMIT)
            .joinToString(" ") { pitch -> pitch.asText() }
        return if (preset.openPitches.size > PREVIEW_LIMIT) {
            "$prefix ..."
        } else {
            prefix
        }
    }

    companion object {
        private const val DEFAULT_STRING_COUNT = 21
        private const val PREVIEW_LIMIT = 5
        private val SUPPORTED_STRING_COUNTS = setOf(21, 22)

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TraditionalPresetsViewModel(
                    repository = DataStoreInstrumentConfigRepository.create(context)
                )
            }
        }
    }
}
