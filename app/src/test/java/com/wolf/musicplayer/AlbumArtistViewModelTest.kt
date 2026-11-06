package com.wolf.musicplayer

import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.ui.screen.album.AlbumViewModel
import com.wolf.musicplayer.ui.screen.artist.ArtistViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumArtistViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repo = mockk<SongRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `AlbumViewModel starts empty and exposes repository albums`() =
        runTest(testDispatcher) {
            val albums = MutableStateFlow(listOf("Giant Steps", "Kind of Blue"))
            every { repo.observeAlbums() } returns albums
            val vm = AlbumViewModel(repo)
            assertTrue(vm.albums.value.isEmpty())

            backgroundScope.launch { vm.albums.collect {} }
            advanceUntilIdle()
            assertEquals(listOf("Giant Steps", "Kind of Blue"), vm.albums.value)

            albums.value = listOf("A Love Supreme")
            advanceUntilIdle()
            assertEquals(listOf("A Love Supreme"), vm.albums.value)
        }

    @Test
    fun `ArtistViewModel starts empty and exposes repository artists`() =
        runTest(testDispatcher) {
            val artists = MutableStateFlow(listOf("John Coltrane", "Miles Davis"))
            every { repo.observeArtists() } returns artists
            val vm = ArtistViewModel(repo)
            assertTrue(vm.artists.value.isEmpty())

            backgroundScope.launch { vm.artists.collect {} }
            advanceUntilIdle()
            assertEquals(listOf("John Coltrane", "Miles Davis"), vm.artists.value)

            artists.value = emptyList()
            advanceUntilIdle()
            assertTrue(vm.artists.value.isEmpty())
        }
}
