package com.leokinder2k.koratuningcompanion.notation.engine

// Port of instrument.js

enum class KoraInstrumentType(val leftCount: Int, val rightCount: Int) {
    KORA_21(leftCount = 11, rightCount = 10),
    KORA_22_CHROMATIC(leftCount = 11, rightCount = 11);

    companion object {
        fun fromString(raw: String): KoraInstrumentType = when (raw.uppercase()) {
            "KORA_22", "KORA_22_CHROMATIC" -> KORA_22_CHROMATIC
            else -> KORA_21
        }
    }
}
