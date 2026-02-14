package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GuidedSetupScreen(
    uiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = uiState.result.pegCorrectTable
    val tuningMode = uiState.result.request.instrumentProfile.tuningMode
    var currentStepIndex by rememberSaveable(
        uiState.rootNote,
        uiState.scaleType,
        uiState.result.request.instrumentProfile.stringCount
    ) {
        mutableIntStateOf(0)
    }

    if (steps.isNotEmpty()) {
        currentStepIndex = currentStepIndex.coerceIn(0, steps.lastIndex)
    } else {
        currentStepIndex = 0
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_guided_setup)) }
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
                text = uiState.profileStatus,
                style = MaterialTheme.typography.bodyMedium
            )

            SelectionControls(
                rootNote = uiState.rootNote,
                scaleType = uiState.scaleType,
                onRootNoteSelected = onRootNoteSelected,
                onScaleTypeSelected = onScaleTypeSelected
            )

            if (steps.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.guided_setup_no_steps),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val step = steps[currentStepIndex]
                val progress = (currentStepIndex + 1).toFloat() / steps.size.toFloat()
                GuidedStepCard(
                    step = step,
                    currentStep = currentStepIndex + 1,
                    totalSteps = steps.size,
                    progress = progress,
                    tuningMode = tuningMode
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentStepIndex = (currentStepIndex - 1).coerceAtLeast(0) },
                        enabled = currentStepIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_previous))
                    }
                    Button(
                        onClick = {
                            currentStepIndex = (currentStepIndex + 1).coerceAtMost(steps.lastIndex)
                        },
                        enabled = currentStepIndex < steps.lastIndex,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedStepCard(
    step: PegCorrectStringResult,
    currentStep: Int,
    totalSteps: Int,
    progress: Float,
    tuningMode: KoraTuningMode
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.guided_setup_step_counter, currentStep, totalSteps),
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.guided_setup_string_line,
                    step.role.asLabel(),
                    step.stringNumber
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.guided_setup_target_pitch_line,
                    step.selectedPitch.asText()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.guided_setup_intonation_line,
                    signed(step.selectedIntonationCents)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (tuningMode == KoraTuningMode.LEVERED) {
                Text(
                    text = stringResource(
                        R.string.guided_setup_lever_line,
                        step.selectedLeverState.name
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = if (step.pegRetuneRequired) {
                    stringResource(
                        R.string.guided_setup_peg_adjustment_line,
                        signed(step.pegRetuneSemitones)
                    )
                } else {
                    stringResource(R.string.guided_setup_peg_adjustment_none)
                },
                style = MaterialTheme.typography.bodyMedium
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

private fun signed(value: Int): String {
    return if (value >= 0) "+$value" else value.toString()
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.1f".format(value)}" else "%.1f".format(value)
}

