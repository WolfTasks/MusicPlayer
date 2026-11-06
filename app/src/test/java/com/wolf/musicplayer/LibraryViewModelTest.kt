package com.wolf.musicplayer

import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.ui.screen.library.LibraryState
import com.wolf.musicplayer.ui.screen.library.LibraryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repo = mockk<SongRepository>(relaxed = true)

    private val songs =
        listOf(
            testSong(1, title = "Blue in Green", artist = "Miles Davis", album = "Kind of Blue"),
            testSong(2, title = "Giant Steps", artist = "John Coltrane", album = "Giant Steps"),
            testSong(3, title = "So What", artist = "Miles Davis", album = "Kind of Blue"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filtered returns all songs for blank query`() {
        assertEquals(songs, LibraryState(songs = songs, query = "   ").filtered)
        assertEquals(songs, LibraryState(songs = songs, query = "").filtered)
    }

    @Test
    fun `filtered matches title case-insensitively`() {
        val result = LibraryState(songs = songs, query = "giant").filtered
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `filtered matches artist`() {
        val result = LibraryState(songs = songs, query = "MILES").filtered
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun `filtered matches album`() {
        val result = LibraryState(songs = songs, query = "kind of").filtered
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun `filtered returns empty list when nothing matches`() {
        assertTrue(LibraryState(songs = songs, query = "Beethoven").filtered.isEmpty())
    }

    @Test
    fun `state reflects repository songs and query`() =
        runTest(testDispatcher) {
            every { repo.observeAll() } returns MutableStateFlow(songs)
            val vm = LibraryViewModel(repo)
            backgroundScope.launch { vm.state.collect {} }

            vm.setQuery("coltrane")
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(songs, state.songs)
            assertEquals("coltrane", state.query)
            assertEquals(listOf(2L), state.filtered.map { it.id })
        }

    @Test
    fun `syncLocalMusic shows loading while syncing and calls repository`() =
        runTest(testDispatcher) {
            every { repo.observeAll() } returns MutableStateFlow(emptyList())
            val syncGate = CompletableDeferred<Unit>()
            coEvery { repo.syncLocalMusic() } coAnswers { syncGate.await() }
            val vm = LibraryViewModel(repo)
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            assertFalse(vm.state.value.isLoading)

            vm.syncLocalMusic()
            advanceUntilIdle()
            assertTrue(vm.state.value.isLoading)

            syncGate.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.state.value.isLoading)
            coVerify(exactly = 1) { repo.syncLocalMusic() }
        }
}
