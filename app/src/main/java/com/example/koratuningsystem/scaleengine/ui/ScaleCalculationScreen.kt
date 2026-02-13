package com.example.koratuningsystem.scaleengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.example.koratuningsystem.instrumentconfig.model.NoteName
import com.example.koratuningsystem.scaleengine.model.LeverOnlyStringResult
import com.example.koratuningsystem.scaleengine.model.PegCorrectStringResult
import com.example.koratuningsystem.scaleengine.model.ScaleType
import com.example.koratuningsystem.scaleengine.model.VoicingConflict
import com.example.koratuningsystem.scaleengine.model.VoicingSuggestion

@Composable
fun ScaleCalculationRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val viewModel: ScaleCalculationViewModel = viewModel(
        factory = ScaleCalculationViewModel.factory(context)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScaleCalculationScreen(
        uiState = uiState,
        onRootNoteSelected = viewModel::onRootNoteSelected,
        onScaleTypeSelected = viewModel::onScaleTypeSelected,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScaleCalculationScreen(
    uiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scale Calculation Engine") }
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
                        text = "Pick root + scale, review lever-only table, then apply peg-correct plan if required.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SelectionSection(
                title = "Root note",
                options = NoteName.entries,
                selected = uiState.rootNote,
                optionLabel = { note -> note.symbol },
                onOptionSelected = onRootNoteSelected
            )

            SelectionSection(
                title = "Scale type",
                options = ScaleType.entries,
                selected = uiState.scaleType,
                optionLabel = { type -> type.displayName },
                onOptionSelected = onScaleTypeSelected
            )

            SummaryCard(
                scaleNoteLabels = uiState.result.scaleNotes.joinToString(" ") { note -> note.symbol },
                leverRetuneCount = uiState.result.leverOnlyTable.count { row -> row.pegRetuneRequired },
                pegRetuneCount = uiState.result.pegCorrectTable.count { row -> row.pegRetuneRequired }
            )

            LeverOnlyTableCard(rows = uiState.result.leverOnlyTable)
            PegCorrectTableCard(rows = uiState.result.pegCorrectTable)
            ConflictCard(conflicts = uiState.result.conflicts)
            SuggestionCard(suggestions = uiState.result.suggestions)
        }
    }
}

@Composable
private fun <T> SelectionSection(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onOptionSelected(option) },
                    label = { Text(optionLabel(option)) }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    scaleNoteLabels: String,
    leverRetuneCount: Int,
    pegRetuneCount: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Scale Notes: $scaleNoteLabels",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Lever-only peg retunes: $leverRetuneCount",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Peg-correct peg retunes: $pegRetuneCount",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LeverOnlyTableCard(rows: List<LeverOnlyStringResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Lever-only Table",
                style = MaterialTheme.typography.titleMedium
            )
            rows.forEach { row ->
                val leverLabel = row.selectedLeverState?.name ?: "N/A"
                val selectedPitchLabel = row.selectedPitch?.asText() ?: "-"
                val pegLabel = if (row.pegRetuneRequired) "YES" else "NO"
                Text(
                    text = "${row.role.asLabel()} S${row.stringNumber} | O:${row.openPitch.asText()} C:${row.closedPitch.asText()} | Lever:$leverLabel | Pitch:$selectedPitchLabel | Int:${signed(row.selectedIntonationCents)}c | Peg:$pegLabel",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PegCorrectTableCard(rows: List<PegCorrectStringResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Peg-correct Table",
                style = MaterialTheme.typography.titleMedium
            )
            rows.forEach { row ->
                val retuneLabel = if (row.pegRetuneSemitones >= 0) {
                    "+${row.pegRetuneSemitones}"
                } else {
                    row.pegRetuneSemitones.toString()
                }
                Text(
                    text = "${row.role.asLabel()} S${row.stringNumber} | Lever:${row.selectedLeverState.name} | Pitch:${row.selectedPitch.asText()} | Int:${signed(row.selectedIntonationCents)}c | Open:${row.retunedOpenPitch.asText()} Closed:${row.retunedClosedPitch.asText()} | Peg:$retuneLabel",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.1f".format(value)}" else "%.1f".format(value)
}

@Composable
private fun ConflictCard(conflicts: List<VoicingConflict>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Voicing Conflicts",
                style = MaterialTheme.typography.titleMedium
            )
            if (conflicts.isEmpty()) {
                Text(
                    text = "No conflicts detected.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                conflicts.forEach { conflict ->
                    Text(
                        text = "${conflict.mode.name}: ${conflict.side.shortLabel}-side S${conflict.lowerStringNumber}->S${conflict.higherStringNumber} (${conflict.detail})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestions: List<VoicingSuggestion>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Alternative Voicing Suggestions",
                style = MaterialTheme.typography.titleMedium
            )
            if (suggestions.isEmpty()) {
                Text(
                    text = "No suggestions required.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                suggestions.forEach { suggestion ->
                    Text(
                        text = "${suggestion.mode.name}: ${suggestion.suggestion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
