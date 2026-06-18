package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.displaySymbol
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackClassifier
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerPerformanceMode
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerUiState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerViewModel
import com.leokinder2k.koratuningcompanion.platform.isMicPermissionGranted
import com.leokinder2k.koratuningcompanion.platform.rememberKoraViewModel
import com.leokinder2k.koratuningcompanion.platform.rememberMicPermissionLauncher
import com.leokinder2k.koratuningcompanion.ui.theme.KoraFlatColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraInTuneColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraSharpColor
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ln

@Composable
fun InstrumentConfigurationRoute(
    enharmonicPreference: EnharmonicPreference = EnharmonicPreference.SHARPS,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val configViewModel: InstrumentConfigurationViewModel = rememberKoraViewModel { InstrumentConfigurationViewModel() }
    val tunerViewModel: LiveTunerViewModel = rememberKoraViewModel { LiveTunerViewModel() }
    val uiState by configViewModel.uiState.collectAsState()
    val tunerUiState by tunerViewModel.uiState.collectAsState()

    InstrumentConfigurationScreen(
        uiState = uiState,
        tunerUiState = tunerUiState,
        onStringCountSelected = configViewModel::onStringCountSelected,
        onTuningModeSelected = configViewModel::onTuningModeSelected,
        onHomeLeverPositionSelected = configViewModel::onHomeLeverPositionSelected,
        onRootNoteSelected = configViewModel::onRootNoteSelected,
        onOpenPitchChanged = configViewModel::onOpenPitchChanged,
        onOpenIntonationChanged = configViewModel::onOpenIntonationChanged,
        onClosedIntonationChanged = configViewModel::onClosedIntonationChanged,
        onLoadStarterProfile = configViewModel::loadStarterProfile,
        onSaveProfile = configViewModel::saveProfile,
        onSetCurrentAsHome = configViewModel::setCurrentAsBase,
        onRestoreToHome = configViewModel::restoreToBase,
        onAudioPermissionChanged = tunerViewModel::onAudioPermissionChanged,
        onPerformanceModeSelected = tunerViewModel::onPerformanceModeSelected,
        onStartListening = tunerViewModel::startListening,
        onStopListening = tunerViewModel::stopListening,
        enharmonicPreference = enharmonicPreference,
        isMuted = isMuted,
        onToggleMute = onToggleMute,
        isActive = isActive,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun InstrumentConfigurationScreen(
    uiState: InstrumentConfigurationUiState,
    tunerUiState: LiveTunerUiState,
    onStringCountSelected: (Int) -> Unit,
    onTuningModeSelected: (KoraTuningMode) -> Unit,
    onHomeLeverPositionSelected: (HomeLeverPosition) -> Unit,
    onRootNoteSelected: (NoteName) -> Unit,
    onOpenPitchChanged: (rowIndex: Int, value: String) -> Unit,
    onOpenIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onClosedIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onLoadStarterProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    onSetCurrentAsHome: () -> Unit,
    onRestoreToHome: () -> Unit,
    onAudioPermissionChanged: (Boolean) -> Unit,
    onPerformanceModeSelected: (LiveTunerPerformanceMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    enharmonicPreference: EnharmonicPreference = EnharmonicPreference.SHARPS,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberMicPermissionLauncher(onResult = onAudioPermissionChanged)
    val isGranted = isMicPermissionGranted()

    androidx.compose.runtime.LaunchedEffect(isMuted) {
        if (isMuted) onStopListening()
    }
    LaunchedEffect(isGranted) {
        onAudioPermissionChanged(isGranted)
    }

    val referenceTonePlayer = remember { ReferenceTonePlayer() }
    val confirmationTonePlayer = remember { ReferenceTonePlayer(amplitude = 0.18) }
    var isReferenceTonePlaying by remember { mutableStateOf(false) }
    var isPlayingAll by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            referenceTonePlayer.release()
            confirmationTonePlayer.release()
        }
    }

    // Stop all audio when navigating away from this page
    LaunchedEffect(isActive) {
        if (!isActive) {
            isPlayingAll = false
            isReferenceTonePlaying = false
            referenceTonePlayer.stop()
            confirmationTonePlayer.stop()
        }
    }

    var selectedTuningRowIndex by rememberSaveable(uiState.stringCount) { mutableStateOf(0) }
    var confirmedTuningStringNumber by rememberSaveable(uiState.stringCount) { mutableStateOf<Int?>(null) }
    selectedTuningRowIndex = selectedTuningRowIndex.coerceIn(0, (uiState.rows.size - 1).coerceAtLeast(0))
    val selectedRow = uiState.rows.getOrNull(selectedTuningRowIndex)
    val selectedPitch = selectedRow?.openPitchInput?.let(Pitch::parse)
    val selectedOpenCents = selectedRow?.openIntonationInput?.parseFiniteCents()
    val selectedTargetFrequencyHz = if (selectedPitch != null && selectedOpenCents != null) {
        TunerTargetMatcher.pitchToFrequencyHz(
            pitch = selectedPitch,
            centsOffset = selectedOpenCents
        )
    } else {
        null
    }
    val selectedCentsDeviation = if (
        selectedTargetFrequencyHz != null &&
        tunerUiState.detectedFrequencyHz != null &&
        tunerUiState.detectedFrequencyHz > 0.0
    ) {
        centsDeviation(
            detectedFrequencyHz = tunerUiState.detectedFrequencyHz,
            targetFrequencyHz = selectedTargetFrequencyHz
        )
    } else {
        null
    }
    LaunchedEffect(isReferenceTonePlaying, selectedTargetFrequencyHz, isMuted) {
        if (isReferenceTonePlaying && !isPlayingAll && selectedTargetFrequencyHz != null && !isMuted) {
            referenceTonePlayer.play(instrumentAssistantReferenceFrequencyHz(selectedTargetFrequencyHz))
        } else if (!isReferenceTonePlaying || isMuted) {
            referenceTonePlayer.stop()
            if (isMuted) { isReferenceTonePlaying = false; isPlayingAll = false }
        }
    }

    // Play-all follows the physical tuning sequence, not side grouping.
    val allRows = run {
        val rowsByNumber = uiState.rows.associateBy { it.stringNumber }
        KoraStringLayout.tuningOrder(uiState.stringCount)
            .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }
    }
    LaunchedEffect(isPlayingAll, isMuted, isActive) {
        if (!isPlayingAll || isMuted || !isActive) return@LaunchedEffect
        isReferenceTonePlaying = true
        for (row in allRows) {
            if (!isPlayingAll || isMuted || !isActive) break
            val pitch = Pitch.parse(row.openPitchInput)
            val cents = row.openIntonationInput.parseFiniteCents()
            if (pitch != null && cents != null) {
                val freq = TunerTargetMatcher.pitchToFrequencyHz(pitch = pitch, centsOffset = cents)
                selectedTuningRowIndex = uiState.rows.indexOf(row).coerceAtLeast(0)
                referenceTonePlayer.play(instrumentAssistantReferenceFrequencyHz(freq))
            }
            delay(3000L)
        }
        isPlayingAll = false
        isReferenceTonePlaying = false
        referenceTonePlayer.stop()
    }

    val inTuneThresholdCents = when (uiState.tuningMode) {
        KoraTuningMode.LEVERED -> TuningFeedbackClassifier.DEFAULT_IN_TUNE_THRESHOLD_CENTS
        KoraTuningMode.PEG_TUNING -> PEG_TUNING_IN_TUNE_THRESHOLD_CENTS
    }
    val tuningState = selectedCentsDeviation?.let { deviation ->
        TuningFeedbackClassifier.classify(
            centsDeviation = deviation,
            inTuneThresholdCents = inTuneThresholdCents
        )
    }
    val isPreciseAssistantHit = isInstrumentAssistantSuccessHit(selectedCentsDeviation)
    LaunchedEffect(selectedTuningRowIndex) {
        confirmedTuningStringNumber = null
    }
    LaunchedEffect(
        isPreciseAssistantHit,
        selectedRow?.stringNumber,
        tunerUiState.isListening,
        isMuted,
        isReferenceTonePlaying,
        isPlayingAll,
        isActive,
        uiState.stringCount
    ) {
        val row = selectedRow
        if (!isPreciseAssistantHit) {
            confirmedTuningStringNumber = null
            return@LaunchedEffect
        }
        if (
            row == null ||
            !tunerUiState.isListening ||
            isMuted ||
            isReferenceTonePlaying ||
            isPlayingAll ||
            !isActive ||
            confirmedTuningStringNumber == row.stringNumber
        ) {
            return@LaunchedEffect
        }

        confirmedTuningStringNumber = row.stringNumber
        playAssistantConfirmationTone(confirmationTonePlayer)
        delay(ASSISTANT_AUTO_ADVANCE_DELAY_MS)
        KoraStringLayout.nextOnSameSide(
            stringCount = uiState.stringCount,
            stringNumber = row.stringNumber
        )?.let { nextStringNumber ->
            selectedTuningRowIndex = (nextStringNumber - 1)
                .coerceIn(0, (uiState.rows.size - 1).coerceAtLeast(0))
        }
    }
    val selectedPresetName = uiState.presetOptions
        .firstOrNull { option -> option.id == uiState.selectedPresetId }
        ?.displayName
        ?: uiState.selectedPresetId
    val tuningSourceLabel = if (
        uiState.selectedPresetId == MANUAL_PRESET_ID ||
        !uiState.autoCalibrateEnabled
    ) {
        stringResource(Res.string.instrument_tuning_assistant_source_rows)
    } else {
        stringResource(
            Res.string.instrument_tuning_assistant_source_preset,
            selectedPresetName,
            uiState.lowestLeftPitchInput
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_instrument_configuration)) },
                actions = {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) MaterialTheme.colorScheme.error
                                   else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(Res.string.instrument_config_section_strings),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.stringCount == 21,
                        onClick = { onStringCountSelected(21) },
                        label = { Text(stringResource(Res.string.strings_count, 21)) }
                    )
                    FilterChip(
                        selected = uiState.stringCount == 22,
                        onClick = { onStringCountSelected(22) },
                        label = { Text(stringResource(Res.string.strings_count, 22)) }
                    )
                }
            }

            item {
                Text(
                    text = when (uiState.tuningMode) {
                        KoraTuningMode.LEVERED -> stringResource(Res.string.instrument_config_description_levered)
                        KoraTuningMode.PEG_TUNING -> stringResource(Res.string.instrument_config_description_peg_tuning)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.instrument_config_section_instrument_type),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.tuningMode == KoraTuningMode.LEVERED,
                        onClick = { onTuningModeSelected(KoraTuningMode.LEVERED) },
                        label = { Text(stringResource(Res.string.instrument_type_levers)) }
                    )
                    FilterChip(
                        selected = uiState.tuningMode == KoraTuningMode.PEG_TUNING,
                        onClick = { onTuningModeSelected(KoraTuningMode.PEG_TUNING) },
                        label = { Text(stringResource(Res.string.instrument_type_pegs)) }
                    )
                }
            }

            if (uiState.tuningMode == KoraTuningMode.LEVERED) {
                item {
                    Text(
                        text = stringResource(Res.string.instrument_config_section_home_lever_position),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.homeLeverPosition == HomeLeverPosition.OPEN,
                            onClick = { onHomeLeverPositionSelected(HomeLeverPosition.OPEN) },
                            label = { Text(stringResource(Res.string.instrument_config_home_lever_position_open)) }
                        )
                        FilterChip(
                            selected = uiState.homeLeverPosition == HomeLeverPosition.CLOSED,
                            onClick = { onHomeLeverPositionSelected(HomeLeverPosition.CLOSED) },
                            label = { Text(stringResource(Res.string.instrument_config_home_lever_position_closed)) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.instrument_config_section_root_note),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.instrument_config_description_instrument_key),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NoteName.entries) { note ->
                        FilterChip(
                            selected = uiState.rootNote == note,
                            onClick = { onRootNoteSelected(note) },
                            label = { Text(note.displaySymbol(enharmonicPreference)) }
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.quick_start_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(Res.string.instrument_config_quick_start_step_1),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(Res.string.instrument_config_quick_start_step_2),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(Res.string.instrument_config_quick_start_step_3),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 4.dp)
                ) {
                    InstrumentTuningAssistantCard(
                        rows = uiState.rows,
                        selectedRowIndex = selectedTuningRowIndex,
                        onSelectedRowIndexChanged = { index ->
                            when {
                                // Tapping the current chip while playing stops the tone
                                (isReferenceTonePlaying || isPlayingAll) && index == selectedTuningRowIndex -> {
                                    isPlayingAll = false
                                    isReferenceTonePlaying = false
                                }
                                // Tapping a different chip while playing all stops the sequence and plays that string
                                isPlayingAll -> {
                                    isPlayingAll = false
                                    selectedTuningRowIndex = index
                                    isReferenceTonePlaying = true
                                }
                                else -> selectedTuningRowIndex = index
                            }
                        },
                        selectedPitchLabel = selectedPitch?.asText(enharmonicPreference),
                        selectedOpenCents = selectedOpenCents,
                        selectedTargetFrequencyHz = selectedTargetFrequencyHz,
                        detectedFrequencyHz = tunerUiState.detectedFrequencyHz,
                        selectedCentsDeviation = selectedCentsDeviation,
                        tuningState = tuningState,
                        tunerUiState = tunerUiState,
                        tuningSourceLabel = tuningSourceLabel,
                        enharmonicPreference = enharmonicPreference,
                        isReferenceTonePlaying = isReferenceTonePlaying || isPlayingAll,
                        isPlayingAll = isPlayingAll,
                        onPlayAll = { if (!isMuted) isPlayingAll = true },
                        onStopReferenceTone = {
                            isPlayingAll = false
                            isReferenceTonePlaying = false
                        },
                        onRequestPermission = permissionLauncher,
                        onPerformanceModeSelected = onPerformanceModeSelected,
                        onStartListening = onStartListening,
                        onStopListening = onStopListening,
                        isMuted = isMuted
                    )
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.instrument_config_auto_save_note),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val rowsByNumber = uiState.rows.associateBy { row -> row.stringNumber }
            val leftRows = KoraStringLayout.leftOrder(uiState.stringCount)
                .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }
            val rightRows = KoraStringLayout.rightOrder(uiState.stringCount)
                .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }

            item {
                SideSectionHeader(label = "L")
            }
            items(leftRows, key = { row -> row.stringNumber }) { row ->
                StringConfigurationCard(
                    row = row,
                    stringCount = uiState.stringCount,
                    showLeverFields = uiState.tuningMode == KoraTuningMode.LEVERED,
                    onOpenPitchChanged = { value -> onOpenPitchChanged(row.stringNumber - 1, value) },
                    onOpenIntonationChanged = { value -> onOpenIntonationChanged(row.stringNumber - 1, value) },
                    onClosedIntonationChanged = { value -> onClosedIntonationChanged(row.stringNumber - 1, value) }
                )
            }
            item {
                SideSectionHeader(label = "R")
            }
            items(rightRows, key = { row -> row.stringNumber }) { row ->
                StringConfigurationCard(
                    row = row,
                    stringCount = uiState.stringCount,
                    showLeverFields = uiState.tuningMode == KoraTuningMode.LEVERED,
                    onOpenPitchChanged = { value -> onOpenPitchChanged(row.stringNumber - 1, value) },
                    onOpenIntonationChanged = { value -> onOpenIntonationChanged(row.stringNumber - 1, value) },
                    onClosedIntonationChanged = { value -> onClosedIntonationChanged(row.stringNumber - 1, value) }
                )
            }

            item {
                HomeTuningCard(
                    basePitchInputs = uiState.basePitchInputs,
                    onSetCurrentAsHome = onSetCurrentAsHome,
                    onRestoreToHome = onRestoreToHome
                )
            }

            item {
                OutlinedButton(
                    onClick = onLoadStarterProfile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.instrument_config_action_load_starter_profile))
                }
            }

            item {
                Button(
                    onClick = onSaveProfile,
                    enabled = uiState.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.instrument_config_action_save_profile))
                }
            }

            item {
                uiState.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SideSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun HomeTuningCard(
    basePitchInputs: List<String>,
    onSetCurrentAsHome: () -> Unit,
    onRestoreToHome: () -> Unit
) {
    val allValid = basePitchInputs.all { Pitch.parse(it) != null }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.instrument_config_section_home_tuning),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(Res.string.instrument_config_home_tuning_description),
                style = MaterialTheme.typography.bodySmall
            )
            if (allValid) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(basePitchInputs) { index, pitch ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.instrument_config_home_tuning_string_chip,
                                    index + 1,
                                    pitch
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.instrument_config_home_not_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSetCurrentAsHome,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(Res.string.instrument_config_action_set_current_as_home),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = onRestoreToHome,
                    enabled = allValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(Res.string.instrument_config_action_restore_to_home),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentTuningAssistantCard(
    rows: List<InstrumentStringRowUiState>,
    selectedRowIndex: Int,
    onSelectedRowIndexChanged: (Int) -> Unit,
    selectedPitchLabel: String?,
    selectedOpenCents: Double?,
    selectedTargetFrequencyHz: Double?,
    detectedFrequencyHz: Double?,
    selectedCentsDeviation: Double?,
    tuningState: TuningFeedbackState?,
    tunerUiState: LiveTunerUiState,
    tuningSourceLabel: String,
    enharmonicPreference: EnharmonicPreference,
    isReferenceTonePlaying: Boolean,
    isPlayingAll: Boolean,
    onPlayAll: () -> Unit,
    onStopReferenceTone: () -> Unit,
    onRequestPermission: () -> Unit,
    onPerformanceModeSelected: (LiveTunerPerformanceMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    isMuted: Boolean
) {
    val rowsByNumber = rows.associateBy { row -> row.stringNumber }
    val leftRows = KoraStringLayout.leftOrder(rows.size)
        .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }
    val rightRows = KoraStringLayout.rightOrder(rows.size)
        .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }
    val statusColor = tuningState?.let(::tuningStateColor) ?: MaterialTheme.colorScheme.outlineVariant
    val showActiveTuningIndicators =
        tunerUiState.isListening && tuningState != null && selectedCentsDeviation != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = statusColor.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.instrument_tuning_assistant_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = tuningSourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.widthIn(max = 120.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!tunerUiState.hasAudioPermission) {
                        Button(onClick = onRequestPermission) {
                            Text(stringResource(Res.string.action_grant_mic_short))
                        }
                    } else if (!tunerUiState.isListening) {
                        Button(
                            onClick = onStartListening,
                            enabled = !isMuted
                        ) {
                            Text(stringResource(Res.string.action_start))
                        }
                    } else {
                        OutlinedButton(onClick = onStopListening) {
                            Text(stringResource(Res.string.action_stop))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LiveTunerPerformanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == tunerUiState.performanceMode,
                        onClick = { onPerformanceModeSelected(mode) },
                        label = { Text(liveTunerPerformanceModeLabel(mode)) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.instrument_tuning_assistant_target_line,
                            selectedPitchLabel ?: "--",
                            selectedOpenCents?.let(::signed) ?: "--"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            Res.string.instrument_tuning_assistant_target_hz_line,
                            formatFrequency(selectedTargetFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.instrument_tuning_assistant_detected_hz_line,
                            formatFrequency(detectedFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (selectedCentsDeviation != null) {
                        val color = tuningStateColor(requireNotNull(tuningState))
                        Text(
                            text = stringResource(
                                Res.string.instrument_tuning_assistant_deviation_line,
                                signed(selectedCentsDeviation),
                                tuningStateLabel(tuningState)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.instrument_tuning_assistant_deviation_none),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            TuningLightsRow(
                activeState = tuningState,
                selectedCentsDeviation = selectedCentsDeviation,
                showActiveIndicators = showActiveTuningIndicators
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReferenceTonePlaying) {
                    OutlinedButton(
                        onClick = onStopReferenceTone,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.live_tuner_reference_tone_action_stop))
                    }
                } else {
                    Button(
                        onClick = onPlayAll,
                        enabled = rows.any { row ->
                            Pitch.parse(row.openPitchInput) != null &&
                                row.openIntonationInput.parseFiniteCents() != null
                        } && !isMuted,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.instrument_config_action_play_all_strings))
                    }
                }
            }

            CompactStringSelectorRow(
                sideLabel = "L",
                rows = leftRows,
                stringCount = rows.size,
                selectedRowIndex = selectedRowIndex,
                enharmonicPreference = enharmonicPreference,
                onSelectedRowIndexChanged = onSelectedRowIndexChanged
            )
            CompactStringSelectorRow(
                sideLabel = "R",
                rows = rightRows,
                stringCount = rows.size,
                selectedRowIndex = selectedRowIndex,
                enharmonicPreference = enharmonicPreference,
                onSelectedRowIndexChanged = onSelectedRowIndexChanged
            )

            tunerUiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CompactStringSelectorRow(
    sideLabel: String,
    rows: List<InstrumentStringRowUiState>,
    stringCount: Int,
    selectedRowIndex: Int,
    enharmonicPreference: EnharmonicPreference,
    onSelectedRowIndexChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sideLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp)
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items = rows, key = { row -> row.stringNumber }) { row ->
                CompactStringChip(
                    row = row,
                    stringCount = stringCount,
                    selected = row.stringNumber - 1 == selectedRowIndex,
                    enharmonicPreference = enharmonicPreference,
                    onClick = { onSelectedRowIndexChanged(row.stringNumber - 1) }
                )
            }
        }
    }
}

