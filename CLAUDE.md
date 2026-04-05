# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All commands run via Gradle wrapper from the project root:

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.wolf.musicplayer.WeightedShufflerTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

## Pre-Commit Quality Checks

Run once after cloning to activate:
```bash
bash scripts/install-hooks.sh
```

Fires automatically on `git commit` when `.kt` files are staged. Runs: ktlint → unit tests → AI code review (Claude). Errors block the commit; warnings allow it.

To bypass in an emergency: `git commit --no-verify`

## Architecture

Single-module Android app using **MVVM + Repository pattern**. Strict layer separation:

```
UI (Jetpack Compose)
  └── ViewModel (StateFlow)
        └── Repository
              ├── LocalDataSource    (MediaStore + Room)
              ├── ServerDataSource   (Retrofit → Subsonic/Jellyfin)
              └── CacheDataSource    (Room + FileSystem)
```

**Rule:** UI only touches ViewModels. ViewModels only call Repositories. Repositories own DataSources.

## Key Tech Stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose (5-tab bottom nav) |
| Playback | Media3 / ExoPlayer via `MusicService` (MediaSessionService) |
| DI | Hilt (KSP) |
| Database | Room |
| Images | Coil |
| Async | Coroutines + StateFlow |

## Playback Architecture

`MusicService` (foreground `MediaSessionService`) owns the `ExoPlayer` instance injected by Hilt (`PlayerModule`). `PlayerViewModel` connects via `MediaController` — it never holds the player directly. All playback state is synced from `Player.Listener` callbacks into `PlayerState` (StateFlow).

Play weight tracking lives in `PlayerViewModel`: skip detection uses `MEDIA_ITEM_TRANSITION_REASON_SEEK`, full-play detection fires when `STATE_ENDED` at ≥ 80% position.

## Weighted Shuffle Algorithm

`WeightedShuffler` in `domain/` does a weighted random draw proportional to each song's `weight` float.

`PlayWeightRepository` manages the weights:
- Default weight: `1.0`
- On manual skip: `weight = max(0.1, weight × 0.75)`
- On full play (≥80% played): `weight = min(1.0, weight × 1.1)`
- Natural song end is NOT a skip

## Navigation

5 bottom-nav tabs defined in `AppNavigation.kt` as `Screen` sealed class: Library, Playlists, Artists, Albums, Player. `PlayerViewModel` is hoisted at the `AppNavigation` level and shared across screens so playback state persists across tab switches.

## Data Model (Room)

Core entities: `Song`, `PlayWeight`, `Playlist`, `PlaylistSong`, `Server`.
- `Song.sourceType`: `LOCAL | SERVER`
- `Song.localPath` / `cachedPath`: nullable paths for playback URI resolution
- `Server` stores encrypted passwords (Android Keystore — never plaintext in Room)

## Planned Features (Not Yet Implemented)

From the design spec (`docs/superpowers/specs/`):
- Subsonic/Jellyfin server integration (`ServerDataSource`, `ServerEntity` DB table exists)
- Offline caching via WorkManager
- `ServerListScreen`, `ServerEditScreen`, `SongDetailScreen`, `SettingsScreen`, `OfflineScreen`
- Lock-screen / notification controls (MediaSession foundation is in place)
- Library filter chips: All / Local / [ServerName]
