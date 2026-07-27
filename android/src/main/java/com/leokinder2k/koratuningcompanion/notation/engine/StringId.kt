package com.leokinder2k.koratuningcompanion.notation.engine

// Port of stringId.js

data class ParsedStringId(
    val side: Char,                    // 'L' or 'R'
    val physicalIndexFromGourd: Int,   // 1-based
)

fun parseStringId(stringId: String): ParsedStringId {
    require(stringId.length >= 2) { "Invalid stringId: $stringId" }
    val side = stringId[0]
    require(side == 'L' || side == 'R') { "Invalid stringId side: $stringId" }
    val num = stringId.substring(1).toIntOrNull()
        ?: error("Invalid stringId index: $stringId")
    require(if (side == 'R') num >= 0 else num >= 1) { "Invalid stringId index: $stringId" }
    return ParsedStringId(side, num)
}

fun allStringIds(instrumentType: KoraInstrumentType): List<String> {
    val ids = mutableListOf<String>()
    for (i in 1..instrumentType.leftCount) ids.add("L$i")
    val firstRight = if (instrumentType == KoraInstrumentType.KORA_22_CHROMATIC) 0 else 1
    for (i in firstRight until firstRight + instrumentType.rightCount) ids.add("R$i")
    return ids
}

/**
 * Thumb numbering starts at the bass end: the lowest string in the thumb zone is 1.
 * Finger numbering starts at the treble end: the highest string in the finger zone is 1.
 */
fun renderedNumber(instrumentType: KoraInstrumentType, stringId: String, digitLine: String): Int {
    val (side, displayIndex) = parseStringId(stringId)
    val n = if (side == 'L') instrumentType.leftCount else instrumentType.rightCount
    val physicalIndex = if (side == 'R' && instrumentType == KoraInstrumentType.KORA_22_CHROMATIC) {
        displayIndex + 1
    } else {
        displayIndex
    }
    require(physicalIndex in 1..n) { "Invalid stringId index for instrument: $stringId" }
    val usesFingerNumbering = digitLine == "LF" || digitLine == "RF"
    val usesThumbNumbering = digitLine == "LT" || digitLine == "RT"
    require(usesFingerNumbering || usesThumbNumbering) { "Unknown digitLine: $digitLine" }
    return if (usesThumbNumbering) {
        require(physicalIndex <= MAX_TAB_DIGIT) {
            "$stringId is outside the six-string thumb zone for $digitLine"
        }
        physicalIndex
    } else {
        val firstFingerIndex = maxOf(1, n - MAX_TAB_DIGIT + 1)
        require(physicalIndex >= firstFingerIndex) {
            "$stringId is outside the six-string finger zone for $digitLine"
        }
        n - physicalIndex + 1
    }
}

private const val MAX_TAB_DIGIT = 6
