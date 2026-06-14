package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

@Composable
expect fun <T : ViewModel> rememberKoraViewModel(factory: () -> T): T
