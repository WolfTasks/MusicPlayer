package com.wolf.musicplayer

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import com.google.common.util.concurrent.SettableFuture
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.QueueContext
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import com.wolf.musicplayer.service.MusicService
import com.wolf.musicplayer.ui.screen.player.PlayerViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val controller = mockk<MediaController>(relaxed = true)
    private val songRepository = mockk<SongRepository>(relaxed = true)
    private val playWeightRepository = mockk<PlayWeightRepository>(relaxed = true)
    private val shuffler = mockk<WeightedShuffler>()
    private val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
    private val shuffleStateHolder = mockk<ShuffleStateHolder>(relaxed = true)
    private val sessionRepository = mockk<PlaybackSessionRepository>(relaxed = true)
    private val listener = slot<Player.Listener>()

    // Completed only after the ViewModel is constructed: PlayerViewModel declares `playerListener`
    // after its init block, so a connection that completes synchronously inside init would register
    // a not-yet-initialised (null) listener. In the app the connection is always asynchronous.
    private val connection = SettableFuture.create<MediaController>()

    private val songs = listOf(song(1), song(2), song(3))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        // SessionToken(context, component) looks the service up in the PackageManager.
        val component = ComponentName(app, MusicService::class.java)
        shadowOf(app.packageManager).apply {
            addServiceIfNotPresent(component)
            addIntentFilterForService(component, IntentFilter(MediaLibraryService.SERVICE_INTERFACE))
        }
        mockkConstructor(MediaController.Builder::class)
        every { anyConstructed<MediaController.Builder>().buildAsync() } returns connection
        every { controller.addListener(capture(listener)) } returns Unit
        every { playlistRepo.observeAll() } returns flowOf(emptyList())
        every { playlistRepo.observePlaylistIdsForSong(any()) } returns flowOf(emptySet())
        coEvery { playWeightRepository.getWeight(any()) } returns 1.0f
        every { controller.repeatMode } returns Player.REPEAT_MODE_OFF
        every { controller.duration } returns 200_000L
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun song(id: Long) =
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            duration = 200_000L,
            sourceType = SourceType.LOCAL,
            serverId = null,
            serverSongId = null,
            localPath = "content://media/external/audio/media/$id",
            cachedPath = null,
        )

    private fun createViewModel(
        connect: Boolean = true,
        shuffler: WeightedShuffler = this.shuffler,
    ): PlayerViewModel {
        val vm =
            PlayerViewModel(
                RuntimeEnvironment.getApplication(),
                songRepository,
                playWeightRepository,
                shuffler,
                playlistRepo,
                shuffleStateHolder,
                sessionRepository,
            )
        if (connect) connectController()
        return vm
    }

    private fun connectController() {
        connection.set(controller)
    }

    private fun mediaIds(items: List<MediaItem>) = items.map { it.mediaId }

    /** Starts playback of [songs] at [startIndex]; the controller reports [startIndex] as current item. */
    private fun kotlinx.coroutines.test.TestScope.playing(
        vm: PlayerViewModel,
        startIndex: Int = 0,
    ) {
        every { controller.currentMediaItemIndex } returns startIndex
        vm.playSongs(songs, startIndex)
        advanceUntilIdle()
    }

    @Test
    fun `connects to the service and registers a player listener`() =
        runTest(dispatcher) {
            every { controller.repeatMode } returns Player.REPEAT_MODE_ALL
            val vm = createViewModel()

            assertTrue(listener.isCaptured)
            assertEquals(Player.REPEAT_MODE_ALL, vm.state.value.repeatMode)
        }

    @Test
    fun `playSongs without shuffle sets items in order, starts playback and saves session`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            val items = slot<List<MediaItem>>()
            every { controller.setMediaItems(capture(items), any<Int>(), any<Long>()) } returns Unit
            every { controller.currentMediaItemIndex } returns 1

            vm.playSongs(songs, startIndex = 1, queueContext = QueueContext.AlbumCtx("Album"))
            advanceUntilIdle()

            assertEquals(listOf("song:1", "song:2", "song:3"), mediaIds(items.captured))
            verify { controller.setMediaItems(any(), 1, 0L) }
            verify { controller.prepare() }
            verify { controller.play() }
            verify { sessionRepository.save(QueueContext.AlbumCtx("Album"), 2L) }
            assertEquals(2L, vm.state.value.currentSong?.id)
            assertEquals(songs, vm.state.value.queue)
            verify(exactly = 0) { shuffler.permute<Song>(any(), any()) }
        }

    @Test
    fun `playSongs with shuffle uses permuted order and starts at the chosen song`() =
        runTest(dispatcher) {
            // Enable shuffle while the controller is still connecting (toggleShuffle then only flips state).
            val vm = createViewModel(connect = false)
            vm.toggleShuffle()
            advanceUntilIdle()
            connectController()
            val permuted = listOf(songs[2], songs[0], songs[1])
            every { shuffler.permute(songs, listOf(1f, 1f, 1f)) } returns permuted
            val items = slot<List<MediaItem>>()
            every { controller.setMediaItems(capture(items), any<Int>(), any<Long>()) } returns Unit
            every { controller.currentMediaItemIndex } returns 1

            vm.playSongs(songs, startIndex = 0)
            advanceUntilIdle()

            assertEquals(listOf("song:3", "song:1", "song:2"), mediaIds(items.captured))
            // song 1 (startIndex 0 in the original list) sits at index 1 in the shuffled queue
            verify { controller.setMediaItems(any(), 1, 0L) }
            assertEquals(1L, vm.state.value.currentSong?.id)
        }

    @Test
    fun `manual skip (seek transition) records a skip for the song that was playing`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 0)

            every { controller.currentMediaItemIndex } returns 1
            listener.captured.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            advanceUntilIdle()

            coVerify(exactly = 1) { playWeightRepository.recordSkip(1L) }
            verify { sessionRepository.save(QueueContext.AllSongs, 2L) }
            assertEquals(2L, vm.state.value.currentSong?.id)
        }

    @Test
    fun `automatic transition does not record a skip`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 0)

            every { controller.currentMediaItemIndex } returns 1
            listener.captured.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            advanceUntilIdle()

            coVerify(exactly = 0) { playWeightRepository.recordSkip(any()) }
            assertEquals(2L, vm.state.value.currentSong?.id)
        }

    @Test
    fun `playback ended at 80 percent or more records a full play`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 0)
            every { controller.currentPosition } returns 160_000L // exactly 80 %

            listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            coVerify(exactly = 1) { playWeightRepository.recordFullPlay(1L) }
        }

    @Test
    fun `playback ended below 80 percent does not record a full play`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 0)
            every { controller.currentPosition } returns 159_000L

            listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            coVerify(exactly = 0) { playWeightRepository.recordFullPlay(any()) }
        }

    @Test
    fun `ready state publishes the duration`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            every { controller.duration } returns 123_000L

            listener.captured.onPlaybackStateChanged(Player.STATE_READY)

            assertEquals(123_000L, vm.state.value.duration)
        }

    @Test
    fun `pausing saves the session with the current position`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 2)
            every { controller.currentPosition } returns 42_000L

            listener.captured.onIsPlayingChanged(false)
            advanceUntilIdle()

            assertEquals(false, vm.state.value.isPlaying)
            verify { sessionRepository.save(QueueContext.AllSongs, 3L, 42_000L) }
        }

    @Test
    fun `repeat mode changes from the player are reflected in state`() =
        runTest(dispatcher) {
            val vm = createViewModel()

            listener.captured.onRepeatModeChanged(Player.REPEAT_MODE_ONE)

            assertEquals(Player.REPEAT_MODE_ONE, vm.state.value.repeatMode)
        }

    @Test
    fun `cycleRepeatMode goes OFF to ALL to ONE to OFF`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            val expectedNext =
                mapOf(
                    Player.REPEAT_MODE_OFF to Player.REPEAT_MODE_ALL,
                    Player.REPEAT_MODE_ALL to Player.REPEAT_MODE_ONE,
                    Player.REPEAT_MODE_ONE to Player.REPEAT_MODE_OFF,
                )
            for ((current, next) in expectedNext) {
                listener.captured.onRepeatModeChanged(current)
                vm.cycleRepeatMode()
                verify { controller.repeatMode = next }
            }
        }

    @Test
    fun `playPause pauses while playing and plays while paused`() =
        runTest(dispatcher) {
            val vm = createViewModel()

            every { controller.isPlaying } returns true
            vm.playPause()
            verify(exactly = 1) { controller.pause() }

            every { controller.isPlaying } returns false
            vm.playPause()
            verify(exactly = 1) { controller.play() }
        }

    @Test
    fun `skip buttons delegate to the controller`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            vm.skipToNext()
            vm.skipToPrevious()
            verify { controller.seekToNextMediaItem() }
            verify { controller.seekToPreviousMediaItem() }
        }

    @Test
    fun `seekTo moves the player and updates position immediately`() =
        runTest(dispatcher) {
            val vm = createViewModel()

            vm.seekTo(30_000L)

            verify { controller.seekTo(30_000L) }
            assertEquals(30_000L, vm.state.value.position)
        }

    @Test
    fun `toggleShuffle on reorders the queue around the current song and keeps position`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            playing(vm, startIndex = 1) // song 2 playing
            every { controller.isPlaying } returns true
            every { controller.currentPosition } returns 55_000L
            val permuted = listOf(songs[2], songs[1], songs[0])
            every { shuffler.permute(songs, any()) } returns permuted
            val items = slot<List<MediaItem>>()
            every { controller.setMediaItems(capture(items), any<Int>(), any<Long>()) } returns Unit

            vm.toggleShuffle()
            advanceUntilIdle()

            verify { shuffleStateHolder.setShuffleEnabled(true) }
            assertTrue(vm.state.value.shuffleEnabled)
            assertEquals(listOf("song:3", "song:2", "song:1"), mediaIds(items.captured))
            verify { controller.setMediaItems(any(), 1, 55_000L) }
            verify { sessionRepository.save(QueueContext.AllSongs, 2L, 55_000L) }
            verify(exactly = 2) { controller.play() } // once from playSongs, once to resume
        }

    @Test
    fun `toggleShuffle off restores original order and does not resume a paused player`() =
        runTest(dispatcher) {
            val vm = createViewModel(connect = false)
            vm.toggleShuffle() // on, before the controller is connected
            advanceUntilIdle()
            connectController()
            every { shuffler.permute(songs, any()) } returns songs.reversed()
            playing(vm, startIndex = 0)
            every { controller.isPlaying } returns false
            every { controller.currentPosition } returns 10_000L
            val items = slot<List<MediaItem>>()
            every { controller.setMediaItems(capture(items), any<Int>(), any<Long>()) } returns Unit

            vm.toggleShuffle() // off
            advanceUntilIdle()

            verify { shuffleStateHolder.setShuffleEnabled(false) }
            assertEquals(listOf("song:1", "song:2", "song:3"), mediaIds(items.captured))
            verify(exactly = 1) { controller.play() } // only the initial playSongs
        }

    @Test
    fun `nextWeightedSong is null for an empty queue and uses weighted pick otherwise`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            assertNull(vm.nextWeightedSong())

            playing(vm)
            coEvery { playWeightRepository.getWeight(2L) } returns 0.5f
            every { shuffler.pick(songs, listOf(1f, 0.5f, 1f)) } returns songs[2]

            assertEquals(songs[2], vm.nextWeightedSong())
        }

    @Test
    fun `addSongToPlaylist appends at the end of the playlist`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            coEvery { playlistRepo.getSongsForPlaylist(7L) } returns listOf(song(10), song(11))

            vm.addSongToPlaylist(songs[0], Playlist(id = 7L, name = "Mix", serverId = null, isLocal = true))
            advanceUntilIdle()

            coVerify { playlistRepo.addSong(7L, 1L, 2) }
        }

    @Test
    fun `createPlaylistAndAddSong creates the playlist and adds the song first`() =
        runTest(dispatcher) {
            val vm = createViewModel()
            coEvery { playlistRepo.create("Neu") } returns 9L

            vm.createPlaylistAndAddSong("Neu", songs[1])
            advanceUntilIdle()

            coVerify { playlistRepo.addSong(9L, 2L, 0) }
        }

    // --- Regression: empty queues must not crash (permute([], []) used to throw) ---

    // Regression: with a connected controller but nothing queued, toggleShuffle() called
    // WeightedShuffler.permute([], []), whose require() threw inside viewModelScope → crash
    // when the user presses shuffle before playing anything.
    @Test
    fun `toggleShuffle before anything was played does not crash`() =
        runTest(dispatcher) {
            val vm = createViewModel(shuffler = WeightedShuffler())

            vm.toggleShuffle()
            advanceUntilIdle()

            assertTrue(vm.state.value.shuffleEnabled)
        }

    // Regression: playSongs(emptyList()) with shuffle on called WeightedShuffler.permute([], []) → crash
    // (e.g. playing an empty playlist or album while shuffle is enabled).
    @Test
    fun `playSongs with an empty list and shuffle enabled does not crash`() =
        runTest(dispatcher) {
            val vm = createViewModel(connect = false, shuffler = WeightedShuffler())
            vm.toggleShuffle()
            advanceUntilIdle()
            connectController()

            vm.playSongs(emptyList())
            advanceUntilIdle()

            verify(exactly = 0) { controller.play() }
        }
}
