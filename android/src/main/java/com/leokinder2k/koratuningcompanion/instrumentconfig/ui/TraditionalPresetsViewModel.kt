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
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPreset
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.TraditionalPresets
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.UserPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TraditionalPresetUiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val openPitchPreview: String,
    val isCustom: Boolean
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
    val selectedPresetIsCustom: Boolean,
    val previewRows: List<TraditionalPresetStringRowUiState>,
    val canApply: Boolean,
    val customPresetNameInput: String,
    val canSaveCustomPreset: Boolean,
    val statusMessage: String?
)

class TraditionalPresetsViewModel(
    private val appContext: Context,
    private val repository: InstrumentConfigRepository
) : ViewModel() {

    private var selectedStringCount: Int = DEFAULT_STRING_COUNT
    private var selectedPresetId: String? = null
    private var customPresetNameInput: String = appContext.getString(R.string.traditional_presets_default_custom_name)
    private var statusMessage: String? = null
    private var latestInstrumentProfile: InstrumentProfile? = null
    private var customPresets: List<UserPreset> = emptyList()

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
                latestInstrumentProfile = profile
                val profileStringCount = profile?.stringCount ?: return@collect
                if (profileStringCount != selectedStringCount) {
                    selectedStringCount = profileStringCount
                }
                refreshUiState()
            }
        }

        viewModelScope.launch {
            repository.userPresets.collect { presets ->
                customPresets = presets
                refreshUiState()
            }
        }
    }

    fun onStringCountSelected(stringCount: Int) {
        if (stringCount !in SUPPORTED_STRING_COUNTS) {
            return
        }
        selectedStringCount = stringCount
        statusMessage = null
        refreshUiState()
    }

    fun onPresetSelected(presetId: String) {
        val availableIds = allPresetEntries(stringCount = selectedStringCount)
            .map(PresetEntry::id)
            .toSet()
        if (presetId !in availableIds) {
            return
        }
        selectedPresetId = presetId
        statusMessage = null
        refreshUiState()
    }

    fun onCustomPresetNameChanged(value: String) {
        customPresetNameInput = value
        refreshUiState()
    }

    fun saveCurrentProfileAsCustomPreset() {
        val profile = latestInstrumentProfile ?: run {
            statusMessage = appContext.getString(R.string.traditional_presets_status_save_profile_first)
            refreshUiState()
            return
        }

        val name = customPresetNameInput.trim()
        if (name.isBlank()) {
            statusMessage = appContext.getString(R.string.traditional_presets_status_enter_name)
            refreshUiState()
            return
        }

        viewModelScope.launch {
            selectedStringCount = profile.stringCount
            val createdId = repository.saveUserPreset(
                displayName = name,
                profile = profile
            )
            selectedPresetId = createdId
            statusMessage = appContext.getString(R.string.traditional_presets_status_saved_custom, name)
            refreshUiState()
        }
    }

    fun deleteSelectedCustomPreset() {
        val selectedId = selectedPresetId ?: run {
            statusMessage = appContext.getString(R.string.traditional_presets_status_select_custom_to_delete)
            refreshUiState()
            return
        }
        val preset = customPresets.firstOrNull { item -> item.id == selectedId } ?: run {
            statusMessage = appContext.getString(R.string.traditional_presets_status_not_custom_preset)
            refreshUiState()
            return
        }

        viewModelScope.launch {
            repository.deleteUserPreset(preset.id)
            if (selectedPresetId == preset.id) {
                selectedPresetId = null
            }
            statusMessage = appContext.getString(R.string.traditional_presets_status_deleted_custom, preset.displayName)
            refreshUiState()
        }
    }

    fun applySelectedPreset() {
        val entry = resolvedSelectedEntry(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId
        ) ?: run {
            statusMessage = appContext.getString(R.string.traditional_presets_status_select_preset_before_loading)
            refreshUiState()
            return
        }

        viewModelScope.launch {
            repository.saveInstrumentProfile(entry.profile)
            selectedStringCount = entry.profile.stringCount
            selectedPresetId = entry.id
            statusMessage = appContext.getString(
                R.string.traditional_presets_status_loaded_preset,
                entry.displayName,
                entry.profile.stringCount
            )
            refreshUiState()
        }
    }

    private fun refreshUiState() {
        _uiState.value = buildUiState(
            stringCount = selectedStringCount,
            requestedPresetId = selectedPresetId,
            statusMessage = statusMessage
        )
    }

    private fun buildUiState(
        stringCount: Int,
        requestedPresetId: String?,
        statusMessage: String?
    ): TraditionalPresetsUiState {
        val entries = allPresetEntries(stringCount)
        val resolvedEntry = resolvedSelectedEntry(stringCount, requestedPresetId)
        selectedPresetId = resolvedEntry?.id

        val presetUiModels = entries.map { entry ->
            TraditionalPresetUiModel(
                id = entry.id,
                displayName = entry.displayName,
                description = entry.description,
                openPitchPreview = previewText(entry.profile),
                isCustom = entry.isCustom
            )
        }

        val previewRows = resolvedEntry?.profile?.openPitches?.mapIndexed { index, openPitch ->
            TraditionalPresetStringRowUiState(
                stringNumber = index + 1,
                openPitch = openPitch.asText(),
                closedPitch = openPitch.plusSemitones(1).asText(),
                openIntonationCents = resolvedEntry.profile.openIntonationCents[index],
                closedIntonationCents = resolvedEntry.profile.closedIntonationCents[index]
            )
        }.orEmpty()

        val canSaveCustomPreset = latestInstrumentProfile != null &&
            customPresetNameInput.trim().isNotEmpty()

        return TraditionalPresetsUiState(
            stringCount = stringCount,
            presets = presetUiModels,
            selectedPresetId = selectedPresetId,
            selectedPresetIsCustom = resolvedEntry?.isCustom == true,
            previewRows = previewRows,
            canApply = resolvedEntry != null,
            customPresetNameInput = customPresetNameInput,
            canSaveCustomPreset = canSaveCustomPreset,
            statusMessage = statusMessage
        )
    }

    private fun allPresetEntries(stringCount: Int): List<PresetEntry> {
        val builtIn = TraditionalPresets.presetsForStringCount(stringCount).map { preset ->
            preset.toPresetEntry()
        }
        val user = customPresets
            .filter { preset -> preset.profile.stringCount == stringCount }
            .sortedByDescending { preset -> preset.createdAtEpochMillis }
            .map { preset ->
                PresetEntry(
                    id = preset.id,
                    displayName = preset.displayName,
                    description = appContext.getString(R.string.traditional_presets_user_preset_description),
                    profile = preset.profile,
                    isCustom = true
                )
            }
        return builtIn + user
    }

    private fun resolvedSelectedEntry(
        stringCount: Int,
        requestedPresetId: String?
    ): PresetEntry? {
        val entries = allPresetEntries(stringCount)
        return entries.firstOrNull { entry -> entry.id == requestedPresetId }
            ?: entries.firstOrNull()
    }

    private fun TraditionalPreset.toPresetEntry(): PresetEntry {
        return PresetEntry(
            id = id,
            displayName = displayName,
            description = description,
            profile = toInstrumentProfile(),
            isCustom = false
        )
    }

    private fun previewText(profile: InstrumentProfile): String {
        val prefix = profile.openPitches.take(PREVIEW_LIMIT)
            .joinToString(" ") { pitch -> pitch.asText() }
        return if (profile.openPitches.size > PREVIEW_LIMIT) {
            "$prefix ..."
        } else {
            prefix
        }
    }

    private data class PresetEntry(
        val id: String,
        val displayName: String,
        val description: String,
        val profile: InstrumentProfile,
        val isCustom: Boolean
    )

    companion object {
        private const val DEFAULT_STRING_COUNT = 21
        private const val PREVIEW_LIMIT = 5
        private val SUPPORTED_STRING_COUNTS = setOf(21, 22)

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TraditionalPresetsViewModel(
                    appContext = context.applicationContext,
                    repository = DataStoreInstrumentConfigRepository.create(context)
                )
            }
        }
    }
}

