# Android Auto Queue Sync & Song Restart Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two bugs: (1) song restarts from the beginning after the phone is locked a while, and (2) Android Auto playback leaves the phone UI with no current song selected.

**Architecture:** Add `positionMs` to `PlaybackSessionRepository` so `onPlaybackResumption` can seek to the right position after a service kill. Add an `onTimelineChanged` listener in `PlayerViewModel` — guarded by a `pendingInternalQueueSet` flag — that rebuilds `activeQueue` from the controller when Android Auto has driven a new queue externally.

**Tech Stack:** Kotlin, Media3/ExoPlayer, MockK, kotlinx-coroutines-test, Room

---

## File Map

| File | Change |
|---|---|
| `data/db/dao/SongDao.kt` | Add `getByIds` query |
| `data/repository/SongRepository.kt` | Add `getByIds` method |
| `data/repository/PlaybackSessionRepository.kt` | Add `positionMs` to `save` / `load` |
| `service/AutoLibraryCallback.kt` | Extract `parseSongId` to top-level; use saved position in `onPlaybackResumption` |
| `ui/screen/player/PlayerViewModel.kt` | Fix mediaIds; add flag + `onTimelineChanged` + `rebuildActiveQueueFromController`; save position on pause + periodically |
| `test/.../PlaybackSessionRepositoryTest.kt` | Update all tests for new `Triple` return + new position test |
| `test/.../AutoLibraryCallbackTest.kt` | Update load() mocks to Triple; add position-restore test |
| `test/.../SongRepositoryTest.kt` | New file — test `getByIds` |

---

## Task 1: Add `SongDao.getByIds` and `SongRepository.getByIds`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/data/db/dao/SongDao.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/data/repository/SongRepository.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/SongRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wolf/musicplayer/SongRepositoryTest.kt`:

```kotlin
package com.wolf.musicplayer

import com.wolf.musicplayer.data.db.dao.SongDao
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.data.source.LocalDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SongRepositoryTest {

    private val dao = mockk<SongDao>(relaxed = true)
    private val localDataSource = mockk<LocalDataSource>(relaxed = true)
    private val repo = SongRepository(dao, localDataSource)

    @Test
    fun `getByIds returns mapped songs in DAO order`() = runTest {
        coEvery { dao.getByIds(listOf(3L, 7L)) } returns listOf(
            SongEntity(id = 3, title = "Three", artist = "A", album = "B", duration = 1000, sourceType = "LOCAL"),
            SongEntity(id = 7, title = "Seven", artist = "A", album = "B", duration = 2000, sourceType = "LOCAL"),
        )

        val result = repo.getByIds(listOf(3L, 7L))

        assertEquals(2, result.size)
        assertEquals(3L, result[0].id)
        assertEquals("Three", result[0].title)
        assertEquals(7L, result[1].id)
        assertEquals("Seven", result[1].title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew test --tests "com.wolf.musicplayer.SongRepositoryTest"
```

Expected: FAIL — `getByIds` does not exist yet.

- [ ] **Step 3: Add `getByIds` to `SongDao`**

In `app/src/main/java/com/wolf/musicplayer/data/db/dao/SongDao.kt`, add after the last `suspend fun getAlbums()` query (before the `@Insert` block):

```kotlin
@Query("SELECT * FROM songs WHERE id IN (:ids)")
suspend fun getByIds(ids: List<Long>): List<SongEntity>
```

- [ ] **Step 4: Add `getByIds` to `SongRepository`**

In `app/src/main/java/com/wolf/musicplayer/data/repository/SongRepository.kt`, add after `suspend fun getArtists()`:

```kotlin
suspend fun getByIds(ids: List<Long>): List<Song> = dao.getByIds(ids).map(SongEntity::toDomain)
```

- [ ] **Step 5: Run test to verify it passes**

```
./gradlew test --tests "com.wolf.musicplayer.SongRepositoryTest"
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/data/db/dao/SongDao.kt \
        app/src/main/java/com/wolf/musicplayer/data/repository/SongRepository.kt \
        app/src/test/java/com/wolf/musicplayer/SongRepositoryTest.kt
git commit -m "feat: add SongDao.getByIds and SongRepository.getByIds for bulk lookup"
```

---

## Task 2: Add `positionMs` to `PlaybackSessionRepository`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt`

