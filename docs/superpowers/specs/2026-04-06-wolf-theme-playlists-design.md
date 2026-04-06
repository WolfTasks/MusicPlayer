# Design Spec: Wolf Theme + Playlist Management

**Date:** 2026-04-06
**Status:** Approved

---

## Overview

Three independent feature areas:

1. Replace the current Spotify-green color scheme with a Wolf Silber/Frost palette derived from the app icon.
2. Add full playlist management (create, rename, delete, detail view with song list and playback).
3. Add "Add to Playlist" action on the PlayerScreen via a Bottom Sheet picker.

---

## 1. Color Scheme — Wolf Silber/Frost

Replace all green references in `Color.kt` and `Theme.kt`. No other files need touching — all UI consumes colors via `MaterialTheme.colorScheme`.

| Role | Old | New |
|---|---|---|
| Background | `#121212` | `#0D1830` (icon navy) |
| Surface | `#1E1E1E` | `#1A2744` |
| SurfaceVariant | `#2A2A2A` | `#243559` |
| Primary (accent) | `#1DB954` (green) | `#C8D8E8` (silver-frost) |
| PrimaryContainer | `#158a3e` | `#8099B8` (muted blue-silver) |
| OnPrimary | `#FFFFFF` | `#0D1830` (dark, for contrast on light accent) |
| OnBackground / OnSurface | `#FFFFFF` | `#FFFFFF` (unchanged) |
| OnSurfaceVariant | `#B3B3B3` | `#8099B8` (blue-grey secondary text) |
| Outline | `#535353` | `#243559` |

**Files changed:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`

---

## 2. Playlist Management

### 2a. PlaylistScreen (extended)

Current state: FAB to create, list of playlists, no further interaction.

Changes:
- Each playlist row shows a **play icon** (▶) on the right — tapping it plays the full playlist immediately via `PlayerViewModel.playSongs()`.
- **Long-press** on a playlist row opens a `DropdownMenu` with two items:
  - **Umbenennen** — opens an `AlertDialog` with a pre-filled text field; calls `viewModel.renamePlaylist(id, newName)`.
  - **Löschen** — opens a confirmation `AlertDialog`; calls `viewModel.deletePlaylist(playlist)`.
- Tapping the playlist name/row (not the play icon, not long-press) navigates to `PlaylistDetailScreen`.
- `PlaylistViewModel` gains `renamePlaylist(id, Long, name: String)` — delegates to existing `repo.rename()`.

**Navigation:** `AppNavigation` adds route `"playlists/{playlistId}"` with a `Long` nav argument.

### 2b. PlaylistDetailScreen (new)

**Route:** `playlists/{playlistId}`

**ViewModel:** `PlaylistDetailViewModel` — receives `playlistId` from `SavedStateHandle`. Exposes:
- `playlist: StateFlow<Playlist?>` — name for the top bar.
- `songs: StateFlow<List<Song>>` — from `repo.observeSongsForPlaylist()`.
- `removeSong(songId: Long)` — calls `repo.removeSong(playlistId, songId)`.

**UI:**
- `TopAppBar` with playlist name and back arrow.
- "Alle abspielen" chip/button in the top bar — calls `playerViewModel.playSongs(songs, 0)` then navigates to Player tab.
- `LazyColumn` of `SongRow` items.
- Each row is **swipe-to-dismiss** (`SwipeToDismissBox`) — on dismiss calls `viewModel.removeSong(song.id)`.
- Tapping a row plays the playlist from that song's position.

**PlayerViewModel access:** `PlaylistDetailScreen` receives the hoisted `PlayerViewModel` from `AppNavigation` (same pattern as `PlayerScreen`).

---

## 3. "Add to Playlist" on PlayerScreen

### PlayerScreen

A new icon button is added to the Shuffle/Repeat row (between them). Icon: `Icons.Default.PlaylistAdd`. Tapping it sets `showAddToPlaylist = true` in local state.

### Bottom Sheet (`AddToPlaylistSheet`)

A `ModalBottomSheet` composable, extracted to `ui/component/AddToPlaylistSheet.kt`.

- **Title:** "Zu Wiedergabeliste hinzufügen"
- **List:** all playlists from `playerViewModel.playlists` (new StateFlow).
- Each row shows a checkmark if the current song is already in that playlist (derived from `playerViewModel.songPlaylists`).
- Tapping a playlist row calls `playerViewModel.addSongToPlaylist(song, playlist)` and dismisses.
- A final row **"+ Neue Liste..."** opens a small `AlertDialog` to enter a name, creates the playlist, then immediately adds the song.

### PlayerViewModel extensions

- `val playlists: StateFlow<List<Playlist>>` — from `PlaylistRepository.observeAll()`.
- `val songPlaylists: StateFlow<Set<Long>>` — playlist IDs containing the current song (derived reactively from `currentSong` + `PlaylistDao`).
- `fun addSongToPlaylist(song: Song, playlist: Playlist)` — calls `playlistRepo.addSong(playlistId, songId, position = nextPosition)`.
- `fun createPlaylistAndAddSong(name: String, song: Song)` — creates playlist then adds song.

`PlayerViewModel` gets `PlaylistRepository` injected (it already lives in the Hilt graph).

---

## Data / Repository

No schema changes. One new DAO query is needed:

```kotlin
@Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
fun observePlaylistIdsForSong(songId: Long): Flow<List<Long>>
```

Added to `PlaylistDao` and exposed via `PlaylistRepository.observePlaylistIdsForSong(songId)`.

Existing methods all sufficient:
- `PlaylistDao.observeSongsForPlaylist` ✓
- `PlaylistRepository.addSong(playlistId, songId, position)` — position = current song count in playlist (append) ✓
- `PlaylistRepository.removeSong` / `rename` / `delete` ✓

### PlaylistViewModel — play from list screen

`PlaylistViewModel` gets `SongRepository` injected. New function:

```kotlin
fun playSongsForPlaylist(playlistId: Long, playerViewModel: PlayerViewModel) {
    viewModelScope.launch {
        val songs = repo.getSongsForPlaylist(playlistId) // suspend, one-shot
        playerViewModel.playSongs(songs, 0)
    }
}
```

`PlaylistRepository` adds `suspend fun getSongsForPlaylist(id: Long): List<Song>` as a one-shot fetch alongside the existing Flow.

---

## Files Summary

| Action | File |
|---|---|
| Modify | `ui/theme/Color.kt` |
| Modify | `ui/theme/Theme.kt` |
| Modify | `ui/screen/playlist/PlaylistScreen.kt` |
| Modify | `ui/screen/playlist/PlaylistViewModel.kt` |
| Create | `ui/screen/playlist/PlaylistDetailScreen.kt` |
| Create | `ui/screen/playlist/PlaylistDetailViewModel.kt` |
| Modify | `ui/screen/player/PlayerScreen.kt` |
| Modify | `ui/screen/player/PlayerViewModel.kt` |
| Create | `ui/component/AddToPlaylistSheet.kt` |
| Modify | `ui/navigation/AppNavigation.kt` |
| Modify | `data/db/dao/PlaylistDao.kt` |
| Modify | `data/repository/PlaylistRepository.kt` |
