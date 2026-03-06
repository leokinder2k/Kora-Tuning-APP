package com.leokinder2k.koratuningcompanion.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

actual fun changeLocale(tag: String) {
    if (tag == "system") {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}

actual fun getCurrentLocaleTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val firstLocaleTag = locales[0]?.toLanguageTag().orEmpty()
    return firstLocaleTag.ifEmpty { "system" }
}
