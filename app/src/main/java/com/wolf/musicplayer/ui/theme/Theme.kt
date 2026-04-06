package com.wolf.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WolfColorScheme =
    darkColorScheme(
        primary = WolfSilver,
        onPrimary = WolfOnSilver,
        primaryContainer = WolfSilverMuted,
        onPrimaryContainer = WolfWhite,
        background = WolfNavy,
        onBackground = WolfWhite,
        surface = WolfNavySurface,
        onSurface = WolfWhite,
        surfaceVariant = WolfNavyVariant,
        onSurfaceVariant = WolfSilverMuted,
        outline = WolfNavyVariant,
    )

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WolfColorScheme,
        typography = Typography,
        content = content,
    )
}
