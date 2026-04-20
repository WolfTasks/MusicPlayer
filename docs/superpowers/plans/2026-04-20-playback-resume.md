# Playback Session Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the active queue context (library / playlist / album / artist) and last-played song so Android Auto resumes the correct queue on cold start instead of always restarting the full library.

**Architecture:** A new `QueueContext` sealed class models the four queue sources. `PlaybackSessionRepository` persists the active context and song ID to SharedPreferences. `PlayerViewModel` saves the session when playback starts or advances to a new song. `AutoLibraryCallback.onPlaybackResumption` restores the correct queue from the saved session.

**Tech Stack:** Kotlin sealed classes, Android SharedPreferences, MockK for unit tests, Hilt DI (no new modules needed — `@Singleton` + `@Inject` on the new repository is enough).

---

## File Map

| Action  | File |
|---------|------|
| Create  | `app/src/main/java/com/wolf/musicplayer/domain/model/QueueContext.kt` |
| Create  | `app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt` |
| Create  | `app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt` |
| Modify  | `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt` |
| Modify  | `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt` |
| Modify  | `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt` |
| Modify  | `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistViewModel.kt` |
| Modify  | `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistScreen.kt` |
| Modify  | `app/src/test/java/com/wolf/musicplayer/PlaylistViewModelTest.kt` |
| Modify  | `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt` |

---

### Task 1: QueueContext sealed class

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/domain/model/QueueContext.kt`

- [ ] **Step 1: Create QueueContext.kt**

```kotlin
package com.wolf.musicplayer.domain.model

sealed class QueueContext {
    object AllSongs : QueueContext()
    data class PlaylistCtx(val id: Long) : QueueContext()
    data class AlbumCtx(val name: String) : QueueContext()
    data class ArtistCtx(val name: String) : QueueContext()
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/domain/model/QueueContext.kt
git commit -m "feat: add QueueContext sealed class for playback session tracking"
```

---

### Task 2: PlaybackSessionRepository

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt`:

```kotlin
package com.wolf.musicplayer

import android.content.Context
import android.content.SharedPreferences
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.domain.model.QueueContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackSessionRepositoryTest {
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private lateinit var repo: PlaybackSessionRepository

    @Before
    fun setup() {
        every { mockContext.getSharedPreferences("playback_session", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        repo = PlaybackSessionRepository(mockContext)
    }

    @Test
    fun `load returns null when no session saved`() {
        every { mockPrefs.getString("context_type", null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when song id is minus 1`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns -1L
        assertNull(repo.load())
    }

    @Test
    fun `load returns AllSongs context`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns 42L

        val result = repo.load()
        assertEquals(QueueContext.AllSongs, result?.first)
        assertEquals(42L, result?.second)
    }

    @Test
    fun `load returns PlaylistCtx with correct id`() {
        every { mockPrefs.getString("context_type", null) } returns "PLAYLIST"
        every { mockPrefs.getString("context_id", null) } returns "7"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 99L

        val result = repo.load()
        assertEquals(QueueContext.PlaylistCtx(7L), result?.first)
        assertEquals(99L, result?.second)
    }

    @Test
    fun `load returns AlbumCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ALBUM"
        every { mockPrefs.getString("context_id", null) } returns "Abbey Road"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 5L

        val result = repo.load()
        assertEquals(QueueContext.AlbumCtx("Abbey Road"), result?.first)
        assertEquals(5L, result?.second)
    }

    @Test
    fun `load returns ArtistCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ARTIST"
        every { mockPrefs.getString("context_id", null) } returns "The Beatles"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 3L

        val result = repo.load()
        assertEquals(QueueContext.ArtistCtx("The Beatles"), result?.first)
        assertEquals(3L, result?.second)
    }

    @Test
    fun `save AllSongs writes correct type, empty id, and song id`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        val songIdSlot = slot<Long>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor
        every { mockEditor.putLong("last_song_id", capture(songIdSlot)) } returns mockEditor

        repo.save(QueueContext.AllSongs, 10L)

        assertEquals("ALL", typeSlot.captured)
        assertEquals("", idSlot.captured)
        assertEquals(10L, songIdSlot.captured)
        verify { mockEditor.apply() }
    }

    @Test
    fun `save PlaylistCtx writes playlist id as string`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.PlaylistCtx(3L), 55L)

        assertEquals("PLAYLIST", typeSlot.captured)
        assertEquals("3", idSlot.captured)
    }

    @Test
    fun `save AlbumCtx writes album name as context id`() {
        val idSlot = slot<String>()
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.AlbumCtx("Thriller"), 1L)

        assertEquals("Thriller", idSlot.captured)
    }

    @Test
    fun `save ArtistCtx writes artist name as context id`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.ArtistCtx("The Beatles"), 2L)

        assertEquals("ARTIST", typeSlot.captured)
        assertEquals("The Beatles", idSlot.captured)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.wolf.musicplayer.PlaybackSessionRepositoryTest"`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement PlaybackSessionRepository**

