package com.leokinder2k.koratuningcompanion.instrumentconfig.data

import platform.Foundation.NSDate

internal actual fun currentTimeMillis(): Long =
    (NSDate.date().timeIntervalSince1970 * 1000.0).toLong()
