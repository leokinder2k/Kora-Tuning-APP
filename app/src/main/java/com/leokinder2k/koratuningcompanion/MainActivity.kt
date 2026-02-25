package com.leokinder2k.koratuningcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leokinder2k.koratuningcompanion.navigation.KoraAuthorityApp
import com.leokinder2k.koratuningcompanion.settings.AppSettingsRepository
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = remember { AppSettingsRepository(applicationContext) }
            val themeMode by repo.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val scope = rememberCoroutineScope()
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            KoraTuningSystemTheme(darkTheme = darkTheme) {
                KoraAuthorityApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> scope.launch { repo.setThemeMode(mode) } }
                )
            }
        }
    }
}
