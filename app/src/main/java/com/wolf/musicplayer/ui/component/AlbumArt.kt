package com.wolf.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wolf.musicplayer.R

@Composable
fun AlbumArt(
    artUrl: String?,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val clipModifier =
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF0D1830))

    AsyncImage(
        model = artUrl,
        contentDescription = "Album art",
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_launcher_foreground),
        error = painterResource(R.drawable.ic_launcher_foreground),
        fallback = painterResource(R.drawable.ic_launcher_foreground),
        modifier = clipModifier,
    )
}