@Composable
private fun CompactStringChip(
    row: InstrumentStringRowUiState,
    stringCount: Int,
    selected: Boolean,
    enharmonicPreference: EnharmonicPreference,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Text(
            text = "${KoraStringLayout.roleLabel(stringCount, row.stringNumber)} ${Pitch.parse(row.openPitchInput)?.asText(enharmonicPreference) ?: row.openPitchInput.ifBlank { "--" }}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TuningLightsRow(
    activeState: TuningFeedbackState?,
    selectedCentsDeviation: Double?,
    showActiveIndicators: Boolean
) {
    val markerFraction = if (selectedCentsDeviation == null || !selectedCentsDeviation.isFinite()) {
        0.5f
    } else {
        (
            (
                selectedCentsDeviation
                    .coerceIn(-TUNING_GRADIENT_RANGE_CENTS, TUNING_GRADIENT_RANGE_CENTS) /
                    TUNING_GRADIENT_RANGE_CENTS
            ) + 1.0
        ).toFloat() * 0.5f
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
        ) {
            val trackShape = RoundedCornerShape(11.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(KoraFlatColor, KoraInTuneColor, KoraSharpColor)
                        ),
                        shape = trackShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        shape = trackShape
                    )
            )

            if (showActiveIndicators) {
                val markerWidth = 4.dp
                val markerOffset = (maxWidth - markerWidth) * markerFraction.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(start = markerOffset)
                        .width(markerWidth)
                        .fillMaxSize()
                        .background(
                            color = Color.White.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.tuning_flat),
                style = MaterialTheme.typography.labelSmall,
                color = if (showActiveIndicators && activeState == TuningFeedbackState.FLAT) {
                    KoraFlatColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(Res.string.tuning_in_tune),
                style = MaterialTheme.typography.labelSmall,
                color = if (showActiveIndicators && activeState == TuningFeedbackState.IN_TUNE) {
                    KoraInTuneColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(Res.string.tuning_sharp),
                style = MaterialTheme.typography.labelSmall,
                color = if (showActiveIndicators && activeState == TuningFeedbackState.SHARP) {
                    KoraSharpColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private const val TUNING_GRADIENT_RANGE_CENTS = 50.0

@Composable
private fun StringConfigurationCard(
    row: InstrumentStringRowUiState,
    stringCount: Int,
    showLeverFields: Boolean,
    onOpenPitchChanged: (String) -> Unit,
    onOpenIntonationChanged: (String) -> Unit,
    onClosedIntonationChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = KoraStringLayout.roleLabel(stringCount, row.stringNumber),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "S${row.stringNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = row.openPitchInput,
                onValueChange = onOpenPitchChanged,
                label = { Text(stringResource(Res.string.instrument_config_open_pitch_label)) },
                placeholder = { Text(stringResource(Res.string.instrument_config_open_pitch_placeholder)) },
                singleLine = true,
                isError = row.inputError != null,
                supportingText = {
                    when {
                        row.inputError != null -> Text(row.inputError)
                        showLeverFields && row.closedPitch != null -> Text(
                            stringResource(Res.string.instrument_config_closed_pitch_supporting, row.closedPitch)
                        )
                        showLeverFields -> Text(stringResource(Res.string.instrument_config_open_pitch_supporting))
                        else -> Text(stringResource(Res.string.instrument_config_open_pitch_supporting_peg))
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = row.openIntonationInput,
                    onValueChange = onOpenIntonationChanged,
                    label = { Text(stringResource(Res.string.instrument_config_open_cents_label)) },
                    placeholder = { Text(stringResource(Res.string.instrument_config_cents_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = row.openIntonationError != null,
                    supportingText = {
                        row.openIntonationError?.let { error -> Text(error) }
                    }
                )
                if (showLeverFields) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = row.closedIntonationInput,
                        onValueChange = onClosedIntonationChanged,
                        label = { Text(stringResource(Res.string.instrument_config_closed_cents_label)) },
                        placeholder = { Text(stringResource(Res.string.instrument_config_cents_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = row.closedIntonationError != null,
                        supportingText = {
                            row.closedIntonationError?.let { error -> Text(error) }
                        }
                    )
                }
            }
        }
    }
}

private fun String.parseFiniteCents(): Double? {
    return trim().toDoubleOrNull()
        ?.takeIf { cents -> cents.isFinite() && cents in MIN_INTONATION_CENTS..MAX_INTONATION_CENTS }
}

private fun centsDeviation(
    detectedFrequencyHz: Double,
    targetFrequencyHz: Double
): Double {
    return 1200.0 * (ln(detectedFrequencyHz / targetFrequencyHz) / ln(2.0))
}

internal fun instrumentAssistantReferenceFrequencyHz(targetFrequencyHz: Double): Double = targetFrequencyHz

internal fun isInstrumentAssistantSuccessHit(centsDeviation: Double?): Boolean {
    return centsDeviation?.let { deviation ->
        kotlin.math.abs(deviation) <= ASSISTANT_SUCCESS_THRESHOLD_CENTS
    } == true
}

private suspend fun playAssistantConfirmationTone(player: ReferenceTonePlayer) {
    try {
        player.play(880.0)
        delay(70L)
        player.play(1320.0)
        delay(90L)
    } finally {
        player.stop()
    }
}

private fun formatDouble2(value: Double): String {
    val abs = kotlin.math.abs(value)
    val rounded = kotlin.math.round(abs * 100.0)
    val whole = rounded / 100
    val dec = rounded % 100
    return "$whole.${dec.toString().padStart(2, '0')}"
}

private fun formatFrequency(frequencyHz: Double?): String {
    return if (frequencyHz == null || !frequencyHz.isFinite()) "--"
    else "${formatDouble2(frequencyHz)} Hz"
}

private fun signed(value: Double): String {
    val formatted = formatDouble2(kotlin.math.abs(value))
    return if (value >= 0.0) "+$formatted" else "-$formatted"
}

private const val MIN_INTONATION_CENTS = -1200.0
private const val MAX_INTONATION_CENTS = 1200.0
private const val MANUAL_PRESET_ID = "manual"
private const val ASSISTANT_SUCCESS_THRESHOLD_CENTS = 0.10
private const val ASSISTANT_AUTO_ADVANCE_DELAY_MS = 180L

private fun tuningStateColor(state: TuningFeedbackState): Color {
    return when (state) {
        TuningFeedbackState.IN_TUNE -> KoraInTuneColor
        TuningFeedbackState.FLAT -> KoraFlatColor
        TuningFeedbackState.SHARP -> KoraSharpColor
    }
}

@Composable
private fun tuningStateLabel(state: TuningFeedbackState): String {
    return when (state) {
        TuningFeedbackState.IN_TUNE -> stringResource(Res.string.tuning_in_tune)
        TuningFeedbackState.FLAT -> stringResource(Res.string.tuning_flat)
        TuningFeedbackState.SHARP -> stringResource(Res.string.tuning_sharp)
    }
}

@Composable
private fun liveTunerPerformanceModeLabel(mode: LiveTunerPerformanceMode): String {
    return when (mode) {
        LiveTunerPerformanceMode.REALTIME -> stringResource(Res.string.live_tuner_mode_realtime_label)
        LiveTunerPerformanceMode.PRECISION -> stringResource(Res.string.live_tuner_mode_precision_label)
    }
}

private const val PEG_TUNING_IN_TUNE_THRESHOLD_CENTS = 200.0
