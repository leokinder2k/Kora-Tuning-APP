package com.leokinder2k.koratuningcompanion.notation.engine

// Port of digits.js

private val DIGIT_LINE_ORDER = listOf("LF", "LT", "RT", "RF")

/**
 * Default: both tab lines on each side can address all strings on that side.
 * LF and LT → all left strings; RF and RT → all right strings.
 */
fun defaultStringToDigitAssignments(instrumentType: KoraInstrumentType): Map<String, List<String>> {
    val ids = allStringIds(instrumentType)
    val left = ids.filter { it.startsWith('L') }
    val right = ids.filter { it.startsWith('R') }
    return mapOf("LF" to left, "LT" to left, "RF" to right, "RT" to right)
}

fun validateStringToDigitAssignments(instrumentType: KoraInstrumentType, assignments: Map<String, List<String>>) {
    val expected = allStringIds(instrumentType).toSet()
    val missingLines = DIGIT_LINE_ORDER.filter { it !in assignments }
    require(missingLines.isEmpty()) { "Assignments missing lines: ${missingLines.joinToString()}" }

    val perLineSets = mutableMapOf<String, Set<String>>()
    for ((line, list) in assignments) {
        require(line in setOf("LF", "LT", "RF", "RT")) { "Unknown digit line: $line" }
        for (id in list) {
            require(id in expected) { "Unknown stringId in assignments: $id" }
            val side = parseStringId(id).side
            if (line == "LF" || line == "LT") require(side == 'L') { "$id must be left side for $line" }
            if (line == "RF" || line == "RT") require(side == 'R') { "$id must be right side for $line" }
        }
        perLineSets[line] = list.toSet()
    }

    val leftCoverage = (perLineSets["LF"] ?: emptySet()) + (perLineSets["LT"] ?: emptySet())
    val rightCoverage = (perLineSets["RF"] ?: emptySet()) + (perLineSets["RT"] ?: emptySet())
    val missing = expected.filter { id ->
        if (id.startsWith('L')) id !in leftCoverage else id !in rightCoverage
    }
    require(missing.isEmpty()) { "Assignments missing stringIds: ${missing.joinToString()}" }
}

/** All digit lines that include [stringId], in canonical order LF → LT → RT → RF. */
fun digitLinesForString(assignments: Map<String, List<String>>, stringId: String): List<String> {
    val result = DIGIT_LINE_ORDER.filter { line ->
        assignments[line]?.contains(stringId) == true
    }
    if (result.isNotEmpty()) return result
    // Fallback: any line
    return assignments.entries
        .filter { (_, list) -> stringId in list }
        .map { (line, _) -> line }
}
