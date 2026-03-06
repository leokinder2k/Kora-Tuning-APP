package com.leokinder2k.koratuningcompanion.platform

import android.os.Build

actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
