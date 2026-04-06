# Design: Albums/Artists Detail Screens & Seekbar Fix

**Date:** 2026-04-06  
**Status:** Approved

## Summary

Three related improvements to the Music Player app:
1. Fix the frozen seekbar in `PlayerScreen` so it reflects real-time playback position and allows seeking.
2. Make the Albums tab functional by navigating to a detail screen listing songs for the selected album.
3. Make the Artists tab functional by navigating to a detail screen listing songs for the selected artist.

---

## Part 1: Seekbar Position Update

**Problem:** `PlayerViewModel` never updates `state.position` during playback. The `Slider` in `PlayerScreen` is wired to `state.position` and `seekTo()` is already connected, but position stays at 0.

**Solution:** Add a `positionTracker: Job?` field to `PlayerViewModel`. A 500 ms polling coroutine writes `controller.currentPosition` into `_state`.

**Lifecycle:**
- Start polling: inside `onIsPlayingChanged(isPlaying = true)`
- Stop polling: inside `onIsPlayingChanged(isPlaying = false)` and `onPlaybackStateChanged(STATE_ENDED)`
- On `seekTo()`: immediately sync position via `_state.update { it.copy(position = positionMs) }` before the next tick

**No changes needed** to `PlayerScreen.kt` — the existing `Slider` and `onValueChange` lambda already call `viewModel.seekTo()`.

---

## Part 2: Navigation — New Routes

Two new named routes are added to the existing `NavHost` in `AppNavigation.kt`:

| Route | Screen |
|---|---|
| `artists/{artistName}` | `ArtistDetailScreen` |
| `albums/{albumName}` | `AlbumDetailScreen` |

`ArtistScreen` and `AlbumScreen` each receive a new `onItemClick: (String) -> Unit` parameter. The `NavHost` provides a lambda that navigates to the respective detail route. Artist and album names are encoded with `Uri.encode()` before embedding in the route string to handle special characters (e.g. slashes in `AC/DC`).

Both detail screens receive `playerViewModel: PlayerViewModel` so they can call `playerViewModel.playSongs(songs, index)` and navigate to `Screen.Player.route` on song tap — identical to the pattern in `LibraryScreen`.

---

## Part 3: Detail Screens

`ArtistDetailScreen` and `AlbumDetailScreen` follow the same structure as `LibraryScreen`.

**ViewModel:**
- `ArtistDetailViewModel`: injects `SongRepository` and `SavedStateHandle`. Exposes `StateFlow<List<Song>>` from `repo.observeByArtist(artistName)`.
- `AlbumDetailViewModel`: same pattern using `repo.observeByAlbum(albumName)`.
- The name argument is read from `SavedStateHandle["artistName"]` / `SavedStateHandle["albumName"]` — standard Hilt + Navigation Compose pattern.

**UI:**
- `TopAppBar` showing the album/artist name with a back button (`navController.popBackStack()`)
- `LazyColumn` of `SongRow` items
- Tapping a song calls `playerViewModel.playSongs(songs, index)` then navigates to `Screen.Player.route`

**Files to create:**
- `ui/screen/artist/ArtistDetailViewModel.kt`
- `ui/screen/artist/ArtistDetailScreen.kt`
- `ui/screen/album/AlbumDetailViewModel.kt`
- `ui/screen/album/AlbumDetailScreen.kt`

**Files to modify:**
- `ui/screen/artist/ArtistScreen.kt` — add `onItemClick` parameter, wire `ListItem` onClick
- `ui/screen/album/AlbumScreen.kt` — add `onItemClick` parameter, wire `ListItem` onClick
- `ui/navigation/AppNavigation.kt` — add two new routes, pass `playerViewModel` and nav callbacks
- `ui/screen/player/PlayerViewModel.kt` — add position polling coroutine

---

## Architecture Notes

- Layer boundaries are preserved: ViewModels call Repositories, UI calls ViewModels only.
- No new data layer changes needed — `observeByArtist()` and `observeByAlbum()` already exist in `SongRepository`.
- No new database migrations required.

---

## Out of Scope

- Album art display in detail screens (no art URL available for local songs currently)
- Sort order controls within detail screens
- Server-sourced songs in Albums/Artists (depends on future ServerDataSource implementation)
