package com.example.koratuningsystem.instrumentconfig.ui

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.koratuningsystem.ui.theme.KoraTuningSystemTheme

@Composable
fun InstrumentConfigurationRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val viewModel: InstrumentConfigurationViewModel = viewModel(
        factory = InstrumentConfigurationViewModel.factory(context)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InstrumentConfigurationScreen(
        uiState = uiState,
        onStringCountSelected = viewModel::onStringCountSelected,
        onOpenPitchChanged = viewModel::onOpenPitchChanged,
        onOpenIntonationChanged = viewModel::onOpenIntonationChanged,
        onClosedIntonationChanged = viewModel::onClosedIntonationChanged,
        onLoadStarterProfile = viewModel::loadStarterProfile,
        onSaveProfile = viewModel::saveProfile,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun InstrumentConfigurationScreen(
    uiState: InstrumentConfigurationUiState,
    onStringCountSelected: (Int) -> Unit,
    onOpenPitchChanged: (rowIndex: Int, value: String) -> Unit,
    onOpenIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onClosedIntonationChanged: (rowIndex: Int, value: String) -> Unit,
    onLoadStarterProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Instrument Configuration") }
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
                text = "Strings",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            Text(
                text = "Set open tuning for each string. Closed pitch is calculated automatically (+1 semitone). You can also enter open/closed intonation offsets in cents.",
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
                        text = "Quick Start",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "1. Choose 21 or 22 strings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Enter open pitch and optional cents offsets per string.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Save profile, then use Scale/Guided/Overview/Tuner tabs.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            uiState.rows.forEachIndexed { index, row ->
                StringConfigurationCard(
                    row = row,
                    onOpenPitchChanged = { value -> onOpenPitchChanged(index, value) },
                    onOpenIntonationChanged = { value -> onOpenIntonationChanged(index, value) },
                    onClosedIntonationChanged = { value -> onClosedIntonationChanged(index, value) }
                )
            }

            OutlinedButton(
                onClick = onLoadStarterProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Starter Profile")
            }

            Button(
                onClick = onSaveProfile,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Instrument Profile")
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

@Composable
private fun StringConfigurationCard(
    row: InstrumentStringRowUiState,
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
                text = "String ${row.stringNumber}",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = row.openPitchInput,
                onValueChange = onOpenPitchChanged,
                label = { Text("Open pitch") },
                placeholder = { Text("E3") },
                singleLine = true,
                isError = row.inputError != null,
                supportingText = {
                    when {
                        row.inputError != null -> Text(row.inputError)
                        row.closedPitch != null -> Text("Closed pitch: ${row.closedPitch}")
                        else -> Text("Enter open pitch to calculate closed pitch.")
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
                    label = { Text("Open cents") },
                    placeholder = { Text("0.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = row.openIntonationError != null,
                    supportingText = {
                        row.openIntonationError?.let { error ->
                            Text(error)
                        }
                    }
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = row.closedIntonationInput,
                    onValueChange = onClosedIntonationChanged,
                    label = { Text("Closed cents") },
                    placeholder = { Text("0.0") },
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

@Preview(showBackground = true)
@Composable
private fun InstrumentConfigurationScreenPreview() {
    KoraTuningSystemTheme {
        InstrumentConfigurationScreen(
            uiState = InstrumentConfigurationUiState(
                stringCount = 21,
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
            onStringCountSelected = {},
            onOpenPitchChanged = { _, _ -> },
            onOpenIntonationChanged = { _, _ -> },
            onClosedIntonationChanged = { _, _ -> },
            onLoadStarterProfile = {},
            onSaveProfile = {}
        )
    }
}
