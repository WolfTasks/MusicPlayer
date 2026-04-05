package com.wolf.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = GreenAccent,
        onPrimary = OnDark,
        primaryContainer = GreenAccentDim,
        onPrimaryContainer = OnDark,
        background = DarkBackground,
        onBackground = OnDark,
        surface = DarkSurface,
        onSurface = OnDark,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = OnDarkSecondary,
        outline = OnDarkDisabled,
    )

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
