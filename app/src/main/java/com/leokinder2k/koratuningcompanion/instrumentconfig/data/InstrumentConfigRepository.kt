package com.leokinder2k.koratuningcompanion.instrumentconfig.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.InstrumentProfile
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.UserPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

interface InstrumentConfigRepository {
    val instrumentProfile: Flow<InstrumentProfile?>
    val userPresets: Flow<List<UserPreset>>
    suspend fun saveInstrumentProfile(profile: InstrumentProfile)
    suspend fun saveUserPreset(displayName: String, profile: InstrumentProfile): String
    suspend fun deleteUserPreset(presetId: String)
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

    override val userPresets: Flow<List<UserPreset>> = dataStore.data.map { preferences ->
        parseUserPresetList(preferences[USER_PRESETS_KEY])
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

    override suspend fun saveUserPreset(displayName: String, profile: InstrumentProfile): String {
        val presetId = buildPresetId()
        val preset = UserPreset(
            id = presetId,
            displayName = sanitizePresetName(displayName),
            profile = profile,
            createdAtEpochMillis = System.currentTimeMillis()
        )

        dataStore.edit { preferences ->
            val existing = parseUserPresetList(preferences[USER_PRESETS_KEY])
            preferences[USER_PRESETS_KEY] = serializeUserPresetList(existing + preset)
        }

        return presetId
    }

    override suspend fun deleteUserPreset(presetId: String) {
        dataStore.edit { preferences ->
            val existing = parseUserPresetList(preferences[USER_PRESETS_KEY])
            val filtered = existing.filterNot { preset -> preset.id == presetId }
            preferences[USER_PRESETS_KEY] = serializeUserPresetList(filtered)
        }
    }

    companion object {
        private val Context.instrumentConfigDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "instrument_config"
        )

        private val SUPPORTED_STRING_COUNTS = setOf(21, 22)
        private const val PITCH_DELIMITER = "|"
        private const val PRESET_RECORD_DELIMITER = "\u001E"
        private const val PRESET_FIELD_DELIMITER = "\u001F"
        private val STRING_COUNT_KEY = intPreferencesKey("instrument_string_count")
        private val OPEN_TUNING_KEY = stringPreferencesKey("instrument_open_tuning")
        private val OPEN_INTONATION_CENTS_KEY = stringPreferencesKey("instrument_open_intonation_cents")
        private val CLOSED_INTONATION_CENTS_KEY = stringPreferencesKey("instrument_closed_intonation_cents")
        private val USER_PRESETS_KEY = stringPreferencesKey("instrument_user_presets")

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

        private fun parseUserPresetList(serialized: String?): List<UserPreset> {
            if (serialized.isNullOrBlank()) {
                return emptyList()
            }

            return serialized.split(PRESET_RECORD_DELIMITER)
                .mapNotNull { record -> parseUserPresetRecord(record) }
                .sortedByDescending { preset -> preset.createdAtEpochMillis }
        }

        private fun parseUserPresetRecord(record: String): UserPreset? {
            val fields = record.split(PRESET_FIELD_DELIMITER)
            if (fields.size != 7) {
                return null
            }

            val id = decodeField(fields[0]) ?: return null
            val displayName = decodeField(fields[1]) ?: return null
            val createdAt = decodeField(fields[2])?.toLongOrNull() ?: return null
            val stringCount = decodeField(fields[3])?.toIntOrNull() ?: return null
            if (stringCount !in SUPPORTED_STRING_COUNTS) {
                return null
            }

            val openPitchTexts = decodeField(fields[4])
                ?.split(PITCH_DELIMITER)
                ?.filter { text -> text.isNotBlank() }
                ?: return null
            val openPitches = openPitchTexts.mapNotNull(Pitch::parse)
            if (openPitches.size != stringCount) {
                return null
            }

            val openIntonation = decodeField(fields[5])
                ?.split(PITCH_DELIMITER)
                ?.mapNotNull { value -> value.toDoubleOrNull() }
                ?: return null
            if (openIntonation.size != stringCount) {
                return null
            }

            val closedIntonation = decodeField(fields[6])
                ?.split(PITCH_DELIMITER)
                ?.mapNotNull { value -> value.toDoubleOrNull() }
                ?: return null
            if (closedIntonation.size != stringCount) {
                return null
            }

            return UserPreset(
                id = id,
                displayName = displayName,
                createdAtEpochMillis = createdAt,
                profile = InstrumentProfile(
                    stringCount = stringCount,
                    openPitches = openPitches,
                    openIntonationCents = openIntonation,
                    closedIntonationCents = closedIntonation
                )
            )
        }

        private fun serializeUserPresetList(presets: List<UserPreset>): String {
            if (presets.isEmpty()) {
                return ""
            }
            return presets.joinToString(PRESET_RECORD_DELIMITER) { preset ->
                listOf(
                    encodeField(preset.id),
                    encodeField(preset.displayName),
                    encodeField(preset.createdAtEpochMillis.toString()),
                    encodeField(preset.profile.stringCount.toString()),
                    encodeField(preset.profile.openPitches.joinToString(PITCH_DELIMITER) { pitch -> pitch.asText() }),
                    encodeField(preset.profile.openIntonationCents.joinToString(PITCH_DELIMITER) { cents -> cents.toString() }),
                    encodeField(preset.profile.closedIntonationCents.joinToString(PITCH_DELIMITER) { cents -> cents.toString() })
                ).joinToString(PRESET_FIELD_DELIMITER)
            }
        }

        private fun sanitizePresetName(name: String): String {
            val cleaned = name.trim().replace(Regex("\\s+"), " ")
            return if (cleaned.isBlank()) {
                "Custom Preset"
            } else {
                cleaned
            }
        }

        private fun buildPresetId(): String {
            return "user_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        }

        private fun encodeField(value: String): String {
            val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), flags)
        }

        private fun decodeField(value: String): String? {
            return runCatching {
                val flags = Base64.URL_SAFE or Base64.NO_WRAP
                String(Base64.decode(value, flags), Charsets.UTF_8)
            }.getOrNull()
        }
    }
}

