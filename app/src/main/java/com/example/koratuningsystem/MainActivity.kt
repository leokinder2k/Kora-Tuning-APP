package com.example.koratuningsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.koratuningsystem.navigation.KoraAuthorityApp
import com.example.koratuningsystem.ui.theme.KoraTuningSystemTheme

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
