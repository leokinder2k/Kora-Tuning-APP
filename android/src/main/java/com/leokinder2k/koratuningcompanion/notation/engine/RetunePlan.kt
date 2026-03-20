package com.leokinder2k.koratuningcompanion.notation.engine

// Port of retunePlan.js

fun generateRetunePlan(mappedEvents: List<MappedEvent>, lastMeasureNumber: Int? = null): RetunePlan {
    // Collect delta counts per (stringId, measureNumber)
    data class Counts(var p1: Int = 0, var m1: Int = 0)
    val countsByStringByMeasure = mutableMapOf<String, MutableMap<Int, Counts>>()
    var maxMeasureSeen = 0

    for (e in mappedEvents) {
        if (e.omit || e.stringId == null) continue
        val measure = e.measureNumber
        require(measure >= 1) { "Mapped event missing valid measureNumber: $e" }
        if (measure > maxMeasureSeen) maxMeasureSeen = measure

        val delta = when (e.accidentalSuggestion) {
            AccidentalSuggestion.SHARP -> 1
            AccidentalSuggestion.FLAT -> -1
            AccidentalSuggestion.NONE -> 0
        }
        if (delta == 0) continue

        val byMeasure = countsByStringByMeasure.getOrPut(e.stringId) { mutableMapOf() }
        val counts = byMeasure.getOrPut(measure) { Counts() }
        if (delta == 1) counts.p1++ else counts.m1++
    }

    val plannedLast = lastMeasureNumber ?: maxMeasureSeen

    val perStringNetChange = mutableMapOf<String, Int>()
    val barInstructions = mutableListOf<BarInstruction>()

    for ((stringId, byMeasure) in countsByStringByMeasure) {
        fun desiredAt(m: Int): Int {
            val v = byMeasure[m] ?: return 0
            return when {
                v.p1 > 0 && v.m1 == 0 -> 1
                v.m1 > 0 && v.p1 == 0 -> -1
                else -> if (v.p1 >= v.m1) 1 else -1
            }
        }

        perStringNetChange[stringId] = desiredAt(1)

        var current = 0
        for (m in 1..plannedLast) {
            val target = desiredAt(m)
            if (target == current) continue
            val instructionMeasure = maxOf(1, m - 1)
            barInstructions.add(
                BarInstruction(
                    measureNumber = instructionMeasure,
                    appliesFromMeasureNumber = m,
                    stringId = stringId,
                    deltaSemitones = target,
                )
            )
            current = target
        }
    }

    barInstructions.sortWith(
        compareBy({ it.measureNumber }, { it.appliesFromMeasureNumber }, { it.stringId })
    )

    return RetunePlan(perStringNetChange = perStringNetChange, barInstructions = barInstructions)
}
