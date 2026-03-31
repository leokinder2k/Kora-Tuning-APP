package com.leokinder2k.koratuningcompanion.scaleengine.ui

import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState

internal enum class DiagramStringActionState {
    OPEN,
    LEVER_ONLY,
    PEG_ONLY,
    LEVER_AND_PEG
}

internal fun effectivePegDelta(
    pegRetuneSemitones: Int,
    manualSemitoneShift: Int
): Int {
    return pegRetuneSemitones + manualSemitoneShift
}

internal fun diagramStringActionState(
    selectedLeverState: LeverState,
    pegRetuneSemitones: Int,
    manualSemitoneShift: Int
): DiagramStringActionState {
    val pegChange = effectivePegDelta(
        pegRetuneSemitones = pegRetuneSemitones,
        manualSemitoneShift = manualSemitoneShift
    ) != 0
    val leverClosed = selectedLeverState == LeverState.CLOSED
    return when {
        leverClosed && pegChange -> DiagramStringActionState.LEVER_AND_PEG
        pegChange -> DiagramStringActionState.PEG_ONLY
        leverClosed -> DiagramStringActionState.LEVER_ONLY
        else -> DiagramStringActionState.OPEN
    }
}
