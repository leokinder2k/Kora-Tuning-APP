package com.leokinder2k.koratuningcompanion.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leokinder2k.koratuningcompanion.platform.createAppSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppSettingsRepository {

    private val dataStore = createAppSettingsDataStore()

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** Emits "SYSTEM", "LIGHT", or "DARK". */
    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }
}
