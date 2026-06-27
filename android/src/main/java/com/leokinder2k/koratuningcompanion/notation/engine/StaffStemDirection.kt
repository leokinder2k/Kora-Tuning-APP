package com.leokinder2k.koratuningcompanion.notation.engine

internal fun shouldStemUpForStaffPosition(noteY: Float, staffMiddleLineY: Float): Boolean {
    return noteY > staffMiddleLineY
}
