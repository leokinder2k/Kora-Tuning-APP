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
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private data class ScaleTypeFamily(
    val label: String,
    val types: List<ScaleType>
)

@Composable
private fun scaleTypeFamilies(): List<ScaleTypeFamily> = listOf(
    ScaleTypeFamily(stringResource(Res.string.scale_family_modes), listOf(
        ScaleType.IONIAN, ScaleType.DORIAN, ScaleType.PHRYGIAN, ScaleType.LYDIAN,
        ScaleType.MIXOLYDIAN, ScaleType.AEOLIAN, ScaleType.LOCRIAN
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_pentatonic), listOf(
        ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_blues), listOf(
        ScaleType.MAJOR_BLUES, ScaleType.MINOR_BLUES
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_kora_scales), listOf(
        ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_hexatonic), listOf(
        ScaleType.MAJOR_HEXATONIC, ScaleType.MINOR_HEXATONIC, ScaleType.WHOLE_TONE
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_beebop), listOf(
        ScaleType.BEEBOP_MAJOR, ScaleType.BEEBOP_DOMINANT, ScaleType.BEEBOP_DORIAN
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_diminished), listOf(
        ScaleType.DIMINISHED_WHOLE_HALF, ScaleType.DIMINISHED_HALF_WHOLE
    )),
    ScaleTypeFamily(stringResource(Res.string.scale_family_other), listOf(ScaleType.CHROMATIC)),
    ScaleTypeFamily(stringResource(Res.string.scale_family_world_scales), listOf(
        ScaleType.HUNGARIAN_MINOR, ScaleType.HUNGARIAN_MAJOR,
        ScaleType.RAGA_BHAIRAV, ScaleType.RAGA_YAMAN, ScaleType.RAGA_KAFI, ScaleType.RAGA_BHAIRAVI,
        ScaleType.NEAPOLITAN_MAJOR, ScaleType.NEAPOLITAN_MINOR,
        ScaleType.PHRYGIAN_DOMINANT, ScaleType.DOUBLE_HARMONIC, ScaleType.PERSIAN,
        ScaleType.HIRAJOSHI, ScaleType.JAPANESE_IN, ScaleType.IWATO, ScaleType.INSEN,
        ScaleType.PROMETHEUS, ScaleType.ENIGMATIC, ScaleType.TRITONE,
        ScaleType.AUGMENTED, ScaleType.AUGMENTED_INVERSE
    ))
)

