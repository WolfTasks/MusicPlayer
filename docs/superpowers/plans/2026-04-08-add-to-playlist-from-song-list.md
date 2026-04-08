# Add to Playlist from Any Song List — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a ⋮ icon to every song row so users can add any song to a playlist without playing it first.

**Architecture:** `SongRow` gets an optional `onMoreClick` callback that renders a ⋮ button. `PlayerViewModel` gains a `_addToPlaylistSong` StateFlow. `AppNavigation` (which already hoists `PlayerViewModel`) renders `AddToPlaylistSheet` globally when a song is selected, and passes the `onMoreClick` callback into all four song-list screens.

**Tech Stack:** Jetpack Compose, Material3, StateFlow/flatMapLatest, Hilt, existing `AddToPlaylistSheet` and `PlaylistRepository`

---

## File Map

| File | Change |
|---|---|
| `ui/component/SongRow.kt` | Add optional `onMoreClick` parameter + ⋮ icon |
| `ui/screen/player/PlayerViewModel.kt` | Add `_addToPlaylistSong` state + `setAddToPlaylistSong()` |
| `ui/screen/library/LibraryScreen.kt` | Add `onMoreClick` param, forward to `SongRow` |
| `ui/screen/artist/ArtistDetailScreen.kt` | Add `onMoreClick` param, forward to `SongRow` |
| `ui/screen/album/AlbumDetailScreen.kt` | Add `onMoreClick` param, forward to `SongRow` |
| `ui/screen/playlist/PlaylistDetailScreen.kt` | Add `onMoreClick` param, forward to `SongRow` |
| `ui/navigation/AppNavigation.kt` | Collect new state, render `AddToPlaylistSheet`, pass callbacks |

---

