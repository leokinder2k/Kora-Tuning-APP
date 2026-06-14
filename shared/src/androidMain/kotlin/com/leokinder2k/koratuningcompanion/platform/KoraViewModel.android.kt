package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
actual fun <T : ViewModel> rememberKoraViewModel(factory: () -> T): T = viewModel { factory() }
