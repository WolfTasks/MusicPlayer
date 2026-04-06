package com.wolf.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WolfColorScheme =
    darkColorScheme(
        primary = WolfSilver,
        onPrimary = WolfOnSilver,
        primaryContainer = WolfSilverMuted,
        onPrimaryContainer = WolfOnSilver,
        background = WolfNavy,
        onBackground = WolfWhite,
        surface = WolfNavySurface,
        onSurface = WolfWhite,
        surfaceVariant = WolfNavyVariant,
        onSurfaceVariant = WolfSilverSubtle,
        outline = WolfSilverMuted,
    )

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WolfColorScheme,
        typography = Typography,
        content = content,
    )
}
