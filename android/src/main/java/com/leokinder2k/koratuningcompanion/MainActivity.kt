package com.leokinder2k.koratuningcompanion

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatActivity
import com.leokinder2k.koratuningcompanion.navigation.KoraAuthorityApp
import com.leokinder2k.koratuningcompanion.settings.AppSettingsRepository
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val usbAttachRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isUsbMidiAttach(intent)) {
            usbAttachRequests.value = 1
        }
        enableEdgeToEdge()
        setContent {
            val repo = remember { AppSettingsRepository(applicationContext) }
            val themeMode by repo.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val usbAttachRequest by usbAttachRequests.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            KoraTuningSystemTheme(darkTheme = darkTheme) {
                KoraAuthorityApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> scope.launch { repo.setThemeMode(mode) } },
                    usbAttachRequest = usbAttachRequest
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isUsbMidiAttach(intent)) {
            usbAttachRequests.value += 1
        }
    }
}

private fun isUsbMidiAttach(intent: Intent?): Boolean {
    return intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED
}
