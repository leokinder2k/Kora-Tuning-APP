package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel

@Composable
actual fun <T : ViewModel> rememberKoraViewModel(factory: () -> T): T = remember { factory() }
