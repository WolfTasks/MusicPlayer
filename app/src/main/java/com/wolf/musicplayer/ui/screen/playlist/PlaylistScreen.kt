package com.wolf.musicplayer.ui.screen.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    onPlaylistClick: (Long) -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    var menuTarget by remember { mutableStateOf<Playlist?>(null) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Neue Wiedergabeliste") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) viewModel.createPlaylist(newName)
                    newName = ""
                    showCreateDialog = false
                }) { Text("Erstellen") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Neuer Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) viewModel.renamePlaylist(target.id, renameText)
                    renameTarget = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Abbrechen") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Löschen") },
            text = { Text("\"${target.name}\" wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target)
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wiedergabelisten") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Neue Playlist")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(contentPadding),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Box {
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        trailingContent = {
                            IconButton(onClick = {
                                viewModel.playPlaylist(playlist.id, onPlaySongs)
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Abspielen")
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .combinedClickable(
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onLongClick = { menuTarget = playlist },
                                ),
                    )
                    DropdownMenu(
                        expanded = menuTarget?.id == playlist.id,
                        onDismissRequest = { menuTarget = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            onClick = {
                                renameText = playlist.name
                                renameTarget = playlist
                                menuTarget = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            onClick = {
                                deleteTarget = playlist
                                menuTarget = null
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
