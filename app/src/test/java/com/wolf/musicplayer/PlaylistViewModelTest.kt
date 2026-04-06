package com.wolf.musicplayer

import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.ui.screen.playlist.PlaylistViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class PlaylistViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repo = mockk<PlaylistRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): PlaylistViewModel {
        every { repo.observeAll() } returns flowOf(emptyList())
        return PlaylistViewModel(repo)
    }

    @Test
    fun `renamePlaylist delegates to repository`() =
        runTest {
            val vm = makeVm()
            coEvery { repo.rename(1L, "New Name") } returns Unit

            vm.renamePlaylist(1L, "New Name")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.rename(1L, "New Name") }
        }

    @Test
    fun `playPlaylist fetches songs and invokes callback`() =
        runTest {
            val vm = makeVm()
            val song = Song(1L, "T", "A", "Al", 1000L, SourceType.LOCAL, null, null, "/p", null)
            coEvery { repo.getSongsForPlaylist(5L) } returns listOf(song)

            var received: List<Song>? = null
            vm.playPlaylist(5L) { received = it }
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.getSongsForPlaylist(5L) }
            assert(received == listOf(song))
        }
}
