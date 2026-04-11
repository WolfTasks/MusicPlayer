# Android Auto Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Android Auto so the car's controls drive the existing ExoPlayer and a full browse tree (Songs / Playlists / Albums / Artists) appears on the head unit.

**Architecture:** `MusicService` is upgraded from `MediaSessionService` to `MediaLibraryService`; a new `AutoLibraryCallback` singleton (injected via Hilt) handles the browse tree and queue resolution. `PlayerViewModel` connects via `MediaController` unchanged — `MediaLibrarySession` is a subclass of `MediaSession`.

**Tech Stack:** Media3 1.3.1 (`media3-session` already present), Hilt, Room, Kotlin Coroutines + `SettableFuture` (from Guava, transitive via media3-session)

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `data/db/dao/SongDao.kt` | Add one-shot suspend queries |
| Modify | `data/db/dao/PlaylistDao.kt` | Add one-shot `getAll()` |
| Modify | `data/repository/SongRepository.kt` | Expose new suspend methods |
| Modify | `data/repository/PlaylistRepository.kt` | Expose `getAll()` |
| **Create** | `domain/ShuffleStateHolder.kt` | Shared shuffle flag (ViewModel ↔ AutoLibraryCallback) |
| Modify | `ui/screen/player/PlayerViewModel.kt` | Write shuffle state to `ShuffleStateHolder` |
| **Create** | `res/xml/automotive_app_desc.xml` | Declares app as Android Auto media app |
| **Create** | `service/AutoLibraryCallback.kt` | Browse tree + queue resolution |
| **Create** | `di/AutoModule.kt` | Hilt: provide `ApplicationScope` |
| Modify | `service/MusicService.kt` | Extend `MediaLibraryService`, inject callback |
| Modify | `AndroidManifest.xml` | Add intent filters + meta-data |
| **Create** | `test/.../AutoLibraryCallbackTest.kt` | Unit tests for callback |

---

## Task 1: Add one-shot DAO queries

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/data/db/dao/SongDao.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/data/db/dao/PlaylistDao.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/data/repository/SongRepository.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/data/repository/PlaylistRepository.kt`

The existing DAOs only expose `Flow`-returning queries. The `AutoLibraryCallback` needs one-shot `suspend` reads. Room generates these automatically from `@Query` annotations.

- [ ] **Step 1: Add suspend queries to `SongDao`**

Add these five methods directly after the existing `observeAlbums()` method:

```kotlin
@Query("SELECT * FROM songs ORDER BY title ASC")
suspend fun getAll(): List<SongEntity>

@Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, title")
suspend fun getByArtist(artist: String): List<SongEntity>

@Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
suspend fun getByAlbum(album: String): List<SongEntity>

@Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
suspend fun getArtists(): List<String>

@Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
suspend fun getAlbums(): List<String>
```

- [ ] **Step 2: Add `getAll()` to `PlaylistDao`**

Add after the existing `observeAll()` method:

```kotlin
@Query("SELECT * FROM playlists ORDER BY name ASC")
suspend fun getAll(): List<PlaylistEntity>
```

- [ ] **Step 3: Expose methods on `SongRepository`**

Add after `getById()`:

```kotlin
suspend fun getAll(): List<Song> = dao.getAll().map(SongEntity::toDomain)

suspend fun getByAlbum(album: String): List<Song> = dao.getByAlbum(album).map(SongEntity::toDomain)

suspend fun getByArtist(artist: String): List<Song> = dao.getByArtist(artist).map(SongEntity::toDomain)

suspend fun getAlbums(): List<String> = dao.getAlbums()

suspend fun getArtists(): List<String> = dao.getArtists()
```

- [ ] **Step 4: Expose `getAll()` on `PlaylistRepository`**

Add after `getSongsForPlaylist()`:

```kotlin
suspend fun getAll(): List<Playlist> = dao.getAll().map(PlaylistEntity::toDomain)
```

- [ ] **Step 5: Build and verify compilation**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/data/db/dao/SongDao.kt \
        app/src/main/java/com/wolf/musicplayer/data/db/dao/PlaylistDao.kt \
        app/src/main/java/com/wolf/musicplayer/data/repository/SongRepository.kt \
        app/src/main/java/com/wolf/musicplayer/data/repository/PlaylistRepository.kt
git commit -m "feat: add one-shot suspend queries to DAO and repositories for Auto browse"
```

---

## Task 2: Create `ShuffleStateHolder` and wire into `PlayerViewModel`

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/domain/ShuffleStateHolder.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

