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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R

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
                title = { Text(stringResource(R.string.title_traditional_presets)) }
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
                text = stringResource(R.string.traditional_presets_intro),
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
                        text = stringResource(R.string.traditional_presets_workflow_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.traditional_presets_workflow_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.traditional_presets_custom_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.traditional_presets_custom_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.customPresetNameInput,
                        onValueChange = onCustomPresetNameChanged,
                        label = { Text(stringResource(R.string.traditional_presets_custom_name_label)) },
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
                            Text(stringResource(R.string.traditional_presets_action_save_custom))
                        }
                        if (uiState.selectedPresetIsCustom) {
                            OutlinedButton(
                                onClick = onDeleteSelectedCustomPreset,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.traditional_presets_action_delete_selected))
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
                                stringResource(
                                    R.string.traditional_presets_custom_suffix,
                                    preset.displayName
                                )
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
                            text = stringResource(
                                R.string.traditional_presets_open_tuning_preview,
                                preset.openPitchPreview
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { onPresetSelected(preset.id) },
                            enabled = !selected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (selected) {
                                    stringResource(R.string.traditional_presets_selected)
                                } else {
                                    stringResource(R.string.traditional_presets_action_select_preset)
                                }
                            )
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
                            text = stringResource(R.string.traditional_presets_selected_strings_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.previewRows.forEach { row ->
                            Text(
                                text = stringResource(
                                    R.string.traditional_presets_selected_string_row,
                                    row.stringNumber,
                                    row.openPitch,
                                    signed(row.openIntonationCents),
                                    row.closedPitch,
                                    signed(row.closedIntonationCents)
                                ),
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
                Text(stringResource(R.string.traditional_presets_action_load_selected))
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

