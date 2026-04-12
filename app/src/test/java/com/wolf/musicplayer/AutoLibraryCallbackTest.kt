package com.wolf.musicplayer

import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.service.AutoLibraryCallback
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AutoLibraryCallbackTest {
    private lateinit var songRepo: SongRepository
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var playWeightRepo: PlayWeightRepository
    private lateinit var shuffleStateHolder: ShuffleStateHolder
    private lateinit var testScope: TestScope
    private lateinit var callback: AutoLibraryCallback

    private val mockSession = mockk<MediaLibrarySession>(relaxed = true)
    private val mockBrowser = mockk<MediaSession.ControllerInfo>(relaxed = true)

    private fun song(
        id: Long,
        title: String = "Song $id",
        artist: String = "Artist",
        album: String = "Album",
    ) = Song(
        id = id, title = title, artist = artist, album = album,
        duration = 180_000L, sourceType = SourceType.LOCAL,
        serverId = null, serverSongId = null,
        localPath = "/music/$id.mp3", cachedPath = null,
    )

    @Before
    fun setup() {
        songRepo = mockk(relaxed = true)
        playlistRepo = mockk(relaxed = true)
        playWeightRepo = mockk(relaxed = true)
        shuffleStateHolder = ShuffleStateHolder()
        testScope = TestScope()
        callback =
            AutoLibraryCallback(
                songRepository = songRepo,
                playlistRepository = playlistRepo,
                playWeightRepository = playWeightRepo,
                shuffler = WeightedShuffler(),
                shuffleStateHolder = shuffleStateHolder,
                scope = testScope,
            )
    }

    // --- onGetLibraryRoot ---

    @Test
    fun `onGetLibraryRoot returns root item with correct mediaId`() =
        runTest {
            val future = callback.onGetLibraryRoot(mockSession, mockBrowser, null)
            testScope.advanceUntilIdle()
            val result = future.get()
            assertEquals("root", result.value?.mediaId)
        }

    @Test
    fun `root item is browsable and not playable`() =
        runTest {
            val future = callback.onGetLibraryRoot(mockSession, mockBrowser, null)
            testScope.advanceUntilIdle()
            val item = future.get().value!!
            assertEquals(true, item.mediaMetadata.isBrowsable)
            assertEquals(false, item.mediaMetadata.isPlayable)
        }

    // --- onGetChildren: root ---

    @Test
    fun `onGetChildren root returns 4 category items`() =
        runTest {
            val future = callback.onGetChildren(mockSession, mockBrowser, "root", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!
            assertEquals(4, items.size)
            assertEquals(listOf("songs", "playlists", "albums", "artists"), items.map { it.mediaId })
        }

    @Test
    fun `category items are browsable and not playable`() =
        runTest {
            val future = callback.onGetChildren(mockSession, mockBrowser, "root", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!
            items.forEach { item ->
                assertEquals("${item.mediaId} should be browsable", true, item.mediaMetadata.isBrowsable)
                assertEquals("${item.mediaId} should not be playable", false, item.mediaMetadata.isPlayable)
            }
        }

    // --- onGetChildren: songs ---

    @Test
    fun `onGetChildren songs returns all songs as playable items`() =
        runTest {
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2))

            val future = callback.onGetChildren(mockSession, mockBrowser, "songs", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("song:1", items[0].mediaId)
            assertEquals("song:2", items[1].mediaId)
            items.forEach {
                assertEquals(false, it.mediaMetadata.isBrowsable)
                assertEquals(true, it.mediaMetadata.isPlayable)
            }
        }

    // --- onGetChildren: playlists ---

    @Test
    fun `onGetChildren playlists returns each playlist as browsable`() =
        runTest {
            coEvery { playlistRepo.getAll() } returns
                listOf(
                    Playlist(id = 10L, name = "Rock", serverId = null, isLocal = true),
                    Playlist(id = 11L, name = "Jazz", serverId = null, isLocal = true),
                )

            val future = callback.onGetChildren(mockSession, mockBrowser, "playlists", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("playlist:10", items[0].mediaId)
            assertEquals("playlist:11", items[1].mediaId)
            items.forEach {
                assertEquals(true, it.mediaMetadata.isBrowsable)
                assertEquals(false, it.mediaMetadata.isPlayable)
            }
        }

    // --- onGetChildren: albums ---

    @Test
    fun `onGetChildren albums returns each album as browsable`() =
        runTest {
            coEvery { songRepo.getAlbums() } returns listOf("Abbey Road", "Thriller")

            val future = callback.onGetChildren(mockSession, mockBrowser, "albums", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("album:Abbey Road", items[0].mediaId)
            assertEquals("album:Thriller", items[1].mediaId)
            items.forEach { assertEquals(true, it.mediaMetadata.isBrowsable) }
        }

    // --- onGetChildren: artists ---

    @Test
    fun `onGetChildren artists returns each artist as browsable`() =
        runTest {
            coEvery { songRepo.getArtists() } returns listOf("Beatles", "MJ")

            val future = callback.onGetChildren(mockSession, mockBrowser, "artists", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("artist:Beatles", items[0].mediaId)
            assertEquals("artist:MJ", items[1].mediaId)
        }

    // --- onGetChildren: drill-down ---

    @Test
    fun `onGetChildren playlist id returns songs with compound mediaId`() =
        runTest {
            coEvery { playlistRepo.getSongsForPlaylist(10L) } returns listOf(song(1), song(2))

            val future = callback.onGetChildren(mockSession, mockBrowser, "playlist:10", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("playlist:10/song:1", items[0].mediaId)
            assertEquals("playlist:10/song:2", items[1].mediaId)
        }

    @Test
    fun `onGetChildren album name returns songs with compound mediaId`() =
        runTest {
            coEvery { songRepo.getByAlbum("Abbey Road") } returns listOf(song(3), song(4))

            val future = callback.onGetChildren(mockSession, mockBrowser, "album:Abbey Road", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(2, items.size)
            assertEquals("album:Abbey Road/song:3", items[0].mediaId)
            assertEquals("album:Abbey Road/song:4", items[1].mediaId)
        }

    @Test
    fun `onGetChildren artist name returns songs with compound mediaId`() =
        runTest {
            coEvery { songRepo.getByArtist("Beatles") } returns listOf(song(5))

            val future = callback.onGetChildren(mockSession, mockBrowser, "artist:Beatles", 0, 100, null)
            testScope.advanceUntilIdle()
            val items = future.get().value!!

            assertEquals(1, items.size)
            assertEquals("artist:Beatles/song:5", items[0].mediaId)
        }

    // --- onGetItem ---

    @Test
    fun `onGetItem returns song for valid mediaId`() =
        runTest {
            coEvery { songRepo.getById(7L) } returns song(7, title = "Lucky Number 7")

            val future = callback.onGetItem(mockSession, mockBrowser, "song:7")
            testScope.advanceUntilIdle()
            val item = future.get().value

            assertNotNull(item)
            assertEquals("song:7", item!!.mediaId)
            assertEquals("Lucky Number 7", item.mediaMetadata.title.toString())
        }

    @Test
    fun `onGetItem returns error for unknown id`() =
        runTest {
            coEvery { songRepo.getById(999L) } returns null

            val future = callback.onGetItem(mockSession, mockBrowser, "song:999")
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(false, result.resultCode == LibraryResult.RESULT_SUCCESS)
        }
}
