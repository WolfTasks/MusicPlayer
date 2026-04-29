# Design: Android Auto Queue Sync & Song Restart Fix

## Problems

**Bug 1 — Android Auto: no song selected after leaving Auto**

After Android Auto drives playback (via `onPlaybackResumption` on reconnect, or `onSetMediaItems` when the user taps a song in Auto), the app UI shows no current song when returning to the phone. Intermittently, Auto itself also can't play songs immediately after reconnecting.

**Bug 2 — Song restarts from the beginning after phone is locked for a while**

When the phone is locked and left idle, the OS kills `MusicService` under memory pressure. When the service revives, `onPlaybackResumption` always returns `startPositionMs = 0L`. The song restarts from the beginning.

---

## Root Causes

### Bug 1

`PlayerViewModel` owns `activeQueue` as an in-memory list. It is only updated when the ViewModel itself calls `playSongs()` or `toggleShuffle()`. When Android Auto calls `onPlaybackResumption` or `onSetMediaItems`, ExoPlayer's queue is updated service-side but `activeQueue` in the ViewModel is never told. `syncStateFromController()` does `activeQueue.getOrNull(ctrl.currentMediaItemIndex)` → `null` → `currentSong = null`.

### Bug 2

`PlaybackSessionRepository.save()` takes no position argument, and `onPlaybackResumption` always passes `startPositionMs = 0L`. The service is a valid foreground service per the manifest, but Android (especially aggressive OEMs) can still kill it after extended background time. The right defence is graceful save/restore, not fighting the OS.

### Secondary issue (prerequisite fix)

`playSongs()` and `toggleShuffle()` build `MediaItem`s with bare song IDs (e.g. `"123"`), while Auto callbacks (`onPlaybackResumption`, `onSetMediaItems`) use the `"song:123"` prefix. All callers should use the same format so the queue-sync logic can parse IDs consistently.

---

## Design

### 1. MediaId consistency

Change `playSongs()` and `toggleShuffle()` in `PlayerViewModel` to build `MediaItem`s with `mediaId = "song:${song.id}"` instead of `song.id.toString()`. No other callers need updating — `parseSongId` already handles both formats.

### 2. Position save/restore

**`PlaybackSessionRepository`**

Add `positionMs: Long` parameter to `save()`. Store it under key `"position_ms"` in `SharedPreferences`. Update `load()` to return `Triple<QueueContext, Long, Long>` (context, songId, positionMs). Missing key returns `0L` (safe default).

**Where to save position:**

- On pause: `onIsPlayingChanged(false)` in `PlayerViewModel.playerListener` → `playbackSessionRepository.save(currentQueueContext, songId, ctrl.currentPosition)`
- Every 10 seconds during playback: a `saveCounter` in the existing 500 ms position-tracking loop increments each tick; at 20 ticks it saves and resets. No extra coroutine.

**Where to restore:**

`onPlaybackResumption` in `AutoLibraryCallback` uses `savedSession.third` (positionMs) instead of the hardcoded `0L` when setting `MediaItemsWithStartPosition`.

Existing `save()` call sites:
- **Queue start** (`playSongs()`): passes `0L` — correct, new playback always starts from the beginning
- **Song transition** (`onMediaItemTransition`): passes `0L` — correct, the new song starts at 0
- **Shuffle toggle** (`toggleShuffle()`): passes the captured `position` (i.e. `ctrl.currentPosition` at toggle time) — the queue is replaced but playback resumes mid-song, so the position must be preserved here

### 3. External queue sync

**`parseSongId` extraction**

Convert the existing `internal fun AutoLibraryCallback.parseSongId(mediaId: String): Long?` from an extension function on the class to a standalone top-level `internal fun` in `AutoLibraryCallback.kt`. Both `AutoLibraryCallback` and `PlayerViewModel` are in the same module so `internal` visibility is sufficient.

**Flag-based change detection in `PlayerViewModel`**

Add `private var pendingInternalQueueSet = false`. Set it to `true` immediately before every `ctrl.setMediaItems()` call in `playSongs()` and `toggleShuffle()`. Both execute on the main thread; `onTimelineChanged` also fires on main — no race.

**New listener callback**

```kotlin
override fun onTimelineChanged(timeline: Timeline, reason: Int) {
    if (pendingInternalQueueSet) {
        pendingInternalQueueSet = false
        return
    }
    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
        viewModelScope.launch { rebuildActiveQueueFromController() }
    }
}
```

**`rebuildActiveQueueFromController()`**

1. Read `controller.mediaItemCount` items; extract song IDs via `parseSongId(item.mediaId)`
2. Bulk-load songs via `songRepository.getByIds(ids)` (new method — one DB query)
3. Reconstruct `activeQueue` in the same order as the controller's items (preserve Auto's ordering)
4. Set `currentQueue = activeQueue`
5. Restore `currentQueueContext` from `playbackSessionRepository.load()` (best-effort; falls back to `AllSongs`)
6. Call `syncStateFromController()` to push `currentSong` into `_state`

### 4. `SongRepository.getByIds` / `SongDao.getByIds`

Add to `SongDao`:
```kotlin
@Query("SELECT * FROM songs WHERE id IN (:ids)")
suspend fun getByIds(ids: List<Long>): List<SongEntity>
```

Add to `SongRepository`:
```kotlin
suspend fun getByIds(ids: List<Long>): List<Song> =
    dao.getByIds(ids).map(SongEntity::toDomain)
```

---

## Files Changed

| File | Change |
|---|---|
| `data/repository/PlaybackSessionRepository.kt` | Add `positionMs` to `save()` / `load()` |
| `data/db/dao/SongDao.kt` | Add `getByIds` query |
| `data/repository/SongRepository.kt` | Add `getByIds` method |
| `service/AutoLibraryCallback.kt` | Extract `parseSongId` to top-level; restore position in `onPlaybackResumption` |
| `ui/screen/player/PlayerViewModel.kt` | Fix mediaIds; add flag + `onTimelineChanged`; save position on pause + periodically; `rebuildActiveQueueFromController()` |

---

## Testing

- Play a song, lock the phone for several minutes, unlock → song resumes at (approximately) the saved position, not from 0
- Play via Android Auto, disconnect Auto, open app → correct song shown as current
- Play via Android Auto, disconnect, reconnect, tap a different song in Auto → plays correctly; open app → correct song shown
- Toggle shuffle while playing → song and position preserved (existing behaviour unchanged)
- `playSongs()` from library/album/artist/playlist → correct song plays from start (existing behaviour unchanged)
