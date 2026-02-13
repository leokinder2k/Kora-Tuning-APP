package com.example.koratuningsystem.instrumentconfig.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.koratuningsystem.instrumentconfig.model.InstrumentProfile
import com.example.koratuningsystem.instrumentconfig.model.Pitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface InstrumentConfigRepository {
    val instrumentProfile: Flow<InstrumentProfile?>
    suspend fun saveInstrumentProfile(profile: InstrumentProfile)
}

class DataStoreInstrumentConfigRepository private constructor(
    private val dataStore: DataStore<Preferences>
) : InstrumentConfigRepository {

    override val instrumentProfile: Flow<InstrumentProfile?> = dataStore.data.map { preferences ->
        val stringCount = preferences[STRING_COUNT_KEY] ?: return@map null
        if (stringCount !in SUPPORTED_STRING_COUNTS) {
            return@map null
        }

        val serializedOpenTuning = preferences[OPEN_TUNING_KEY] ?: return@map null
        val parsedPitches = serializedOpenTuning.split(PITCH_DELIMITER)
            .map { item -> item.trim() }
            .filter { item -> item.isNotEmpty() }
            .mapNotNull(Pitch::parse)

        if (parsedPitches.size != stringCount) {
            return@map null
        }

        val openIntonation = parseCentsList(preferences[OPEN_INTONATION_CENTS_KEY], stringCount)
        val closedIntonation = parseCentsList(preferences[CLOSED_INTONATION_CENTS_KEY], stringCount)

        InstrumentProfile(
            stringCount = stringCount,
            openPitches = parsedPitches,
            openIntonationCents = openIntonation,
            closedIntonationCents = closedIntonation
        )
    }

    override suspend fun saveInstrumentProfile(profile: InstrumentProfile) {
        dataStore.edit { preferences ->
            preferences[STRING_COUNT_KEY] = profile.stringCount
            preferences[OPEN_TUNING_KEY] = profile.openPitches.joinToString(PITCH_DELIMITER) { pitch ->
                pitch.asText()
            }
            preferences[OPEN_INTONATION_CENTS_KEY] = profile.openIntonationCents.joinToString(PITCH_DELIMITER) { cents ->
                cents.toString()
            }
            preferences[CLOSED_INTONATION_CENTS_KEY] = profile.closedIntonationCents.joinToString(PITCH_DELIMITER) { cents ->
                cents.toString()
            }
        }
    }

    companion object {
        private val Context.instrumentConfigDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "instrument_config"
        )

        private val SUPPORTED_STRING_COUNTS = setOf(21, 22)
        private const val PITCH_DELIMITER = "|"
        private val STRING_COUNT_KEY = intPreferencesKey("instrument_string_count")
        private val OPEN_TUNING_KEY = stringPreferencesKey("instrument_open_tuning")
        private val OPEN_INTONATION_CENTS_KEY = stringPreferencesKey("instrument_open_intonation_cents")
        private val CLOSED_INTONATION_CENTS_KEY = stringPreferencesKey("instrument_closed_intonation_cents")

        private fun parseCentsList(serialized: String?, stringCount: Int): List<Double> {
            val fallback = List(stringCount) { 0.0 }
            if (serialized.isNullOrBlank()) {
                return fallback
            }

            val parsed = serialized.split(PITCH_DELIMITER)
                .map { item -> item.trim() }
                .filter { item -> item.isNotEmpty() }
                .mapNotNull { value -> value.toDoubleOrNull() }

            return if (parsed.size == stringCount) {
                parsed
            } else {
                fallback
            }
        }

        fun create(context: Context): DataStoreInstrumentConfigRepository {
            return DataStoreInstrumentConfigRepository(
                dataStore = context.applicationContext.instrumentConfigDataStore
            )
        }
    }
}
