package com.wolf.musicplayer.ui.screen.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.wolf.musicplayer.ui.component.AddToPlaylistSheet
import com.wolf.musicplayer.ui.component.AlbumArt

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val songPlaylists by viewModel.songPlaylists.collectAsStateWithLifecycle()
    val song = state.currentSong
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        AlbumArt(
            artUrl = null,
            size = 280.dp,
            cornerRadius = 16.dp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song?.title ?: "Kein Song",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = song?.artist ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Seekbar
        val sliderValue =
            if (isDragging) {
                dragValue
            } else if (state.duration > 0) {
                state.position.toFloat() / state.duration
            } else {
                0f
            }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Slider(
                value = sliderValue,
                onValueChange = { v ->
                    isDragging = true
                    dragValue = v
                },
                onValueChangeFinished = {
                    viewModel.seekTo((dragValue * state.duration).toLong())
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val displayPosition = if (isDragging) (dragValue * state.duration).toLong() else state.position
                Text(displayPosition.toMinSec(), style = MaterialTheme.typography.labelSmall)
                Text(state.duration.toMinSec(), style = MaterialTheme.typography.labelSmall)
            }
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.skipToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { viewModel.playPause() },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
        }

        // Shuffle + Playlist + Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint =
                        if (state.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            IconButton(
                onClick = { showAddToPlaylist = true },
                enabled = song != null,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Zu Playlist hinzufügen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                Icon(
                    imageVector =
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                    contentDescription = "Repeat",
                    tint =
                        if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        // Add-to-playlist bottom sheet
        if (showAddToPlaylist && song != null) {
            AddToPlaylistSheet(
                song = song,
                playlists = playlists,
                songPlaylistIds = songPlaylists,
                onAddToPlaylist = { playlist -> viewModel.addSongToPlaylist(song, playlist) },
                onCreateAndAdd = { name -> viewModel.createPlaylistAndAddSong(name, song) },
                onDismiss = { showAddToPlaylist = false },
            )
        }
    }
}

private fun Long.toMinSec(): String {
    val totalSeconds = this / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%d:%02d".format(min, sec)
}
