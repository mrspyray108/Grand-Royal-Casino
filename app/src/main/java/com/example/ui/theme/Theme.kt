package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF473A00),
    onPrimaryContainer = GoldLight,
    secondary = GoldSecondary,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = Color(0xFF382F00),
    onSecondaryContainer = GoldLight,
    tertiary = EmeraldGreen,
    onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF005322),
    onTertiaryContainer = Color(0xFF6CFF99),
    error = RubyRed,
    onError = Color.White,
    background = CasinoBackground,
    onBackground = TextPrimary,
    surface = CasinoSurface,
    onSurface = TextPrimary,
    surfaceVariant = CasinoSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CasinoCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Always preserve the luxury dark casino gold theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