### Task 1: Extend SongRow with optional ⋮ button

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/component/SongRow.kt`

> No unit test possible for Compose UI without an Android test environment. Compile verification is the gate.

- [ ] **Step 1: Replace the full content of `SongRow.kt`**

```kotlin
package com.wolf.musicplayer.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wolf.musicplayer.domain.model.Song

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: ((Song) -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(artUrl = null, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = song.duration.toMinuteSeconds(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onMoreClick != null) {
            IconButton(onClick = { onMoreClick(song) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Mehr",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Long.toMinuteSeconds(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/component/SongRow.kt
git commit -m "feat: add optional onMoreClick button to SongRow"
```

---

### Task 2: Extend PlayerViewModel with addToPlaylistSong state

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

> `PlayerViewModel` requires `Context` for `MediaController` and cannot be unit-tested without an Android environment. No test file exists for it. Build verification is the gate.

- [ ] **Step 1: Add the new state fields and method to `PlayerViewModel`**

In `PlayerViewModel.kt`, find the existing `songPlaylists` block (around line 66–77). Insert the following block **directly after** the closing `.stateIn(...)` line of `songPlaylists`:

```kotlin
        private val _addToPlaylistSong = MutableStateFlow<Song?>(null)
        val addToPlaylistSong: StateFlow<Song?> = _addToPlaylistSong.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val addToPlaylistSongPlaylists: StateFlow<Set<Long>> =
            _addToPlaylistSong
                .flatMapLatest { song ->
                    if (song != null) {
                        playlistRepo.observePlaylistIdsForSong(song.id)
                    } else {
                        flowOf(emptySet())
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun setAddToPlaylistSong(song: Song?) {
            _addToPlaylistSong.value = song
        }
```

No new imports are needed — `MutableStateFlow`, `StateFlow`, `asStateFlow`, `ExperimentalCoroutinesApi`, `flatMapLatest`, `flowOf`, `SharingStarted`, `stateIn` are all already imported in `PlayerViewModel.kt`.

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "feat: add addToPlaylistSong state to PlayerViewModel"
```

---

### Task 3: Add onMoreClick to LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/library/LibraryScreen.kt`

- [ ] **Step 1: Add `onMoreClick` parameter and forward it to `SongRow`**

Replace the `LibraryScreen` function signature and `SongRow` call:

**Old signature (line 36–40):**
```kotlin
fun LibraryScreen(
    contentPadding: PaddingValues,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
```

**New signature:**
```kotlin
fun LibraryScreen(
    contentPadding: PaddingValues,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    onMoreClick: (Song) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
```

**Old SongRow call (lines 81–84):**
```kotlin
                        SongRow(
                            song = song,
                            onClick = { onSongClick(state.filtered, index) },
                        )
```

**New SongRow call:**
```kotlin
                        SongRow(
                            song = song,
                            onClick = { onSongClick(state.filtered, index) },
                            onMoreClick = onMoreClick,
                        )
```

- [ ] **Step 2: Build to verify (AppNavigation will fail until Task 7 — that's expected)**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: errors referencing `AppNavigation.kt` only (missing `onMoreClick` argument — fixed in Task 6). `LibraryScreen.kt` itself must have no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/library/LibraryScreen.kt
git commit -m "feat: add onMoreClick parameter to LibraryScreen"
```

---

### Task 4: Add onMoreClick to ArtistDetailScreen and AlbumDetailScreen

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt`

- [ ] **Step 1: Update `ArtistDetailScreen`**

**Old signature (lines 28–33):**
```kotlin
fun ArtistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
```

**New signature:**
```kotlin
fun ArtistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    onMoreClick: (Song) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
```

**Old SongRow call (lines 56–59):**
```kotlin
                SongRow(
                    song = song,
                    onClick = { onSongClick(songs, index) },
                )
```

**New SongRow call:**
```kotlin
                SongRow(
                    song = song,
                    onClick = { onSongClick(songs, index) },
                    onMoreClick = onMoreClick,
                )
```

- [ ] **Step 2: Update `AlbumDetailScreen`** (identical change)

**Old signature (lines 28–33):**
```kotlin
fun AlbumDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
```

**New signature:**
```kotlin
fun AlbumDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    onMoreClick: (Song) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
```

**Old SongRow call (lines 56–59):**
```kotlin
                SongRow(
                    song = song,
                    onClick = { onSongClick(songs, index) },
                )
```

**New SongRow call:**
```kotlin
                SongRow(
                    song = song,
                    onClick = { onSongClick(songs, index) },
                    onMoreClick = onMoreClick,
                )
```

- [ ] **Step 3: Commit both files**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt
git add app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt
git commit -m "feat: add onMoreClick parameter to ArtistDetailScreen and AlbumDetailScreen"
```

---

### Task 5: Add onMoreClick to PlaylistDetailScreen

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 1: Update `PlaylistDetailScreen`**

**Old signature (lines 38–43):**
```kotlin
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
```

**New signature:**
```kotlin
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    onMoreClick: (Song) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
```

**Old SongRow call (lines 109–112):**
```kotlin
                    SongRow(
                        song = song,
                        onClick = { onSongClick(songs, index) },
                    )
```

**New SongRow call:**
```kotlin
                    SongRow(
                        song = song,
                        onClick = { onSongClick(songs, index) },
                        onMoreClick = onMoreClick,
                    )
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailScreen.kt
git commit -m "feat: add onMoreClick parameter to PlaylistDetailScreen"
```

---

### Task 6: Wire everything together in AppNavigation

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt`

This is the integration task. `AppNavigation` already hoists `PlayerViewModel`. It needs to:
1. Collect the three new StateFlows
2. Render `AddToPlaylistSheet` when `addToPlaylistSong != null`
3. Pass `onMoreClick = { song -> playerViewModel.setAddToPlaylistSong(song) }` to all four song-list screens

- [ ] **Step 1: Add the `AddToPlaylistSheet` import**

At the top of `AppNavigation.kt`, add this import alongside the existing screen imports:

```kotlin
import com.wolf.musicplayer.ui.component.AddToPlaylistSheet
```

- [ ] **Step 2: Collect the new state in `AppNavigation()`**

After the existing state collection lines (after `val playerState by ...` on line 63), add:

```kotlin
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val addToPlaylistSong by playerViewModel.addToPlaylistSong.collectAsStateWithLifecycle()
    val addToPlaylistSongPlaylists by playerViewModel.addToPlaylistSongPlaylists.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Render `AddToPlaylistSheet` inside the Scaffold content**

In the `Scaffold` content lambda (the `{ innerPadding -> ... }` block), add the sheet **after** the `NavHost { ... }` block (before the closing brace of the Scaffold content lambda):

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

- [ ] **Step 4: Pass `onMoreClick` to all four song-list screens**

Define a single lambda at the top of the Scaffold content block (before `NavHost`):

```kotlin
        val onMoreClick: (com.wolf.musicplayer.domain.model.Song) -> Unit = { song ->
            playerViewModel.setAddToPlaylistSong(song)
        }
```

Then update each screen call inside `NavHost`:

**LibraryScreen (line ~91):**
```kotlin
            composable(Screen.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
```

**PlaylistDetailScreen (line ~115):**
```kotlin
            composable(
                route = "playlists/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
```

**ArtistDetailScreen (line ~136):**
```kotlin
            composable(
                route = "artists/{artistName}",
                arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
            ) {
                ArtistDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
```

**AlbumDetailScreen (line ~157):**
```kotlin
            composable(
                route = "albums/{albumName}",
                arguments = listOf(navArgument("albumName") { type = NavType.StringType }),
            ) {
                AlbumDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
```

- [ ] **Step 5: Build to verify everything compiles**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no errors

- [ ] **Step 6: Run unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with all tests passing

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt
git commit -m "feat: wire AddToPlaylistSheet into AppNavigation for all song lists"
```

---

## Manual Testing

After all tasks are committed:

1. **Library screen:** Open the app → tap Library tab → tap ⋮ on any song → `AddToPlaylistSheet` opens → add song to a playlist → verify song appears in that playlist
2. **Artist detail:** Library → tap an artist → tap ⋮ on a song → sheet opens → add to playlist
3. **Album detail:** Albums → tap an album → tap ⋮ on a song → sheet opens → add to playlist
4. **Playlist detail:** Playlists → open a playlist → tap ⋮ on a song → sheet opens → add to another playlist
5. **Already-in-playlist checkmark:** Tap ⋮ on a song already in a playlist → that playlist shows a checkmark and is not tappable
6. **Create new playlist:** Tap ⋮ → tap "Neue Liste..." → enter name → confirm → song is added to the new playlist
7. **Dismiss without action:** Tap ⋮ → tap outside the sheet → sheet closes, no change
8. **Player screen unchanged:** Open Player tab → PlaylistAdd button still works for the currently playing song