Create `app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt`:

```kotlin
package com.wolf.musicplayer.data.repository

import android.content.Context
import com.wolf.musicplayer.domain.model.QueueContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("playback_session", Context.MODE_PRIVATE)

        fun save(
            context: QueueContext,
            songId: Long,
        ) {
            prefs.edit()
                .putString("context_type", context.toTypeString())
                .putString("context_id", context.toIdString())
                .putLong("last_song_id", songId)
                .apply()
        }

        fun load(): Pair<QueueContext, Long>? {
            val type = prefs.getString("context_type", null) ?: return null
            val id = prefs.getString("context_id", null) ?: ""
            val songId = prefs.getLong("last_song_id", -1L)
            if (songId == -1L) return null
            val ctx =
                when (type) {
                    "ALL" -> QueueContext.AllSongs
                    "PLAYLIST" -> QueueContext.PlaylistCtx(id.toLongOrNull() ?: return null)
                    "ALBUM" -> QueueContext.AlbumCtx(id)
                    "ARTIST" -> QueueContext.ArtistCtx(id)
                    else -> return null
                }
            return Pair(ctx, songId)
        }

        private fun QueueContext.toTypeString() =
            when (this) {
                is QueueContext.AllSongs -> "ALL"
                is QueueContext.PlaylistCtx -> "PLAYLIST"
                is QueueContext.AlbumCtx -> "ALBUM"
                is QueueContext.ArtistCtx -> "ARTIST"
            }

        private fun QueueContext.toIdString() =
            when (this) {
                is QueueContext.AllSongs -> ""
                is QueueContext.PlaylistCtx -> id.toString()
                is QueueContext.AlbumCtx -> name
                is QueueContext.ArtistCtx -> name
            }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.wolf.musicplayer.PlaybackSessionRepositoryTest"`
Expected: All 9 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt \
        app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt
