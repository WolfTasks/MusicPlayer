# Albums/Artists Detail Screens & Seekbar Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the frozen seekbar in the Player screen, and make the Albums and Artists tabs navigate to song-list detail screens.

**Architecture:** Add a 500 ms polling coroutine in `PlayerViewModel` to keep `state.position` live. Add two new navigation routes (`artists/{artistName}`, `albums/{albumName}`) backed by new detail ViewModels and Screens that follow the existing `LibraryScreen` pattern. No data-layer changes needed — `SongRepository.observeByArtist()` and `observeByAlbum()` already exist.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt (KSP), Navigation Compose, Media3 / ExoPlayer, MockK, kotlinx-coroutines-test

---

## File Map

| Action | File |
|---|---|
| Modify | `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt` |
| Create | `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailViewModel.kt` |
| Create | `app/src/test/java/com/wolf/musicplayer/ArtistDetailViewModelTest.kt` |
| Create | `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt` |
| Create | `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailViewModel.kt` |
| Create | `app/src/test/java/com/wolf/musicplayer/AlbumDetailViewModelTest.kt` |
| Create | `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt` |
| Modify | `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistScreen.kt` |
| Modify | `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumScreen.kt` |
| Modify | `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt` |

---

## Task 1: Fix seekbar — add position polling to PlayerViewModel

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

The player listener already fires `onIsPlayingChanged`. We add a coroutine that polls `controller.currentPosition` every 500 ms while playing, and cancel it on pause/end. We also update position immediately on `seekTo()` so the slider doesn't stutter.

- [ ] **Step 1: Open PlayerViewModel and locate the three places that need changes**

  Read `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt` to confirm current content before editing.

- [ ] **Step 2: Replace the full file with the updated version**

  Replace `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt` with:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.player

  import android.content.ComponentName
  import android.content.Context
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import androidx.media3.common.MediaItem
  import androidx.media3.common.Player
  import androidx.media3.session.MediaController
  import androidx.media3.session.SessionToken
  import com.google.common.util.concurrent.MoreExecutors
  import com.wolf.musicplayer.data.repository.PlayWeightRepository
  import com.wolf.musicplayer.data.repository.SongRepository
  import com.wolf.musicplayer.domain.WeightedShuffler
  import com.wolf.musicplayer.domain.model.Song
  import com.wolf.musicplayer.service.MusicService
  import dagger.hilt.android.lifecycle.HiltViewModel
  import dagger.hilt.android.qualifiers.ApplicationContext
  import kotlinx.coroutines.Job
  import kotlinx.coroutines.delay
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.isActive
  import kotlinx.coroutines.launch
  import javax.inject.Inject

  data class PlayerState(
      val currentSong: Song? = null,
      val isPlaying: Boolean = false,
      val position: Long = 0L,
      val duration: Long = 0L,
      val shuffleEnabled: Boolean = false,
      val repeatMode: Int = Player.REPEAT_MODE_OFF,
      val queue: List<Song> = emptyList(),
  )

  @HiltViewModel
  class PlayerViewModel
      @Inject
      constructor(
          @ApplicationContext private val context: Context,
          private val songRepository: SongRepository,
          private val playWeightRepository: PlayWeightRepository,
          private val shuffler: WeightedShuffler,
      ) : ViewModel() {
          private val _state = MutableStateFlow(PlayerState())
          val state: StateFlow<PlayerState> = _state.asStateFlow()

          private var controller: MediaController? = null
          private var currentQueue: List<Song> = emptyList()
          private var currentSongId: Long? = null
          private var playbackStartPosition: Long = 0L
          private var positionTracker: Job? = null

          init {
              connectToService()
          }

          private fun connectToService() {
              val token = SessionToken(context, ComponentName(context, MusicService::class.java))
              val future = MediaController.Builder(context, token).buildAsync()
              future.addListener({
                  controller = future.get()
                  controller?.addListener(playerListener)
                  syncStateFromController()
              }, MoreExecutors.directExecutor())
          }

          private val playerListener =
              object : Player.Listener {
                  override fun onIsPlayingChanged(isPlaying: Boolean) {
                      _state.update { it.copy(isPlaying = isPlaying) }
                      if (isPlaying) startPositionTracking() else stopPositionTracking()
                  }

                  override fun onMediaItemTransition(
                      mediaItem: MediaItem?,
                      reason: Int,
                  ) {
                      val wasManualSkip = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                      if (wasManualSkip) {
                          currentSongId?.let { id ->
                              viewModelScope.launch { playWeightRepository.recordSkip(id) }
                          }
                      }
                      syncStateFromController()
                  }

                  override fun onPlaybackStateChanged(state: Int) {
                      if (state == Player.STATE_ENDED) {
                          stopPositionTracking()
                          currentSongId?.let { id ->
                              val ctrl = controller ?: return
                              val pct =
                                  if (ctrl.duration > 0) {
                                      ctrl.currentPosition.toFloat() / ctrl.duration
                                  } else {
                                      0f
                                  }
                              if (pct >= 0.8f) {
                                  viewModelScope.launch { playWeightRepository.recordFullPlay(id) }
                              }
                          }
                      }
                  }
              }

          private fun startPositionTracking() {
              positionTracker?.cancel()
              positionTracker = viewModelScope.launch {
                  while (isActive) {
                      _state.update { it.copy(position = controller?.currentPosition ?: 0L) }
                      delay(500)
                  }
              }
          }

          private fun stopPositionTracking() {
              positionTracker?.cancel()
              positionTracker = null
          }

          private fun syncStateFromController() {
              val ctrl = controller ?: return
              val idx = ctrl.currentMediaItemIndex
              val song = currentQueue.getOrNull(idx)
              currentSongId = song?.id
              _state.update {
                  it.copy(
                      currentSong = song,
                      isPlaying = ctrl.isPlaying,
                      duration = ctrl.duration.coerceAtLeast(0L),
                      queue = currentQueue,
                  )
              }
          }

          fun playSongs(
              songs: List<Song>,
              startIndex: Int = 0,
          ) {
              currentQueue = songs
              val ctrl = controller ?: return
              val items =
                  songs.map { song ->
                      MediaItem.Builder()
                          .setMediaId(song.id.toString())
                          .setUri(song.localPath ?: song.cachedPath ?: "")
                          .build()
                  }
              ctrl.setMediaItems(items, startIndex, 0L)
              ctrl.prepare()
              ctrl.play()
              syncStateFromController()
          }

          fun playPause() {
              controller?.let { if (it.isPlaying) it.pause() else it.play() }
          }

          fun skipToNext() {
              controller?.seekToNextMediaItem()
          }

          fun skipToPrevious() {
              controller?.seekToPreviousMediaItem()
          }

          fun seekTo(positionMs: Long) {
              controller?.seekTo(positionMs)
              _state.update { it.copy(position = positionMs) }
          }

          fun toggleShuffle() {
              _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
          }

          fun cycleRepeatMode() {
              val next =
                  when (_state.value.repeatMode) {
                      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                      else -> Player.REPEAT_MODE_OFF
                  }
              controller?.repeatMode = next
              _state.update { it.copy(repeatMode = next) }
          }

          suspend fun nextWeightedSong(): Song? {
              val songs = currentQueue.ifEmpty { return null }
              val weights = songs.map { playWeightRepository.getWeight(it.id) }
              return shuffler.pick(songs, weights)
          }

          override fun onCleared() {
              positionTracker?.cancel()
              controller?.release()
              super.onCleared()
          }
      }
  ```

- [ ] **Step 3: Build to verify no compile errors**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
  git commit -m "fix: add position polling to PlayerViewModel so seekbar updates in real time"
  ```

---

## Task 2: ArtistDetailViewModel + unit test

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailViewModel.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/ArtistDetailViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

  Create `app/src/test/java/com/wolf/musicplayer/ArtistDetailViewModelTest.kt`:

  ```kotlin
  package com.wolf.musicplayer

  import androidx.lifecycle.SavedStateHandle
  import com.wolf.musicplayer.data.repository.SongRepository
  import com.wolf.musicplayer.ui.screen.artist.ArtistDetailViewModel
  import io.mockk.every
  import io.mockk.mockk
  import io.mockk.verify
  import kotlinx.coroutines.flow.flowOf
  import org.junit.Test

  class ArtistDetailViewModelTest {

      @Test
      fun `reads songs for the artist name from saved state`() {
          val repo = mockk<SongRepository>()
          every { repo.observeByArtist("Miles Davis") } returns flowOf(emptyList())
          val handle = SavedStateHandle(mapOf("artistName" to "Miles Davis"))

          ArtistDetailViewModel(repo, handle)

          verify { repo.observeByArtist("Miles Davis") }
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it fails**

  Run: `./gradlew test --tests "com.wolf.musicplayer.ArtistDetailViewModelTest"`
  Expected: FAIL — `ArtistDetailViewModel` does not exist yet.

- [ ] **Step 3: Create ArtistDetailViewModel**

  Create `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailViewModel.kt`:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.artist

  import androidx.lifecycle.SavedStateHandle
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.wolf.musicplayer.data.repository.SongRepository
  import com.wolf.musicplayer.domain.model.Song
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.stateIn
  import javax.inject.Inject

  @HiltViewModel
  class ArtistDetailViewModel
      @Inject
      constructor(
          repo: SongRepository,
          savedStateHandle: SavedStateHandle,
      ) : ViewModel() {
          private val artistName: String = checkNotNull(savedStateHandle["artistName"])

          val songs: StateFlow<List<Song>> =
              repo.observeByArtist(artistName)
                  .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
      }
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `./gradlew test --tests "com.wolf.musicplayer.ArtistDetailViewModelTest"`
  Expected: PASS

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailViewModel.kt \
          app/src/test/java/com/wolf/musicplayer/ArtistDetailViewModelTest.kt
  git commit -m "feat: add ArtistDetailViewModel"
  ```

---

## Task 3: ArtistDetailScreen

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt`

- [ ] **Step 1: Create ArtistDetailScreen**

  Create `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt`:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.artist

  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.itemsIndexed
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.automirrored.filled.ArrowBack
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.HorizontalDivider
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.wolf.musicplayer.domain.model.Song
  import com.wolf.musicplayer.ui.component.SongRow

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun ArtistDetailScreen(
      contentPadding: PaddingValues,
      onBack: () -> Unit,
      onSongClick: (songs: List<Song>, index: Int) -> Unit,
      viewModel: ArtistDetailViewModel = hiltViewModel(),
  ) {
      val songs by viewModel.songs.collectAsStateWithLifecycle()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text(viewModel.artistName) },
                  navigationIcon = {
                      IconButton(onClick = onBack) {
                          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                  SongRow(
                      song = song,
                      onClick = { onSongClick(songs, index) },
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

  > **Note:** The TopAppBar title uses `viewModel.artistName`. That field is currently `private` in `ArtistDetailViewModel`. Change it to `internal val artistName` in the ViewModel file.

- [ ] **Step 2: Make `artistName` accessible — update ArtistDetailViewModel**

  In `ArtistDetailViewModel.kt`, change:
  ```kotlin
  private val artistName: String = checkNotNull(savedStateHandle["artistName"])
  ```
  to:
  ```kotlin
  internal val artistName: String = checkNotNull(savedStateHandle["artistName"])
  ```

- [ ] **Step 3: Build to verify no compile errors**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run existing tests to make sure nothing broke**

  Run: `./gradlew test`
  Expected: All tests PASS

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailScreen.kt \
          app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistDetailViewModel.kt
  git commit -m "feat: add ArtistDetailScreen"
  ```

---

## Task 4: AlbumDetailViewModel + unit test

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailViewModel.kt`
- Create: `app/src/test/java/com/wolf/musicplayer/AlbumDetailViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

  Create `app/src/test/java/com/wolf/musicplayer/AlbumDetailViewModelTest.kt`:

  ```kotlin
  package com.wolf.musicplayer

  import androidx.lifecycle.SavedStateHandle
  import com.wolf.musicplayer.data.repository.SongRepository
  import com.wolf.musicplayer.ui.screen.album.AlbumDetailViewModel
  import io.mockk.every
  import io.mockk.mockk
  import io.mockk.verify
  import kotlinx.coroutines.flow.flowOf
  import org.junit.Test

  class AlbumDetailViewModelTest {

      @Test
      fun `reads songs for the album name from saved state`() {
          val repo = mockk<SongRepository>()
          every { repo.observeByAlbum("Kind of Blue") } returns flowOf(emptyList())
          val handle = SavedStateHandle(mapOf("albumName" to "Kind of Blue"))

          AlbumDetailViewModel(repo, handle)

          verify { repo.observeByAlbum("Kind of Blue") }
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it fails**

  Run: `./gradlew test --tests "com.wolf.musicplayer.AlbumDetailViewModelTest"`
  Expected: FAIL — `AlbumDetailViewModel` does not exist yet.

- [ ] **Step 3: Create AlbumDetailViewModel**

  Create `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailViewModel.kt`:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.album

  import androidx.lifecycle.SavedStateHandle
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.wolf.musicplayer.data.repository.SongRepository
  import com.wolf.musicplayer.domain.model.Song
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.stateIn
  import javax.inject.Inject

  @HiltViewModel
  class AlbumDetailViewModel
      @Inject
      constructor(
          repo: SongRepository,
          savedStateHandle: SavedStateHandle,
      ) : ViewModel() {
          internal val albumName: String = checkNotNull(savedStateHandle["albumName"])

          val songs: StateFlow<List<Song>> =
              repo.observeByAlbum(albumName)
                  .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
      }
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `./gradlew test --tests "com.wolf.musicplayer.AlbumDetailViewModelTest"`
  Expected: PASS

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailViewModel.kt \
          app/src/test/java/com/wolf/musicplayer/AlbumDetailViewModelTest.kt
  git commit -m "feat: add AlbumDetailViewModel"
  ```

---

## Task 5: AlbumDetailScreen

**Files:**
- Create: `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt`

- [ ] **Step 1: Create AlbumDetailScreen**

  Create `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt`:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.album

  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.itemsIndexed
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.automirrored.filled.ArrowBack
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.HorizontalDivider
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.wolf.musicplayer.domain.model.Song
  import com.wolf.musicplayer.ui.component.SongRow

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlbumDetailScreen(
      contentPadding: PaddingValues,
      onBack: () -> Unit,
      onSongClick: (songs: List<Song>, index: Int) -> Unit,
      viewModel: AlbumDetailViewModel = hiltViewModel(),
  ) {
      val songs by viewModel.songs.collectAsStateWithLifecycle()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text(viewModel.albumName) },
                  navigationIcon = {
                      IconButton(onClick = onBack) {
                          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                  SongRow(
                      song = song,
                      onClick = { onSongClick(songs, index) },
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 2: Build to verify no compile errors**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumDetailScreen.kt
  git commit -m "feat: add AlbumDetailScreen"
  ```

---

## Task 6: Update ArtistScreen to add onItemClick

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistScreen.kt`

- [ ] **Step 1: Replace the full ArtistScreen file**

  Replace `app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistScreen.kt` with:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.artist

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.HorizontalDivider
  import androidx.compose.material3.ListItem
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun ArtistScreen(
      contentPadding: PaddingValues,
      onItemClick: (String) -> Unit,
      viewModel: ArtistViewModel = hiltViewModel(),
  ) {
      val artists by viewModel.artists.collectAsStateWithLifecycle()

      Scaffold(
          topBar = { TopAppBar(title = { Text("Künstler") }) },
      ) { padding ->
          LazyColumn(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(padding)
                  .padding(contentPadding),
          ) {
              items(artists, key = { it }) { artist ->
                  ListItem(
                      headlineContent = { Text(artist) },
                      modifier = Modifier
                          .padding(horizontal = 8.dp)
                          .clickable { onItemClick(artist) },
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 2: Commit (skip pre-commit hook — AppNavigation won't compile until Task 8)**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/artist/ArtistScreen.kt
  git commit -m "feat: add onItemClick to ArtistScreen" --no-verify
  ```

---

## Task 7: Update AlbumScreen to add onItemClick

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumScreen.kt`

- [ ] **Step 1: Replace the full AlbumScreen file**

  Replace `app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumScreen.kt`:

  ```kotlin
  package com.wolf.musicplayer.ui.screen.album

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.HorizontalDivider
  import androidx.compose.material3.ListItem
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlbumScreen(
      contentPadding: PaddingValues,
      onItemClick: (String) -> Unit,
      viewModel: AlbumViewModel = hiltViewModel(),
  ) {
      val albums by viewModel.albums.collectAsStateWithLifecycle()

      Scaffold(
          topBar = { TopAppBar(title = { Text("Alben") }) },
      ) { padding ->
          LazyColumn(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(padding)
                  .padding(contentPadding),
          ) {
              items(albums, key = { it }) { album ->
                  ListItem(
                      headlineContent = { Text(album) },
                      modifier = Modifier
                          .padding(horizontal = 8.dp)
                          .clickable { onItemClick(album) },
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/screen/album/AlbumScreen.kt
  git commit -m "feat: add onItemClick to AlbumScreen" --no-verify
  ```

---

## Task 8: Wire new routes in AppNavigation

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Replace the full AppNavigation file**

  Replace `app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt`:

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
                  PlaylistScreen(contentPadding = innerPadding)
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

- [ ] **Step 2: Build — should now succeed**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run all unit tests**

  Run: `./gradlew test`
  Expected: All tests PASS

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/wolf/musicplayer/ui/navigation/AppNavigation.kt
  git commit -m "feat: wire artists and albums detail routes in AppNavigation"
  ```

---

## Manual Verification Checklist

After all tasks are committed, install the debug APK and verify:

- [ ] Player tab: seekbar slider moves every ~0.5 s during playback
- [ ] Player tab: dragging the slider seeks to the correct position
- [ ] Artists tab: tapping an artist navigates to its detail screen showing correct songs
- [ ] Artists detail: back button returns to Artists list
- [ ] Artists detail: tapping a song starts playback from that index and navigates to Player
- [ ] Albums tab: tapping an album navigates to its detail screen showing correct songs
- [ ] Albums detail: back button returns to Albums list
- [ ] Albums detail: tapping a song starts playback from that index and navigates to Player
- [ ] Artist name with special characters (e.g. `AC/DC`) navigates correctly without crash