@Composable
fun ScaleTypeDropdownMenus(
    selectedScaleType: ScaleType,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFamilyMenuExpanded by remember { mutableStateOf(false) }
    var isScaleMenuExpanded by remember { mutableStateOf(false) }

    val families = scaleTypeFamilies()
    val selectedFamily = families.firstOrNull { it.types.contains(selectedScaleType) } ?: families.last()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.scale_family_label),
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
                families.forEach { family ->
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
            text = stringResource(Res.string.scale_label),
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isScaleMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(scaleTypeLabel(selectedScaleType))
            }
            DropdownMenu(
                expanded = isScaleMenuExpanded,
                onDismissRequest = { isScaleMenuExpanded = false }
            ) {
                selectedFamily.types.forEach { scaleType ->
                    DropdownMenuItem(
                        text = { Text(scaleTypeLabel(scaleType)) },
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

@Composable
fun scaleTypeLabel(scaleType: ScaleType): String {
    return when (scaleType) {
        ScaleType.MAJOR -> stringResource(Res.string.scale_type_major)
        ScaleType.NATURAL_MINOR -> stringResource(Res.string.scale_type_natural_minor)
        ScaleType.HARMONIC_MINOR -> stringResource(Res.string.scale_type_harmonic_minor)
        ScaleType.MELODIC_MINOR -> stringResource(Res.string.scale_type_melodic_minor)
        ScaleType.IONIAN -> stringResource(Res.string.scale_type_ionian)
        ScaleType.DORIAN -> stringResource(Res.string.scale_type_dorian)
        ScaleType.PHRYGIAN -> stringResource(Res.string.scale_type_phrygian)
        ScaleType.LYDIAN -> stringResource(Res.string.scale_type_lydian)
        ScaleType.MIXOLYDIAN -> stringResource(Res.string.scale_type_mixolydian)
        ScaleType.AEOLIAN -> stringResource(Res.string.scale_type_aeolian)
        ScaleType.LOCRIAN -> stringResource(Res.string.scale_type_locrian)
        ScaleType.MAJOR_PENTATONIC -> stringResource(Res.string.scale_type_major_pentatonic)
        ScaleType.MINOR_PENTATONIC -> stringResource(Res.string.scale_type_minor_pentatonic)
        ScaleType.MAJOR_HEXATONIC -> stringResource(Res.string.scale_type_major_hexatonic)
        ScaleType.MINOR_HEXATONIC -> stringResource(Res.string.scale_type_minor_hexatonic)
        ScaleType.WHOLE_TONE -> stringResource(Res.string.scale_type_whole_tone)
        ScaleType.MAJOR_BLUES -> stringResource(Res.string.scale_type_major_blues)
        ScaleType.MINOR_BLUES -> stringResource(Res.string.scale_type_minor_blues)
        ScaleType.BEEBOP_MAJOR -> stringResource(Res.string.scale_type_beebop_major)
        ScaleType.BEEBOP_DOMINANT -> stringResource(Res.string.scale_type_beebop_dominant)
        ScaleType.BEEBOP_DORIAN -> stringResource(Res.string.scale_type_beebop_dorian)
        ScaleType.DIMINISHED_WHOLE_HALF -> stringResource(Res.string.scale_type_diminished_whole_half)
        ScaleType.DIMINISHED_HALF_WHOLE -> stringResource(Res.string.scale_type_diminished_half_whole)
        ScaleType.CHROMATIC -> stringResource(Res.string.scale_type_chromatic)
        ScaleType.HUNGARIAN_MINOR -> stringResource(Res.string.scale_type_hungarian_minor)
        ScaleType.HUNGARIAN_MAJOR -> stringResource(Res.string.scale_type_hungarian_major)
        ScaleType.RAGA_BHAIRAV -> stringResource(Res.string.scale_type_raga_bhairav)
        ScaleType.RAGA_YAMAN -> stringResource(Res.string.scale_type_raga_yaman)
        ScaleType.RAGA_KAFI -> stringResource(Res.string.scale_type_raga_kafi)
        ScaleType.RAGA_BHAIRAVI -> stringResource(Res.string.scale_type_raga_bhairavi)
        ScaleType.NEAPOLITAN_MAJOR -> stringResource(Res.string.scale_type_neapolitan_major)
        ScaleType.NEAPOLITAN_MINOR -> stringResource(Res.string.scale_type_neapolitan_minor)
        ScaleType.PHRYGIAN_DOMINANT -> stringResource(Res.string.scale_type_phrygian_dominant)
        ScaleType.DOUBLE_HARMONIC -> stringResource(Res.string.scale_type_double_harmonic)
        ScaleType.PERSIAN -> stringResource(Res.string.scale_type_persian)
        ScaleType.HIRAJOSHI -> stringResource(Res.string.scale_type_hirajoshi)
        ScaleType.JAPANESE_IN -> stringResource(Res.string.scale_type_japanese_in)
        ScaleType.IWATO -> stringResource(Res.string.scale_type_iwato)
        ScaleType.INSEN -> stringResource(Res.string.scale_type_insen)
        ScaleType.PROMETHEUS -> stringResource(Res.string.scale_type_prometheus)
        ScaleType.ENIGMATIC -> stringResource(Res.string.scale_type_enigmatic)
        ScaleType.TRITONE -> stringResource(Res.string.scale_type_tritone)
        ScaleType.AUGMENTED -> stringResource(Res.string.scale_type_augmented)
        ScaleType.AUGMENTED_INVERSE -> stringResource(Res.string.scale_type_augmented_inverse)
    }
}