The return type changes from `Pair<QueueContext, Long>?` to `Triple<QueueContext, Long, Long>?`. The `second` component (songId) stays in the same position, so existing callers that access `.second` keep working. A default of `positionMs: Long = 0L` means all existing `save()` call sites compile without change until explicitly updated.

- [ ] **Step 1: Update `PlaybackSessionRepository`**

Replace the entire file contents of `app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt` with:

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
            queueContext: QueueContext,
            songId: Long,
            positionMs: Long = 0L,
        ) {
            prefs.edit()
                .putString("context_type", queueContext.toTypeString())
                .putString("context_id", queueContext.toIdString())
                .putLong("last_song_id", songId)
                .putLong("position_ms", positionMs)
                .apply()
        }

        fun load(): Triple<QueueContext, Long, Long>? {
            val type = prefs.getString("context_type", null) ?: return null
            val id = prefs.getString("context_id", null) ?: ""
            val songId = prefs.getLong("last_song_id", -1L)
            if (songId == -1L) return null
            val positionMs = prefs.getLong("position_ms", 0L)
            val ctx =
                when (type) {
                    "ALL" -> QueueContext.AllSongs
                    "PLAYLIST" -> QueueContext.PlaylistCtx(id.toLongOrNull() ?: return null)
                    // Names are used as keys and may not survive a media rescan/tag edit — fallback handled by caller
                    "ALBUM" -> QueueContext.AlbumCtx(id)
                    "ARTIST" -> QueueContext.ArtistCtx(id)
                    else -> return null
                }
            return Triple(ctx, songId, positionMs)
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

- [ ] **Step 2: Update `PlaybackSessionRepositoryTest`**

Replace the entire file contents of `app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt` with:

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
        every { mockPrefs.getLong("position_ms", 0L) } returns 0L
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
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns PlaylistCtx with correct id`() {
        every { mockPrefs.getString("context_type", null) } returns "PLAYLIST"
        every { mockPrefs.getString("context_id", null) } returns "7"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 99L

        val result = repo.load()
        assertEquals(QueueContext.PlaylistCtx(7L), result?.first)
        assertEquals(99L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns AlbumCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ALBUM"
        every { mockPrefs.getString("context_id", null) } returns "Abbey Road"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 5L

        val result = repo.load()
        assertEquals(QueueContext.AlbumCtx("Abbey Road"), result?.first)
        assertEquals(5L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns ArtistCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ARTIST"
        every { mockPrefs.getString("context_id", null) } returns "The Beatles"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 3L

        val result = repo.load()
        assertEquals(QueueContext.ArtistCtx("The Beatles"), result?.first)
        assertEquals(3L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns saved positionMs`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns 10L
        every { mockPrefs.getLong("position_ms", 0L) } returns 45_000L

        val result = repo.load()
        assertEquals(45_000L, result?.third)
    }

    @Test
    fun `save AllSongs writes correct type, empty id, song id, and position`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        val songIdSlot = slot<Long>()
        val positionSlot = slot<Long>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor
        every { mockEditor.putLong("last_song_id", capture(songIdSlot)) } returns mockEditor
        every { mockEditor.putLong("position_ms", capture(positionSlot)) } returns mockEditor

        repo.save(QueueContext.AllSongs, 10L, 30_000L)

        assertEquals("ALL", typeSlot.captured)
        assertEquals("", idSlot.captured)
        assertEquals(10L, songIdSlot.captured)
        assertEquals(30_000L, positionSlot.captured)
        verify { mockEditor.apply() }
    }

    @Test
    fun `save defaults positionMs to 0 when not provided`() {
        val positionSlot = slot<Long>()
        every { mockEditor.putLong("position_ms", capture(positionSlot)) } returns mockEditor

        repo.save(QueueContext.AllSongs, 10L)

        assertEquals(0L, positionSlot.captured)
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

    @Test
    fun `load returns null when context id is null and type is PLAYLIST`() {
        every { mockPrefs.getString("context_type", null) } returns "PLAYLIST"
        every { mockPrefs.getString("context_id", null) } returns null
        every { mockPrefs.getLong("last_song_id", -1L) } returns 10L

        val result = repo.load()
        assertNull(result)
    }

    @Test
    fun `load returns null when type is unknown`() {
        every { mockPrefs.getString("context_type", null) } returns "UNKNOWN_TYPE"
        every { mockPrefs.getString("context_id", null) } returns "some_id"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 20L

        val result = repo.load()
        assertNull(result)
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

```
./gradlew test --tests "com.wolf.musicplayer.PlaybackSessionRepositoryTest"
```

Expected: all tests PASS. If any fail, confirm you replaced the whole file — not patched a partial change.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/data/repository/PlaybackSessionRepository.kt \
        app/src/test/java/com/wolf/musicplayer/PlaybackSessionRepositoryTest.kt
git commit -m "feat: add positionMs to PlaybackSessionRepository save/load"
```

---

## Task 3: Restore position in `AutoLibraryCallback.onPlaybackResumption`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

`load()` now returns `Triple<QueueContext, Long, Long>?`. The existing `val (ctx, _) = savedSession` destructuring needs a third ignored component, and the hardcoded `0L` in `MediaItemsWithStartPosition` becomes `savedSession?.third ?: 0L`.

- [ ] **Step 1: Update `onPlaybackResumption` in `AutoLibraryCallback.kt`**

Find this block in `onPlaybackResumption` (around line 160–195):

```kotlin
                    val rawSongs =
                        if (savedSession != null) {
                            val (ctx, _) = savedSession
```

Replace `val (ctx, _) = savedSession` with:

```kotlin
                            val (ctx, _, _) = savedSession
```

Then find:

```kotlin
                    val items = queue.map { it.toMediaItem("song:${it.id}") }
                    future.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, 0L))
```

Replace with:

```kotlin
                    val items = queue.map { it.toMediaItem("song:${it.id}") }
                    val startPositionMs = savedSession?.third ?: 0L
                    future.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs))
