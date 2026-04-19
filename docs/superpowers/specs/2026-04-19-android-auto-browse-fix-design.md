# Android Auto Browse & Resumption Fix — Design Spec

**Date:** 2026-04-19  
**Status:** Approved

## Problem

Android Auto can open the app and shows the four root categories (Songs, Playlists, Albums, Artists), but tapping any category shows a loading spinner and then nothing. Car controls have no effect.

**Root cause:** `AutoLibraryCallback` does not override `onSubscribe`. Media3's default implementation returns `RESULT_ERROR_NOT_SUPPORTED`. Android Auto subscribes to a browse node before fetching its children; a rejected subscription means children are never delivered. The spinner appears while the car waits for a subscription-success notification that never arrives.

Secondary gap: `onPlaybackResumption` is not implemented, so the head unit's "Resume" button (cold-start) silently fails.

## Scope

One file changed: `service/AutoLibraryCallback.kt`. Three new method overrides, no new dependencies, no schema changes.

## Changes

### 1. `onSubscribe` + `onUnsubscribe`

Accept all subscription requests unconditionally. Once the subscription succeeds, Android Auto calls `onGetChildren` — which already works correctly.

```kotlin
override fun onSubscribe(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    params: LibraryParams?,
): ListenableFuture<LibraryResult<Void>> =
    Futures.immediateFuture(LibraryResult.ofVoid())

override fun onUnsubscribe(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
): ListenableFuture<LibraryResult<Void>> =
    Futures.immediateFuture(LibraryResult.ofVoid())
```

### 2. `onPlaybackResumption`

Called when the head unit cold-starts the app via a "Resume" button and there is no active queue. Returns all songs in library order (or weighted-shuffled if shuffle is enabled), starting at index 0. If the library is empty, returns an empty list — the head unit shows "no content" gracefully.

```kotlin
override fun onPlaybackResumption(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
    callbackScope.launch {
        try {
            val songs = songRepository.getAll()
            val queue = if (shuffleStateHolder.shuffleEnabled) {
                val weights = songs.map { playWeightRepository.getWeight(it.id) }
                shuffler.permute(songs, weights)
            } else songs
            val items = queue.map { it.toMediaItem("song:${it.id}") }
            future.set(MediaSession.MediaItemsWithStartPosition(items, 0, 0L))
        } catch (e: Exception) {
            future.setException(e)
        }
    }
    return future
}
```

## Car Controls

No changes needed. Once a song is playing, ExoPlayer handles play/pause/next/previous through the existing `MediaLibrarySession` automatically.

## Testing

- **Unit tests:** Add three test cases to `AutoLibraryCallbackTest`:
  1. `onSubscribe` returns success
  2. `onUnsubscribe` returns success
  3. `onPlaybackResumption` returns all songs with correct start index (0) and respects shuffle flag
- **Manual verification:** Use Android Desktop Head Unit (DHU) — tap a category, confirm songs appear; use car Resume button from cold start, confirm queue loads.
