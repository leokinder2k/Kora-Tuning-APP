package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted

actual fun isMicPermissionGranted(): Boolean =
    AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

@Composable
actual fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    return remember {
        {
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                onResult(granted)
            }
        }
    }
}
