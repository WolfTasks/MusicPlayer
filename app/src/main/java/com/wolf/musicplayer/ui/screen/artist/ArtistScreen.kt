package com.wolf.musicplayer.ui.screen.artist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    contentPadding: PaddingValues,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Künstler") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(contentPadding)) {
            items(artists, key = { it }) { artist ->
                ListItem(
                    headlineContent = { Text(artist) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
