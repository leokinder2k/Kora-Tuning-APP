package com.leokinder2k.koratuningcompanion.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

private val appSettingsStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            (AppContextHolder.context.filesDir.absolutePath + "/datastore/app_settings.preferences_pb").toPath()
        }
    )
}

private val instrumentConfigStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            (AppContextHolder.context.filesDir.absolutePath + "/datastore/instrument_config.preferences_pb").toPath()
        }
    )
}

actual fun createAppSettingsDataStore(): DataStore<Preferences> = appSettingsStore
actual fun createInstrumentConfigDataStore(): DataStore<Preferences> = instrumentConfigStore
