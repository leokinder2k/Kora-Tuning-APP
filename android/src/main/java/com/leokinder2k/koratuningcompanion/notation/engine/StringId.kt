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
 * Finger numbering is physical index (1-based).
 * Thumb numbering is reversed: N+1 - physicalIndex.
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
    return if (usesFingerNumbering) physicalIndex else n + 1 - physicalIndex
}
