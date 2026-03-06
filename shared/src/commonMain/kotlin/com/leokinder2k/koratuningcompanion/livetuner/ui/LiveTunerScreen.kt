package com.leokinder2k.koratuningcompanion.livetuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTarget
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackClassifier
import com.leokinder2k.koratuningcompanion.livetuner.model.TuningFeedbackState
import com.leokinder2k.koratuningcompanion.platform.isMicPermissionGranted
import com.leokinder2k.koratuningcompanion.platform.rememberMicPermissionLauncher
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationUiState
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleTypeDropdownMenus
import com.leokinder2k.koratuningcompanion.ui.theme.KoraFlatColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraInTuneColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraSharpColor
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

@Composable
fun LiveTunerRoute(
    scaleUiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: LiveTunerViewModel = viewModel { LiveTunerViewModel() }
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
    val permissionLauncher = rememberMicPermissionLauncher(onResult = onAudioPermissionChanged)
    val isGranted = isMicPermissionGranted()

    LaunchedEffect(isGranted) {
        onAudioPermissionChanged(isGranted)
    }

    val guidedSteps = scaleUiState.result.pegCorrectTable
    val targets = TunerTargetMatcher.buildTargets(guidedSteps)
    val tuningMode = scaleUiState.result.request.instrumentProfile.tuningMode
    val inTuneThresholdCents = when (tuningMode) {
        KoraTuningMode.LEVERED -> TuningFeedbackClassifier.DEFAULT_IN_TUNE_THRESHOLD_CENTS
        KoraTuningMode.PEG_TUNING -> PEG_TUNING_IN_TUNE_THRESHOLD_CENTS
    }

    var selectedTargetStringNumber by rememberSaveable(
        scaleUiState.rootNote,
        scaleUiState.scaleType,
        scaleUiState.result.request.instrumentProfile.stringCount
    ) { mutableStateOf(targets.firstOrNull()?.stringNumber ?: -1) }

    LaunchedEffect(targets) {
        if (targets.none { it.stringNumber == selectedTargetStringNumber }) {
            selectedTargetStringNumber = targets.firstOrNull()?.stringNumber ?: -1
        }
    }

    val selectedTarget = targets.firstOrNull { it.stringNumber == selectedTargetStringNumber }

    var currentGuidedStepIndex by rememberSaveable(
        scaleUiState.rootNote,
        scaleUiState.scaleType,
        scaleUiState.result.request.instrumentProfile.stringCount
    ) { mutableStateOf(0) }

    if (guidedSteps.isNotEmpty()) {
        currentGuidedStepIndex = currentGuidedStepIndex.coerceIn(0, guidedSteps.lastIndex)
    } else {
        currentGuidedStepIndex = 0
    }

    LaunchedEffect(guidedSteps, currentGuidedStepIndex) {
        val guidedStepStringNumber = guidedSteps.getOrNull(currentGuidedStepIndex)?.stringNumber
            ?: return@LaunchedEffect
        if (targets.any { it.stringNumber == guidedStepStringNumber }) {
            selectedTargetStringNumber = guidedStepStringNumber
        }
    }

    val referenceTonePlayer = remember { ReferenceTonePlayer() }
    var isReferenceTonePlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isReferenceTonePlaying, selectedTarget?.stringNumber) {
        if (isReferenceTonePlaying && selectedTarget != null) {
            referenceTonePlayer.play(selectedTarget.targetFrequencyHz * REFERENCE_TONE_OCTAVE_MULTIPLIER)
        } else {
            referenceTonePlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { referenceTonePlayer.release() }
    }

    val match = tunerUiState.detectedFrequencyHz?.let { detected ->
        TunerTargetMatcher.matchNearestTarget(detected, targets)
    }

    LaunchedEffect(match?.target?.stringNumber) {
        val matchStringNumber = match?.target?.stringNumber ?: return@LaunchedEffect
        if (targets.any { it.stringNumber == matchStringNumber }) {
            selectedTargetStringNumber = matchStringNumber
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_live_tuner)) }
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
            Text(text = scaleUiState.profileStatus, style = MaterialTheme.typography.bodyMedium)

            SelectionControls(
                rootNote = scaleUiState.rootNote,
                scaleType = scaleUiState.scaleType,
                onRootNoteSelected = onRootNoteSelected,
                onScaleTypeSelected = onScaleTypeSelected
            )

            GuidedTuningCard(
                steps = guidedSteps,
                currentStepIndex = currentGuidedStepIndex,
                onPreviousStep = { currentGuidedStepIndex = (currentGuidedStepIndex - 1).coerceAtLeast(0) },
                onNextStep = {
                    if (guidedSteps.isNotEmpty()) {
                        currentGuidedStepIndex = (currentGuidedStepIndex + 1).coerceAtMost(guidedSteps.lastIndex)
                    }
                },
                tuningMode = tuningMode
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = stringResource(Res.string.quick_start_title), style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(Res.string.live_tuner_quick_start_step_1), style = MaterialTheme.typography.bodySmall)
                    Text(text = stringResource(Res.string.live_tuner_quick_start_step_2), style = MaterialTheme.typography.bodySmall)
                    Text(text = stringResource(Res.string.live_tuner_quick_start_step_3), style = MaterialTheme.typography.bodySmall)
                    if (tuningMode == KoraTuningMode.PEG_TUNING) {
                        Text(text = stringResource(Res.string.live_tuner_peg_tuning_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(Res.string.live_tuner_section_microphone), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(Res.string.live_tuner_microphone_mode_label), style = MaterialTheme.typography.bodyMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiveTunerPerformanceMode.entries) { mode ->
                            FilterChip(
                                selected = mode == tunerUiState.performanceMode,
                                onClick = { onPerformanceModeSelected(mode) },
                                label = { Text(liveTunerPerformanceModeLabel(mode)) }
                            )
                        }
                    }
                    Text(text = liveTunerPerformanceModeSummary(tunerUiState.performanceMode), style = MaterialTheme.typography.bodySmall)
                    if (!tunerUiState.hasAudioPermission) {
                        Button(onClick = { permissionLauncher() }) {
                            Text(stringResource(Res.string.action_grant_microphone_permission))
                        }
                    } else if (!tunerUiState.isListening) {
                        Button(onClick = onStartListening) {
                            Text(stringResource(Res.string.live_tuner_action_start))
                        }
                    } else {
                        OutlinedButton(onClick = onStopListening) {
                            Text(stringResource(Res.string.live_tuner_action_stop))
                        }
                    }
                    tunerUiState.errorMessage?.let { message ->
                        Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = stringResource(Res.string.live_tuner_section_detection), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(Res.string.live_tuner_detected_pitch_line, formatFrequency(tunerUiState.detectedFrequencyHz)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(Res.string.live_tuner_confidence_line, formatPercent(tunerUiState.confidence)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(Res.string.live_tuner_signal_rms_line, "%.4f".format(tunerUiState.rms)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = stringResource(Res.string.live_tuner_section_nearest_target), style = MaterialTheme.typography.titleMedium)
                    val showActiveTuningIndicators = tunerUiState.isListening && match != null
                    val activeTuningState = match?.let {
                        TuningFeedbackClassifier.classify(
                            centsDeviation = it.centsDeviation,
                            inTuneThresholdCents = inTuneThresholdCents
                        )
                    }
                    TuningGradientBar(
                        centsDeviation = match?.centsDeviation,
                        activeState = activeTuningState,
                        showActiveMarker = showActiveTuningIndicators
                    )
                    if (match == null) {
                        Text(text = stringResource(Res.string.live_tuner_nearest_target_no_match), style = MaterialTheme.typography.bodySmall)
                    } else {
                        val tuningState = requireNotNull(activeTuningState)
                        val tuningColor = tuningStateColor(tuningState)
                        Text(
                            text = stringResource(Res.string.live_tuner_nearest_target_string_line, match.target.roleLabel, match.target.stringNumber),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(Res.string.live_tuner_nearest_target_pitch_line, match.target.targetPitch.asText(), formatFrequency(match.target.targetFrequencyHz)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(Res.string.live_tuner_nearest_target_intonation_line, signedDouble(match.target.targetIntonationCents)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(Res.string.live_tuner_nearest_target_status_line, tuningStateLabel(tuningState)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tuningColor
                        )
                        if (showActiveTuningIndicators) {
                            TuningLightsRow(activeState = tuningState)
                        }
                        Text(
                            text = stringResource(Res.string.live_tuner_nearest_target_deviation_line, signedDouble(match.centsDeviation)),
                            style = MaterialTheme.typography.bodySmall,
                            color = tuningColor
                        )
                        if (tuningMode == KoraTuningMode.LEVERED) {
                            Text(
                                text = stringResource(Res.string.live_tuner_nearest_target_lever_line, match.target.requiredLeverState.name),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = if (match.target.pegRetuneSemitones != 0) {
                                stringResource(Res.string.live_tuner_nearest_target_peg_adjustment_line, signedInt(match.target.pegRetuneSemitones))
                            } else {
                                stringResource(Res.string.live_tuner_nearest_target_peg_adjustment_none)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            ReferenceToneCard(
                targets = targets,
                selectedTargetStringNumber = selectedTargetStringNumber,
                onTargetSelected = { selected ->
                    selectedTargetStringNumber = selected
                    isReferenceTonePlaying = true
                },
                isReferenceTonePlaying = isReferenceTonePlaying,
                onPlay = { isReferenceTonePlaying = true },
                onStop = { isReferenceTonePlaying = false }
            )

            Text(text = stringResource(Res.string.live_tuner_tip), style = MaterialTheme.typography.bodySmall)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(Res.string.live_tuner_save_preset_prompt_title), style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(Res.string.live_tuner_save_preset_prompt_body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GuidedTuningCard(
    steps: List<PegCorrectStringResult>,
    currentStepIndex: Int,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    tuningMode: KoraTuningMode
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.title_guided_setup), style = MaterialTheme.typography.titleMedium)
            if (steps.isEmpty()) {
                Text(text = stringResource(Res.string.guided_setup_no_steps), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            val step = steps[currentStepIndex]
            val progress = (currentStepIndex + 1).toFloat() / steps.size.toFloat()
            Text(
                text = stringResource(Res.string.guided_setup_step_counter, currentStepIndex + 1, steps.size),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(Res.string.guided_setup_string_line, step.role.asLabel(), step.stringNumber),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(Res.string.guided_setup_target_pitch_line, step.selectedPitch.asText()),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(Res.string.guided_setup_intonation_line, signedDouble(step.selectedIntonationCents)),
                style = MaterialTheme.typography.bodySmall
            )
            if (tuningMode == KoraTuningMode.LEVERED) {
                Text(
                    text = stringResource(Res.string.guided_setup_lever_line, step.selectedLeverState.name),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = if (step.pegRetuneRequired) {
                    stringResource(Res.string.guided_setup_peg_adjustment_line, signedInt(step.pegRetuneSemitones))
                } else {
                    stringResource(Res.string.guided_setup_peg_adjustment_none)
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreviousStep, enabled = currentStepIndex > 0, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.action_previous))
                }
                Button(onClick = onNextStep, enabled = currentStepIndex < steps.lastIndex, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.action_next))
                }
            }
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
        Text(text = stringResource(Res.string.scale_root_note_label), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NoteName.entries) { note ->
                FilterChip(
                    selected = note == rootNote,
                    onClick = { onRootNoteSelected(note) },
                    label = { Text(note.symbol) }
                )
            }
        }
        Text(text = stringResource(Res.string.scale_type_label), style = MaterialTheme.typography.titleMedium)
        ScaleTypeDropdownMenus(selectedScaleType = scaleType, onScaleTypeSelected = onScaleTypeSelected)
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
    val leftTargets = targets.filter { it.roleLabel.startsWith("L") }.sortedBy { targetRolePosition(it) }
    val rightTargets = targets.filter { it.roleLabel.startsWith("R") }.sortedBy { targetRolePosition(it) }
    val selectedTarget = targets.firstOrNull { it.stringNumber == selectedTargetStringNumber }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.live_tuner_reference_tone_title), style = MaterialTheme.typography.titleMedium)
            if (targets.isEmpty()) {
                Text(text = stringResource(Res.string.live_tuner_reference_tone_none), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Text(text = stringResource(Res.string.instrument_tuning_assistant_left_side), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = leftTargets, key = { it.stringNumber }) { target ->
                    FilterChip(
                        selected = target.stringNumber == selectedTargetStringNumber,
                        onClick = { onTargetSelected(target.stringNumber) },
                        label = { Text("${target.roleLabel} ${target.targetPitch.asText()}") }
                    )
                }
            }
            Text(text = stringResource(Res.string.instrument_tuning_assistant_right_side), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = rightTargets, key = { it.stringNumber }) { target ->
                    FilterChip(
                        selected = target.stringNumber == selectedTargetStringNumber,
                        onClick = { onTargetSelected(target.stringNumber) },
                        label = { Text("${target.roleLabel} ${target.targetPitch.asText()}") }
                    )
                }
            }
            if (selectedTarget != null) {
                val shiftedFrequencyHz = selectedTarget.targetFrequencyHz * REFERENCE_TONE_OCTAVE_MULTIPLIER
                Text(
                    text = stringResource(Res.string.live_tuner_reference_tone_selected_line, selectedTarget.roleLabel, selectedTarget.stringNumber, selectedTarget.targetPitch.asText()),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(Res.string.live_tuner_reference_tone_frequency_line, formatFrequency(shiftedFrequencyHz)),
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isReferenceTonePlaying) {
                    Button(onClick = onPlay) { Text(stringResource(Res.string.live_tuner_reference_tone_action_play)) }
                } else {
                    OutlinedButton(onClick = onStop) { Text(stringResource(Res.string.live_tuner_reference_tone_action_stop)) }
                }
            }
        }
    }
}

private fun formatFrequency(frequencyHz: Double?): String {
    return if (frequencyHz == null || !frequencyHz.isFinite()) "--" else "%.2f Hz".format(frequencyHz)
}

private fun formatPercent(value: Double): String {
    val safe = value.coerceIn(0.0, 1.0) * 100.0
    return "%.1f%%".format(safe)
}

private fun signedInt(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun signedDouble(value: Double): String {
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
        TuningFeedbackState.IN_TUNE -> stringResource(Res.string.tuning_in_tune)
        TuningFeedbackState.FLAT -> stringResource(Res.string.tuning_flat)
        TuningFeedbackState.SHARP -> stringResource(Res.string.tuning_sharp)
    }
}

@Composable
private fun TuningGradientBar(centsDeviation: Double?, activeState: TuningFeedbackState?, showActiveMarker: Boolean) {
    val markerFraction = (
        ((centsDeviation ?: 0.0).coerceIn(-LIVE_TUNER_GRADIENT_RANGE_CENTS, LIVE_TUNER_GRADIENT_RANGE_CENTS) / LIVE_TUNER_GRADIENT_RANGE_CENTS) + 1.0
    ).toFloat() * 0.5f
    val markerColor = if (activeState == TuningFeedbackState.IN_TUNE) Color.White else Color.White.copy(alpha = 0.92f)
    val markerWidth = 4.dp
    val trackShape = RoundedCornerShape(12.dp)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        val markerOffset = (maxWidth - markerWidth) * markerFraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(colors = listOf(KoraFlatColor, KoraInTuneColor, KoraSharpColor)),
                    shape = trackShape
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), trackShape)
        )
        if (showActiveMarker) {
            Box(
                modifier = Modifier
                    .padding(start = markerOffset)
                    .width(markerWidth)
                    .fillMaxSize()
                    .background(markerColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun TuningLightsRow(activeState: TuningFeedbackState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TuningLightItem(stringResource(Res.string.tuning_flat), KoraFlatColor, activeState == TuningFeedbackState.FLAT)
        TuningLightItem(stringResource(Res.string.tuning_in_tune), KoraInTuneColor, activeState == TuningFeedbackState.IN_TUNE)
        TuningLightItem(stringResource(Res.string.tuning_sharp), KoraSharpColor, activeState == TuningFeedbackState.SHARP)
    }
}

@Composable
private fun TuningLightItem(label: String, color: Color, isActive: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(14.dp)
                .border(1.dp, if (isActive) Color.White.copy(alpha = 0.9f) else color.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                .background(if (isActive) color else color.copy(alpha = 0.25f), RoundedCornerShape(7.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun targetRolePosition(target: TunerTarget): Int = target.roleLabel.drop(1).toIntOrNull() ?: Int.MAX_VALUE

private const val REFERENCE_TONE_OCTAVE_MULTIPLIER = 2.0
private const val PEG_TUNING_IN_TUNE_THRESHOLD_CENTS = 200.0
private const val LIVE_TUNER_GRADIENT_RANGE_CENTS = 50.0

@Composable
private fun liveTunerPerformanceModeLabel(mode: LiveTunerPerformanceMode): String = when (mode) {
    LiveTunerPerformanceMode.REALTIME -> stringResource(Res.string.live_tuner_mode_realtime_label)
    LiveTunerPerformanceMode.PRECISION -> stringResource(Res.string.live_tuner_mode_precision_label)
}

@Composable
private fun liveTunerPerformanceModeSummary(mode: LiveTunerPerformanceMode): String = when (mode) {
    LiveTunerPerformanceMode.REALTIME -> stringResource(Res.string.live_tuner_mode_realtime_summary)
    LiveTunerPerformanceMode.PRECISION -> stringResource(Res.string.live_tuner_mode_precision_summary)
}
