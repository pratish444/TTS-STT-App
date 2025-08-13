package com.example.talkmate.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// This is the single source of truth for the app's color scheme.
private val LightColorScheme = lightColorScheme(
    primary = VoiceBlue,
    onPrimary = Color.White,
    primaryContainer = VoiceBlueLight,
    onPrimaryContainer = VoiceBlueDark,

    secondary = VoiceGreen,
    onSecondary = Color.White,
    secondaryContainer = VoiceGreenLight,
    onSecondaryContainer = VoiceGreenDark,

    tertiary = AccentPink,
    onTertiary = Color.Black,

    background = AppBackground,
    onBackground = OnSurfaceText,

    surface = AppSurface,
    onSurface = OnSurfaceText,

    error = VoiceRed,
    onError = Color.White,

    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,

    outline = OutlineLight
)

@Composable
fun TTSSTTAppTheme(
    content: @Composable () -> Unit
) {
    // The colorScheme is always LightColorScheme for this app.
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to match the primary color from our scheme.
            window.statusBarColor = colorScheme.primary.toArgb()
            // Set navigation bar color to match the background color.
            window.navigationBarColor = colorScheme.background.toArgb()

            // Set status bar icons to be dark or light. `false` means light icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            // Set navigation bar icons to be dark. `true` means dark icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}