package com.leokinder2k.koratuningcompanion.leverharp

import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.audio.ReferenceTonePlayer
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import java.util.Locale

@Composable
fun LeverHarpRoute(
    enharmonicPreference: EnharmonicPreference,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = remember { standardLeverHarpStrings() }
    var order by rememberSaveable { mutableStateOf(TuningOrder.LowToHigh.name) }
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
            referenceTonePlayer.play(selectedString.frequencyHz)
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
            SelectedStringCard(
                string = selectedString,
                enharmonicPreference = enharmonicPreference,
                isMuted = isMuted,
                isPlaying = playingReference,
                onToggleReference = { playingReference = !playingReference }
            )
        }
        item {
            KeyLeverCard()
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
                enharmonicPreference = enharmonicPreference,
                onClick = {
                    selectedStringNumber = string.highestFirstNumber
                    if (playingReference && !isMuted) {
                        referenceTonePlayer.play(string.frequencyHz)
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
private fun SelectedStringCard(
    string: LeverHarpString,
    enharmonicPreference: EnharmonicPreference,
    isMuted: Boolean,
    isPlaying: Boolean,
    onToggleReference: () -> Unit
) {
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
                        text = "Levers down: ${string.leverDownPitch.asText(enharmonicPreference)}  ${formatHz(string.frequencyHz)}",
                        style = MaterialTheme.typography.bodyMedium
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
private fun KeyLeverCard() {
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
                        text = setting.raisedLevers.ifEmpty { "All down" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HarpStringRow(
    string: LeverHarpString,
    selected: Boolean,
    enharmonicPreference: EnharmonicPreference,
    onClick: () -> Unit
) {
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
                    text = "Tune down: ${string.leverDownPitch.asText(enharmonicPreference)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = formatHz(string.frequencyHz),
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

private fun standardLeverHarpStrings(): List<LeverHarpString> {
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
            frequencyHz = TunerTargetMatcher.pitchToFrequencyHz(leverDownPitch)
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

private data class LeverHarpString(
    val highestFirstNumber: Int,
    val nominalLetter: String,
    val nominalName: String,
    val leverDownPitch: Pitch,
    val frequencyHz: Double
)

private data class NominalHarpString(
    val letter: String,
    val octave: Int
)

private data class LeverKeySetting(
    val keyName: String,
    val raisedLevers: String
)

private val DiatonicLetters = listOf("C", "D", "E", "F", "G", "A", "B")

private val LeverKeySettings = listOf(
    LeverKeySetting("Eb major / C minor", ""),
    LeverKeySetting("Bb major / G minor", "Raise A"),
    LeverKeySetting("F major / D minor", "Raise A, E"),
    LeverKeySetting("C major / A minor", "Raise A, E, B"),
    LeverKeySetting("G major / E minor", "Raise A, E, B, F"),
    LeverKeySetting("D major / B minor", "Raise A, E, B, F, C"),
    LeverKeySetting("A major / F# minor", "Raise A, E, B, F, C, G"),
    LeverKeySetting("E major / C# minor", "All up")
)
