package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraStringLayout
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackClassifier
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerPerformanceMode
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerUiState
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerViewModel
import com.leokinder2k.koratuningcompanion.ui.theme.KoraFlatColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraInTuneColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraSharpColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme
import kotlin.math.ln

@Composable
fun InstrumentConfigurationRoute(modifier: Modifier = Modifier) {
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
        onOpenPitchChanged = configViewModel::onOpenPitchChanged,
        onOpenIntonationChanged = configViewModel::onOpenIntonationChanged,
        onClosedIntonationChanged = configViewModel::onClosedIntonationChanged,
        onLoadStarterProfile = configViewModel::loadStarterProfile,
        onSaveProfile = configViewModel::saveProfile,
        onAudioPermissionChanged = tunerViewModel::onAudioPermissionChanged,
        onPerformanceModeSelected = tunerViewModel::onPerformanceModeSelected,
        onStartListening = tunerViewModel::startListening,
        onStopListening = tunerViewModel::stopListening,
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
    onOpenPitchChanged: (rowIndex: Int, value: String) -> Unit,
    onOpenIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onClosedIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onLoadStarterProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    onAudioPermissionChanged: (Boolean) -> Unit,
    onPerformanceModeSelected: (LiveTunerPerformanceMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
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
    LaunchedEffect(isGranted) {
        onAudioPermissionChanged(isGranted)
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
                title = { Text(stringResource(R.string.title_instrument_configuration)) }
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
                Text(
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.quick_start_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_1),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_2),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.instrument_config_quick_start_step_3),
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
                        onSelectedRowIndexChanged = { index -> selectedTuningRowIndex = index },
                        selectedPitchLabel = selectedPitch?.asText(),
                        selectedOpenCents = selectedOpenCents,
                        selectedTargetFrequencyHz = selectedTargetFrequencyHz,
                        detectedFrequencyHz = tunerUiState.detectedFrequencyHz,
                        selectedCentsDeviation = selectedCentsDeviation,
                        tuningState = tuningState,
                        tunerUiState = tunerUiState,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onPerformanceModeSelected = onPerformanceModeSelected,
                        onStartListening = onStartListening,
                        onStopListening = onStopListening
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.instrument_config_auto_save_note),
                    style = MaterialTheme.typography.bodySmall
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
    onRequestPermission: () -> Unit,
    onPerformanceModeSelected: (LiveTunerPerformanceMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val rowsByNumber = rows.associateBy { row -> row.stringNumber }
    val leftRows = KoraStringLayout.leftOrder(rows.size)
        .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }
    val rightRows = KoraStringLayout.rightOrder(rows.size)
        .mapNotNull { stringNumber -> rowsByNumber[stringNumber] }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.instrument_tuning_assistant_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.instrument_tuning_assistant_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    modifier = Modifier.widthIn(max = 210.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.instrument_tuning_assistant_controls_title),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LiveTunerPerformanceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = mode == tunerUiState.performanceMode,
                                onClick = { onPerformanceModeSelected(mode) },
                                label = { Text(liveTunerPerformanceModeLabel(mode)) }
                            )
                        }
                    }
                    if (!tunerUiState.hasAudioPermission) {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_grant_mic_short))
                        }
                    } else if (!tunerUiState.isListening) {
                        Button(
                            onClick = onStartListening,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_start))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStopListening,
                            modifier = Modifier.fillMaxWidth()
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.instrument_tuning_assistant_target_line,
                            selectedPitchLabel ?: "--",
                            selectedOpenCents?.let(::signed) ?: "--"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.instrument_tuning_assistant_target_hz_line,
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
                            R.string.instrument_tuning_assistant_detected_hz_line,
                            formatFrequency(detectedFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (selectedCentsDeviation != null) {
                        val color = tuningStateColor(requireNotNull(tuningState))
                        Text(
                            text = stringResource(
                                R.string.instrument_tuning_assistant_deviation_line,
                                signed(selectedCentsDeviation),
                                tuningStateLabel(tuningState)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.instrument_tuning_assistant_deviation_none),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            TuningLightsRow(activeState = tuningState)

            Text(
                text = stringResource(R.string.instrument_tuning_assistant_left_side),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = leftRows, key = { row -> row.stringNumber }) { row ->
                    FilterChip(
                        selected = row.stringNumber - 1 == selectedRowIndex,
                        onClick = { onSelectedRowIndexChanged(row.stringNumber - 1) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.instrument_tuning_assistant_string_chip,
                                    row.stringNumber,
                                    row.openPitchInput.ifBlank { "--" }
                                )
                            )
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.instrument_tuning_assistant_right_side),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = rightRows, key = { row -> row.stringNumber }) { row ->
                    FilterChip(
                        selected = row.stringNumber - 1 == selectedRowIndex,
                        onClick = { onSelectedRowIndexChanged(row.stringNumber - 1) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.instrument_tuning_assistant_string_chip,
                                    row.stringNumber,
                                    row.openPitchInput.ifBlank { "--" }
                                )
                            )
                        }
                    )
                }
            }

            tunerUiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TuningLightsRow(activeState: TuningFeedbackState?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TuningLight(
            label = stringResource(R.string.tuning_flat),
            color = KoraFlatColor,
            isActive = activeState == TuningFeedbackState.FLAT
        )
        TuningLight(
            label = stringResource(R.string.tuning_in_tune),
            color = KoraInTuneColor,
            isActive = activeState == TuningFeedbackState.IN_TUNE
        )
        TuningLight(
            label = stringResource(R.string.tuning_sharp),
            color = KoraSharpColor,
            isActive = activeState == TuningFeedbackState.SHARP
        )
    }
}

@Composable
private fun TuningLight(
    label: String,
    color: Color,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (isActive) color else color.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
                .padding(12.dp),
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

@Composable
private fun liveTunerPerformanceModeLabel(mode: LiveTunerPerformanceMode): String {
    return when (mode) {
        LiveTunerPerformanceMode.REALTIME -> stringResource(R.string.live_tuner_mode_realtime_label)
        LiveTunerPerformanceMode.PRECISION -> stringResource(R.string.live_tuner_mode_precision_label)
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
                statusMessage = null
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
            onOpenPitchChanged = { _, _ -> },
            onOpenIntonationChanged = { _, _ -> },
            onClosedIntonationChanged = { _, _ -> },
            onLoadStarterProfile = {},
            onSaveProfile = {},
            onAudioPermissionChanged = {},
            onPerformanceModeSelected = {},
            onStartListening = {},
            onStopListening = {}
        )
    }
}

