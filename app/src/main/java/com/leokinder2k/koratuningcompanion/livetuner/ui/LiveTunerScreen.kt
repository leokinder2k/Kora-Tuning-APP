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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
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
                title = { Text("Live Tuner") }
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
                        text = "Quick Start",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "1. Select mode: Realtime for response, Precision for cent accuracy.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Grant microphone access and start tuner.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Pluck one string cleanly and match cent deviation to 0.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        text = "Microphone",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiveTunerPerformanceMode.entries) { mode ->
                            FilterChip(
                                selected = mode == tunerUiState.performanceMode,
                                onClick = { onPerformanceModeSelected(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Text(
                        text = tunerUiState.performanceMode.summary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!tunerUiState.hasAudioPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        ) {
                            Text("Grant Microphone Permission")
                        }
                    } else {
                        if (!tunerUiState.isListening) {
                            Button(onClick = onStartListening) {
                                Text("Start Live Tuner")
                            }
                        } else {
                            OutlinedButton(onClick = onStopListening) {
                                Text("Stop Live Tuner")
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
                        text = "Detection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Detected pitch: ${formatFrequency(tunerUiState.detectedFrequencyHz)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Confidence: ${formatPercent(tunerUiState.confidence)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Signal level (RMS): ${"%.4f".format(tunerUiState.rms)}",
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
                        text = "Nearest Target",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (match == null) {
                        Text(
                            text = "No match yet. Start tuner and play a string.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val tuningState = TuningFeedbackClassifier.classify(match.centsDeviation)
                        val tuningColor = tuningStateColor(tuningState)
                        Text(
                            text = "String: ${match.target.roleLabel} (S${match.target.stringNumber})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Target pitch: ${match.target.targetPitch.asText()} (${formatFrequency(match.target.targetFrequencyHz)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Target intonation: ${signed(match.target.targetIntonationCents)} cent",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Tuning status: ${tuningStateLabel(tuningState)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tuningColor
                        )
                        Text(
                            text = "Cent deviation: ${signed(match.centsDeviation)} cent",
                            style = MaterialTheme.typography.bodySmall,
                            color = tuningColor
                        )
                        Text(
                            text = "Lever: ${match.target.requiredLeverState.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (match.target.pegRetuneSemitones != 0) {
                                "Peg adjustment: ${signed(match.target.pegRetuneSemitones.toDouble())} semitone(s)"
                            } else {
                                "Peg adjustment: none"
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
                text = "Tip: stabilize pluck strength and sustain to improve cent-level consistency.",
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
            text = "Root note",
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
            text = "Scale type",
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
                text = "Reference Tone (Tune to Specific Pitch)",
                style = MaterialTheme.typography.titleMedium
            )
            if (targets.isEmpty()) {
                Text(
                    text = "No target pitches available for current setup.",
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }

            Text(
                text = "Left side (Bass -> High)",
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
                text = "Right side (Bass -> High)",
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
                    text = "Selected: ${selectedTarget.roleLabel} (S${selectedTarget.stringNumber}) ${selectedTarget.targetPitch.asText()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Reference frequency: ${formatFrequency(selectedTarget.targetFrequencyHz)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isReferenceTonePlaying) {
                    Button(onClick = onPlay) {
                        Text("Play Reference Tone")
                    }
                } else {
                    OutlinedButton(onClick = onStop) {
                        Text("Stop Reference Tone")
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

private fun tuningStateLabel(state: TuningFeedbackState): String {
    return when (state) {
        TuningFeedbackState.IN_TUNE -> "In Tune"
        TuningFeedbackState.FLAT -> "Flat"
        TuningFeedbackState.SHARP -> "Sharp"
    }
}

private fun targetRolePosition(target: TunerTarget): Int {
    return target.roleLabel
        .drop(1)
        .toIntOrNull()
        ?: Int.MAX_VALUE
}

