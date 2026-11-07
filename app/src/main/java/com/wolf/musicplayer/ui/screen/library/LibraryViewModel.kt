package com.wolf.musicplayer.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
) {
    val filtered: List<Song>
        get() =
            if (query.isBlank()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }
            }
}

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val songRepository: SongRepository,
    ) : ViewModel() {
        private val queryFlow = MutableStateFlow("")
        private val loadingFlow = MutableStateFlow(false)

        val state =
            combine(songRepository.observeAll(), queryFlow, loadingFlow) { songs, query, loading ->
                LibraryState(songs = songs, query = query, isLoading = loading)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())

        fun setQuery(query: String) {
            queryFlow.update { query }
        }

        fun syncLocalMusic() {
            viewModelScope.launch {
                loadingFlow.update { true }
                songRepository.syncLocalMusic()
                loadingFlow.update { false }
            }
        }
    }