```

- [ ] **Step 2: Update `AutoLibraryCallbackTest` — fix all `load()` mocks to return `Triple`**

In `AutoLibraryCallbackTest.kt`, the `setup()` method has:
```kotlin
every { playbackSessionRepo.load() } returns null
```
That stays as-is (null is fine for Triple too).

Find every line of the form:
```kotlin
every { playbackSessionRepo.load() } returns Pair(...)
```
and replace each with `Triple(...)`. There are six such lines in the session-aware restore tests. Here are the exact replacements:

```kotlin
// Line ~525 — AllSongs session test
every { playbackSessionRepo.load() } returns Triple(QueueContext.AllSongs, 2L, 0L)

// Line ~543 — playlist session test
every { playbackSessionRepo.load() } returns Triple(QueueContext.PlaylistCtx(3L), 11L, 0L)

// Line ~557 — album session test
every { playbackSessionRepo.load() } returns Triple(QueueContext.AlbumCtx("Abbey Road"), 22L, 0L)

// Line ~571 — artist session test
every { playbackSessionRepo.load() } returns Triple(QueueContext.ArtistCtx("The Beatles"), 31L, 0L)

// Line ~589 — fallback to index 0 test
every { playbackSessionRepo.load() } returns Triple(QueueContext.AlbumCtx("Abbey Road"), 999L, 0L)

// Line ~603 — fallback to full library test
every { playbackSessionRepo.load() } returns Triple(QueueContext.PlaylistCtx(99L), 1L, 0L)
```

- [ ] **Step 3: Add a new test for position restoration**

Add this test to `AutoLibraryCallbackTest.kt` after the `onPlaybackResumption` section (before the closing `}`):

```kotlin
    @Test
    fun `onPlaybackResumption restores startPositionMs from saved session`() =
        runTest {
            val songs = listOf(song(1), song(2), song(3))
            coEvery { songRepo.getAll() } returns songs
            every { playbackSessionRepo.load() } returns Triple(QueueContext.AllSongs, 2L, 45_000L)

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(1, result.startIndex) // song:2 at index 1
            assertEquals(45_000L, result.startPositionMs)
        }
```

- [ ] **Step 4: Run tests to verify all pass**

```
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: all tests PASS including the new one.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "fix: restore playback position in onPlaybackResumption after service kill"
```

---

## Task 4: Save position on pause and periodically in `PlayerViewModel`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

Three changes in this task:
1. In `toggleShuffle()`, the session save should include the captured position (not default 0).
2. In `onIsPlayingChanged(false)`, save position when paused.
3. In the position-tracking loop, save every 10 seconds (20 × 500 ms ticks).

- [ ] **Step 1: Update `toggleShuffle()` to save the current position**

In `PlayerViewModel.kt`, inside `toggleShuffle()`, find this block (around line 303):

```kotlin
                activeQueue.getOrNull(newIndex)?.let { song ->
                    playbackSessionRepository.save(currentQueueContext, song.id)
                }
