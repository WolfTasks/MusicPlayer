package com.wolf.musicplayer

import android.content.Context
import android.net.Uri
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.QueueContext
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.service.AutoLibraryCallback
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoLibraryCallbackTest {
    private lateinit var songRepo: SongRepository
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var playWeightRepo: PlayWeightRepository
    private lateinit var shuffleStateHolder: ShuffleStateHolder
    private lateinit var testScope: TestScope
    private lateinit var playbackSessionRepo: PlaybackSessionRepository
    private lateinit var callback: AutoLibraryCallback

    private val mockContext = mockk<Context>(relaxed = true)
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
        every { mockContext.packageName } returns "com.wolf.musicplayer"
        playbackSessionRepo = mockk(relaxed = true)
        every { playbackSessionRepo.load() } returns null
        callback =
            AutoLibraryCallback(
                context = mockContext,
                songRepository = songRepo,
                playlistRepository = playlistRepo,
                playWeightRepository = playWeightRepo,
                shuffler = WeightedShuffler(),
                shuffleStateHolder = shuffleStateHolder,
                playbackSessionRepository = playbackSessionRepo,
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

    // --- onSetMediaItems ---

    private val mockMediaSession = mockk<MediaSession>(relaxed = true)
    private val mockController =
        mockk<MediaSession.ControllerInfo>(relaxed = true) {
            every { packageName } returns "com.google.android.projection.gearhead"
        }

    private fun mediaItemWithId(mediaId: String): androidx.media3.common.MediaItem =
        androidx.media3.common.MediaItem.Builder().setMediaId(mediaId).build()

    @Test
    fun `onSetMediaItems for song context loads all songs starting at tapped song`() =
        runTest {
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("song:2")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:2 is at index 1
        }

    @Test
    fun `onSetMediaItems for album context loads album songs`() =
        runTest {
            coEvery { songRepo.getByAlbum("Abbey Road") } returns listOf(song(10), song(11))

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("album:Abbey Road/song:11")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(2, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:11 is at index 1
        }

    @Test
    fun `onSetMediaItems for playlist context loads playlist songs`() =
        runTest {
            coEvery { playlistRepo.getSongsForPlaylist(5L) } returns listOf(song(20), song(21))

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("playlist:5/song:20")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(2, result.mediaItems.size)
            assertEquals(0, result.startIndex) // song:20 is at index 0
        }

    @Test
    fun `onSetMediaItems for artist context loads artist songs`() =
        runTest {
            coEvery { songRepo.getByArtist("Beatles") } returns
                listOf(song(30), song(31), song(32))

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("artist:Beatles/song:31")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:31 is at index 1
        }

    @Test
    fun `onSetMediaItems with shuffle enabled permutes the queue`() =
        runTest {
            shuffleStateHolder.setShuffleEnabled(true)
            val songs = (1L..10L).map { song(it) }
            coEvery { songRepo.getAll() } returns songs
            coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("song:5")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            // All 10 songs still present
            assertEquals(10, result.mediaItems.size)
            // song:5 must be at startIndex
            assertEquals("song:5", result.mediaItems[result.startIndex].mediaId)
        }

    @Test
    fun `onSetMediaItems with shuffle disabled preserves natural order`() =
        runTest {
            shuffleStateHolder.setShuffleEnabled(false)
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))
            coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    mockController,
                    listOf(mediaItemWithId("song:1")),
                    0,
                    0L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals("song:1", result.mediaItems[0].mediaId)
            assertEquals("song:2", result.mediaItems[1].mediaId)
            assertEquals("song:3", result.mediaItems[2].mediaId)
            assertEquals(0, result.startIndex)
        }

    @Test
    fun `onSetMediaItems from in-app controller passes items through unchanged`() =
        runTest {
            // Simulate PlayerViewModel: plain song IDs, URIs already set, no "song:" prefix
            val inAppController =
                mockk<MediaSession.ControllerInfo>(relaxed = true) {
                    every { packageName } returns "com.wolf.musicplayer"
                }
            val item1 =
                androidx.media3.common.MediaItem.Builder()
                    .setMediaId("1")
                    .setUri(Uri.parse("/music/1.mp3"))
                    .build()
            val item2 =
                androidx.media3.common.MediaItem.Builder()
                    .setMediaId("2")
                    .setUri(Uri.parse("/music/2.mp3"))
                    .build()

            val future =
                callback.onSetMediaItems(
                    mockMediaSession,
                    inAppController,
                    listOf(item1, item2),
                    1,
                    30_000L,
                )
            testScope.advanceUntilIdle()
            val result = future.get()

            // Items returned unchanged — no DB lookup, same IDs and count
            assertEquals(2, result.mediaItems.size)
            assertEquals("1", result.mediaItems[0].mediaId)
            assertEquals("2", result.mediaItems[1].mediaId)
            assertEquals(1, result.startIndex)
            assertEquals(30_000L, result.startPositionMs)
            // Assert URIs are preserved — items must be the exact same objects
            // This proves URIs (and all other fields) are intact, not rebuilt from mediaId alone
            assertTrue("Item 0 URI preserved", result.mediaItems[0] === item1)
            assertTrue("Item 1 URI preserved", result.mediaItems[1] === item2)
        }

    // --- onSubscribe / onUnsubscribe ---

    @Test
    fun `onSubscribe returns success for any parentId`() =
        runTest {
            val future = callback.onSubscribe(mockSession, mockBrowser, "songs", null)
            assertEquals(0, future.get().resultCode)
        }

    @Test
    fun `onUnsubscribe returns success for any parentId`() =
        runTest {
            val future = callback.onUnsubscribe(mockSession, mockBrowser, "songs")
            assertEquals(0, future.get().resultCode)
        }

    // --- onPlaybackResumption ---

    @Test
    fun `onPlaybackResumption returns all songs at index 0`() =
        runTest {
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(0, result.startIndex)
            assertEquals("song:1", result.mediaItems[0].mediaId)
            assertEquals("song:2", result.mediaItems[1].mediaId)
            assertEquals("song:3", result.mediaItems[2].mediaId)
        }

    @Test
    fun `onPlaybackResumption with shuffle enabled returns all songs with index 0`() =
        runTest {
            shuffleStateHolder.setShuffleEnabled(true)
            val songs = (1L..10L).map { song(it) }
            coEvery { songRepo.getAll() } returns songs
            coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(10, result.mediaItems.size)
            assertEquals(0, result.startIndex)
        }

    @Test
    fun `onPlaybackResumption returns empty list when repository throws`() =
        runTest {
            coEvery { songRepo.getAll() } throws RuntimeException("DB failure")

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(0, result.mediaItems.size)
            assertEquals(0, result.startIndex)
        }

    // --- onPlaybackResumption: session-aware restore ---

    @Test
    fun `onPlaybackResumption with AllSongs session restores library at last song`() =
        runTest {
            val songs = listOf(song(1), song(2), song(3))
            coEvery { songRepo.getAll() } returns songs
            every { playbackSessionRepo.load() } returns Pair(QueueContext.AllSongs, 2L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:2 is at index 1
            assertEquals("song:2", result.mediaItems[1].mediaId)
            assertEquals(0L, result.startPositionMs)
        }

    @Test
    fun `onPlaybackResumption with playlist session restores playlist queue at last song`() =
        runTest {
            val songs = listOf(song(10), song(11), song(12))
            coEvery { playlistRepo.getSongsForPlaylist(3L) } returns songs
            every { playbackSessionRepo.load() } returns Pair(QueueContext.PlaylistCtx(3L), 11L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:11 at index 1
            assertEquals("song:11", result.mediaItems[1].mediaId)
            assertEquals(0L, result.startPositionMs)
        }

    @Test
    fun `onPlaybackResumption with album session restores album queue at last song`() =
        runTest {
            val songs = listOf(song(20), song(21), song(22))
            coEvery { songRepo.getByAlbum("Abbey Road") } returns songs
            every { playbackSessionRepo.load() } returns Pair(QueueContext.AlbumCtx("Abbey Road"), 22L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(2, result.startIndex) // song:22 at index 2
            assertEquals("song:22", result.mediaItems[2].mediaId)
        }

    @Test
    fun `onPlaybackResumption with artist session restores artist queue at last song`() =
        runTest {
            val songs = listOf(song(30), song(31))
            coEvery { songRepo.getByArtist("The Beatles") } returns songs
            every { playbackSessionRepo.load() } returns Pair(QueueContext.ArtistCtx("The Beatles"), 31L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(2, result.mediaItems.size)
            assertEquals(1, result.startIndex) // song:31 at index 1
        }

    @Test
    fun `onPlaybackResumption falls back to index 0 when session song not found in context`() =
        runTest {
            // song:999 does not exist in album — coerceAtLeast(0) kicks in
            coEvery { songRepo.getByAlbum("Abbey Road") } returns listOf(song(20), song(21))
            every { playbackSessionRepo.load() } returns Pair(QueueContext.AlbumCtx("Abbey Road"), 999L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(2, result.mediaItems.size)
            assertEquals(0, result.startIndex)
        }

    @Test
    fun `onPlaybackResumption falls back to full library when saved playlist is empty`() =
        runTest {
            coEvery { playlistRepo.getSongsForPlaylist(99L) } returns emptyList()
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2))
            every { playbackSessionRepo.load() } returns Pair(QueueContext.PlaylistCtx(99L), 1L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(2, result.mediaItems.size) // fell back to full library
            assertEquals(0, result.startIndex)
        }
}
