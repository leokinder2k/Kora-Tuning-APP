package com.leokinder2k.koratuningcompanion.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

class AppSettingsRepository(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** Emits "SYSTEM", "LIGHT", or "DARK". */
    val themeModeFlow: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }
}
