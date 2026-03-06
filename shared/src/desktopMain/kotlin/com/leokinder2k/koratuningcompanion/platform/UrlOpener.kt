package com.leokinder2k.koratuningcompanion.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}
