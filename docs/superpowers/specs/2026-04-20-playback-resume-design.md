# Design Spec: Playback Session Resume

**Date:** 2026-04-20  
**Status:** Approved

## Problem

`onPlaybackResumption` (called by Android Auto on cold-start resume) always loads the full song library from index 0, ignoring what was last playing. If the user had been playing a playlist, album, or artist, that context is lost.

## Goal

When Android Auto (or any media controller) resumes playback, the app restores:
- The last queue context (all songs, playlist, album, or artist)
- The last playing song (from the beginning — no position seek)
- Shuffle is re-applied fresh from the context (exact shuffle order not preserved)

## Approach

SharedPreferences for session persistence — lightweight, no new dependency, no DB migration.

---

## Components

### 1. `QueueContext` (sealed class — `domain/model/`)

```kotlin
sealed class QueueContext {
    object AllSongs : QueueContext()
    data class PlaylistCtx(val id: Long) : QueueContext()
    data class AlbumCtx(val name: String) : QueueContext()
    data class ArtistCtx(val name: String) : QueueContext()
}
```

### 2. `PlaybackSessionRepository` (`data/repository/`)

Wraps SharedPreferences. Persists and loads the last session.

**Saved keys:**

| Key | Type | Values |
|---|---|---|
| `context_type` | String | `"ALL"`, `"PLAYLIST"`, `"ALBUM"`, `"ARTIST"` |
| `context_id` | String | playlist ID (as string), album name, or artist name |
| `last_song_id` | Long | ID of the last playing song |

**Interface:**
```kotlin
fun save(context: QueueContext, songId: Long)
fun load(): Pair<QueueContext, Long>?  // null if no session saved
```

Injected via Hilt as `@Singleton`.

### 3. `PlayerViewModel` — updated `playSongs()`

Signature change:
```kotlin
fun playSongs(songs: List<Song>, startIndex: Int = 0, context: QueueContext = QueueContext.AllSongs)
```

After building the queue, before calling `ctrl.setMediaItems(...)`:
```kotlin
val songId = activeQueue.getOrNull(activeIndex)?.id ?: return@launch
playbackSessionRepository.save(context, songId)
```

Also saves on `onMediaItemTransition` to keep session in sync as songs advance:
```kotlin
currentSongId?.let { id ->
    val ctx = currentQueueContext  // new field on PlayerViewModel
    playbackSessionRepository.save(ctx, id)
}
```

New private field: `private var currentQueueContext: QueueContext = QueueContext.AllSongs`

### 4. `AutoLibraryCallback` — updated `onPlaybackResumption()`

Injected: `PlaybackSessionRepository`

Logic:
1. Load session → `(context, lastSongId)` — if null, fall back to full library, index 0
2. Load songs from correct source:
   - `AllSongs` → `songRepository.getAll()`
   - `PlaylistCtx(id)` → `playlistRepository.getSongsForPlaylist(id)`
   - `AlbumCtx(name)` → `songRepository.getByAlbum(name)`
   - `ArtistCtx(name)` → `songRepository.getByArtist(name)`
3. If shuffle enabled → permute with weights (same as today)
4. `startIndex` = `queue.indexOfFirst { it.id == lastSongId }.coerceAtLeast(0)`
5. `startPositionMs` = `0L`
6. If song/queue not found → fall back to full library, index 0

### 5. `AppNavigation.kt` — updated `playSongs` call sites

All 5 call sites updated to pass `QueueContext`:

| Screen | Callback | Context passed |
|---|---|---|
| `LibraryScreen` | `onSongClick` | `QueueContext.AllSongs` |
| `PlaylistScreen` | `onPlaySongs` | `QueueContext.PlaylistCtx(playlistId)` — callback signature updated to include `playlistId` |
| `PlaylistDetailScreen` | `onSongClick` | `QueueContext.PlaylistCtx(playlistId)` — from nav argument |
| `ArtistDetailScreen` | `onSongClick` | `QueueContext.ArtistCtx(artistName)` — from nav argument |
| `AlbumDetailScreen` | `onSongClick` | `QueueContext.AlbumCtx(albumName)` — from nav argument |

`PlaylistScreen.onPlaySongs` callback signature changes from `(List<Song>) -> Unit` to `(List<Song>, Long) -> Unit` where the Long is the playlist ID.

---

## Data Flow

```
User plays song in AlbumDetailScreen
  → playSongs(songs, idx, AlbumCtx("Abbey Road"))
    → PlayerViewModel saves session: type=ALBUM, id="Abbey Road", songId=42
  
Car connects → Android Auto cold start
  → onPlaybackResumption called
    → load session: ALBUM / "Abbey Road" / songId=42
    → songRepository.getByAlbum("Abbey Road") → 12 songs
    → find index of song 42 → index 5
    → return MediaItemsWithStartPosition(items, 5, 0L)
```

---

## Edge Cases

| Case | Handling |
|---|---|
| No session saved (first launch) | Fallback: full library, index 0 |
| Last song deleted from library | `indexOfFirst` returns -1 → coerced to 0 |
| Playlist deleted | `getSongsForPlaylist` returns empty → fallback to full library |
| Album/Artist renamed or empty | `getByAlbum/Artist` returns empty → fallback to full library |

---

## Out of Scope

- Restoring exact playback position within a song
- Persisting shuffle queue order
- Server/remote songs (future feature)
