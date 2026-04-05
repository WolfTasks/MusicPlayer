package com.wolf.musicplayer.ui.screen.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repo: PlaylistRepository,
) : ViewModel() {

    val playlists = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch { repo.create(name) }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repo.delete(playlist.id) }
    }
}
