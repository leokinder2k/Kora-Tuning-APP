package com.leokinder2k.koratuningcompanion.livetuner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTarget
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackClassifier
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackState
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationUiState
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleTypeDropdownMenus
import com.leokinder2k.koratuningcompanion.ui.theme.KoraFlatColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraInTuneColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraSharpColor
import kotlin.math.abs

@Composable
fun LiveTunerRoute(
    scaleUiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: LiveTunerViewModel = viewModel(
        factory = LiveTunerViewModel.factory(context)
    )
    val tunerUiState by viewModel.uiState.collectAsStateWithLifecycle()

    LiveTunerScreen(
        scaleUiState = scaleUiState,
        tunerUiState = tunerUiState,
        onRootNoteSelected = onRootNoteSelected,
        onScaleTypeSelected = onScaleTypeSelected,
        onAudioPermissionChanged = viewModel::onAudioPermissionChanged,
        onPerformanceModeSelected = viewModel::onPerformanceModeSelected,
        onStartListening = viewModel::startListening,
        onStopListening = viewModel::stopListening,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LiveTunerScreen(
    scaleUiState: ScaleCalculationUiState,
    tunerUiState: LiveTunerUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
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

    val targets = TunerTargetMatcher.buildTargets(scaleUiState.result.pegCorrectTable)
    val tuningMode = scaleUiState.result.request.instrumentProfile.tuningMode
    val inTuneThresholdCents = when (tuningMode) {
        KoraTuningMode.LEVERED -> TuningFeedbackClassifier.DEFAULT_IN_TUNE_THRESHOLD_CENTS
        KoraTuningMode.PEG_TUNING -> PEG_TUNING_IN_TUNE_THRESHOLD_CENTS
    }
    var selectedTargetStringNumber by rememberSaveable(
        scaleUiState.rootNote,
        scaleUiState.scaleType,
        scaleUiState.result.request.instrumentProfile.stringCount
    ) {
        mutableIntStateOf(targets.firstOrNull()?.stringNumber ?: -1)
    }
    LaunchedEffect(targets) {
        if (targets.none { target -> target.stringNumber == selectedTargetStringNumber }) {
            selectedTargetStringNumber = targets.firstOrNull()?.stringNumber ?: -1
        }
    }
    val selectedTarget = targets.firstOrNull { target ->
        target.stringNumber == selectedTargetStringNumber
    }

    val referenceTonePlayer = remember { ReferenceTonePlayer() }
    var isReferenceTonePlaying by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isReferenceTonePlaying, selectedTarget?.stringNumber) {
        if (isReferenceTonePlaying && selectedTarget != null) {
            referenceTonePlayer.play(selectedTarget.targetFrequencyHz)
        } else {
            referenceTonePlayer.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            referenceTonePlayer.release()
        }
    }

    val match = tunerUiState.detectedFrequencyHz?.let { detected ->
        TunerTargetMatcher.matchNearestTarget(detected, targets)
    }
    LaunchedEffect(match?.target?.stringNumber) {
        val matchStringNumber = match?.target?.stringNumber ?: return@LaunchedEffect
        if (targets.any { target -> target.stringNumber == matchStringNumber }) {
            selectedTargetStringNumber = matchStringNumber
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_live_tuner)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = scaleUiState.profileStatus,
                style = MaterialTheme.typography.bodyMedium
            )

            SelectionControls(
                rootNote = scaleUiState.rootNote,
                scaleType = scaleUiState.scaleType,
                onRootNoteSelected = onRootNoteSelected,
                onScaleTypeSelected = onScaleTypeSelected
            )

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
                        text = stringResource(R.string.live_tuner_quick_start_step_1),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.live_tuner_quick_start_step_2),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.live_tuner_quick_start_step_3),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (tuningMode == KoraTuningMode.PEG_TUNING) {
                        Text(
                            text = stringResource(R.string.live_tuner_peg_tuning_note),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_tuner_section_microphone),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.live_tuner_microphone_mode_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiveTunerPerformanceMode.entries) { mode ->
                            FilterChip(
                                selected = mode == tunerUiState.performanceMode,
                                onClick = { onPerformanceModeSelected(mode) },
                                label = { Text(liveTunerPerformanceModeLabel(mode)) }
                            )
                        }
                    }
                    Text(
                        text = liveTunerPerformanceModeSummary(tunerUiState.performanceMode),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!tunerUiState.hasAudioPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        ) {
                            Text(stringResource(R.string.action_grant_microphone_permission))
                        }
                    } else {
                        if (!tunerUiState.isListening) {
                            Button(onClick = onStartListening) {
                                Text(stringResource(R.string.live_tuner_action_start))
                            }
                        } else {
                            OutlinedButton(onClick = onStopListening) {
                                Text(stringResource(R.string.live_tuner_action_stop))
                            }
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_tuner_section_detection),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.live_tuner_detected_pitch_line,
                            formatFrequency(tunerUiState.detectedFrequencyHz)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.live_tuner_confidence_line,
                            formatPercent(tunerUiState.confidence)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.live_tuner_signal_rms_line,
                            "%.4f".format(tunerUiState.rms)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_tuner_section_nearest_target),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (match == null) {
                        Text(
                            text = stringResource(R.string.live_tuner_nearest_target_no_match),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val tuningState = TuningFeedbackClassifier.classify(
                            centsDeviation = match.centsDeviation,
                            inTuneThresholdCents = inTuneThresholdCents
                        )
                        val tuningColor = tuningStateColor(tuningState)
                        Text(
                            text = stringResource(
                                R.string.live_tuner_nearest_target_string_line,
                                match.target.roleLabel,
                                match.target.stringNumber
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(
                                R.string.live_tuner_nearest_target_pitch_line,
                                match.target.targetPitch.asText(),
                                formatFrequency(match.target.targetFrequencyHz)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(
                                R.string.live_tuner_nearest_target_intonation_line,
                                signed(match.target.targetIntonationCents)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(
                                R.string.live_tuner_nearest_target_status_line,
                                tuningStateLabel(tuningState)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tuningColor
                        )
                        Text(
                            text = stringResource(
                                R.string.live_tuner_nearest_target_deviation_line,
                                signed(match.centsDeviation)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = tuningColor
                        )
                        if (tuningMode == KoraTuningMode.LEVERED) {
                            Text(
                                text = stringResource(
                                    R.string.live_tuner_nearest_target_lever_line,
                                    match.target.requiredLeverState.name
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = if (match.target.pegRetuneSemitones != 0) {
                                stringResource(
                                    R.string.live_tuner_nearest_target_peg_adjustment_line,
                                    signed(match.target.pegRetuneSemitones.toDouble())
                                )
                            } else {
                                stringResource(R.string.live_tuner_nearest_target_peg_adjustment_none)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            ReferenceToneCard(
                targets = targets,
                selectedTargetStringNumber = selectedTargetStringNumber,
                onTargetSelected = { selected -> selectedTargetStringNumber = selected },
                isReferenceTonePlaying = isReferenceTonePlaying,
                onPlay = { isReferenceTonePlaying = true },
                onStop = { isReferenceTonePlaying = false }
            )

            Text(
                text = stringResource(R.string.live_tuner_tip),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SelectionControls(
    rootNote: NoteName,
    scaleType: ScaleType,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.scale_root_note_label),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NoteName.entries) { note ->
                FilterChip(
                    selected = note == rootNote,
                    onClick = { onRootNoteSelected(note) },
                    label = { Text(note.symbol) }
                )
            }
        }

        Text(
            text = stringResource(R.string.scale_type_label),
            style = MaterialTheme.typography.titleMedium
        )
        ScaleTypeDropdownMenus(
            selectedScaleType = scaleType,
            onScaleTypeSelected = onScaleTypeSelected
        )
    }
}

@Composable
private fun ReferenceToneCard(
    targets: List<TunerTarget>,
    selectedTargetStringNumber: Int,
    onTargetSelected: (Int) -> Unit,
    isReferenceTonePlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    val leftTargets = targets
        .filter { target -> target.roleLabel.startsWith("L") }
        .sortedBy { target -> targetRolePosition(target) }
    val rightTargets = targets
        .filter { target -> target.roleLabel.startsWith("R") }
        .sortedBy { target -> targetRolePosition(target) }

    val selectedTarget = targets.firstOrNull { target ->
        target.stringNumber == selectedTargetStringNumber
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.live_tuner_reference_tone_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.live_tuner_reference_tone_none),
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }

            Text(
                text = stringResource(R.string.instrument_tuning_assistant_left_side),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = leftTargets, key = { target -> target.stringNumber }) { target ->
                    FilterChip(
                        selected = target.stringNumber == selectedTargetStringNumber,
                        onClick = { onTargetSelected(target.stringNumber) },
                        label = { Text("${target.roleLabel} ${target.targetPitch.asText()}") }
                    )
                }
            }
            Text(
                text = stringResource(R.string.instrument_tuning_assistant_right_side),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = rightTargets, key = { target -> target.stringNumber }) { target ->
                    FilterChip(
                        selected = target.stringNumber == selectedTargetStringNumber,
                        onClick = { onTargetSelected(target.stringNumber) },
                        label = { Text("${target.roleLabel} ${target.targetPitch.asText()}") }
                    )
                }
            }

            if (selectedTarget != null) {
                Text(
                    text = stringResource(
                        R.string.live_tuner_reference_tone_selected_line,
                        selectedTarget.roleLabel,
                        selectedTarget.stringNumber,
                        selectedTarget.targetPitch.asText()
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        R.string.live_tuner_reference_tone_frequency_line,
                        formatFrequency(selectedTarget.targetFrequencyHz)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isReferenceTonePlaying) {
                    Button(onClick = onPlay) {
                        Text(stringResource(R.string.live_tuner_reference_tone_action_play))
                    }
                } else {
                    OutlinedButton(onClick = onStop) {
                        Text(stringResource(R.string.live_tuner_reference_tone_action_stop))
                    }
                }
            }
        }
    }
}

private fun formatFrequency(frequencyHz: Double?): String {
    return if (frequencyHz == null || !frequencyHz.isFinite()) {
        "--"
    } else {
        "%.2f Hz".format(frequencyHz)
    }
}

private fun formatPercent(value: Double): String {
    val safe = value.coerceIn(0.0, 1.0) * 100.0
    return "%.1f%%".format(safe)
}

private fun signed(value: Double): String {
    val rounded = if (abs(value) < 0.05) 0.0 else value
    return if (rounded >= 0.0) "+${"%.2f".format(rounded)}" else "%.2f".format(rounded)
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

private fun targetRolePosition(target: TunerTarget): Int {
    return target.roleLabel
        .drop(1)
        .toIntOrNull()
        ?: Int.MAX_VALUE
}

private const val PEG_TUNING_IN_TUNE_THRESHOLD_CENTS = 200.0

@Composable
private fun liveTunerPerformanceModeLabel(mode: LiveTunerPerformanceMode): String {
    return when (mode) {
        LiveTunerPerformanceMode.REALTIME -> stringResource(R.string.live_tuner_mode_realtime_label)
        LiveTunerPerformanceMode.PRECISION -> stringResource(R.string.live_tuner_mode_precision_label)
    }
}

@Composable
private fun liveTunerPerformanceModeSummary(mode: LiveTunerPerformanceMode): String {
    return when (mode) {
        LiveTunerPerformanceMode.REALTIME -> stringResource(R.string.live_tuner_mode_realtime_summary)
        LiveTunerPerformanceMode.PRECISION -> stringResource(R.string.live_tuner_mode_precision_summary)
    }
}