git commit -m "feat: add PlaybackSessionRepository persisting queue context to SharedPreferences"
```

---

### Task 3: Update AutoLibraryCallback — inject repository and update onPlaybackResumption

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

- [ ] **Step 1: Add imports to AutoLibraryCallback.kt**

Add these two imports at the top of `AutoLibraryCallback.kt`:

```kotlin
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.domain.model.QueueContext
```

- [ ] **Step 2: Add PlaybackSessionRepository to the constructor**

In `AutoLibraryCallback.kt`, add `private val playbackSessionRepository: PlaybackSessionRepository` to the constructor (insert after `shuffleStateHolder`):

```kotlin
@Singleton
class AutoLibraryCallback
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songRepository: SongRepository,
        private val playlistRepository: PlaylistRepository,
        private val playWeightRepository: PlayWeightRepository,
        private val shuffler: WeightedShuffler,
        private val shuffleStateHolder: ShuffleStateHolder,
        private val playbackSessionRepository: PlaybackSessionRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : MediaLibrarySession.Callback {
```

- [ ] **Step 3: Replace onPlaybackResumption**

Replace the entire `onPlaybackResumption` method with:

```kotlin
override fun onPlaybackResumption(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
    callbackScope.launch {
        try {
            val session = playbackSessionRepository.load()
            val rawSongs =
                if (session != null) {
                    val (ctx, _) = session
                    val ctxSongs =
                        when (ctx) {
                            is QueueContext.AllSongs -> songRepository.getAll()
                            is QueueContext.PlaylistCtx -> playlistRepository.getSongsForPlaylist(ctx.id)
                            is QueueContext.AlbumCtx -> songRepository.getByAlbum(ctx.name)
                            is QueueContext.ArtistCtx -> songRepository.getByArtist(ctx.name)
                        }
                    ctxSongs.ifEmpty { songRepository.getAll() }
                } else {
                    songRepository.getAll()
                }
            val queue =
                if (shuffleStateHolder.shuffleEnabled) {
                    val weights = rawSongs.map { playWeightRepository.getWeight(it.id) }
                    shuffler.permute(rawSongs, weights)
                } else {
                    rawSongs
                }
            val startIndex =
                if (session != null) {
                    queue.indexOfFirst { it.id == session.second }.coerceAtLeast(0)
                } else {
                    0
                }
            val items = queue.map { it.toMediaItem("song:${it.id}") }
            future.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, 0L))
        } catch (e: Exception) {
            future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }
    }
    return future
}
```

- [ ] **Step 4: Update AutoLibraryCallbackTest — add mock to setup**

In `AutoLibraryCallbackTest.kt`:

Add field declaration (after `private lateinit var testScope: TestScope`):
```kotlin
private lateinit var playbackSessionRepo: PlaybackSessionRepository
```

In `setup()`, add before the `callback =` line:
```kotlin
playbackSessionRepo = mockk(relaxed = true)
every { playbackSessionRepo.load() } returns null
```

Update the `callback =` constructor call to include the new parameter (insert after `shuffleStateHolder = shuffleStateHolder,`):
```kotlin
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
```

Add the import:
```kotlin
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
```

- [ ] **Step 5: Run existing tests to verify nothing broke**

Run: `./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"`
Expected: All existing tests PASS

- [ ] **Step 6: Add new onPlaybackResumption tests for session restore**

Add the following tests to `AutoLibraryCallbackTest.kt` inside the class, after the existing `onPlaybackResumption` block:

```kotlin
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
```

Add this import at the top of `AutoLibraryCallbackTest.kt`:
```kotlin
import com.wolf.musicplayer.domain.model.QueueContext
```

- [ ] **Step 7: Run all AutoLibraryCallbackTest tests**

Run: `./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "feat: restore playback session context in AutoLibraryCallback.onPlaybackResumption"
```

---

### Task 4: Update PlayerViewModel to save session

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

`PlayerViewModel` uses `MediaController` (Android framework) which requires instrumented tests — no new unit tests here.

- [ ] **Step 1: Add imports**

Add to `PlayerViewModel.kt`:
```kotlin
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.domain.model.QueueContext
```

