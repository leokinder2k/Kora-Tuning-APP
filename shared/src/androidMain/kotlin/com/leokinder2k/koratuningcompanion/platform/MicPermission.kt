package com.leokinder2k.koratuningcompanion.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

actual fun isMicPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
        AppContextHolder.context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    return { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}
