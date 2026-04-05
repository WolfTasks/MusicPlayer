package com.wolf.musicplayer.ui.screen.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(repo: SongRepository) : ViewModel() {
        val albums =
            repo.observeAlbums()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
