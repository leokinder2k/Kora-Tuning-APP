package com.leokinder2k.koratuningcompanion.leverharp

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun LeverHarpRoute(
    enharmonicPreference: EnharmonicPreference,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = remember { standardLeverHarpStrings() }
    var order by rememberSaveable { mutableStateOf(TuningOrder.LowToHigh.name) }
    var selectedKeyName by rememberSaveable {
        mutableStateOf(LeverKeySettings.first().keyName)
    }
    val selectedKey = LeverKeySettings.firstOrNull { it.keyName == selectedKeyName }
        ?: LeverKeySettings.first()
    val orderedStrings = if (order == TuningOrder.HighToLow.name) {
        strings.reversed()
    } else {
        strings
    }
    var selectedStringNumber by rememberSaveable {
        mutableIntStateOf(strings.first().highestFirstNumber)
    }
    val selectedString = strings.firstOrNull { it.highestFirstNumber == selectedStringNumber }
        ?: strings.first()
    val referenceTonePlayer = remember { ReferenceTonePlayer(amplitude = 0.28) }
    var playingReference by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isMuted, playingReference, selectedString.highestFirstNumber) {
        if (isMuted || !playingReference) {
            referenceTonePlayer.stop()
            if (isMuted) playingReference = false
        } else {
            referenceTonePlayer.play(selectedString.frequencyHz(selectedKey))
        }
    }
    LaunchedEffect(selectedKey.keyName) {
        if (playingReference && !isMuted) {
            referenceTonePlayer.play(selectedString.frequencyHz(selectedKey))
        }
    }
    DisposableEffect(Unit) {
        onDispose { referenceTonePlayer.release() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeaderCard()
        }
        item {
            HarpIllustrationCard(
                strings = strings,
                selectedKey = selectedKey,
                selectedString = selectedString,
                enharmonicPreference = enharmonicPreference,
                isPlaying = playingReference,
                onStringSelected = { selectedStringNumber = it }
            )
        }
        item {
            SelectedStringCard(
                string = selectedString,
                selectedKey = selectedKey,
                enharmonicPreference = enharmonicPreference,
                isMuted = isMuted,
                isPlaying = playingReference,
                onToggleReference = { playingReference = !playingReference }
            )
        }
        item {
            KeyLeverCard(
                selectedKey = selectedKey,
                onSelected = { selectedKeyName = it.keyName }
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tuning chart",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = order == TuningOrder.LowToHigh.name,
                            onClick = { order = TuningOrder.LowToHigh.name },
                            label = { Text("Low to high") }
                        )
                        FilterChip(
                            selected = order == TuningOrder.HighToLow.name,
                            onClick = { order = TuningOrder.HighToLow.name },
                            label = { Text("High to low") }
                        )
                    }
                }
            }
        }
        items(
            items = orderedStrings,
            key = { it.highestFirstNumber }
        ) { string ->
            HarpStringRow(
                string = string,
                selected = string.highestFirstNumber == selectedString.highestFirstNumber,
                selectedKey = selectedKey,
                enharmonicPreference = enharmonicPreference,
                onClick = {
                    selectedStringNumber = string.highestFirstNumber
                    if (playingReference && !isMuted) {
                        referenceTonePlayer.play(string.frequencyHz(selectedKey))
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Standard Lever Harp",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "34 strings, C2 to A6. Tune with all levers down to E-flat major: C, D, Eb, F, G, Ab, Bb.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Raise a lever for a natural or sharp note. Tune slowly upward and avoid pulling a string above the target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HarpIllustrationCard(
    strings: List<LeverHarpString>,
    selectedKey: LeverKeySetting,
    selectedString: LeverHarpString,
    enharmonicPreference: EnharmonicPreference,
    isPlaying: Boolean,
    onStringSelected: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnStringSelected by rememberUpdatedState(onStringSelected)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Playable E-flat harp",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${selectedKey.keyName}: ${selectedKey.leverSummary}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(strings, canvasSize) {
                        detectTapGestures { offset ->
                            resolveHarpStringHit(
                                tapOffset = offset,
                                size = canvasSize,
                                strings = strings
                            )?.let { hit ->
                                latestOnStringSelected(hit.highestFirstNumber)
                            }
                        }
                    }
            ) {
                drawLeverHarpIllustration(
                    strings = strings,
                    selectedKey = selectedKey,
                    selectedStringNumber = selectedString.highestFirstNumber,
                    playingStringNumber = selectedString.highestFirstNumber.takeIf { isPlaying },
                    enharmonicPreference = enharmonicPreference,
                    colors = colors
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IllustrationLegendItem(color = Color(0xFFB3261E), text = "C strings")
                IllustrationLegendItem(color = Color(0xFF1B64B0), text = "F strings")
                IllustrationLegendItem(color = Color(0xFFD98924), text = "Raised lever")
            }
            Text(
                text = "Selected ${selectedString.nominalName}: down ${selectedString.leverDownPitch.asText(enharmonicPreference)}, current ${selectedString.currentPitch(selectedKey).asText(enharmonicPreference)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IllustrationLegendItem(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 8.dp)
                .background(color)
        )
        Text(text = text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SelectedStringCard(
    string: LeverHarpString,
    selectedKey: LeverKeySetting,
    enharmonicPreference: EnharmonicPreference,
    isMuted: Boolean,
    isPlaying: Boolean,
    onToggleReference: () -> Unit
) {
    val leverDownPitch = string.leverDownPitch.asText(enharmonicPreference)
    val leverUpPitch = string.leverUpPitch().asText(enharmonicPreference)
    val currentPitch = string.currentPitch(selectedKey).asText(enharmonicPreference)
    val currentFrequency = string.frequencyHz(selectedKey)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Selected string",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "String ${string.highestFirstNumber} (${string.nominalName})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Current: $currentPitch  ${formatHz(currentFrequency)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Down $leverDownPitch  Up $leverUpPitch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onToggleReference,
                    enabled = !isMuted
                ) {
                    Text(if (isPlaying) "Stop" else "Play")
                }
            }
        }
    }
}

@Composable
private fun KeyLeverCard(
    selectedKey: LeverKeySetting,
    onSelected: (LeverKeySetting) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Common key settings",
                style = MaterialTheme.typography.titleMedium
            )
            LeverKeySettings.forEach { setting ->
                FilterChip(
                    selected = setting == selectedKey,
                    onClick = { onSelected(setting) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = setting.keyName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = setting.leverSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HarpStringRow(
    string: LeverHarpString,
    selected: Boolean,
    selectedKey: LeverKeySetting,
    enharmonicPreference: EnharmonicPreference,
    onClick: () -> Unit
) {
    val leverDownPitch = string.leverDownPitch.asText(enharmonicPreference)
    val currentPitch = string.currentPitch(selectedKey).asText(enharmonicPreference)
    val currentFrequency = string.frequencyHz(selectedKey)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StringColorMarker(string.nominalLetter)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "String ${string.highestFirstNumber}  ${string.nominalName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Down $leverDownPitch  Current $currentPitch",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = formatHz(currentFrequency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StringColorMarker(letter: String) {
    val color = when (letter) {
        "C" -> Color(0xFFB3261E)
        "F" -> Color(0xFF1B64B0)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 44.dp)
            .background(color)
    )
}

private fun DrawScope.drawLeverHarpIllustration(
    strings: List<LeverHarpString>,
    selectedKey: LeverKeySetting,
    selectedStringNumber: Int,
    playingStringNumber: Int?,
    enharmonicPreference: EnharmonicPreference,
    colors: ColorScheme
) {
    val w = size.width
    val h = size.height
    val placements = buildHarpStringPlacements(strings, size)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                colors.surfaceVariant.copy(alpha = 0.18f),
                colors.surface.copy(alpha = 0.96f)
            )
        )
    )
    drawLeverHarpFrame()

    placements.forEachIndexed { index, placement ->
        val string = placement.string
        val isSelected = string.highestFirstNumber == selectedStringNumber
        val isPlaying = string.highestFirstNumber == playingStringNumber
        val isRaised = string.isRaised(selectedKey)
        val ratio = index.toFloat() / (placements.lastIndex.coerceAtLeast(1)).toFloat()
        val baseStroke = w * (0.0022f + (1f - ratio) * 0.0020f)
        val strokeWidth = when {
            isPlaying -> baseStroke * 2.3f
            isSelected -> baseStroke * 1.8f
            else -> baseStroke
        }
        val stringColor = when {
            isPlaying -> colors.primary
            isSelected -> colors.tertiary
            string.nominalLetter == "C" -> Color(0xFFB3261E)
            string.nominalLetter == "F" -> Color(0xFF1B64B0)
            isRaised -> Color(0xFFD98924)
            else -> colors.outline
        }
        drawLine(
            color = stringColor.copy(alpha = if (ratio > 0.82f) 0.70f else 0.92f),
            start = placement.top,
            end = placement.bottom,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLever(
            placement = placement,
            raised = isRaised,
            selected = isSelected,
            colors = colors
        )
    }

    placements.firstOrNull { it.string.highestFirstNumber == selectedStringNumber }?.let { placement ->
        val currentPitch = placement.string.currentPitch(selectedKey).asText(enharmonicPreference)
        val downPitch = placement.string.leverDownPitch.asText(enharmonicPreference)
        drawSelectedStringBadge(
            text = "String ${placement.string.highestFirstNumber} ${placement.string.nominalName}  Down $downPitch  Current $currentPitch",
            anchor = placement.top,
            colors = colors
        )
    }

    drawCanvasText(
        text = "E-flat base tuning",
        x = w * 0.54f,
        y = h * 0.950f,
        color = colors.onSurfaceVariant,
        textSize = w * 0.038f,
        align = Paint.Align.CENTER,
        bold = true
    )
}

private fun DrawScope.drawLeverHarpFrame() {
    val w = size.width
    val h = size.height
    val woodDark = Color(0xFF4B2A12)
    val woodMid = Color(0xFF8A5828)
    val woodLight = Color(0xFFC58948)
    val gold = Color(0xFFCDA45A)

    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(woodDark, woodMid, woodLight)),
        topLeft = Offset(w * 0.06f, h * 0.840f),
        size = Size(w * 0.89f, h * 0.060f),
        cornerRadius = CornerRadius(w * 0.025f, w * 0.025f)
    )
    drawLine(
        color = woodMid,
        start = Offset(w * 0.18f, h * 0.820f),
        end = Offset(w * 0.135f, h * 0.165f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = woodLight.copy(alpha = 0.70f),
        start = Offset(w * 0.208f, h * 0.795f),
        end = Offset(w * 0.160f, h * 0.185f),
        strokeWidth = w * 0.014f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = woodDark,
        start = Offset(w * 0.885f, h * 0.125f),
        end = Offset(w * 0.885f, h * 0.830f),
        strokeWidth = w * 0.070f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = woodLight.copy(alpha = 0.55f),
        start = Offset(w * 0.858f, h * 0.145f),
        end = Offset(w * 0.858f, h * 0.790f),
        strokeWidth = w * 0.014f,
        cap = StrokeCap.Round
    )

    val neck = Path().apply {
        moveTo(w * 0.145f, h * 0.150f)
        cubicTo(w * 0.315f, h * 0.035f, w * 0.680f, h * 0.045f, w * 0.885f, h * 0.135f)
    }
    drawPath(
        path = neck,
        color = woodDark,
        style = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
    )
    drawPath(
        path = neck,
        color = woodLight.copy(alpha = 0.58f),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
    )
    drawCircle(
        color = gold.copy(alpha = 0.90f),
        radius = w * 0.032f,
        center = Offset(w * 0.885f, h * 0.125f)
    )
}

private fun DrawScope.drawLever(
    placement: HarpStringPlacement,
    raised: Boolean,
    selected: Boolean,
    colors: ColorScheme
) {
    val leverCenter = lerp(placement.top, placement.bottom, 0.075f)
    val radius = size.width * if (selected) 0.0105f else 0.0080f
    val color = when {
        raised -> Color(0xFFD98924)
        selected -> colors.primary
        else -> colors.outlineVariant
    }
    drawCircle(
        color = color,
        radius = radius,
        center = leverCenter
    )
    drawLine(
        color = color.copy(alpha = 0.86f),
        start = leverCenter,
        end = leverCenter + Offset(if (raised) size.width * 0.014f else -size.width * 0.010f, -size.height * 0.016f),
        strokeWidth = size.width * 0.004f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSelectedStringBadge(
    text: String,
    anchor: Offset,
    colors: ColorScheme
) {
    val textSize = size.width * 0.027f
    val paddingX = size.width * 0.018f
    val paddingY = size.width * 0.010f
    val paint = Paint().apply {
        color = colors.onPrimaryContainer.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        isFakeBoldText = true
    }
    val badgeWidth = min(paint.measureText(text) + paddingX * 2f, size.width - size.width * 0.06f)
    val badgeHeight = textSize + paddingY * 2.2f
    val left = (anchor.x + size.width * 0.024f).coerceIn(size.width * 0.03f, size.width - badgeWidth - size.width * 0.03f)
    val top = (anchor.y + size.height * 0.030f).coerceIn(size.height * 0.03f, size.height - badgeHeight - size.height * 0.08f)
    drawRoundRect(
        color = colors.primaryContainer.copy(alpha = 0.94f),
        topLeft = Offset(left, top),
        size = Size(badgeWidth, badgeHeight),
        cornerRadius = CornerRadius(size.width * 0.015f, size.width * 0.015f)
    )
    drawRoundRect(
        color = colors.primary.copy(alpha = 0.22f),
        topLeft = Offset(left, top),
        size = Size(badgeWidth, badgeHeight),
        cornerRadius = CornerRadius(size.width * 0.015f, size.width * 0.015f),
        style = Stroke(width = size.width * 0.002f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        left + paddingX,
        top + paddingY + textSize,
        paint
    )
}

private fun DrawScope.drawCanvasText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float,
    align: Paint.Align,
    bold: Boolean = false
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = align
        isAntiAlias = true
        isFakeBoldText = bold
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun buildHarpStringPlacements(
    strings: List<LeverHarpString>,
    size: Size
): List<HarpStringPlacement> {
    if (strings.isEmpty()) return emptyList()
    val maxIndex = strings.lastIndex.coerceAtLeast(1)
    return strings.mapIndexed { index, string ->
        val ratio = index.toFloat() / maxIndex.toFloat()
        val top = Offset(
            x = lerp(size.width * 0.165f, size.width * 0.825f, ratio),
            y = size.height * (0.145f - 0.035f * kotlin.math.sin(ratio * Math.PI).toFloat())
        )
        val bottom = Offset(
            x = lerp(size.width * 0.165f, size.width * 0.455f, ratio),
            y = lerp(size.height * 0.810f, size.height * 0.255f, ratio)
        )
        HarpStringPlacement(string = string, top = top, bottom = bottom)
    }
}

private fun resolveHarpStringHit(
    tapOffset: Offset,
    size: IntSize,
    strings: List<LeverHarpString>
): LeverHarpString? {
    if (size.width <= 0 || size.height <= 0 || strings.isEmpty()) return null
    val placements = buildHarpStringPlacements(
        strings = strings,
        size = Size(size.width.toFloat(), size.height.toFloat())
    )
    val threshold = max(18f, min(size.width, size.height) * 0.038f)
    return placements
        .map { it to distanceToSegment(tapOffset, it.top, it.bottom) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= threshold }
        ?.first
        ?.string
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return distance(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return distance(point, projection)
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun lerp(start: Offset, stop: Offset, fraction: Float): Offset =
    Offset(
        x = lerp(start.x, stop.x, fraction),
        y = lerp(start.y, stop.y, fraction)
    )

internal fun standardLeverHarpStrings(): List<LeverHarpString> {
    val nominalStrings = buildList {
        for (octave in 2..6) {
            for (letter in DiatonicLetters) {
                if (octave == 6 && letter == "B") continue
                add(NominalHarpString(letter = letter, octave = octave))
                if (octave == 6 && letter == "A") return@buildList
            }
        }
    }

    return nominalStrings.mapIndexed { index, nominal ->
        val leverDownPitch = nominal.leverDownPitch()
        LeverHarpString(
            highestFirstNumber = nominalStrings.size - index,
            nominalLetter = nominal.letter,
            nominalName = "${nominal.letter}${nominal.octave}",
            leverDownPitch = leverDownPitch,
            leverDownFrequencyHz = TunerTargetMatcher.pitchToFrequencyHz(leverDownPitch)
        )
    }
}

private fun NominalHarpString.leverDownPitch(): Pitch {
    val note = when (letter) {
        "E" -> NoteName.D_SHARP
        "A" -> NoteName.G_SHARP
        "B" -> NoteName.A_SHARP
        "C" -> NoteName.C
        "D" -> NoteName.D
        "F" -> NoteName.F
        "G" -> NoteName.G
        else -> error("Unexpected harp string letter: $letter")
    }
    return Pitch(note = note, octave = octave)
}

private fun formatHz(value: Double): String = String.format(Locale.US, "%.2f Hz", value)

private enum class TuningOrder {
    LowToHigh,
    HighToLow
}

internal data class LeverHarpString(
    val highestFirstNumber: Int,
    val nominalLetter: String,
    val nominalName: String,
    val leverDownPitch: Pitch,
    val leverDownFrequencyHz: Double
) {
    fun leverUpPitch(): Pitch = leverDownPitch.plusSemitones(1)

    fun isRaised(setting: LeverKeySetting): Boolean =
        nominalLetter in setting.raisedLetters

    fun currentPitch(setting: LeverKeySetting): Pitch =
        if (isRaised(setting)) leverUpPitch() else leverDownPitch

    fun frequencyHz(setting: LeverKeySetting): Double =
        TunerTargetMatcher.pitchToFrequencyHz(currentPitch(setting))
}

private data class NominalHarpString(
    val letter: String,
    val octave: Int
)

private data class HarpStringPlacement(
    val string: LeverHarpString,
    val top: Offset,
    val bottom: Offset
)

internal data class LeverKeySetting(
    val keyName: String,
    val raisedLetters: Set<String>
) {
    val leverSummary: String
        get() = when {
            raisedLetters.isEmpty() -> "All down"
            raisedLetters.size == DiatonicLetters.size -> "All up"
            else -> "Raise ${DiatonicLetters.filter { it in raisedLetters }.joinToString(", ")}"
        }
}

internal val DiatonicLetters = listOf("C", "D", "E", "F", "G", "A", "B")

internal val LeverKeySettings = listOf(
    LeverKeySetting("Eb major / C minor", emptySet()),
    LeverKeySetting("Bb major / G minor", setOf("A")),
    LeverKeySetting("F major / D minor", setOf("A", "E")),
    LeverKeySetting("C major / A minor", setOf("A", "E", "B")),
    LeverKeySetting("G major / E minor", setOf("A", "E", "B", "F")),
    LeverKeySetting("D major / B minor", setOf("A", "E", "B", "F", "C")),
    LeverKeySetting("A major / F# minor", setOf("A", "E", "B", "F", "C", "G")),
    LeverKeySetting("E major / C# minor", DiatonicLetters.toSet())
)
