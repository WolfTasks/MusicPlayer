package com.wolf.musicplayer

import androidx.lifecycle.SavedStateHandle
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.ui.screen.album.AlbumDetailViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

class AlbumDetailViewModelTest {
    @Test
    fun `reads songs for the album name from saved state`() {
        val repo = mockk<SongRepository>()
        every { repo.observeByAlbum("Kind of Blue") } returns flowOf(emptyList())
        val handle = SavedStateHandle(mapOf("albumName" to "Kind of Blue"))

        AlbumDetailViewModel(repo, handle)

        verify { repo.observeByAlbum("Kind of Blue") }
    }
}
