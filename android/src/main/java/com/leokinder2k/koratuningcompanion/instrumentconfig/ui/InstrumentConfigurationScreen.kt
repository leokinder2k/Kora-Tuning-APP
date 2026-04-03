package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.HomeLeverPosition
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackClassifier
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerPerformanceMode
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerUiState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerViewModel
import com.leokinder2k.koratuningcompanion.ui.components.ExpandableText
import com.leokinder2k.koratuningcompanion.ui.theme.KoraFlatColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraInTuneColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraSharpColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme
import kotlin.math.ln

@Composable
fun InstrumentConfigurationRoute(
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val configViewModel: InstrumentConfigurationViewModel = viewModel(
        factory = InstrumentConfigurationViewModel.factory(context)
    )
    val tunerViewModel: LiveTunerViewModel = viewModel(
        factory = LiveTunerViewModel.factory(context)
    )
    val uiState by configViewModel.uiState.collectAsStateWithLifecycle()
    val tunerUiState by tunerViewModel.uiState.collectAsStateWithLifecycle()

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
        onStartListening = tunerViewModel::startListening,
        onStopListening = tunerViewModel::stopListening,
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
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onAudioPermissionChanged
    )
    val isGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(isMuted) {
        if (isMuted) onStopListening()
    }
    LaunchedEffect(isGranted) {
        onAudioPermissionChanged(isGranted)
    }

    val referenceTonePlayer = remember { ReferenceTonePlayer() }
    var isReferenceTonePlaying by remember { mutableStateOf(false) }
    var isPlayingAll by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { referenceTonePlayer.release() } }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppPaused by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isAppPaused = event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isActive, isAppPaused) {
        if (!isActive || isAppPaused) {
            isPlayingAll = false
            isReferenceTonePlaying = false
            referenceTonePlayer.stop()
        }
    }

    var selectedTuningRowIndex by rememberSaveable(uiState.stringCount) { mutableIntStateOf(0) }
    selectedTuningRowIndex = selectedTuningRowIndex.coerceIn(0, (uiState.rows.size - 1).coerceAtLeast(0))
    val selectedRow = uiState.rows.getOrNull(selectedTuningRowIndex)
    val selectedPitch = selectedRow?.openPitchInput?.let(Pitch::parse)
    val selectedOpenCents = selectedRow?.openIntonationInput?.toDoubleOrNull()
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
            referenceTonePlayer.play(selectedTargetFrequencyHz * 2.0)
        } else if (!isReferenceTonePlaying || isMuted) {
            referenceTonePlayer.stop()
            if (isMuted) { isReferenceTonePlaying = false; isPlayingAll = false }
        }
    }

    var playbackDirection by rememberSaveable { mutableStateOf(PlaybackDirection.HIGH_TO_LOW) }
    var playbackSideOrder by rememberSaveable { mutableStateOf(PlaybackSideOrder.LEFT_FIRST) }

    val allRows = run {
        val rowsByNumber = uiState.rows.associateBy { it.stringNumber }
        val left = KoraStringLayout.leftOrder(uiState.stringCount)
        val right = KoraStringLayout.rightOrder(uiState.stringCount)
        val leftOrdered = if (playbackDirection == PlaybackDirection.HIGH_TO_LOW) left.reversed() else left
        val rightOrdered = if (playbackDirection == PlaybackDirection.HIGH_TO_LOW) right.reversed() else right
        val ordered = if (playbackSideOrder == PlaybackSideOrder.LEFT_FIRST) leftOrdered + rightOrdered else rightOrdered + leftOrdered
        ordered.mapNotNull { rowsByNumber[it] }
    }
    LaunchedEffect(isPlayingAll, isMuted, isActive) {
        if (!isPlayingAll || isMuted || !isActive) return@LaunchedEffect
        isReferenceTonePlaying = true
        for (row in allRows) {
            if (!isPlayingAll || isMuted || !isActive) break
            val pitch = Pitch.parse(row.openPitchInput)
            val cents = row.openIntonationInput.toDoubleOrNull()
            if (pitch != null && cents != null) {
                val freq = TunerTargetMatcher.pitchToFrequencyHz(pitch = pitch, centsOffset = cents)
                selectedTuningRowIndex = uiState.rows.indexOf(row).coerceAtLeast(0)
                referenceTonePlayer.play(freq * 2.0)
            }
            delay(4000L)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_instrument_configuration)) },
                actions = {
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(48.dp)) {
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
                    text = stringResource(R.string.instrument_config_section_strings),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.stringCount == 21,
                        onClick = { onStringCountSelected(21) },
                        label = { Text(stringResource(R.string.strings_count, 21)) }
                    )
                    FilterChip(
                        selected = uiState.stringCount == 22,
                        onClick = { onStringCountSelected(22) },
                        label = { Text(stringResource(R.string.strings_count, 22)) }
                    )
                }
            }

            item {
                ExpandableText(
                    text = when (uiState.tuningMode) {
                        KoraTuningMode.LEVERED -> stringResource(R.string.instrument_config_description_levered)
                        KoraTuningMode.PEG_TUNING -> stringResource(R.string.instrument_config_description_peg_tuning)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = stringResource(R.string.instrument_config_section_instrument_type),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.tuningMode == KoraTuningMode.LEVERED,
                        onClick = { onTuningModeSelected(KoraTuningMode.LEVERED) },
                        label = { Text(stringResource(R.string.instrument_type_levers)) }
                    )
                    FilterChip(
                        selected = uiState.tuningMode == KoraTuningMode.PEG_TUNING,
                        onClick = { onTuningModeSelected(KoraTuningMode.PEG_TUNING) },
                        label = { Text(stringResource(R.string.instrument_type_pegs)) }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.instrument_config_section_root_note),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                ExpandableText(
                    text = stringResource(R.string.instrument_config_description_instrument_key),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NoteName.entries) { note ->
                        FilterChip(
                            selected = uiState.rootNote == note,
                            onClick = { onRootNoteSelected(note) },
                            label = { Text(note.symbol) }
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.quick_start_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_1),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_2),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_3),
                            style = MaterialTheme.typography.bodyMedium
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
                                (isReferenceTonePlaying || isPlayingAll) && index == selectedTuningRowIndex -> {
                                    isPlayingAll = false
                                    isReferenceTonePlaying = false
                                }
                                isPlayingAll -> {
                                    isPlayingAll = false
                                    selectedTuningRowIndex = index
                                    isReferenceTonePlaying = true
                                }
                                else -> selectedTuningRowIndex = index
                            }
                        },
                        selectedPitchLabel = selectedPitch?.asText(),
                        selectedOpenCents = selectedOpenCents,
                        selectedTargetFrequencyHz = selectedTargetFrequencyHz,
                        detectedFrequencyHz = tunerUiState.detectedFrequencyHz,
                        selectedCentsDeviation = selectedCentsDeviation,
                        tuningState = tuningState,
                        tunerUiState = tunerUiState,
                        isReferenceTonePlaying = isReferenceTonePlaying || isPlayingAll,
                        isPlayingAll = isPlayingAll,
                        playbackDirection = playbackDirection,
                        playbackSideOrder = playbackSideOrder,
                        onPlaybackDirectionSelected = { playbackDirection = it },
                        onPlaybackSideOrderSelected = { playbackSideOrder = it },
                        onPlayAll = { isPlayingAll = true },
                        onStopReferenceTone = {
                            isPlayingAll = false
                            isReferenceTonePlaying = false
                        },
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onStartListening = onStartListening,
                        onStopListening = onStopListening
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.instrument_config_auto_save_note),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            itemsIndexed(uiState.rows, key = { _, row -> row.stringNumber }) { index, row ->
                StringConfigurationCard(
                    row = row,
                    showLeverFields = uiState.tuningMode == KoraTuningMode.LEVERED,
                    onOpenPitchChanged = { value -> onOpenPitchChanged(index, value) },
                    onOpenIntonationChanged = { value -> onOpenIntonationChanged(index, value) },
                    onClosedIntonationChanged = { value -> onClosedIntonationChanged(index, value) }
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
                    Text(stringResource(R.string.instrument_config_action_load_starter_profile))
                }
            }

            item {
                Button(
                    onClick = onSaveProfile,
                    enabled = uiState.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.instrument_config_action_save_profile))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.instrument_config_section_home_tuning),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.instrument_config_home_tuning_description),
                style = MaterialTheme.typography.bodyMedium
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
                                    R.string.instrument_config_home_tuning_string_chip,
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
                    text = stringResource(R.string.instrument_config_home_not_set),
                    style = MaterialTheme.typography.bodyMedium,
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
                        text = stringResource(R.string.instrument_config_action_set_current_as_home),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = onRestoreToHome,
                    enabled = allValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.instrument_config_action_restore_to_home),
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
    isReferenceTonePlaying: Boolean,
    isPlayingAll: Boolean,
    playbackDirection: PlaybackDirection,
    playbackSideOrder: PlaybackSideOrder,
    onPlaybackDirectionSelected: (PlaybackDirection) -> Unit,
    onPlaybackSideOrderSelected: (PlaybackSideOrder) -> Unit,
    onPlayAll: () -> Unit,
    onStopReferenceTone: () -> Unit,
    onRequestPermission: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.instrument_tuning_assistant_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Column(
                    modifier = Modifier.widthIn(max = 120.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!tunerUiState.hasAudioPermission) {
                        Button(
                            onClick = onRequestPermission
                        ) {
                            Text(stringResource(R.string.action_grant_mic_short))
                        }
                    } else if (!tunerUiState.isListening) {
                        Button(
                            onClick = onStartListening
                        ) {
                            Text(stringResource(R.string.action_start))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStopListening
                        ) {
                            Text(stringResource(R.string.action_stop))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.instrument_tuning_assistant_target_line,
                            selectedPitchLabel ?: "--",
                            selectedOpenCents?.let(::signed) ?: "--"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.instrument_tuning_assistant_target_hz_line,
                            formatFrequency(selectedTargetFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.instrument_tuning_assistant_detected_hz_line,
                            formatFrequency(detectedFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (selectedCentsDeviation != null) {
                        val color = tuningStateColor(requireNotNull(tuningState))
                        Text(
                            text = stringResource(
                                R.string.instrument_tuning_assistant_deviation_line,
                                signed(selectedCentsDeviation),
                                tuningStateLabel(tuningState)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.instrument_tuning_assistant_deviation_none),
                            style = MaterialTheme.typography.bodyMedium
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = playbackDirection == PlaybackDirection.HIGH_TO_LOW,
                    onClick = { onPlaybackDirectionSelected(PlaybackDirection.HIGH_TO_LOW) },
                    label = { Text("High→Low", style = MaterialTheme.typography.labelSmall) },
                    enabled = !isPlayingAll
                )
                FilterChip(
                    selected = playbackDirection == PlaybackDirection.LOW_TO_HIGH,
                    onClick = { onPlaybackDirectionSelected(PlaybackDirection.LOW_TO_HIGH) },
                    label = { Text("Low→High", style = MaterialTheme.typography.labelSmall) },
                    enabled = !isPlayingAll
                )
                FilterChip(
                    selected = playbackSideOrder == PlaybackSideOrder.LEFT_FIRST,
                    onClick = { onPlaybackSideOrderSelected(PlaybackSideOrder.LEFT_FIRST) },
                    label = { Text("L first", style = MaterialTheme.typography.labelSmall) },
                    enabled = !isPlayingAll
                )
                FilterChip(
                    selected = playbackSideOrder == PlaybackSideOrder.RIGHT_FIRST,
                    onClick = { onPlaybackSideOrderSelected(PlaybackSideOrder.RIGHT_FIRST) },
                    label = { Text("R first", style = MaterialTheme.typography.labelSmall) },
                    enabled = !isPlayingAll
                )
            }

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
                        Text(stringResource(R.string.live_tuner_reference_tone_action_stop))
                    }
                } else {
                    Button(
                        onClick = onPlayAll,
                        enabled = rows.any { row ->
                            Pitch.parse(row.openPitchInput) != null &&
                                row.openIntonationInput.toDoubleOrNull() != null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.instrument_config_action_play_all_strings))
                    }
                }
            }

            CompactStringSelectorRow(
                sideLabel = "L",
                rows = leftRows,
                selectedRowIndex = selectedRowIndex,
                onSelectedRowIndexChanged = onSelectedRowIndexChanged
            )
            CompactStringSelectorRow(
                sideLabel = "R",
                rows = rightRows,
                selectedRowIndex = selectedRowIndex,
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
    selectedRowIndex: Int,
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
                    selected = row.stringNumber - 1 == selectedRowIndex,
                    onClick = { onSelectedRowIndexChanged(row.stringNumber - 1) }
                )
            }
        }
    }
}

