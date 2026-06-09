package com.leokinder2k.koratuningcompanion.notation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun KoraNotationRoute(
    modifier: Modifier,
    isMuted: Boolean
) {
    KoraNotationAndroidContent(modifier = modifier, isMuted = isMuted)
}
