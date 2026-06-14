package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun isMicPermissionGranted(): Boolean =
    AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionGranted

@Composable
actual fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    return remember {
        {
            AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    onResult(granted)
                }
            }
        }
    }
}
