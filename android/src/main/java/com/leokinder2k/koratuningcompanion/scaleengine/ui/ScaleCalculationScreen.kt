package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverOnlyStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.model.StringSide
import com.leokinder2k.koratuningcompanion.scaleengine.model.VoicingConflict
import com.leokinder2k.koratuningcompanion.scaleengine.model.VoicingSuggestion

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
    val tuningMode = uiState.result.request.instrumentProfile.tuningMode
    val showLeverInfo = tuningMode == KoraTuningMode.LEVERED

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_scale_calculation_engine)) }
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
                        text = stringResource(R.string.quick_start_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (showLeverInfo) {
                            stringResource(R.string.scale_engine_quick_start_levered)
                        } else {
                            stringResource(R.string.scale_engine_quick_start_peg_tuning)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SelectionSection(
                title = stringResource(R.string.scale_root_note_label),
                options = NoteName.entries,
                selected = uiState.rootNote,
                optionLabel = { note -> note.symbol },
                onOptionSelected = onRootNoteSelected
            )

            ScaleTypeDropdownMenus(
                selectedScaleType = uiState.scaleType,
                onScaleTypeSelected = onScaleTypeSelected
            )

            SummaryCard(
                scaleNoteLabels = uiState.result.scaleNotes.joinToString(" ") { note -> note.symbol },
                leftStringNoteLabels = uiState.result.pegCorrectTable
                    .filter { row -> row.role.side == StringSide.LEFT }
                    .sortedBy { row -> row.role.positionFromLow }
                    .joinToString(" ") { row -> row.selectedPitch.asText() },
                rightStringNoteLabels = uiState.result.pegCorrectTable
                    .filter { row -> row.role.side == StringSide.RIGHT }
                    .sortedBy { row -> row.role.positionFromLow }
                    .joinToString(" ") { row -> row.selectedPitch.asText() },
                showLeverInfo = showLeverInfo,
                leverRetuneCount = uiState.result.leverOnlyTable.count { row -> row.pegRetuneRequired },
                pegRetuneCount = uiState.result.pegCorrectTable.count { row -> row.pegRetuneRequired }
            )

            if (showLeverInfo) {
                LeverOnlyTableCard(rows = uiState.result.leverOnlyTable)
            }
            PegCorrectTableCard(rows = uiState.result.pegCorrectTable, showLeverInfo = showLeverInfo)
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
    leftStringNoteLabels: String,
    rightStringNoteLabels: String,
    showLeverInfo: Boolean,
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
                text = stringResource(R.string.scale_engine_summary_scale_notes, scaleNoteLabels),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.scale_engine_summary_left_strings, leftStringNoteLabels),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.scale_engine_summary_right_strings, rightStringNoteLabels),
                style = MaterialTheme.typography.bodySmall
            )
            if (showLeverInfo) {
                Text(
                    text = stringResource(R.string.scale_engine_summary_lever_only_retunes, leverRetuneCount),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.scale_engine_summary_peg_adjustment_retunes, pegRetuneCount),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = stringResource(R.string.scale_engine_summary_peg_tuning_retunes, pegRetuneCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LeverOnlyTableCard(rows: List<LeverOnlyStringResult>) {
    val leftRows = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val rightRows = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.scale_engine_lever_only_table_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.scale_engine_two_column_note),
                style = MaterialTheme.typography.bodySmall
            )
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftRow = leftRows.getOrNull(index)
                    if (leftRow != null) {
                        SideCell(
                            text = formatLeverOnlyRow(leftRow),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    val rightRow = rightRows.getOrNull(index)
                    if (rightRow != null) {
                        SideCell(
                            text = formatLeverOnlyRow(rightRow),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PegCorrectTableCard(
    rows: List<PegCorrectStringResult>,
    showLeverInfo: Boolean
) {
    val leftRows = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val rightRows = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (showLeverInfo) {
                    stringResource(R.string.scale_engine_peg_adjustment_table_title)
                } else {
                    stringResource(R.string.scale_engine_peg_tuning_table_title)
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.scale_engine_two_column_note),
                style = MaterialTheme.typography.bodySmall
            )
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftRow = leftRows.getOrNull(index)
                    if (leftRow != null) {
                        SideCell(
                            text = formatPegCorrectRow(leftRow, showLeverInfo = showLeverInfo),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    val rightRow = rightRows.getOrNull(index)
                    if (rightRow != null) {
                        SideCell(
                            text = formatPegCorrectRow(rightRow, showLeverInfo = showLeverInfo),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SideColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.table_left_header),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.table_right_header),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SideCell(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun formatLeverOnlyRow(row: LeverOnlyStringResult): String {
    val leverLabel = row.selectedLeverState?.name ?: stringResource(R.string.value_na)
    val selectedPitchLabel = row.selectedPitch?.asText() ?: "-"
    val pegLabel = if (row.pegRetuneRequired) stringResource(R.string.value_yes) else stringResource(R.string.value_no)
    return buildString {
        append("${row.role.asLabel()} (S${row.stringNumber})\n")
        append(stringResource(R.string.scale_engine_lever_only_row_open_closed, row.openPitch.asText(), row.closedPitch.asText()))
        append("\n")
        append(stringResource(R.string.scale_engine_lever_only_row_lever_target, leverLabel, selectedPitchLabel))
        append("\n")
        append(stringResource(R.string.scale_engine_row_int_peg, signed(row.selectedIntonationCents), pegLabel))
    }
}

@Composable
private fun formatPegCorrectRow(row: PegCorrectStringResult, showLeverInfo: Boolean): String {
    val retuneLabel = if (row.pegRetuneSemitones >= 0) {
        "+${row.pegRetuneSemitones}"
    } else {
        row.pegRetuneSemitones.toString()
    }
    return buildString {
        append("${row.role.asLabel()} (S${row.stringNumber})\n")
        if (showLeverInfo) {
            append(
                stringResource(
                    R.string.scale_engine_peg_row_target_lever,
                    row.selectedPitch.asText(),
                    row.selectedLeverState.name
                )
            )
            append("\n")
            append(
                stringResource(
                    R.string.scale_engine_lever_only_row_open_closed,
                    row.retunedOpenPitch.asText(),
                    row.retunedClosedPitch.asText()
                )
            )
            append("\n")
        } else {
            append(stringResource(R.string.scale_engine_peg_tuning_row_target, row.selectedPitch.asText()))
            append("\n")
            append(stringResource(R.string.scale_engine_peg_tuning_row_tune_open, row.retunedOpenPitch.asText()))
            append("\n")
        }
        append(stringResource(R.string.scale_engine_row_int_peg, signed(row.selectedIntonationCents), retuneLabel))
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
                text = stringResource(R.string.scale_engine_conflicts_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (conflicts.isEmpty()) {
                Text(
                    text = stringResource(R.string.scale_engine_conflicts_none),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                conflicts.forEach { conflict ->
                    Text(
                        text = stringResource(
                            R.string.scale_engine_conflict_line,
                            conflict.mode.name,
                            conflict.side.shortLabel,
                            conflict.lowerStringNumber,
                            conflict.higherStringNumber,
                            conflict.detail
                        ),
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
                text = stringResource(R.string.scale_engine_suggestions_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.scale_engine_suggestions_none),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                suggestions.forEach { suggestion ->
                    Text(
                        text = stringResource(
                            R.string.scale_engine_suggestion_line,
                            suggestion.mode.name,
                            suggestion.suggestion
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

