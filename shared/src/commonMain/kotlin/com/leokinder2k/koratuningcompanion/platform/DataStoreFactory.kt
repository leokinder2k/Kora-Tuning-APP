package com.leokinder2k.koratuningcompanion.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

expect fun createAppSettingsDataStore(): DataStore<Preferences>
expect fun createInstrumentConfigDataStore(): DataStore<Preferences>
