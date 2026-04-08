# Design Spec: Add to Playlist from Any Song List

**Date:** 2026-04-08  
**Status:** Approved

## Problem

Songs can only be added to playlists via the Player screen (for the currently playing song). To add a song to a playlist, the user must first play it. This is inconvenient for songs that are not currently playing.

## Goal

Show a ⋮ (three-dot) icon on every song row across all screens. Tapping it opens the existing `AddToPlaylistSheet` without starting playback.

## Scope

Screens affected: `LibraryScreen`, `ArtistDetailScreen`, `AlbumDetailScreen`, `PlaylistDetailScreen`.  
No changes to: `AddToPlaylistSheet`, `PlaylistRepository`, Room, Hilt, or any ViewModel other than `PlayerViewModel`.

## Architecture

`AppNavigation` is the single owner of the `AddToPlaylistSheet` for song-list screens. It already hoists `PlayerViewModel` and passes callbacks to each screen — this pattern is extended for the new feature.

### `SongRow` (modified)

Add optional parameter:

```kotlin
onMoreClick: ((Song) -> Unit)? = null
```

When non-null, renders a ⋮ `IconButton` (`Icons.Default.MoreVert`) at the trailing end of the row. No dropdown or sheet logic lives in `SongRow` itself.

### `PlayerViewModel` (extended)

Add:

```kotlin
private val _addToPlaylistSong = MutableStateFlow<Song?>(null)
val addToPlaylistSong: StateFlow<Song?> = _addToPlaylistSong.asStateFlow()

val addToPlaylistSongPlaylists: StateFlow<Set<Long>> =
    _addToPlaylistSong
        .flatMapLatest { song ->
            if (song != null) playlistRepo.observePlaylistIdsForSong(song.id)
            else flowOf(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

fun setAddToPlaylistSong(song: Song?) {
    _addToPlaylistSong.value = song
}
```

`addSongToPlaylist` and `createPlaylistAndAddSong` are already implemented and reused unchanged.

### `AppNavigation` (extended)

Collect state:

```kotlin
val addToPlaylistSong by playerViewModel.addToPlaylistSong.collectAsStateWithLifecycle()
val addToPlaylistSongPlaylists by playerViewModel.addToPlaylistSongPlaylists.collectAsStateWithLifecycle()
```

Show sheet when a song is selected (outside `NavHost`, inside the `Scaffold` content block):

```kotlin
addToPlaylistSong?.let { song ->
    AddToPlaylistSheet(
        song = song,
        playlists = playlists,
        songPlaylistIds = addToPlaylistSongPlaylists,
        onAddToPlaylist = { playlist -> playerViewModel.addSongToPlaylist(song, playlist) },
        onCreateAndAdd = { name -> playerViewModel.createPlaylistAndAddSong(name, song) },
        onDismiss = { playerViewModel.setAddToPlaylistSong(null) },
    )
}
```

`AddToPlaylistSheet` calls `onDismiss()` internally after every action (playlist selected, new playlist created), so `onDismiss` is the single place that clears the selection.

`AppNavigation` also needs `playlists` collected from `playerViewModel.playlists`.

Each song-list screen gets a new parameter:

```kotlin
onMoreClick: (Song) -> Unit
```

Passed from `AppNavigation` as:

```kotlin
onMoreClick = { song -> playerViewModel.setAddToPlaylistSong(song) }
```

### Song-List Screens (4 screens, identical pattern)

Each of `LibraryScreen`, `ArtistDetailScreen`, `AlbumDetailScreen`, `PlaylistDetailScreen` receives `onMoreClick: (Song) -> Unit` as a parameter and passes it to `SongRow`:

```kotlin
SongRow(
    song = song,
    onClick = { onSongClick(...) },
    onMoreClick = { onMoreClick(song) },
)
```

## Data Flow

```
User taps ⋮ on a SongRow
  → onMoreClick(song)
  → playerViewModel.setAddToPlaylistSong(song)
  → AppNavigation renders AddToPlaylistSheet

User selects a playlist
  → playerViewModel.addSongToPlaylist(song, playlist)
  → AddToPlaylistSheet calls onDismiss() internally
  → playerViewModel.setAddToPlaylistSong(null)   ← sheet closes

User taps "Neue Liste..."
  → playerViewModel.createPlaylistAndAddSong(name, song)
  → AddToPlaylistSheet calls onDismiss() internally
  → playerViewModel.setAddToPlaylistSong(null)   ← sheet closes

User dismisses sheet
  → playerViewModel.setAddToPlaylistSong(null)
```

## What Does NOT Change

- `AddToPlaylistSheet` — reused as-is
- `PlaylistRepository` — reused as-is
- All existing ViewModel classes except `PlayerViewModel`
- Room, Hilt, data layer

## Testing

1. Open Library → tap ⋮ on any song → sheet appears → add to playlist → confirm song appears in playlist
2. Open an Artist detail screen → repeat
3. Open an Album detail screen → repeat
4. Open a Playlist detail screen → repeat
5. Tap ⋮ on a song already in a playlist → sheet shows checkmark for that playlist, entry is not tappable
6. Tap "Neue Liste..." → create playlist → song is added to new playlist
7. Dismiss sheet without selecting → no changes, sheet closes