- [ ] **Step 2: Add PlaybackSessionRepository to constructor**

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
        private val playbackSessionRepository: PlaybackSessionRepository,
    ) : ViewModel() {
```

- [ ] **Step 3: Add currentQueueContext field**

Add this private field directly after `private var playbackStartPosition: Long = 0L`:

```kotlin
private var currentQueueContext: QueueContext = QueueContext.AllSongs
```

- [ ] **Step 4: Replace playSongs() with context-aware version**

Replace the existing `playSongs` function entirely with:

```kotlin
fun playSongs(
    songs: List<Song>,
    startIndex: Int = 0,
    context: QueueContext = QueueContext.AllSongs,
) {
    val ctrl = controller ?: return
    currentQueue = songs
    currentQueueContext = context
    viewModelScope.launch {
        activeQueue =
            if (_state.value.shuffleEnabled) {
                val weights = songs.map { playWeightRepository.getWeight(it.id) }
                shuffler.permute(songs, weights)
            } else {
                songs
            }
        val startSong = songs.getOrNull(startIndex)
        val activeIndex =
            if (startSong != null) {
                activeQueue.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
            } else {
                0
            }
        val items =
            activeQueue.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.localPath ?: song.cachedPath ?: "")
                    .build()
            }
        activeQueue.getOrNull(activeIndex)?.let { song ->
            playbackSessionRepository.save(context, song.id)
        }
        currentSongId = null
        ctrl.setMediaItems(items, activeIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        syncStateFromController()
    }
}
```

- [ ] **Step 5: Update onMediaItemTransition to save session on song advance**

Replace the `onMediaItemTransition` override inside `playerListener` with:

```kotlin
override fun onMediaItemTransition(
    mediaItem: MediaItem?,
    reason: Int,
) {
    val wasManualSkip = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    if (wasManualSkip) {
        currentSongId?.let { id ->
            viewModelScope.launch { playWeightRepository.recordSkip(id) }
        }
    }
    val newIdx = controller?.currentMediaItemIndex ?: 0
    activeQueue.getOrNull(newIdx)?.let { song ->
        viewModelScope.launch {
            playbackSessionRepository.save(currentQueueContext, song.id)
        }
    }
    syncStateFromController()
}
```

- [ ] **Step 6: Build and run all unit tests**

Run: `./gradlew assembleDebug && ./gradlew test`
Expected: `BUILD SUCCESSFUL` and all tests PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "feat: save playback session in PlayerViewModel on queue start and song advance"
```

---

### Task 5: Update PlaylistViewModel and PlaylistScreen callback signature

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistViewModel.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistScreen.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/PlaylistViewModelTest.kt`

The `PlaylistScreen.onPlaySongs` callback currently doesn't expose the playlist ID. We fix this so `AppNavigation` can pass `QueueContext.PlaylistCtx(playlistId)` to `playSongs`.

- [ ] **Step 1: Update PlaylistViewModel.playPlaylist to pass playlist ID to callback**

In `PlaylistViewModel.kt`, change `playPlaylist` from:
```kotlin
fun playPlaylist(
    playlistId: Long,
    onSongsReady: (List<Song>) -> Unit,
) {
    viewModelScope.launch {
        val songs = repo.getSongsForPlaylist(playlistId)
        if (songs.isNotEmpty()) onSongsReady(songs)
    }
}
```
to:
```kotlin
fun playPlaylist(
    playlistId: Long,
    onSongsReady: (List<Song>, Long) -> Unit,
) {
    viewModelScope.launch {
        val songs = repo.getSongsForPlaylist(playlistId)
        if (songs.isNotEmpty()) onSongsReady(songs, playlistId)
    }
}
```

- [ ] **Step 2: Update PlaylistScreen parameter type**

In `PlaylistScreen.kt`, change the `onPlaySongs` parameter from:
```kotlin
onPlaySongs: (List<Song>) -> Unit,
```
to:
```kotlin
onPlaySongs: (List<Song>, Long) -> Unit,
```

The call site inside the screen is `viewModel.playPlaylist(playlist.id, onPlaySongs)` — no other change needed since the types now match.

- [ ] **Step 3: Fix PlaylistViewModelTest**

In `PlaylistViewModelTest.kt`, update the two lambdas passed to `playPlaylist`:

`playPlaylist fetches songs and invokes callback` — change:
```kotlin
var received: List<Song>? = null
vm.playPlaylist(5L) { received = it }
```
to:
```kotlin
var received: List<Song>? = null
vm.playPlaylist(5L) { songs, _ -> received = songs }
```

`playPlaylist does not invoke callback for empty playlist` — change:
```kotlin
var called = false
vm.playPlaylist(5L) { called = true }
```
to:
```kotlin
var called = false
vm.playPlaylist(5L) { _, _ -> called = true }
```

- [ ] **Step 4: Run PlaylistViewModelTest**

Run: `./gradlew test --tests "com.wolf.musicplayer.PlaylistViewModelTest"`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistViewModel.kt \
        app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistScreen.kt \
        app/src/test/java/com/wolf/musicplayer/PlaylistViewModelTest.kt
git commit -m "feat: expose playlist id in PlaylistScreen onPlaySongs callback"
```