`AutoLibraryCallback` lives in the service layer and has no access to `PlayerViewModel`. This singleton bridges the gap.

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/wolf/musicplayer/ShuffleStateHolderTest.kt`:

```kotlin
package com.wolf.musicplayer

import com.wolf.musicplayer.domain.ShuffleStateHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleStateHolderTest {

    @Test
    fun `defaults to false`() {
        val holder = ShuffleStateHolder()
        assertFalse(holder.shuffleEnabled)
    }

    @Test
    fun `setShuffleEnabled true updates flag`() {
        val holder = ShuffleStateHolder()
        holder.setShuffleEnabled(true)
        assertTrue(holder.shuffleEnabled)
    }

    @Test
    fun `setShuffleEnabled false updates flag`() {
        val holder = ShuffleStateHolder()
        holder.setShuffleEnabled(true)
        holder.setShuffleEnabled(false)
        assertFalse(holder.shuffleEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.wolf.musicplayer.ShuffleStateHolderTest"
```

Expected: compilation error — `ShuffleStateHolder` not found.

- [ ] **Step 3: Create `ShuffleStateHolder`**

Create `app/src/main/java/com/wolf/musicplayer/domain/ShuffleStateHolder.kt`:

```kotlin
package com.wolf.musicplayer.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShuffleStateHolder @Inject constructor() {
    @Volatile
    var shuffleEnabled: Boolean = false
        private set

    fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests "com.wolf.musicplayer.ShuffleStateHolderTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passing.

- [ ] **Step 5: Wire `ShuffleStateHolder` into `PlayerViewModel`**

In `PlayerViewModel`, add `shuffleStateHolder: ShuffleStateHolder` to the `@Inject constructor`:

```kotlin
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songRepository: SongRepository,
        private val playWeightRepository: PlayWeightRepository,
        private val shuffler: WeightedShuffler,
        private val playlistRepo: PlaylistRepository,
        private val shuffleStateHolder: ShuffleStateHolder,
    ) : ViewModel() {
```

Then in `toggleShuffle()`, add one line after computing `enabled`:

```kotlin
fun toggleShuffle() {
    viewModelScope.launch {
        val enabled = !_state.value.shuffleEnabled
        shuffleStateHolder.setShuffleEnabled(enabled)   // ← add this line
        _state.update { it.copy(shuffleEnabled = enabled) }
        // ... rest of method unchanged
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/domain/ShuffleStateHolder.kt \
        app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt \
        app/src/test/java/com/wolf/musicplayer/ShuffleStateHolderTest.kt
git commit -m "feat: add ShuffleStateHolder singleton to share shuffle state with AutoLibraryCallback"
```

---

## Task 3: Create `automotive_app_desc.xml`

**Files:**
- Create: `app/src/main/res/xml/automotive_app_desc.xml`

This XML is required by Android Auto. Without it the car head unit will not recognise the app as a media source.

- [ ] **Step 1: Check if `res/xml/` directory exists**

```bash
ls app/src/main/res/xml/
```

Expected: directory exists and contains `data_extraction_rules.xml` and `backup_rules.xml`.

- [ ] **Step 2: Create `automotive_app_desc.xml`**

Create `app/src/main/res/xml/automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media" />
</automotiveApp>
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/automotive_app_desc.xml
git commit -m "feat: add automotive_app_desc.xml for Android Auto media declaration"
```

---

## Task 4: Create `AutoLibraryCallback` — browse tree

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

This task covers `onGetLibraryRoot`, `onGetChildren`, and `onGetItem`. Queue resolution (`onSetMediaItems`) is in Task 5.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`:

```kotlin
package com.wolf.musicplayer

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

    private fun song(id: Long, title: String = "Song $id", artist: String = "Artist", album: String = "Album") = Song(
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
        callback = AutoLibraryCallback(
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
    fun `onGetLibraryRoot returns root item with correct mediaId`() = runTest {
        val future = callback.onGetLibraryRoot(mockSession, mockBrowser, null)
        testScope.advanceUntilIdle()
        val result = future.get()
        assertEquals("root", result.value?.mediaId)
    }

    @Test
    fun `root item is browsable and not playable`() = runTest {
        val future = callback.onGetLibraryRoot(mockSession, mockBrowser, null)
        testScope.advanceUntilIdle()
        val item = future.get().value!!
        assertEquals(true, item.mediaMetadata.isBrowsable)
        assertEquals(false, item.mediaMetadata.isPlayable)
    }

    // --- onGetChildren: root ---

    @Test
    fun `onGetChildren root returns 4 category items`() = runTest {
        val future = callback.onGetChildren(mockSession, mockBrowser, "root", 0, 100, null)
        testScope.advanceUntilIdle()
        val items = future.get().value!!
        assertEquals(4, items.size)
        assertEquals(listOf("songs", "playlists", "albums", "artists"), items.map { it.mediaId })
    }

    @Test
    fun `category items are browsable and not playable`() = runTest {
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
    fun `onGetChildren songs returns all songs as playable items`() = runTest {
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
    fun `onGetChildren playlists returns each playlist as browsable`() = runTest {
        coEvery { playlistRepo.getAll() } returns listOf(
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
    fun `onGetChildren albums returns each album as browsable`() = runTest {
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
    fun `onGetChildren artists returns each artist as browsable`() = runTest {
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
    fun `onGetChildren playlist id returns songs with compound mediaId`() = runTest {
        coEvery { playlistRepo.getSongsForPlaylist(10L) } returns listOf(song(1), song(2))

        val future = callback.onGetChildren(mockSession, mockBrowser, "playlist:10", 0, 100, null)
        testScope.advanceUntilIdle()
        val items = future.get().value!!

        assertEquals(2, items.size)
        assertEquals("playlist:10/song:1", items[0].mediaId)
        assertEquals("playlist:10/song:2", items[1].mediaId)
    }

    @Test
    fun `onGetChildren album name returns songs with compound mediaId`() = runTest {
        coEvery { songRepo.getByAlbum("Abbey Road") } returns listOf(song(3), song(4))

        val future = callback.onGetChildren(mockSession, mockBrowser, "album:Abbey Road", 0, 100, null)
        testScope.advanceUntilIdle()
        val items = future.get().value!!

        assertEquals(2, items.size)
        assertEquals("album:Abbey Road/song:3", items[0].mediaId)
        assertEquals("album:Abbey Road/song:4", items[1].mediaId)
    }

    @Test
    fun `onGetChildren artist name returns songs with compound mediaId`() = runTest {
        coEvery { songRepo.getByArtist("Beatles") } returns listOf(song(5))

        val future = callback.onGetChildren(mockSession, mockBrowser, "artist:Beatles", 0, 100, null)
        testScope.advanceUntilIdle()
        val items = future.get().value!!

        assertEquals(1, items.size)
        assertEquals("artist:Beatles/song:5", items[0].mediaId)
    }

    // --- onGetItem ---

    @Test
    fun `onGetItem returns song for valid mediaId`() = runTest {
        coEvery { songRepo.getById(7L) } returns song(7, title = "Lucky Number 7")

        val future = callback.onGetItem(mockSession, mockBrowser, "song:7")
        testScope.advanceUntilIdle()
        val item = future.get().value

        assertNotNull(item)
        assertEquals("song:7", item!!.mediaId)
        assertEquals("Lucky Number 7", item.mediaMetadata.title.toString())
    }

    @Test
    fun `onGetItem returns error for unknown id`() = runTest {
        coEvery { songRepo.getById(999L) } returns null

        val future = callback.onGetItem(mockSession, mockBrowser, "song:999")
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals(false, result.isSuccess)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: compilation error — `AutoLibraryCallback` not found.

- [ ] **Step 3: Create `AutoLibraryCallback`**

Create `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`:

```kotlin
package com.wolf.musicplayer.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLibraryCallback @Inject constructor(
    internal val songRepository: SongRepository,
    internal val playlistRepository: PlaylistRepository,
    internal val playWeightRepository: PlayWeightRepository,
    internal val shuffler: WeightedShuffler,
    internal val shuffleStateHolder: ShuffleStateHolder,
    internal val scope: CoroutineScope,
) : MediaLibrarySession.Callback {

    companion object {
        const val ROOT_ID = "root"
        const val SONGS_ID = "songs"
        const val PLAYLISTS_ID = "playlists"
        const val ALBUMS_ID = "albums"
        const val ARTISTS_ID = "artists"
    }

    // ── Browse ──────────────────────────────────────────────────────────────

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, null))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            val items: List<MediaItem> = when {
                parentId == ROOT_ID -> buildRootCategories()
                parentId == SONGS_ID -> buildAllSongItems()
                parentId == PLAYLISTS_ID -> buildPlaylistItems()
                parentId == ALBUMS_ID -> buildAlbumItems()
                parentId == ARTISTS_ID -> buildArtistItems()
                parentId.startsWith("playlist:") -> {
                    val id = parentId.removePrefix("playlist:").toLongOrNull()
                    if (id != null) buildPlaylistSongItems(parentId, id) else emptyList()
                }
                parentId.startsWith("album:") ->
                    buildAlbumSongItems(parentId, parentId.removePrefix("album:"))
                parentId.startsWith("artist:") ->
                    buildArtistSongItems(parentId, parentId.removePrefix("artist:"))
                else -> emptyList()
            }
            future.set(LibraryResult.ofItemList(items, null))
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            val songId = parseSongId(mediaId)
            val song = songId?.let { songRepository.getById(it) }
            if (song != null) {
                future.set(LibraryResult.ofItem(song.toMediaItem(mediaId), null))
            } else {
                future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
        }
        return future
    }

    // ── Playback (queue resolution) — implemented in Task 5 ─────────────────

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
        )

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun release() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildRootCategories(): List<MediaItem> = listOf(
        buildBrowsableItem(SONGS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        buildBrowsableItem(PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        buildBrowsableItem(ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        buildBrowsableItem(ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
    )

    private suspend fun buildAllSongItems(): List<MediaItem> =
        songRepository.getAll().map { it.toMediaItem("song:${it.id}") }

    private suspend fun buildPlaylistItems(): List<MediaItem> =
        playlistRepository.getAll().map { playlist ->
            buildBrowsableItem("playlist:${playlist.id}", playlist.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }

    private suspend fun buildAlbumItems(): List<MediaItem> =
        songRepository.getAlbums().map { album ->
            buildBrowsableItem("album:$album", album, MediaMetadata.MEDIA_TYPE_ALBUM)
        }

    private suspend fun buildArtistItems(): List<MediaItem> =
        songRepository.getArtists().map { artist ->
            buildBrowsableItem("artist:$artist", artist, MediaMetadata.MEDIA_TYPE_ARTIST)
        }

    private suspend fun buildPlaylistSongItems(parentId: String, playlistId: Long): List<MediaItem> =
        playlistRepository.getSongsForPlaylist(playlistId)
            .map { it.toMediaItem("$parentId/song:${it.id}") }

    private suspend fun buildAlbumSongItems(parentId: String, albumName: String): List<MediaItem> =
        songRepository.getByAlbum(albumName).map { it.toMediaItem("$parentId/song:${it.id}") }

    private suspend fun buildArtistSongItems(parentId: String, artistName: String): List<MediaItem> =
        songRepository.getByArtist(artistName).map { it.toMediaItem("$parentId/song:${it.id}") }

    private fun buildBrowsableItem(mediaId: String, title: String, @MediaMetadata.MediaType mediaType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build(),
            )
            .build()

    internal fun parseSongId(mediaId: String): Long? = when {
        mediaId.startsWith("song:") -> mediaId.removePrefix("song:").toLongOrNull()
        mediaId.contains("/song:") -> mediaId.substringAfterLast("/song:").toLongOrNull()
        else -> null
    }
}

internal fun Song.toMediaItem(mediaId: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(localPath ?: cachedPath ?: "")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: `BUILD SUCCESSFUL`, all 14 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "feat: add AutoLibraryCallback with browse tree for Android Auto"
```

---

## Task 5: Add `onSetMediaItems` — queue resolution with shuffle

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

When the driver taps a song, Android Auto calls `onSetMediaItems`. The callback resolves the full queue from the mediaId context and applies shuffle if enabled.

- [ ] **Step 1: Write failing tests**

Append these tests to `AutoLibraryCallbackTest` (inside the class, before the closing brace):

```kotlin
    // --- onSetMediaItems ---

    private val mockMediaSession = mockk<MediaSession>(relaxed = true)
    private val mockController = mockk<MediaSession.ControllerInfo>(relaxed = true)

    private fun mediaItemWithId(mediaId: String) =
        MediaItem.Builder().setMediaId(mediaId).build()

    @Test
    fun `onSetMediaItems for song context loads all songs starting at tapped song`() = runTest {
        coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("song:2")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals(3, result.mediaItems.size)
        assertEquals(1, result.startIndex) // song:2 is at index 1
    }

    @Test
    fun `onSetMediaItems for album context loads album songs`() = runTest {
        coEvery { songRepo.getByAlbum("Abbey Road") } returns listOf(song(10), song(11))

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("album:Abbey Road/song:11")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals(2, result.mediaItems.size)
        assertEquals(1, result.startIndex) // song:11 is at index 1
    }

    @Test
    fun `onSetMediaItems for playlist context loads playlist songs`() = runTest {
        coEvery { playlistRepo.getSongsForPlaylist(5L) } returns listOf(song(20), song(21))

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("playlist:5/song:20")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals(2, result.mediaItems.size)
        assertEquals(0, result.startIndex) // song:20 is at index 0
    }

    @Test
    fun `onSetMediaItems for artist context loads artist songs`() = runTest {
        coEvery { songRepo.getByArtist("Beatles") } returns listOf(song(30), song(31), song(32))

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("artist:Beatles/song:31")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals(3, result.mediaItems.size)
        assertEquals(1, result.startIndex) // song:31 is at index 1
    }

    @Test
    fun `onSetMediaItems with shuffle enabled permutes the queue`() = runTest {
        shuffleStateHolder.setShuffleEnabled(true)
        val songs = (1L..10L).map { song(it) }
        coEvery { songRepo.getAll() } returns songs
        coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("song:5")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        // All 10 songs still present
        assertEquals(10, result.mediaItems.size)
        // song:5 must be at startIndex
        assertEquals("song:5", result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `onSetMediaItems with shuffle disabled preserves natural order`() = runTest {
        shuffleStateHolder.setShuffleEnabled(false)
        coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))
        coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

        val future = callback.onSetMediaItems(
            mockMediaSession, mockController,
            listOf(mediaItemWithId("song:1")), 0, 0L,
        )
        testScope.advanceUntilIdle()
        val result = future.get()

        assertEquals("song:1", result.mediaItems[0].mediaId)
        assertEquals("song:2", result.mediaItems[1].mediaId)
        assertEquals("song:3", result.mediaItems[2].mediaId)
        assertEquals(0, result.startIndex)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: the 6 new `onSetMediaItems` tests fail (the stub implementation returns the raw input without resolving the queue).

- [ ] **Step 3: Replace the stub `onSetMediaItems` with the real implementation**

In `AutoLibraryCallback.kt`, replace the stub `onSetMediaItems` with:

```kotlin
override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
    scope.launch {
        val requestedId = mediaItems.firstOrNull()?.mediaId
        if (requestedId == null) {
            future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
            return@launch
        }
        val (songs, requestedSongId) = resolveQueue(requestedId)
        if (songs.isEmpty()) {
            future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
            return@launch
        }
        val queue = if (shuffleStateHolder.shuffleEnabled) {
            val weights = songs.map { playWeightRepository.getWeight(it.id) }
            shuffler.permute(songs, weights)
        } else {
            songs
        }
        val idx = queue.indexOfFirst { it.id == requestedSongId }.coerceAtLeast(0)
        val resolvedItems = queue.map { it.toMediaItem("song:${it.id}") }
        future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, idx, startPositionMs))
    }
    return future
}

private suspend fun resolveQueue(mediaId: String): Pair<List<Song>, Long> {
    return when {
        mediaId.startsWith("album:") -> {
            val rest = mediaId.removePrefix("album:")
            val slashIdx = rest.indexOf("/song:")
            if (slashIdx < 0) return Pair(emptyList(), -1L)
            val albumName = rest.substring(0, slashIdx)
            val songId = rest.substring(slashIdx + 6).toLongOrNull() ?: return Pair(emptyList(), -1L)
            Pair(songRepository.getByAlbum(albumName), songId)
        }
        mediaId.startsWith("artist:") -> {
            val rest = mediaId.removePrefix("artist:")
            val slashIdx = rest.indexOf("/song:")
            if (slashIdx < 0) return Pair(emptyList(), -1L)
            val artistName = rest.substring(0, slashIdx)
            val songId = rest.substring(slashIdx + 6).toLongOrNull() ?: return Pair(emptyList(), -1L)
            Pair(songRepository.getByArtist(artistName), songId)
        }
        mediaId.startsWith("playlist:") -> {
            val rest = mediaId.removePrefix("playlist:")
            val slashIdx = rest.indexOf("/song:")
            if (slashIdx < 0) return Pair(emptyList(), -1L)
            val playlistId = rest.substring(0, slashIdx).toLongOrNull() ?: return Pair(emptyList(), -1L)
            val songId = rest.substring(slashIdx + 6).toLongOrNull() ?: return Pair(emptyList(), -1L)
            Pair(playlistRepository.getSongsForPlaylist(playlistId), songId)
        }
        mediaId.startsWith("song:") -> {
            val songId = mediaId.removePrefix("song:").toLongOrNull() ?: return Pair(emptyList(), -1L)
            Pair(songRepository.getAll(), songId)
        }
        else -> Pair(emptyList(), -1L)
    }
}
```

- [ ] **Step 4: Run all `AutoLibraryCallbackTest` tests**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: `BUILD SUCCESSFUL`, all 20 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "feat: implement onSetMediaItems queue resolution with shuffle support in AutoLibraryCallback"
```

---

## Task 6: Provide `CoroutineScope` and `AutoLibraryCallback` via Hilt

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/di/AutoModule.kt`

`AutoLibraryCallback` needs a `CoroutineScope`. We provide an application-level scope via a custom qualifier so it's injectable and replaceable in tests.

- [ ] **Step 1: Create `AutoModule`**

Create `app/src/main/java/com/wolf/musicplayer/di/AutoModule.kt`:

```kotlin
package com.wolf.musicplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AutoModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

- [ ] **Step 2: Update `AutoLibraryCallback` constructor to use the qualifier**

In `AutoLibraryCallback.kt`, add the import and annotate the `scope` parameter:

```kotlin
import com.wolf.musicplayer.di.ApplicationScope
```

Change the constructor parameter:

```kotlin
@Singleton
class AutoLibraryCallback @Inject constructor(
    internal val songRepository: SongRepository,
    internal val playlistRepository: PlaylistRepository,
    internal val playWeightRepository: PlayWeightRepository,
    internal val shuffler: WeightedShuffler,
    internal val shuffleStateHolder: ShuffleStateHolder,
    @ApplicationScope internal val scope: CoroutineScope,
) : MediaLibrarySession.Callback {
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all tests to confirm nothing broke**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/di/AutoModule.kt \
        app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt
git commit -m "feat: provide ApplicationScope and wire AutoLibraryCallback via Hilt"
```

---

## Task 7: Upgrade `MusicService` to `MediaLibraryService`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt`

`MediaLibraryService` extends `MediaSessionService`. The only changes are: different base class, use `MediaLibrarySession.Builder` instead of `MediaSession.Builder`, and inject `AutoLibraryCallback`.

- [ ] **Step 1: Replace `MusicService` contents**

Replace the entire file `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt` with:

```kotlin
package com.wolf.musicplayer.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaLibraryService() {
    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var autoLibraryCallback: AutoLibraryCallback

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, autoLibraryCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        autoLibraryCallback.release()
        super.onDestroy()
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run unit tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/MusicService.kt
git commit -m "feat: upgrade MusicService to MediaLibraryService with AutoLibraryCallback"
```

---

## Task 8: Update `AndroidManifest.xml`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

Android Auto discovers the app via two intent filters and the `com.google.android.gms.car.application` meta-data entry.

- [ ] **Step 1: Update the `<service>` element in `AndroidManifest.xml`**

Replace the existing `<service>` block:

```xml
<service
    android:name=".service.MusicService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

With:

```xml
<service
    android:name=".service.MusicService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
    <meta-data
        android:name="com.google.android.gms.car.application"
        android:resource="@xml/automotive_app_desc" />
</service>
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run full test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add Android Auto intent filters and meta-data to AndroidManifest"
```

---

## Task 9: Manual verification checklist

No automated tests cover the live Android Auto head unit. Use the Android Desktop Head Unit (DHU) emulator to verify end-to-end behaviour.

- [ ] **Step 1: Install the Desktop Head Unit (DHU)**

Follow the official guide:  
`platform-tools` → enable `Android Auto` in phone developer options → connect phone via USB → run `adb forward tcp:5277 tcp:5277` → launch DHU from Android SDK `extras/google/auto/`.

- [ ] **Step 2: Verify browse tree appears**

In the DHU, open the media app. Confirm four top-level categories: **Songs**, **Playlists**, **Albums**, **Artists**.

- [ ] **Step 3: Verify drill-down and playback**

Tap **Albums** → pick an album → tap a song. Confirm:
- Song starts playing through car speakers (DHU audio output).
- The correct album songs are queued (not all library songs).
- Car next/previous buttons advance the queue.

- [ ] **Step 4: Verify shuffle state is preserved**

In the phone app, enable shuffle. Switch to Android Auto and tap a song. Confirm the queue order is shuffled (DHU shows the queue).

- [ ] **Step 5: Verify skip tracking**

Skip a song via the DHU next button. In Android Studio Database Inspector, confirm the song's `weight` in the `play_weights` table decreased by ~25%.
