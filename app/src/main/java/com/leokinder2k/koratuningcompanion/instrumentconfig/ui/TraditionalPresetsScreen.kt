package com.leokinder2k.koratuningcompanion.instrumentconfig.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TraditionalPresetsRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val viewModel: TraditionalPresetsViewModel = viewModel(
        factory = TraditionalPresetsViewModel.factory(context)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TraditionalPresetsScreen(
        uiState = uiState,
        onStringCountSelected = viewModel::onStringCountSelected,
        onPresetSelected = viewModel::onPresetSelected,
        onCustomPresetNameChanged = viewModel::onCustomPresetNameChanged,
        onSaveCustomPreset = viewModel::saveCurrentProfileAsCustomPreset,
        onDeleteSelectedCustomPreset = viewModel::deleteSelectedCustomPreset,
        onApplyPreset = viewModel::applySelectedPreset,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TraditionalPresetsScreen(
    uiState: TraditionalPresetsUiState,
    onStringCountSelected: (Int) -> Unit,
    onPresetSelected: (String) -> Unit,
    onCustomPresetNameChanged: (String) -> Unit,
    onSaveCustomPreset: () -> Unit,
    onDeleteSelectedCustomPreset: () -> Unit,
    onApplyPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Traditional Presets") }
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
                text = "Select a traditional tuning preset and load it directly into the active instrument profile.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Preset Workflow",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Choose string count, select a preset, review open/closed pitches + cents, then load.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.stringCount == 21,
                    onClick = { onStringCountSelected(21) },
                    label = { Text("21 strings") }
                )
                FilterChip(
                    selected = uiState.stringCount == 22,
                    onClick = { onStringCountSelected(22) },
                    label = { Text("22 strings") }
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Custom Presets",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Save your active instrument tuning as your own reusable preset.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.customPresetNameInput,
                        onValueChange = onCustomPresetNameChanged,
                        label = { Text("Preset name") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveCustomPreset,
                            enabled = uiState.canSaveCustomPreset,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Custom Preset")
                        }
                        if (uiState.selectedPresetIsCustom) {
                            OutlinedButton(
                                onClick = onDeleteSelectedCustomPreset,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete Selected")
                            }
                        }
                    }
                }
            }

            uiState.presets.forEach { preset ->
                val selected = preset.id == uiState.selectedPresetId
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (preset.isCustom) {
                                "${preset.displayName} (Custom)"
                            } else {
                                preset.displayName
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Open tuning preview: ${preset.openPitchPreview}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { onPresetSelected(preset.id) },
                            enabled = !selected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selected) "Selected" else "Select Preset")
                        }
                    }
                }
            }

            if (uiState.previewRows.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Selected Preset Strings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.previewRows.forEach { row ->
                            Text(
                                text = "S${row.stringNumber} | Open ${row.openPitch} (${signed(row.openIntonationCents)}c) | Closed ${row.closedPitch} (${signed(row.closedIntonationCents)}c)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onApplyPreset,
                enabled = uiState.canApply,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Selected Preset")
            }

            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.1f".format(value)}" else "%.1f".format(value)
}

