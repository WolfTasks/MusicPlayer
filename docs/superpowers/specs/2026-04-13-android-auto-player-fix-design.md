# Android Auto + Standard Player Fix — Design Spec

**Date:** 2026-04-13
**Status:** Approved

## Problem

The Android Auto integration introduced in the previous session broke two things:

1. **Android Auto invisible** — The app does not appear on the head unit at all.
2. **Standard player silent** — Tapping any song in the regular phone UI starts nothing; the player stays idle.

## Root Causes

### Bug 1 — Manifest: meta-data in wrong element

`com.google.android.gms.car.application` was placed inside the `<service>` tag.
Android Auto's discovery mechanism looks for this meta-data on the `<application>` element. When it is absent there, the app is never registered as a media source and never shown.

**Affected file:** `app/src/main/AndroidManifest.xml`

### Bug 2 — `onSetMediaItems` intercepts in-app controller

`AutoLibraryCallback.onSetMediaItems()` overrides every `setMediaItems` call, including those from `PlayerViewModel` (via its internal `MediaController`). The ViewModel sends `MediaItem` objects whose `mediaId` is the raw database ID (e.g. `"123"`). The `resolveQueue()` function only recognises prefixed formats (`"song:123"`, `"album:..."`, etc.), so it returns an empty list → ExoPlayer receives nothing → silence.

**Affected file:** `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`

## Design

### Fix 1 — Move meta-data to `<application>`

Move the `<meta-data>` block out of `<service>` and place it as a direct child of `<application>`, before the `<service>` declaration. No other manifest changes.

```xml
<application ...>
    <meta-data
        android:name="com.google.android.gms.car.application"
        android:resource="@xml/automotive_app_desc" />

    <service android:name=".service.MusicService" ...>
        <intent-filter>
            <action android:name="androidx.media3.session.MediaLibraryService" />
        </intent-filter>
        <intent-filter>
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
        <!-- meta-data removed from here -->
    </service>
</application>
```

`automotive_app_desc.xml` is unchanged.

### Fix 2 — Pass through in-app `setMediaItems` calls

Add an early-return guard at the top of `onSetMediaItems` that compares the calling controller's package name against the session's own package name. When they match (same-app `MediaController` from `PlayerViewModel`), return the items unchanged. Only proceed to queue resolution for external controllers (Android Auto).

```kotlin
override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    // In-app controller (PlayerViewModel): queue already built; pass through unchanged.
    if (controller.packageName == mediaSession.token.packageName) {
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        )
    }
    // Android Auto: resolve the single browse-tree mediaId into a full queue.
    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
    callbackScope.launch {
        try {
            val requestedId = mediaItems.firstOrNull()?.mediaId
            if (requestedId == null) {
                future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                return@launch
            }
            val (songs, requestedSongId) = resolveQueue(requestedId)
            if (songs.isEmpty() || requestedSongId == null) {
                future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                return@launch
            }
            val queue =
                if (shuffleStateHolder.shuffleEnabled) {
                    val weights = songs.map { playWeightRepository.getWeight(it.id) }
                    shuffler.permute(songs, weights)
                } else {
                    songs
                }
            val idx = queue.indexOfFirst { it.id == requestedSongId }.coerceAtLeast(0)
            val resolvedItems = queue.map { it.toMediaItem("song:${it.id}") }
            future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, idx, startPositionMs))
        } catch (e: Exception) {
            future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }
    }
    return future
}
```

**Why `packageName` comparison?**
- `PlayerViewModel` connects via `MediaController` from within the same process → `controller.packageName == "com.wolf.musicplayer"`
- Android Auto connects from an external package → different `packageName`
- `mediaSession.token.packageName` is resolved at runtime; no hardcoded string needed

## Files Changed

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Move `<meta-data>` from `<service>` to `<application>` |
| `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt` | Add in-app pass-through guard in `onSetMediaItems` |

## Testing

- Phone UI: tap any song → playback starts normally
- Shuffle toggle → queue reshuffles; playback continues at correct song
- Android Auto (DHU or real car): app appears in media list
- Android Auto: browse Songs/Albums/Artists/Playlists → tap a song → playback starts; skip next/prev works
