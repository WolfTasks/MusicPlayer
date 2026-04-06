package com.wolf.musicplayer.ui.screen.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val repo: PlaylistRepository,
    ) : ViewModel() {
        val playlists =
            repo.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun createPlaylist(name: String) {
            viewModelScope.launch { repo.create(name) }
        }

        fun renamePlaylist(
            id: Long,
            name: String,
        ) {
            viewModelScope.launch { repo.rename(id, name) }
        }

        fun deletePlaylist(playlist: Playlist) {
            viewModelScope.launch { repo.delete(playlist.id) }
        }

        fun playPlaylist(
            playlistId: Long,
            onSongsReady: (List<Song>) -> Unit,
        ) {
            viewModelScope.launch {
                val songs = repo.getSongsForPlaylist(playlistId)
                if (songs.isNotEmpty()) onSongsReady(songs)
            }
        }
    }
