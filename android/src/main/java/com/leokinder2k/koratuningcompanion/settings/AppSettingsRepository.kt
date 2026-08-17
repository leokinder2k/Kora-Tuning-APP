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
        val NAVIGATION_TAB_ORDER = stringPreferencesKey("navigation_tab_order")
        val VISIBLE_NAVIGATION_TABS = stringPreferencesKey("visible_navigation_tabs")
    }

    /** Emits "SYSTEM", "LIGHT", or "DARK". */
    val themeModeFlow: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "SYSTEM"
    }

    val navigationTabOrderFlow: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[NAVIGATION_TAB_ORDER].orEmpty()
    }

    val visibleNavigationTabsFlow: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[VISIBLE_NAVIGATION_TABS].orEmpty()
    }

    suspend fun setThemeMode(mode: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    suspend fun setNavigationTabs(visibleTabNames: List<String>, tabOrderNames: List<String>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[VISIBLE_NAVIGATION_TABS] = visibleTabNames.joinToString(",")
            prefs[NAVIGATION_TAB_ORDER] = tabOrderNames.joinToString(",")
        }
    }
}
