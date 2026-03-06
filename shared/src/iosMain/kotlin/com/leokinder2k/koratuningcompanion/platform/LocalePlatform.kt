package com.leokinder2k.koratuningcompanion.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual fun changeLocale(tag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun getCurrentLocaleTag(): String =
    NSLocale.currentLocale.localeIdentifier.replace("_", "-")
