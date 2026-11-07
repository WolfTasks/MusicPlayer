package com.wolf.musicplayer.ui.screen.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        repo: SongRepository,
    ) : ViewModel() {
        val artists =
            repo
                .observeArtists()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