```

Replace with:

```kotlin
                activeQueue.getOrNull(newIndex)?.let { song ->
                    playbackSessionRepository.save(currentQueueContext, song.id, position)
                }
```

(`position` is already captured earlier in the same function as `val position = ctrl.currentPosition`.)

- [ ] **Step 2: Save position on pause**

In `playerListener`, find:

```kotlin
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startPositionTracking() else stopPositionTracking()
                }
```

Replace with:

```kotlin
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        startPositionTracking()
                    } else {
                        stopPositionTracking()
                        currentSongId?.let { id ->
                            viewModelScope.launch {
                                playbackSessionRepository.save(
                                    currentQueueContext,
                                    id,
                                    controller?.currentPosition ?: 0L,
                                )
                            }
                        }
                    }
                }
```

- [ ] **Step 3: Save position every 10 seconds during playback**

In `PlayerViewModel.kt`, add a `private var positionSaveCounter = 0` field alongside the other private vars (near `private var positionTracker: Job? = null`):

```kotlin
        private var positionSaveCounter = 0
```

Then in `startPositionTracking()`, find the inner loop:

```kotlin
                    while (isActive) {
                        _state.update { s ->
                            s.copy(
                                position = controller?.currentPosition ?: s.position,
                                duration = controller?.duration?.coerceAtLeast(0L) ?: s.duration,
                            )
                        }
                        delay(500)
                    }
```

Replace with:

```kotlin
                    positionSaveCounter = 0
                    while (isActive) {
                        val pos = controller?.currentPosition ?: _state.value.position
                        _state.update { s ->
                            s.copy(
                                position = pos,
                                duration = controller?.duration?.coerceAtLeast(0L) ?: s.duration,
                            )
                        }
                        positionSaveCounter++
                        if (positionSaveCounter >= 20) {
                            positionSaveCounter = 0
                            currentSongId?.let { id ->
                                playbackSessionRepository.save(currentQueueContext, id, pos)
                            }
                        }
                        delay(500)
                    }
```

- [ ] **Step 4: Run all unit tests to verify nothing is broken**

```
./gradlew test
```

Expected: all tests PASS. (PlayerViewModel has no unit tests — this change is verified by building.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "fix: save playback position on pause and every 10s during playback"
```

---

## Task 5: Extract `parseSongId` to a top-level function

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`

`parseSongId` is currently an extension function on `AutoLibraryCallback` marked `internal`. Converting it to a standalone top-level `internal fun` in the same file makes it importable from `PlayerViewModel` in the next task.

- [ ] **Step 1: Convert `parseSongId` to top-level**

In `AutoLibraryCallback.kt`, find:

```kotlin
        internal fun parseSongId(mediaId: String): Long? =
            when {
                mediaId.startsWith("song:") -> mediaId.removePrefix("song:").toLongOrNull()
                mediaId.contains("/song:") -> mediaId.substringAfterLast("/song:").toLongOrNull()
                else -> null
            }
```

Replace with (remove the enclosing class indentation — this becomes a top-level function in the file, outside the class body):

```kotlin
internal fun parseSongId(mediaId: String): Long? =
    when {
        mediaId.startsWith("song:") -> mediaId.removePrefix("song:").toLongOrNull()
        mediaId.contains("/song:") -> mediaId.substringAfterLast("/song:").toLongOrNull()
        else -> null
    }
```

Verify `parseSongId` is now at file scope (same indentation as `internal fun Song.toMediaItem`). The only call site inside the class (`onGetItem`) calls `parseSongId(mediaId)` without a receiver, which now resolves to the top-level function.

- [ ] **Step 2: Build to confirm no compilation errors**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests**

```
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: all tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt
git commit -m "refactor: extract parseSongId to top-level internal function"
```

---

## Task 6: Fix mediaId consistency in `PlayerViewModel`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

`playSongs()` and `toggleShuffle()` currently build `MediaItem`s with bare IDs (`song.id.toString()`). Changing to `"song:${song.id}"` makes them consistent with Auto callbacks and enables `parseSongId` to identify all queue items uniformly.

- [ ] **Step 1: Fix `playSongs()`**

In `PlayerViewModel.kt`, inside `playSongs()`, find:

```kotlin
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId(song.id.toString())
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
```

Replace with:

```kotlin
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId("song:${song.id}")
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
```

- [ ] **Step 2: Fix `toggleShuffle()`**

In `toggleShuffle()`, find the same pattern:

```kotlin
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId(song.id.toString())
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
```

Replace with:

```kotlin
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId("song:${song.id}")
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
```

- [ ] **Step 3: Build and run tests**

```
./gradlew assembleDebug && ./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

