package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType

private data class ScaleTypeFamily(
    @param:StringRes val labelRes: Int,
    val types: List<ScaleType>
)

private val ScaleTypeFamilies = listOf(
    ScaleTypeFamily(
        labelRes = R.string.scale_family_modes,
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
        labelRes = R.string.scale_family_pentatonic,
        types = listOf(
            ScaleType.MAJOR_PENTATONIC,
            ScaleType.MINOR_PENTATONIC
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_blues,
        types = listOf(
            ScaleType.MAJOR_BLUES,
            ScaleType.MINOR_BLUES
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_kora_scales,
        types = listOf(
            ScaleType.MAJOR,
            ScaleType.NATURAL_MINOR,
            ScaleType.HARMONIC_MINOR,
            ScaleType.MELODIC_MINOR
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_hexatonic,
        types = listOf(
            ScaleType.MAJOR_HEXATONIC,
            ScaleType.MINOR_HEXATONIC,
            ScaleType.WHOLE_TONE
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_beebop,
        types = listOf(
            ScaleType.BEEBOP_MAJOR,
            ScaleType.BEEBOP_DOMINANT,
            ScaleType.BEEBOP_DORIAN
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_diminished,
        types = listOf(
            ScaleType.DIMINISHED_WHOLE_HALF,
            ScaleType.DIMINISHED_HALF_WHOLE
        )
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_other,
        types = listOf(ScaleType.CHROMATIC)
    ),
    ScaleTypeFamily(
        labelRes = R.string.scale_family_world_scales,
        types = listOf(
            ScaleType.HUNGARIAN_MINOR, ScaleType.HUNGARIAN_MAJOR,
            ScaleType.RAGA_BHAIRAV, ScaleType.RAGA_YAMAN, ScaleType.RAGA_KAFI, ScaleType.RAGA_BHAIRAVI,
            ScaleType.NEAPOLITAN_MAJOR, ScaleType.NEAPOLITAN_MINOR,
            ScaleType.PHRYGIAN_DOMINANT, ScaleType.DOUBLE_HARMONIC, ScaleType.PERSIAN,
            ScaleType.HIRAJOSHI, ScaleType.JAPANESE_IN, ScaleType.IWATO, ScaleType.INSEN,
            ScaleType.PROMETHEUS, ScaleType.ENIGMATIC, ScaleType.TRITONE,
            ScaleType.AUGMENTED, ScaleType.AUGMENTED_INVERSE
        )
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
            text = stringResource(R.string.scale_family_label),
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isFamilyMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(selectedFamily.labelRes))
            }
            DropdownMenu(
                expanded = isFamilyMenuExpanded,
                onDismissRequest = { isFamilyMenuExpanded = false },
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                ScaleTypeFamilies.forEach { family ->
                    DropdownMenuItem(
                        text = { Text(stringResource(family.labelRes)) },
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
            text = stringResource(R.string.scale_label),
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
                onDismissRequest = { isScaleMenuExpanded = false },
                modifier = Modifier.widthIn(max = 280.dp)
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

private fun familyFor(scaleType: ScaleType): ScaleTypeFamily {
    return ScaleTypeFamilies.firstOrNull { family -> scaleType in family.types }
        ?: ScaleTypeFamilies.last()
}

@Composable
internal fun scaleTypeLabel(scaleType: ScaleType): String {
    return when (scaleType) {
        ScaleType.MAJOR -> stringResource(R.string.scale_type_major)
        ScaleType.NATURAL_MINOR -> stringResource(R.string.scale_type_natural_minor)
        ScaleType.HARMONIC_MINOR -> stringResource(R.string.scale_type_harmonic_minor)
        ScaleType.MELODIC_MINOR -> stringResource(R.string.scale_type_melodic_minor)

        ScaleType.IONIAN -> stringResource(R.string.scale_type_ionian)
        ScaleType.DORIAN -> stringResource(R.string.scale_type_dorian)
        ScaleType.PHRYGIAN -> stringResource(R.string.scale_type_phrygian)
        ScaleType.LYDIAN -> stringResource(R.string.scale_type_lydian)
        ScaleType.MIXOLYDIAN -> stringResource(R.string.scale_type_mixolydian)
        ScaleType.AEOLIAN -> stringResource(R.string.scale_type_aeolian)
        ScaleType.LOCRIAN -> stringResource(R.string.scale_type_locrian)

        ScaleType.MAJOR_PENTATONIC -> stringResource(R.string.scale_type_major_pentatonic)
        ScaleType.MINOR_PENTATONIC -> stringResource(R.string.scale_type_minor_pentatonic)

        ScaleType.MAJOR_HEXATONIC -> stringResource(R.string.scale_type_major_hexatonic)
        ScaleType.MINOR_HEXATONIC -> stringResource(R.string.scale_type_minor_hexatonic)
        ScaleType.WHOLE_TONE -> stringResource(R.string.scale_type_whole_tone)
        ScaleType.MAJOR_BLUES -> stringResource(R.string.scale_type_major_blues)
        ScaleType.MINOR_BLUES -> stringResource(R.string.scale_type_minor_blues)

        ScaleType.BEEBOP_MAJOR -> stringResource(R.string.scale_type_beebop_major)
        ScaleType.BEEBOP_DOMINANT -> stringResource(R.string.scale_type_beebop_dominant)
        ScaleType.BEEBOP_DORIAN -> stringResource(R.string.scale_type_beebop_dorian)

        ScaleType.DIMINISHED_WHOLE_HALF -> stringResource(R.string.scale_type_diminished_whole_half)
        ScaleType.DIMINISHED_HALF_WHOLE -> stringResource(R.string.scale_type_diminished_half_whole)

        ScaleType.CHROMATIC -> stringResource(R.string.scale_type_chromatic)
        ScaleType.HUNGARIAN_MINOR -> stringResource(R.string.scale_type_hungarian_minor)
        ScaleType.HUNGARIAN_MAJOR -> stringResource(R.string.scale_type_hungarian_major)
        ScaleType.RAGA_BHAIRAV -> stringResource(R.string.scale_type_raga_bhairav)
        ScaleType.RAGA_YAMAN -> stringResource(R.string.scale_type_raga_yaman)
        ScaleType.RAGA_KAFI -> stringResource(R.string.scale_type_raga_kafi)
        ScaleType.RAGA_BHAIRAVI -> stringResource(R.string.scale_type_raga_bhairavi)
        ScaleType.NEAPOLITAN_MAJOR -> stringResource(R.string.scale_type_neapolitan_major)
        ScaleType.NEAPOLITAN_MINOR -> stringResource(R.string.scale_type_neapolitan_minor)
        ScaleType.PHRYGIAN_DOMINANT -> stringResource(R.string.scale_type_phrygian_dominant)
        ScaleType.DOUBLE_HARMONIC -> stringResource(R.string.scale_type_double_harmonic)
        ScaleType.PERSIAN -> stringResource(R.string.scale_type_persian)
        ScaleType.HIRAJOSHI -> stringResource(R.string.scale_type_hirajoshi)
        ScaleType.JAPANESE_IN -> stringResource(R.string.scale_type_japanese_in)
        ScaleType.IWATO -> stringResource(R.string.scale_type_iwato)
        ScaleType.INSEN -> stringResource(R.string.scale_type_insen)
        ScaleType.PROMETHEUS -> stringResource(R.string.scale_type_prometheus)
        ScaleType.ENIGMATIC -> stringResource(R.string.scale_type_enigmatic)
        ScaleType.TRITONE -> stringResource(R.string.scale_type_tritone)
        ScaleType.AUGMENTED -> stringResource(R.string.scale_type_augmented)
        ScaleType.AUGMENTED_INVERSE -> stringResource(R.string.scale_type_augmented_inverse)
    }
}

