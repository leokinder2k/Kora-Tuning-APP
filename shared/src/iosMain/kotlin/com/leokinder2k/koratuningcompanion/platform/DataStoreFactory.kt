@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leokinder2k.koratuningcompanion.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private fun dataStoreDir(): String {
    val urls = NSFileManager.defaultManager.URLsForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask
    )
    val basePath = (urls.first() as NSURL).path!!
    val appPath = "$basePath/KoraTuningCompanion"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = appPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    return appPath
}

private val appSettingsStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${dataStoreDir()}/app_settings.preferences_pb".toPath() }
    )
}

private val instrumentConfigStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${dataStoreDir()}/instrument_config.preferences_pb".toPath() }
    )
}

actual fun createAppSettingsDataStore(): DataStore<Preferences> = appSettingsStore
actual fun createInstrumentConfigDataStore(): DataStore<Preferences> = instrumentConfigStore
