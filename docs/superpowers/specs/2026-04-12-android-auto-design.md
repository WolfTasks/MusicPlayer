# Android Auto Integration — Design Spec

**Date:** 2026-04-12  
**Status:** Approved

## Overview

When the phone connects to a car with Android Auto, the music player shall be controllable via the car's controls and play audio through the car's speakers. The car displays a full browse tree (Songs, Playlists, Albums, Artists). The existing Weighted Shuffle and skip-tracking logic applies unchanged in the car context.

---

## Architecture

`MusicService` is upgraded from `MediaSessionService` to `MediaLibraryService`. Browse logic is extracted into a separate `AutoLibraryCallback` class injected via Hilt, keeping `MusicService` lean.

```
MusicService (MediaLibraryService)
  ├── ExoPlayer                   (via Hilt, unchanged)
  ├── MediaLibrarySession
  │     └── AutoLibraryCallback   (new, Hilt-injected)
  │           ├── SongRepository
  │           └── PlaylistRepository
  └── Intent-Filter: MediaLibraryService

PlayerViewModel
  └── MediaController → MusicService   (unchanged)
```

`MediaLibrarySession` is a subclass of `MediaSession`, so `PlayerViewModel`'s `MediaController` connection requires no changes.

---

## Browse Tree

Android Auto renders a hierarchical content tree:

```
ROOT
├── Songs       — all local songs, alphabetical
├── Playlists   — all playlists; tap → songs of that playlist
├── Albums      — all albums; tap → songs of that album
└── Artists     — all artists; tap → songs of that artist
```

### `AutoLibraryCallback` methods

| Method | Returns |
|---|---|
| `onGetLibraryRoot` | Root node (not searchable, no offline hint) |
| `onGetChildren(parentId)` | 4 category nodes, or songs within a category |
| `onGetItem(mediaId)` | Single song by ID |

Each `MediaItem` carries `MediaMetadata` with title, artist, album, and album-art URI (local path where available).

- Category nodes: `isBrowsable = true`, `isPlayable = false`
- Song nodes: `isBrowsable = false`, `isPlayable = true`

### Playback on tap

When the driver taps a song, Android Auto calls `onSetMediaItems` (Media3's replacement for the legacy `onPlayFromMediaId`). `AutoLibraryCallback` overrides `onSetMediaItems` and `onAddMediaItems` on the `MediaLibrarySession.Callback`. It receives the requested `MediaItem`, resolves the full song list for the current browse level from the repository, sets the queue on the player, and starts playback. The current shuffle state is preserved — if shuffle was on, the queue is permuted using `WeightedShuffler.permute`.

---

## Skip & Play-Weight Tracking

No additional tracking code is needed. `PlayerViewModel`'s `Player.Listener` is registered on the `MediaController` and fires on all playback events regardless of input source (phone screen or car controls):

- **Car skip (steering wheel next):** `MEDIA_ITEM_TRANSITION_REASON_SEEK` → `PlayWeightRepository.recordSkip`
- **Full play (≥80%):** `STATE_ENDED` at ≥80% position → `PlayWeightRepository.recordFullPlay`

---

## Shuffle Behaviour

When the driver starts playback from the car:
- The current `shuffleEnabled` flag from `PlayerViewModel` is read.
- If `true`: queue is permuted via `WeightedShuffler.permute` with current weights.
- If `false`: queue is played in natural order (alphabetical / playlist / album order).

---

## New Files

| File | Purpose |
|---|---|
| `service/AutoLibraryCallback.kt` | `MediaLibrarySession.Callback` implementation — browse tree + queue resolution on item selection |
| `res/xml/automotive_app_desc.xml` | Declares app as a media app for Android Auto |

## Changed Files

| File | Change |
|---|---|
| `service/MusicService.kt` | Extend `MediaLibraryService`; build `MediaLibrarySession` with injected `AutoLibraryCallback` |
| `AndroidManifest.xml` | Add `MediaLibraryService` intent filter; add `com.google.android.gms.car.application` meta-data pointing to `automotive_app_desc` |
| `di/AppModule.kt` or new `di/AutoModule.kt` | Provide `AutoLibraryCallback` via Hilt |

---

## Manifest Changes

```xml
<!-- In <service android:name=".service.MusicService"> -->
<intent-filter>
    <action android:name="androidx.media3.session.MediaLibraryService" />
</intent-filter>
<intent-filter>
    <action android:name="android.media.browse.MediaBrowserService" />
</intent-filter>
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

---

## `automotive_app_desc.xml`

```xml
<automotiveApp>
    <uses name="media" />
</automotiveApp>
```

---

## Dependencies

No new Gradle dependencies required. `androidx.media3:media3-session` (already present) covers `MediaLibraryService` and `MediaLibrarySession`.

---

## Testing

- **Unit tests for `AutoLibraryCallback`**: mock repositories, assert correct `MediaItem` structure for each `parentId`.
- **Manual test**: connect phone to Android Auto (Desktop Head Unit emulator or real car), verify browse tree shows all categories and songs play correctly.
- **Tracking test**: skip a song via car controls, verify weight decreases in Room DB.
