package com.leokinder2k.koratuningcompanion.ui.theme

import androidx.compose.ui.graphics.Color

val KoraOpenLeverColor = Color(0xFF2EBD59)         // green  — lever open, no action
val KoraClosedLeverColor = Color(0xFFEF5350)        // red    — used for tuner sharp indicator
val KoraLeverClosedStringColor = Color(0xFFFF8C00)  // amber  — close lever only (colorblind-safe)
val KoraDetunedColor = Color(0xFF6EC6FF)            // blue   — peg retune only
val KoraCombinedChangesColor = Color(0xFF9C27B0)    // purple — both close lever AND peg retune
val KoraActionBadgeColor = Color(0xFFC0C0C0)        // silver — neutral action badge color

val KoraInTuneColor = KoraOpenLeverColor
val KoraSharpColor = KoraClosedLeverColor
val KoraFlatColor = KoraDetunedColor