---

### Task 6: Update AppNavigation call sites

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt`

All five `playSongs` call sites are in this file. The `composable { it -> }` block gives access to `NavBackStackEntry` (`it`) in scope, so nav arguments are available directly.

- [ ] **Step 1: Add QueueContext import**

Add to `AppNavigation.kt`:
```kotlin
import com.wolf.musicplayer.domain.model.QueueContext
```

- [ ] **Step 2: Update LibraryScreen onSongClick (~line 101)**

Change:
```kotlin
onSongClick = { songs, index ->
    playerViewModel.playSongs(songs, index)
    navController.navigate(Screen.Player.route)
},
```
to:
```kotlin
onSongClick = { songs, index ->
    playerViewModel.playSongs(songs, index, QueueContext.AllSongs)
    navController.navigate(Screen.Player.route)
},
```

- [ ] **Step 3: Update PlaylistScreen onPlaySongs (~line 114)**

Change:
```kotlin
onPlaySongs = { songs ->
    playerViewModel.playSongs(songs, 0)
    navController.navigate(Screen.Player.route)
},
```
to:
```kotlin
onPlaySongs = { songs, playlistId ->
    playerViewModel.playSongs(songs, 0, QueueContext.PlaylistCtx(playlistId))
    navController.navigate(Screen.Player.route)
},
```

- [ ] **Step 4: Update PlaylistDetailScreen onSongClick (~line 127)**

Change:
```kotlin
onSongClick = { songs, index ->
    playerViewModel.playSongs(songs, index)
    navController.navigate(Screen.Player.route)
},
```
to:
```kotlin
onSongClick = { songs, index ->
    val playlistId = it.arguments?.getLong("playlistId") ?: 0L
    playerViewModel.playSongs(songs, index, QueueContext.PlaylistCtx(playlistId))
    navController.navigate(Screen.Player.route)
},
```

- [ ] **Step 5: Update ArtistDetailScreen onSongClick (~line 149)**

Change:
```kotlin
onSongClick = { songs, index ->
    playerViewModel.playSongs(songs, index)
    navController.navigate(Screen.Player.route)
},
```
to:
```kotlin
onSongClick = { songs, index ->
    val artistName = it.arguments?.getString("artistName") ?: ""
    playerViewModel.playSongs(songs, index, QueueContext.ArtistCtx(artistName))
    navController.navigate(Screen.Player.route)
},
```

- [ ] **Step 6: Update AlbumDetailScreen onSongClick (~line 171)**

Change:
```kotlin
onSongClick = { songs, index ->
    playerViewModel.playSongs(songs, index)
    navController.navigate(Screen.Player.route)
},
```
to:
```kotlin
onSongClick = { songs, index ->
    val albumName = it.arguments?.getString("albumName") ?: ""
    playerViewModel.playSongs(songs, index, QueueContext.AlbumCtx(albumName))
    navController.navigate(Screen.Player.route)
},
```

- [ ] **Step 7: Build and run all tests**

Run: `./gradlew assembleDebug && ./gradlew test`
Expected: `BUILD SUCCESSFUL` and all tests PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt
git commit -m "feat: pass QueueContext to playSongs from all AppNavigation call sites"
```
