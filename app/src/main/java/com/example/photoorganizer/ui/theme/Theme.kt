package com.example.photoorganizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5F0FF),
        onPrimaryContainer = Color(0xFF003A75),
        secondary = Color(0xFF34C759),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE7F8EC),
        onSecondaryContainer = Color(0xFF0E4A20),
        tertiary = Color(0xFF5856D6),
        onTertiary = Color.White,
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        surface = Color.White,
        onSurface = Color(0xFF1D1D1F),
        surfaceVariant = Color(0xFFF2F2F7),
        onSurfaceVariant = Color(0xFF6E6E73),
        outline = Color(0xFFD1D1D6),
        error = Color(0xFFFF3B30),
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E5),
        onErrorContainer = Color(0xFF7A1D18),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF063B73),
        onPrimaryContainer = Color(0xFFD6E9FF),
        secondary = Color(0xFF30D158),
        onSecondary = Color(0xFF041F0C),
        secondaryContainer = Color(0xFF123D1D),
        onSecondaryContainer = Color(0xFFDDF8E5),
        tertiary = Color(0xFF5E5CE6),
        onTertiary = Color.White,
        background = Color(0xFF000000),
        onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF1C1C1E),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFAEAEB2),
        outline = Color(0xFF3A3A3C),
        error = Color(0xFFFF453A),
        onError = Color.White,
        errorContainer = Color(0xFF4A1714),
        onErrorContainer = Color(0xFFFFDAD7),
    )

@Composable
fun PhotoOrganizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