@Composable
private fun CompactStringChip(
    row: InstrumentStringRowUiState,
    selected: Boolean,
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
            .heightIn(min = 48.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Text(
            text = "${row.stringNumber} ${row.openPitchInput.ifBlank { "--" }}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                text = stringResource(R.string.tuning_flat),
                style = MaterialTheme.typography.labelSmall,
                color = if (showActiveIndicators && activeState == TuningFeedbackState.FLAT) {
                    KoraFlatColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(R.string.tuning_in_tune),
                style = MaterialTheme.typography.labelSmall,
                color = if (showActiveIndicators && activeState == TuningFeedbackState.IN_TUNE) {
                    KoraInTuneColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(R.string.tuning_sharp),
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
    showLeverFields: Boolean,
    onOpenPitchChanged: (String) -> Unit,
    onOpenIntonationChanged: (String) -> Unit,
    onClosedIntonationChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        Text(
            text = stringResource(R.string.instrument_config_string_title, row.stringNumber),
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = row.openPitchInput,
            onValueChange = onOpenPitchChanged,
            label = { Text(stringResource(R.string.instrument_config_open_pitch_label)) },
            placeholder = { Text(stringResource(R.string.instrument_config_open_pitch_placeholder)) },
            singleLine = true,
            isError = row.inputError != null,
            supportingText = {
                when {
                    row.inputError != null -> Text(row.inputError)
                    showLeverFields && row.closedPitch != null -> Text(
                        stringResource(R.string.instrument_config_closed_pitch_supporting, row.closedPitch)
                    )

                    showLeverFields -> Text(stringResource(R.string.instrument_config_open_pitch_supporting))
                    else -> Text(stringResource(R.string.instrument_config_open_pitch_supporting_peg))
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
                    label = { Text(stringResource(R.string.instrument_config_open_cents_label)) },
                    placeholder = { Text(stringResource(R.string.instrument_config_cents_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = row.openIntonationError != null,
                    supportingText = {
                        row.openIntonationError?.let { error ->
                            Text(error)
                        }
                    }
                )
                if (showLeverFields) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = row.closedIntonationInput,
                        onValueChange = onClosedIntonationChanged,
                        label = { Text(stringResource(R.string.instrument_config_closed_cents_label)) },
                        placeholder = { Text(stringResource(R.string.instrument_config_cents_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = row.closedIntonationError != null,
                        supportingText = {
                            row.closedIntonationError?.let { error ->
                                Text(error)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun centsDeviation(
    detectedFrequencyHz: Double,
    targetFrequencyHz: Double
): Double {
    return 1200.0 * (ln(detectedFrequencyHz / targetFrequencyHz) / ln(2.0))
}

private fun formatFrequency(frequencyHz: Double?): String {
    return if (frequencyHz == null || !frequencyHz.isFinite()) {
        "--"
    } else {
        "%.2f Hz".format(frequencyHz)
    }
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.2f".format(value)}" else "%.2f".format(value)
}

private enum class PlaybackDirection { HIGH_TO_LOW, LOW_TO_HIGH }
private enum class PlaybackSideOrder { LEFT_FIRST, RIGHT_FIRST }

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
        TuningFeedbackState.IN_TUNE -> stringResource(R.string.tuning_in_tune)
        TuningFeedbackState.FLAT -> stringResource(R.string.tuning_flat)
        TuningFeedbackState.SHARP -> stringResource(R.string.tuning_sharp)
    }
}


private const val PEG_TUNING_IN_TUNE_THRESHOLD_CENTS = 200.0

@Preview(showBackground = true)
@Composable
private fun InstrumentConfigurationScreenPreview() {
    KoraTuningSystemTheme {
        InstrumentConfigurationScreen(
            uiState = InstrumentConfigurationUiState(
                stringCount = 21,
                tuningMode = KoraTuningMode.LEVERED,
                presetOptions = listOf(
                    InstrumentPresetOptionUiState(
                        id = "manual",
                        displayName = "Manual"
                    )
                ),
                selectedPresetId = "manual",
                lowestLeftPitchInput = "F2",
                autoCalibrateEnabled = false,
                rows = listOf(
                    InstrumentStringRowUiState(
                        stringNumber = 1,
                        openPitchInput = "E3",
                        closedPitch = "F3",
                        inputError = null,
                        openIntonationInput = "-12.0",
                        closedIntonationInput = "-2.0",
                        openIntonationError = null,
                        closedIntonationError = null
                    ),
                    InstrumentStringRowUiState(
                        stringNumber = 2,
                        openPitchInput = "F#3",
                        closedPitch = "G3",
                        inputError = null,
                        openIntonationInput = "0.0",
                        closedIntonationInput = "3.9",
                        openIntonationError = null,
                        closedIntonationError = null
                    ),
                    InstrumentStringRowUiState(
                        stringNumber = 3,
                        openPitchInput = "Q9",
                        closedPitch = null,
                        inputError = "Use format like E3 or F#4.",
                        openIntonationInput = "abc",
                        closedIntonationInput = "0.0",
                        openIntonationError = "Use cents like -13.7 or 0.0.",
                        closedIntonationError = null
                    )
                ),
                canSave = false,
                statusMessage = null,
                rootNote = NoteName.F,
                basePitchInputs = listOf("E3", "F#3", "Q9"),
                basePitchErrors = listOf(null, null, "Use format like E3 or F#4."),
                homeLeverPosition = HomeLeverPosition.OPEN
            ),
            tunerUiState = LiveTunerUiState(
                hasAudioPermission = false,
                performanceMode = LiveTunerPerformanceMode.PRECISION,
                isListening = false,
                detectedFrequencyHz = null,
                confidence = 0.0,
                rms = 0.0,
                errorMessage = null
            ),
            onStringCountSelected = {},
            onTuningModeSelected = {},
            onHomeLeverPositionSelected = {},
            onOpenPitchChanged = { _, _ -> },
            onOpenIntonationChanged = { _, _ -> },
            onClosedIntonationChanged = { _, _ -> },
            onLoadStarterProfile = {},
            onSaveProfile = {},
            onSetCurrentAsHome = {},
            onRestoreToHome = {},
            onAudioPermissionChanged = {},
            onStartListening = {},
            onStopListening = {},
            onRootNoteSelected = {}
        )
    }
}

