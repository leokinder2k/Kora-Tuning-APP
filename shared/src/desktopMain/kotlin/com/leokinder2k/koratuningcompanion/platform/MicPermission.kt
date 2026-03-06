package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable

actual fun isMicPermissionGranted(): Boolean = true

@Composable
actual fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit = { }
