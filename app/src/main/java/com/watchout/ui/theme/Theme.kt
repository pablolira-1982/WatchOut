package com.watchout.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colors extracted from Screens.png
val DeepBackground = Color(0xFF080C13)
val CardSurface = Color(0xFF141C2A)
val BrightBlue = Color(0xFF0066FF)
val SuccessGreen = Color(0xFF00C853)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0AAB5)

private val WatchOutColorScheme = darkColorScheme(
    primary = BrightBlue,
    secondary = SuccessGreen,
    tertiary = Color(0xFF333333),
    background = DeepBackground,
    surface = CardSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun WatchOutTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = WatchOutColorScheme,
        content = content
    )
}
