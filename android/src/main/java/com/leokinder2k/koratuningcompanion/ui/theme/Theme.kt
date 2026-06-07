package com.leokinder2k.koratuningcompanion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KoraGoldLight,
    onPrimary = KoraNight,
    primaryContainer = KoraGoldDark,
    onPrimaryContainer = KoraSand,
    secondary = KoraStone,
    onSecondary = KoraNight,
    secondaryContainer = KoraNightSurfaceHigh,
    onSecondaryContainer = KoraMist,
    tertiary = KoraGold,
    onTertiary = KoraNight,
    background = KoraNight,
    onBackground = KoraMist,
    surface = KoraNightSurface,
    onSurface = KoraMist,
    surfaceVariant = KoraNightSurfaceHigh,
    onSurfaceVariant = KoraStone,
    outline = KoraClay,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = KoraGoldDark,
    onPrimary = Color.White,
    primaryContainer = KoraGoldLight,
    onPrimaryContainer = KoraNight,
    secondary = KoraClay,
    onSecondary = Color.White,
    secondaryContainer = KoraStone,
    onSecondaryContainer = KoraNight,
    tertiary = KoraGold,
    onTertiary = KoraNight,
    background = KoraSand,
    onBackground = KoraNight,
    surface = Color.White,
    onSurface = KoraNight,
    surfaceVariant = Color(0xFFF0E6D4),
    onSurfaceVariant = KoraClay,
    outline = Color(0xFF8D7B63),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun KoraTuningSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = KoraShapes,
        content = content
    )
}
