package com.brp.assistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// iOS/Modern UI Colors
val AppleBlue = Color(0xFF007AFF)
val AppleGrey = Color(0xFF8E8E93)
val AppleSystemBackgroundLight = Color(0xFFF2F2F7)
val AppleSystemBackgroundDark = Color(0xFF000000)
val AppleSecondaryBackgroundLight = Color(0xFFFFFFFF)
val AppleSecondaryBackgroundDark = Color(0xFF1C1C1E)

private val LightColors = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = AppleBlue,
    secondary = AppleGrey,
    onSecondary = Color.White,
    background = AppleSystemBackgroundLight,
    onBackground = Color.Black,
    surface = AppleSecondaryBackgroundLight,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFFD1D1D6)
)

private val DarkColors = darkColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004080),
    onPrimaryContainer = Color(0xFFE5F1FF),
    secondary = AppleGrey,
    onSecondary = Color.Black,
    background = AppleSystemBackgroundDark,
    onBackground = Color.White,
    surface = AppleSecondaryBackgroundDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFEBEBF5),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C)
)

@Composable
fun BRPAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assuming Typography is defined elsewhere
        content = content
    )
}
