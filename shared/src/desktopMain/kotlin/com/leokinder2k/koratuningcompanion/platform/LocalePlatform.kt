package com.leokinder2k.koratuningcompanion.platform

actual fun changeLocale(tag: String) { /* no-op on desktop */ }
actual fun getCurrentLocaleTag(): String = "system"
