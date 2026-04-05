package com.wolf.musicplayer.ui.screen.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alben") }) },
        contentPadding = contentPadding,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(albums, key = { it }) { album ->
                ListItem(
                    headlineContent = { Text(album) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
