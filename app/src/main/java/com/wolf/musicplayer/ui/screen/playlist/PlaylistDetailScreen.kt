package com.wolf.musicplayer.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.ui.component.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { onSongClick(songs, 0) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Alle abspielen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(contentPadding),
        ) {
            itemsIndexed(
                songs,
                key = { _, song -> song.id },
            ) { index, song ->
                val dismissState =
                    rememberSwipeToDismissBoxState(
                        confirmValueChange =
                            { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeSong(song.id)
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFB00020)),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Entfernen",
                                tint = Color.White,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    },
                ) {
                    SongRow(
                        song = song,
                        onClick = { onSongClick(songs, index) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
