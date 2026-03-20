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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun TraditionalPresetsRoute(modifier: Modifier = Modifier) {
    val viewModel: TraditionalPresetsViewModel = viewModel { TraditionalPresetsViewModel() }
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            Text(
                text = stringResource(Res.string.traditional_presets_intro),
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Common Instrument Tunings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Standard reference tunings for common instruments. Select a note on the Chromatic Tuner tab to tune string by string.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(
                        "Guitar (Standard)" to "E2 – A2 – D3 – G3 – B3 – E4",
                        "Guitar (Drop D)" to "D2 – A2 – D3 – G3 – B3 – E4",
                        "Ukulele (Standard)" to "G4 – C4 – E4 – A4",
                        "Ukulele (Baritone)" to "D3 – G3 – B3 – E4",
                        "Banjo (5-string Open G)" to "G4 – D3 – G3 – B3 – D4",
                        "Banjo (4-string Tenor)" to "C3 – G3 – D4 – A4",
                        "Bass (Standard 4-str)" to "E1 – A1 – D2 – G2",
                        "Mandolin / Violin" to "G3 – D4 – A4 – E5",
                        "Cello" to "C2 – G2 – D3 – A3",
                        "Viola" to "C3 – G3 – D4 – A4",
                    ).forEach { (name, tuning) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = tuning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.traditional_presets_workflow_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(Res.string.traditional_presets_workflow_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.stringCount == 19,
                    onClick = { onStringCountSelected(19) },
                    label = { Text("Kadanu") }
                )
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.traditional_presets_custom_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(Res.string.traditional_presets_custom_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.customPresetNameInput,
                        onValueChange = onCustomPresetNameChanged,
                        label = { Text(stringResource(Res.string.traditional_presets_custom_name_label)) },
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
                            Text(stringResource(Res.string.traditional_presets_action_save_custom))
                        }
                        if (uiState.selectedPresetIsCustom) {
                            OutlinedButton(
                                onClick = onDeleteSelectedCustomPreset,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.traditional_presets_action_delete_selected))
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
                                stringResource(Res.string.traditional_presets_custom_suffix, preset.displayName)
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
                                Res.string.traditional_presets_open_tuning_preview,
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
                                    stringResource(Res.string.traditional_presets_selected)
                                } else {
                                    stringResource(Res.string.traditional_presets_action_select_preset)
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
                            text = stringResource(Res.string.traditional_presets_selected_strings_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.previewRows.forEach { row ->
                            Text(
                                text = stringResource(
                                    Res.string.traditional_presets_selected_string_row,
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
                Text(stringResource(Res.string.traditional_presets_action_load_selected))
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

private fun signed(value: Double): String {
    val abs = kotlin.math.abs(value)
    val rounded = kotlin.math.round(abs * 10.0)
    val whole = rounded / 10
    val dec = rounded % 10
    val formatted = "$whole.$dec"
    return if (value >= 0.0) "+$formatted" else "-$formatted"
}
