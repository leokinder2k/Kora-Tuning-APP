package com.leokinder2k.koratuningcompanion.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import java.io.File

private val configDir: File = File(System.getProperty("user.home"), ".config/KoraTuningCompanion")
    .also { it.mkdirs() }

private val appSettingsStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { File(configDir, "app_settings.preferences_pb").absolutePath.toPath() }
    )
}

private val instrumentConfigStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { File(configDir, "instrument_config.preferences_pb").absolutePath.toPath() }
    )
}

actual fun createAppSettingsDataStore(): DataStore<Preferences> = appSettingsStore
actual fun createInstrumentConfigDataStore(): DataStore<Preferences> = instrumentConfigStore
