package com.wolf.musicplayer

import androidx.lifecycle.SavedStateHandle
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(playlistId: Long = 3L): Triple<PlaylistDetailViewModel, PlaylistRepository, SavedStateHandle> {
        val repo = mockk<PlaylistRepository>(relaxed = true)
        val playlist = Playlist(playlistId, "Rock Mix", null, true)
        every { repo.observeAll() } returns flowOf(listOf(playlist))
        every { repo.observeSongsForPlaylist(playlistId) } returns flowOf(emptyList())
        val handle = SavedStateHandle(mapOf("playlistId" to playlistId))
        return Triple(PlaylistDetailViewModel(repo, handle), repo, handle)
    }

    @Test
    fun `reads playlistId from saved state and observes songs`() {
        val (_, repo, _) = makeVm(3L)
        io.mockk.verify { repo.observeSongsForPlaylist(3L) }
    }

    @Test
    fun `removeSong delegates to repository`() =
        runTest(testDispatcher) {
            val (vm, repo, _) = makeVm(3L)
            vm.removeSong(99L)
            advanceUntilIdle()
            coVerify { repo.removeSong(3L, 99L) }
        }
}
