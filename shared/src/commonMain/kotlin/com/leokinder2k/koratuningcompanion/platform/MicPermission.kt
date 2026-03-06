package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable

expect fun isMicPermissionGranted(): Boolean

@Composable
expect fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit
