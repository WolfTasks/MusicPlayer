# Wolf Theme + Playlist Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Spotify-green with a Wolf Silber/Frost palette, add full playlist management (create/rename/delete/detail), and add "Add to Playlist" from the PlayerScreen.

**Architecture:** Color changes are isolated to `Color.kt`/`Theme.kt`. Playlist detail introduces `PlaylistDetailViewModel` + `PlaylistDetailScreen` following the existing AlbumDetail pattern. The "Add to Playlist" bottom sheet lives in a new `AddToPlaylistSheet` component; `PlayerViewModel` gets `PlaylistRepository` injected.

**Tech Stack:** Jetpack Compose + Material3 1.3.0, Hilt, Room, Kotlin Coroutines/Flow, MockK (tests)

**Build commands:**
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.SomeTest"
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

---

## Task 1: Wolf Color Scheme

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/theme/Theme.kt`

- [ ] **Step 1: Replace Color.kt**

```kotlin
package com.wolf.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color

// Wolf Silber/Frost palette — derived from app icon
val WolfNavy         = Color(0xFF0D1830)
val WolfNavySurface  = Color(0xFF1A2744)
val WolfNavyVariant  = Color(0xFF243559)
val WolfSilver       = Color(0xFFC8D8E8)   // primary accent
val WolfSilverMuted  = Color(0xFF8099B8)   // primary container / secondary text
val WolfWhite        = Color(0xFFFFFFFF)
val WolfOnSilver     = Color(0xFF0D1830)   // text on silver buttons
```

- [ ] **Step 2: Replace Theme.kt**

```kotlin
package com.wolf.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WolfColorScheme = darkColorScheme(
    primary            = WolfSilver,
    onPrimary          = WolfOnSilver,
    primaryContainer   = WolfSilverMuted,
    onPrimaryContainer = WolfWhite,
    background         = WolfNavy,
    onBackground       = WolfWhite,
    surface            = WolfNavySurface,
    onSurface          = WolfWhite,
    surfaceVariant     = WolfNavyVariant,
    onSurfaceVariant   = WolfSilverMuted,
    outline            = WolfNavyVariant,
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WolfColorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 3: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/theme/Color.kt \
        app/src/main/java/com/wolf/musicplayer/ui/theme/Theme.kt
git commit -m "feat: apply Wolf Silber/Frost color scheme"
```

---

## Task 2: PlaylistDao + PlaylistRepository Extensions

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/data/db/dao/PlaylistDao.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/data/repository/PlaylistRepository.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/PlaylistRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.wolf.musicplayer

import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.entity.PlaylistSongEntity
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.data.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRepositoryTest {

    private val dao = mockk<PlaylistDao>()
    private val repo = PlaylistRepository(dao)

    @Test
    fun `observePlaylistIdsForSong returns set of playlist ids`() = runTest {
        every { dao.observePlaylistIdsForSong(42L) } returns flowOf(listOf(1L, 3L))

        val result = repo.observePlaylistIdsForSong(42L).first()

        assertEquals(setOf(1L, 3L), result)
    }

    @Test
    fun `getSongsForPlaylist maps entities to domain songs`() = runTest {
        val entity = SongEntity(
            id = 7L, title = "Test", artist = "A", album = "B",
            duration = 1000L, sourceType = "LOCAL",
            serverId = null, serverSongId = null, localPath = "/p", cachedPath = null,
        )
        coEvery { dao.getSongsForPlaylist(10L) } returns listOf(entity)

        val songs = repo.getSongsForPlaylist(10L)

        assertEquals(1, songs.size)
        assertEquals("Test", songs[0].title)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistRepositoryTest"
```
Expected: FAIL — `observePlaylistIdsForSong` / `getSongsForPlaylist` not found

- [ ] **Step 3: Add two queries to PlaylistDao**

In `PlaylistDao.kt`, add after the existing `observeSongsForPlaylist` method:

```kotlin
@Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
fun observePlaylistIdsForSong(songId: Long): Flow<List<Long>>

@Query("""
    SELECT s.* FROM songs s
    INNER JOIN playlist_songs ps ON ps.songId = s.id
    WHERE ps.playlistId = :playlistId
    ORDER BY ps.position ASC
""")
suspend fun getSongsForPlaylist(playlistId: Long): List<SongEntity>
```

- [ ] **Step 4: Add two methods to PlaylistRepository**

In `PlaylistRepository.kt`, add after `observeSongsForPlaylist`:

```kotlin
fun observePlaylistIdsForSong(songId: Long): Flow<Set<Long>> =
    dao.observePlaylistIdsForSong(songId).map { it.toSet() }

suspend fun getSongsForPlaylist(playlistId: Long): List<Song> =
    dao.getSongsForPlaylist(playlistId).map(SongEntity::toDomain)
```

(The private `SongEntity.toDomain()` extension is already defined at the bottom of the file.)

- [ ] **Step 5: Run tests to confirm they pass**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, 2 tests PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/data/db/dao/PlaylistDao.kt \
        app/src/main/java/com/wolf/musicplayer/data/repository/PlaylistRepository.kt \
        app/src/test/java/com/wolf/musicplayer/PlaylistRepositoryTest.kt
git commit -m "feat: add observePlaylistIdsForSong and getSongsForPlaylist"
```

---

## Task 3: PlaylistViewModel Extensions

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistViewModel.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/PlaylistViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.wolf.musicplayer

import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.ui.screen.playlist.PlaylistViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaylistViewModelTest {

    private val repo = mockk<PlaylistRepository>(relaxed = true)

    private fun makeVm(): PlaylistViewModel {
        every { repo.observeAll() } returns flowOf(emptyList())
        return PlaylistViewModel(repo)
    }

    @Test
    fun `renamePlaylist delegates to repository`() = runTest {
        val vm = makeVm()
        coEvery { repo.rename(1L, "New Name") } returns Unit

        vm.renamePlaylist(1L, "New Name")

        coVerify { repo.rename(1L, "New Name") }
    }

    @Test
    fun `playPlaylist fetches songs and invokes callback`() = runTest {
        val vm = makeVm()
        val song = Song(1L, "T", "A", "Al", 1000L, SourceType.LOCAL, null, null, "/p", null)
        coEvery { repo.getSongsForPlaylist(5L) } returns listOf(song)

        var received: List<Song>? = null
        vm.playPlaylist(5L) { received = it }

        coVerify { repo.getSongsForPlaylist(5L) }
        assert(received == listOf(song))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistViewModelTest"
```
Expected: FAIL

- [ ] **Step 3: Update PlaylistViewModel**

Replace the entire file:

```kotlin
package com.wolf.musicplayer.ui.screen.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val repo: PlaylistRepository,
    ) : ViewModel() {

        val playlists =
            repo.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun createPlaylist(name: String) {
            viewModelScope.launch { repo.create(name) }
        }

        fun renamePlaylist(id: Long, name: String) {
            viewModelScope.launch { repo.rename(id, name) }
        }

        fun deletePlaylist(playlist: Playlist) {
            viewModelScope.launch { repo.delete(playlist.id) }
        }

        fun playPlaylist(playlistId: Long, onSongsReady: (List<Song>) -> Unit) {
            viewModelScope.launch {
                val songs = repo.getSongsForPlaylist(playlistId)
                if (songs.isNotEmpty()) onSongsReady(songs)
            }
        }
    }
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistViewModelTest"
```
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistViewModel.kt \
        app/src/test/java/com/wolf/musicplayer/PlaylistViewModelTest.kt
git commit -m "feat: add renamePlaylist and playPlaylist to PlaylistViewModel"
```

---

## Task 4: PlaylistDetailViewModel

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailViewModel.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/PlaylistDetailViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.wolf.musicplayer

import androidx.lifecycle.SavedStateHandle
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

class PlaylistDetailViewModelTest {

    private fun makeVm(playlistId: Long = 3L): Triple<PlaylistDetailViewModel, PlaylistRepository, SavedStateHandle> {
        val repo = mockk<PlaylistRepository>(relaxed = true)
        val playlist = Playlist(playlistId, "Rock Mix", null, true)
        every { repo.observeAll() } returns flowOf(listOf(playlist))
        every { repo.observeSongsForPlaylist(playlistId) } returns flowOf(emptyList())
        val handle = SavedStateHandle(mapOf("playlistId" to playlistId))
        return Triple(PlaylistDetailViewModel(repo, handle), repo, handle)
    }

    @Test
    fun `reads playlistId from saved state and observes songs`() {
        val (_, repo, _) = makeVm(3L)
        io.mockk.verify { repo.observeSongsForPlaylist(3L) }
    }

    @Test
    fun `removeSong delegates to repository`() {
        val (vm, repo, _) = makeVm(3L)
        vm.removeSong(99L)
        coVerify { repo.removeSong(3L, 99L) }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistDetailViewModelTest"
```
Expected: FAIL — class not found

- [ ] **Step 3: Create PlaylistDetailViewModel**

```kotlin
package com.wolf.musicplayer.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        private val repo: PlaylistRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {

        private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

        val playlist =
            repo.observeAll()
                .map { lists -> lists.find { it.id == playlistId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val songs =
            repo.observeSongsForPlaylist(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun removeSong(songId: Long) {
            viewModelScope.launch { repo.removeSong(playlistId, songId) }
        }
    }
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --tests "com.wolf.musicplayer.PlaylistDetailViewModelTest"
```
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailViewModel.kt \
        app/src/test/java/com/wolf/musicplayer/PlaylistDetailViewModelTest.kt
git commit -m "feat: add PlaylistDetailViewModel"
```

---

## Task 5: PlaylistDetailScreen

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 1: Create PlaylistDetailScreen.kt**

```kotlin
package com.wolf.musicplayer.ui.screen.playlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.ui.component.SongRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { onSongClick(songs, 0) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Alle abspielen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.removeSong(song.id)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFB00020)),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Entfernen",
                                tint = Color.White,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    },
                ) {
                    SongRow(
                        song = song,
                        onClick = { onSongClick(songs, index) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistDetailScreen.kt
git commit -m "feat: add PlaylistDetailScreen with swipe-to-dismiss"
```

---

## Task 6: PlaylistScreen Extended

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistScreen.kt`

- [ ] **Step 1: Replace PlaylistScreen.kt**

```kotlin
package com.wolf.musicplayer.ui.screen.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    onPlaylistClick: (Long) -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Create dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // Rename dialog state
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Delete dialog state
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    // Context menu state
    var menuTarget by remember { mutableStateOf<Playlist?>(null) }

    // --- Create dialog ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Neue Wiedergabeliste") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) viewModel.createPlaylist(newName)
                    newName = ""
                    showCreateDialog = false
                }) { Text("Erstellen") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    // --- Rename dialog ---
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Neuer Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) viewModel.renamePlaylist(target.id, renameText)
                    renameTarget = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Abbrechen") }
            },
        )
    }

    // --- Delete confirmation dialog ---
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Löschen") },
            text = { Text("\"${target.name}\" wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target)
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wiedergabelisten") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Neue Playlist")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.playPlaylist(playlist.id, onPlaySongs)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Abspielen")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .combinedClickable(
                            onClick = { onPlaylistClick(playlist.id) },
                            onLongClick = {
                                menuTarget = playlist
                            },
                        ),
                )
                // Context menu anchored to the item
                if (menuTarget?.id == playlist.id) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuTarget = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            onClick = {
                                renameText = playlist.name
                                renameTarget = playlist
                                menuTarget = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            onClick = {
                                deleteTarget = playlist
                                menuTarget = null
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/playlist/PlaylistScreen.kt
git commit -m "feat: add long-press context menu and play button to PlaylistScreen"
```

---

## Task 7: AppNavigation Update

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Update AppNavigation.kt**

Replace only the `composable(Screen.Playlists.route)` block and add the detail route. The full modified file:

```kotlin
package com.wolf.musicplayer.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wolf.musicplayer.ui.screen.album.AlbumDetailScreen
import com.wolf.musicplayer.ui.screen.album.AlbumScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistDetailScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistScreen
import com.wolf.musicplayer.ui.screen.library.LibraryScreen
import com.wolf.musicplayer.ui.screen.player.PlayerScreen
import com.wolf.musicplayer.ui.screen.player.PlayerViewModel
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailScreen
import com.wolf.musicplayer.ui.screen.playlist.PlaylistScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Library : Screen("library", "Bibliothek", Icons.Default.LibraryMusic)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.QueueMusic)
    object Artists : Screen("artists", "Künstler", Icons.Default.Person)
    object Albums : Screen("albums", "Alben", Icons.Default.Album)
    object Player : Screen("player", "Jetzt läuft", Icons.Default.PlayCircle)
}

private val tabs = listOf(
    Screen.Library,
    Screen.Playlists,
    Screen.Artists,
    Screen.Albums,
    Screen.Player,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Library.route) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistScreen(
                    contentPadding = innerPadding,
                    onPlaylistClick = { playlistId ->
                        navController.navigate("playlists/$playlistId")
                    },
                    onPlaySongs = { songs ->
                        playerViewModel.playSongs(songs, 0)
                        navController.navigate(Screen.Player.route)
                    },
                )
            }
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
                )
            }
            composable(Screen.Artists.route) {
                ArtistScreen(
                    contentPadding = innerPadding,
                    onItemClick = { artistName ->
                        navController.navigate("artists/${Uri.encode(artistName)}")
                    },
                )
            }
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
                )
            }
            composable(Screen.Albums.route) {
                AlbumScreen(
                    contentPadding = innerPadding,
                    onItemClick = { albumName ->
                        navController.navigate("albums/${Uri.encode(albumName)}")
                    },
                )
            }
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
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt
git commit -m "feat: wire PlaylistDetailScreen and playlist play into AppNavigation"
```

---

## Task 8: PlayerViewModel — Playlist Extensions

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

- [ ] **Step 1: Add PlaylistRepository injection and new members**

Add `PlaylistRepository` as a constructor parameter and add the playlist-related members. The full updated constructor and new fields (insert after the existing `state` declaration):

```kotlin
// In constructor, add:
private val playlistRepo: PlaylistRepository,
```

Add these imports at the top:
```kotlin
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
```

Add these new StateFlows after the `state` declaration:

```kotlin
val playlists =
    playlistRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
val songPlaylists =
    state
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            if (songId != null) playlistRepo.observePlaylistIdsForSong(songId)
            else flowOf(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
```

Add these new functions before `onCleared()`:

```kotlin
fun addSongToPlaylist(song: Song, playlist: Playlist) {
    viewModelScope.launch {
        val currentCount = playlistRepo.getSongsForPlaylist(playlist.id).size
        playlistRepo.addSong(playlist.id, song.id, currentCount)
    }
}

fun createPlaylistAndAddSong(name: String, song: Song) {
    viewModelScope.launch {
        val playlistId = playlistRepo.create(name)
        playlistRepo.addSong(playlistId, song.id, 0)
    }
}
```

The full updated constructor signature:

```kotlin
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songRepository: SongRepository,
        private val playWeightRepository: PlayWeightRepository,
        private val shuffler: WeightedShuffler,
        private val playlistRepo: PlaylistRepository,
    ) : ViewModel() {
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "feat: inject PlaylistRepository into PlayerViewModel, add playlist actions"
```

---

## Task 9: AddToPlaylistSheet Component

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/component/AddToPlaylistSheet.kt`

- [ ] **Step 1: Create AddToPlaylistSheet.kt**

```kotlin
package com.wolf.musicplayer.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    songPlaylistIds: Set<Long>,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreateAndAdd: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Neue Wiedergabeliste") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onCreateAndAdd(newName)
                        newName = ""
                        showCreateDialog = false
                        onDismiss()
                    }
                }) { Text("Erstellen & Hinzufügen") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Zu Wiedergabeliste hinzufügen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        LazyColumn {
            items(playlists, key = { it.id }) { playlist ->
                val alreadyAdded = playlist.id in songPlaylistIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !alreadyAdded) {
                            onAddToPlaylist(playlist)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (alreadyAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(playlist.name)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Neue Liste...",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/component/AddToPlaylistSheet.kt
git commit -m "feat: add AddToPlaylistSheet bottom sheet component"
```

---

## Task 10: PlayerScreen — Add to Playlist Button

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerScreen.kt`

- [ ] **Step 1: Add PlaylistAdd icon and bottom sheet to PlayerScreen**

Add these imports:
```kotlin
import androidx.compose.material.icons.filled.PlaylistAdd
import com.wolf.musicplayer.ui.component.AddToPlaylistSheet
```

Add local state for the sheet after the existing `isDragging`/`dragValue` state:
```kotlin
var showAddToPlaylist by remember { mutableStateOf(false) }
```

Add these two StateFlow collections after `val state by`:
```kotlin
val playlists by viewModel.playlists.collectAsStateWithLifecycle()
val songPlaylists by viewModel.songPlaylists.collectAsStateWithLifecycle()
```

Replace the existing `// Shuffle + Repeat` Row with:
```kotlin
// Shuffle + Playlist + Repeat
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
) {
    IconButton(onClick = { viewModel.toggleShuffle() }) {
        Icon(
            imageVector = Icons.Default.Shuffle,
            contentDescription = "Shuffle",
            tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = { if (song != null) showAddToPlaylist = true }) {
        Icon(
            imageVector = Icons.Default.PlaylistAdd,
            contentDescription = "Zu Playlist hinzufügen",
            tint = if (song != null) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.outline,
        )
    }
    IconButton(onClick = { viewModel.cycleRepeatMode() }) {
        Icon(
            imageVector = when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                else -> Icons.Default.Repeat
            },
            contentDescription = "Repeat",
            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Add-to-playlist bottom sheet
if (showAddToPlaylist && song != null) {
    AddToPlaylistSheet(
        song = song,
        playlists = playlists,
        songPlaylistIds = songPlaylists,
        onAddToPlaylist = { playlist -> viewModel.addSongToPlaylist(song, playlist) },
        onCreateAndAdd = { name -> viewModel.createPlaylistAndAddSong(name, song) },
        onDismiss = { showAddToPlaylist = false },
    )
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run full test suite**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test
```
Expected: `BUILD SUCCESSFUL`, all tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerScreen.kt
git commit -m "feat: add PlaylistAdd button and bottom sheet to PlayerScreen"
```

---

## Task 11: Install and Smoke Test

- [ ] **Step 1: Install on device**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug
```

- [ ] **Step 2: Manual smoke test checklist**

1. **Color:** App opens with dark navy background and silver accent (buttons, active nav items)
2. **Create playlist:** Playlists tab → FAB → enter name → "Erstellen" → appears in list
3. **Rename:** Long-press on playlist → "Umbenennen" → change name → "Speichern" → updated in list
4. **Delete:** Long-press → "Löschen" → confirmation → playlist gone
5. **Playlist detail:** Tap playlist name → detail screen opens with songs (if any)
6. **Play all:** In detail screen → "Alle abspielen" → Player tab opens, song plays
7. **Swipe to remove:** In detail screen → swipe song left → song disappears from list
8. **Play from list:** In Playlists tab → ▶ icon on a playlist → song plays
9. **Add to playlist:** Play a song → Player tab → PlaylistAdd icon (between Shuffle and Repeat) → bottom sheet opens → select a playlist → song added
10. **Create & add:** Same flow → "Neue Liste..." → name → "Erstellen & Hinzufügen" → new playlist created with song
