package com.wolf.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
fun AlbumArt(
    artUrl: String?,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = artUrl,
        contentDescription = "Album art",
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius)),
        error = {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
