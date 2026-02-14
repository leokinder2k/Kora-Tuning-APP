package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType

private data class ScaleTypeFamily(
    val label: String,
    val types: List<ScaleType>
)

private val ScaleTypeFamilies = listOf(
    ScaleTypeFamily(
        label = "Modes",
        types = listOf(
            ScaleType.IONIAN,
            ScaleType.DORIAN,
            ScaleType.PHRYGIAN,
            ScaleType.LYDIAN,
            ScaleType.MIXOLYDIAN,
            ScaleType.AEOLIAN,
            ScaleType.LOCRIAN
        )
    ),
    ScaleTypeFamily(
        label = "Pentatonic",
        types = listOf(
            ScaleType.MAJOR_PENTATONIC,
            ScaleType.MINOR_PENTATONIC
        )
    ),
    ScaleTypeFamily(
        label = "Blues",
        types = listOf(
            ScaleType.MAJOR_BLUES,
            ScaleType.MINOR_BLUES
        )
    ),
    ScaleTypeFamily(
        label = "Kora Scales",
        types = listOf(
            ScaleType.MAJOR,
            ScaleType.NATURAL_MINOR,
            ScaleType.HARMONIC_MINOR,
            ScaleType.MELODIC_MINOR
        )
    ),
    ScaleTypeFamily(
        label = "Hexatonic",
        types = listOf(
            ScaleType.MAJOR_HEXATONIC,
            ScaleType.MINOR_HEXATONIC,
            ScaleType.WHOLE_TONE
        )
    ),
    ScaleTypeFamily(
        label = "Beebop",
        types = listOf(
            ScaleType.BEEBOP_MAJOR,
            ScaleType.BEEBOP_DOMINANT,
            ScaleType.BEEBOP_DORIAN
        )
    ),
    ScaleTypeFamily(
        label = "Diminished",
        types = listOf(
            ScaleType.DIMINISHED_WHOLE_HALF,
            ScaleType.DIMINISHED_HALF_WHOLE
        )
    ),
    ScaleTypeFamily(
        label = "Other",
        types = listOf(ScaleType.CHROMATIC)
    )
)

@Composable
fun ScaleTypeDropdownMenus(
    selectedScaleType: ScaleType,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFamilyMenuExpanded by remember { mutableStateOf(false) }
    var isScaleMenuExpanded by remember { mutableStateOf(false) }

    val selectedFamily = familyFor(selectedScaleType)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Scale family",
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isFamilyMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedFamily.label)
            }
            DropdownMenu(
                expanded = isFamilyMenuExpanded,
                onDismissRequest = { isFamilyMenuExpanded = false }
            ) {
                ScaleTypeFamilies.forEach { family ->
                    DropdownMenuItem(
                        text = { Text(family.label) },
                        onClick = {
                            isFamilyMenuExpanded = false
                            if (selectedScaleType !in family.types) {
                                onScaleTypeSelected(family.types.first())
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = "Scale",
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isScaleMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedScaleType.displayName)
            }
            DropdownMenu(
                expanded = isScaleMenuExpanded,
                onDismissRequest = { isScaleMenuExpanded = false }
            ) {
                selectedFamily.types.forEach { scaleType ->
                    DropdownMenuItem(
                        text = { Text(scaleType.displayName) },
                        onClick = {
                            isScaleMenuExpanded = false
                            onScaleTypeSelected(scaleType)
                        }
                    )
                }
            }
        }
    }
}

private fun familyFor(scaleType: ScaleType): ScaleTypeFamily {
    return ScaleTypeFamilies.firstOrNull { family -> scaleType in family.types }
        ?: ScaleTypeFamilies.last()
}

