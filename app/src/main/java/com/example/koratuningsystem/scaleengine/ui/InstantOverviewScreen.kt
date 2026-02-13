package com.example.koratuningsystem.scaleengine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.koratuningsystem.instrumentconfig.model.NoteName
import com.example.koratuningsystem.scaleengine.model.LeverState
import com.example.koratuningsystem.scaleengine.model.PegCorrectStringResult
import com.example.koratuningsystem.scaleengine.model.ScaleType
import com.example.koratuningsystem.scaleengine.model.StringSide

private enum class OverviewViewMode {
    DIAGRAM,
    TABLE
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun InstantOverviewScreen(
    uiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by rememberSaveable { mutableStateOf(OverviewViewMode.DIAGRAM) }
    val rows = uiState.result.pegCorrectTable

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Instant Overview Mode") }
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

            OverviewSelectionControls(
                rootNote = uiState.rootNote,
                scaleType = uiState.scaleType,
                onRootNoteSelected = onRootNoteSelected,
                onScaleTypeSelected = onScaleTypeSelected
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewMode == OverviewViewMode.DIAGRAM,
                    onClick = { viewMode = OverviewViewMode.DIAGRAM },
                    label = { Text("Diagram") }
                )
                FilterChip(
                    selected = viewMode == OverviewViewMode.TABLE,
                    onClick = { viewMode = OverviewViewMode.TABLE },
                    label = { Text("Table") }
                )
            }

            when (viewMode) {
                OverviewViewMode.DIAGRAM -> DiagramOverview(rows = rows)
                OverviewViewMode.TABLE -> TableOverview(rows = rows)
            }
        }
    }
}

@Composable
private fun OverviewSelectionControls(
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

@Composable
private fun DiagramOverview(rows: List<PegCorrectStringResult>) {
    val left = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val right = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val colors = MaterialTheme.colorScheme

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Visual Kora Diagram",
                style = MaterialTheme.typography.titleMedium
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawKoraDiagram(
                        leftRows = left,
                        rightRows = right,
                        colors = colors
                    )
                }
            }

            DiagramLegend(colors = colors)
        }
    }
}

private fun DrawScope.drawKoraDiagram(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    colors: ColorScheme
) {
    val width = size.width
    val height = size.height
    val centerX = width * 0.5f
    val neckTop = height * 0.06f
    val neckBottom = height * 0.96f
    val bridgeCenterY = height * 0.56f
    val bridgeTop = bridgeCenterY - (height * 0.17f)
    val bridgeBottom = bridgeCenterY + (height * 0.17f)

    drawLine(
        color = colors.outline,
        start = Offset(centerX, neckTop),
        end = Offset(centerX, neckBottom),
        strokeWidth = width * 0.01f,
        cap = StrokeCap.Round
    )

    drawOval(
        color = colors.surfaceVariant.copy(alpha = 0.7f),
        topLeft = Offset(width * 0.26f, height * 0.68f),
        size = Size(width * 0.48f, height * 0.28f)
    )

    drawRoundRect(
        color = colors.tertiary.copy(alpha = 0.5f),
        topLeft = Offset(width * 0.47f, bridgeTop),
        size = Size(width * 0.06f, bridgeBottom - bridgeTop),
        cornerRadius = CornerRadius(width * 0.015f, width * 0.015f)
    )

    drawStringSet(
        rows = leftRows,
        isLeft = true,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom,
        colors = colors
    )
    drawStringSet(
        rows = rightRows,
        isLeft = false,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom,
        colors = colors
    )
}

private fun DrawScope.drawStringSet(
    rows: List<PegCorrectStringResult>,
    isLeft: Boolean,
    bridgeTop: Float,
    bridgeBottom: Float,
    colors: ColorScheme
) {
    if (rows.isEmpty()) {
        return
    }

    val width = size.width
    val topY = size.height * 0.1f
    val bottomY = size.height * 0.9f
    val pegX = if (isLeft) width * 0.11f else width * 0.89f
    val bridgeX = if (isLeft) width * 0.485f else width * 0.515f
    val maxIndex = rows.lastIndex.coerceAtLeast(1)

    rows.forEachIndexed { index, row ->
        val ratio = index.toFloat() / maxIndex.toFloat()
        val pegY = lerp(start = topY, stop = bottomY, fraction = ratio)
        val bridgeY = lerp(start = bridgeTop, stop = bridgeBottom, fraction = ratio)
        val leverColor = when (row.selectedLeverState) {
            LeverState.OPEN -> colors.secondary
            LeverState.CLOSED -> colors.primary
        }
        val strokeWidth = if (row.pegRetuneRequired) {
            width * 0.0058f
        } else {
            width * 0.004f
        }

        drawLine(
            color = leverColor,
            start = Offset(pegX, pegY),
            end = Offset(bridgeX, bridgeY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = if (row.pegRetuneRequired) colors.error else colors.outline,
            radius = if (row.pegRetuneRequired) width * 0.010f else width * 0.007f,
            center = Offset(pegX, pegY)
        )

        drawCircle(
            color = leverColor,
            radius = width * 0.006f,
            center = Offset(bridgeX, bridgeY)
        )
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
private fun DiagramLegend(colors: ColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem(
            color = colors.secondary,
            text = "Open lever"
        )
        LegendItem(
            color = colors.primary,
            text = "Closed lever"
        )
        LegendItem(
            color = colors.error,
            text = "Peg retune required (peg marker)"
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = color, shape = MaterialTheme.shapes.small)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TableOverview(rows: List<PegCorrectStringResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Professional Table View",
                style = MaterialTheme.typography.titleMedium
            )
            rows.forEach { row ->
                val pegIndicator = if (row.pegRetuneRequired) {
                    signed(row.pegRetuneSemitones)
                } else {
                    "0"
                }
                Text(
                    text = "${row.role.asLabel()} S${row.stringNumber} | target ${row.selectedPitch.asText()} | int ${signed(row.selectedIntonationCents)}c | lever ${row.selectedLeverState.name} | peg $pegIndicator",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun signed(value: Int): String {
    return if (value >= 0) "+$value" else value.toString()
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.1f".format(value)}" else "%.1f".format(value)
}
