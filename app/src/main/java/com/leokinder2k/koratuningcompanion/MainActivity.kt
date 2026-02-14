package com.leokinder2k.koratuningcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leokinder2k.koratuningcompanion.navigation.KoraAuthorityApp
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTuningSystemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoraTuningSystemTheme {
                KoraAuthorityApp()
            }
        }
    }
}

