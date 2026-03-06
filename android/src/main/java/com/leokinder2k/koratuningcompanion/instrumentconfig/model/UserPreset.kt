package com.leokinder2k.koratuningcompanion.instrumentconfig.model

data class UserPreset(
    val id: String,
    val displayName: String,
    val profile: InstrumentProfile,
    val createdAtEpochMillis: Long
)

