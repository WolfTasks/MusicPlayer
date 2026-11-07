package com.wolf.musicplayer.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        private val repo: PlaylistRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

        val playlist =
            repo
                .observeAll()
                .map { lists -> lists.find { it.id == playlistId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val songs =
            repo
                .observeSongsForPlaylist(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun removeSong(songId: Long) {
            viewModelScope.launch { repo.removeSong(playlistId, songId) }
        }
    }
