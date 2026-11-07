package com.wolf.musicplayer.ui.screen.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel
    @Inject
    constructor(
        repo: SongRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        internal val albumName: String = checkNotNull(savedStateHandle["albumName"])

        val songs: StateFlow<List<Song>> =
            repo
                .observeByAlbum(albumName)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
