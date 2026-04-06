package com.wolf.musicplayer

import androidx.lifecycle.SavedStateHandle
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.ui.screen.artist.ArtistDetailViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

class ArtistDetailViewModelTest {
    @Test
    fun `reads songs for the artist name from saved state`() {
        val repo = mockk<SongRepository>()
        every { repo.observeByArtist("Miles Davis") } returns flowOf(emptyList())
        val handle = SavedStateHandle(mapOf("artistName" to "Miles Davis"))

        ArtistDetailViewModel(repo, handle)

        verify { repo.observeByArtist("Miles Davis") }
    }
}
