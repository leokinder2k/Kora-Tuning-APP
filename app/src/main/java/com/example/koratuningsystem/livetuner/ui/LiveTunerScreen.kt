package com.example.koratuningsystem.livetuner.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.koratuningsystem.instrumentconfig.model.NoteName
import com.example.koratuningsystem.livetuner.model.TunerTargetMatcher
import com.example.koratuningsystem.scaleengine.ui.ScaleCalculationUiState
import com.example.koratuningsystem.scaleengine.model.ScaleType
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
    val match = tunerUiState.detectedFrequencyHz?.let { detected ->
        TunerTargetMatcher.matchNearestTarget(detected, targets)
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
                            text = "Cent deviation: ${signed(match.centsDeviation)} cent",
                            style = MaterialTheme.typography.bodySmall
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScaleType.entries) { type ->
                FilterChip(
                    selected = type == scaleType,
                    onClick = { onScaleTypeSelected(type) },
                    label = { Text(type.displayName) }
                )
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