The `onSetMediaItems from in-app controller passes items through unchanged` test in `AutoLibraryCallbackTest` still passes — it tests the pass-through behaviour with arbitrary IDs, not the specific format PlayerViewModel uses.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "fix: use song: prefix for mediaIds in PlayerViewModel to match Auto callbacks"
```

---

## Task 7: Add external queue sync to `PlayerViewModel`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

When Android Auto changes the ExoPlayer queue (via `onPlaybackResumption` or `onSetMediaItems`), `onTimelineChanged` fires on the controller. A `pendingInternalQueueSet` flag distinguishes Auto-driven changes from our own `setMediaItems` calls. On external change, `rebuildActiveQueueFromController()` reads the controller's current items, looks up the matching `Song` objects by ID, and updates `activeQueue` so `syncStateFromController()` can find the current song.

- [ ] **Step 1: Add imports to `PlayerViewModel.kt`**

At the top of `PlayerViewModel.kt`, add these two imports alongside the existing Media3 imports:

```kotlin
import androidx.media3.common.Timeline
import com.wolf.musicplayer.service.parseSongId
```

- [ ] **Step 2: Add the `pendingInternalQueueSet` field**

In `PlayerViewModel`, add this field alongside the other private vars (near `private var positionTracker: Job? = null`):

```kotlin
        private var pendingInternalQueueSet = false
```

- [ ] **Step 3: Set the flag before `setMediaItems` in `playSongs()`**

Inside `playSongs()`, find:

```kotlin
                currentSongId = null
                ctrl.setMediaItems(items, activeIndex, 0L)
```

Replace with:

```kotlin
                currentSongId = null
                pendingInternalQueueSet = true
                ctrl.setMediaItems(items, activeIndex, 0L)
```

- [ ] **Step 4: Set the flag before `setMediaItems` in `toggleShuffle()`**

Inside `toggleShuffle()`, find:

```kotlin
                currentSongId = null
                activeQueue.getOrNull(newIndex)?.let { song ->
```

This block ends with `ctrl.setMediaItems(items, newIndex, position)`. Find:

```kotlin
                ctrl.setMediaItems(items, newIndex, position)
```

Replace with:

```kotlin
                pendingInternalQueueSet = true
                ctrl.setMediaItems(items, newIndex, position)
```

- [ ] **Step 5: Add `onTimelineChanged` to `playerListener`**

In `playerListener`, add the following after `onRepeatModeChanged`:

```kotlin
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    if (pendingInternalQueueSet) {
                        pendingInternalQueueSet = false
                        return
                    }
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        viewModelScope.launch { rebuildActiveQueueFromController() }
                    }
                }
```

- [ ] **Step 6: Add `rebuildActiveQueueFromController()`**

Add this private suspend function inside `PlayerViewModel`, after `syncStateFromController()`:

```kotlin
        private suspend fun rebuildActiveQueueFromController() {
            val ctrl = controller ?: return
            val count = ctrl.mediaItemCount
            if (count == 0) return
            val ids = (0 until count).mapNotNull { i -> parseSongId(ctrl.getMediaItemAt(i).mediaId) }
            if (ids.isEmpty()) return
            val songsById = songRepository.getByIds(ids).associateBy { it.id }
            val rebuilt = ids.mapNotNull { songsById[it] }
            if (rebuilt.isEmpty()) return
            activeQueue = rebuilt
            currentQueue = rebuilt
            playbackSessionRepository.load()?.let { triple -> currentQueueContext = triple.first }
            syncStateFromController()
        }
```

- [ ] **Step 7: Build to confirm no compilation errors**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Run all unit tests**

```
./gradlew test
```

Expected: all tests PASS

- [ ] **Step 9: Manual verification**

Verify the two bugs are fixed:
1. Play a song from the phone, lock the screen for a few minutes → song resumes near the saved position (within ~10 s)
2. Connect Android Auto, play a song, disconnect Auto, open the app → correct song shown as current with playback controls active
3. Connect Android Auto, play several songs, disconnect → app shows the correct last-played song

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "fix: rebuild PlayerViewModel activeQueue when Android Auto drives playback externally"
```
