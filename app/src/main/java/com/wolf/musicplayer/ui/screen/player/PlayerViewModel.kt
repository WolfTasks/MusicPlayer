package com.wolf.musicplayer.ui.screen.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.QueueContext
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.service.MusicService
import com.wolf.musicplayer.service.parseSongId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private val playlistRepo: PlaylistRepository,
        private val shuffleStateHolder: ShuffleStateHolder,
        private val playbackSessionRepository: PlaybackSessionRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlayerState())
        val state: StateFlow<PlayerState> = _state.asStateFlow()

        val playlists =
            playlistRepo.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        val songPlaylists =
            state
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId != null) {
                        playlistRepo.observePlaylistIdsForSong(songId)
                    } else {
                        flowOf(emptySet())
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        private val _addToPlaylistSong = MutableStateFlow<Song?>(null)
        val addToPlaylistSong: StateFlow<Song?> = _addToPlaylistSong.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val addToPlaylistSongPlaylists: StateFlow<Set<Long>> =
            _addToPlaylistSong
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId != null) {
                        playlistRepo.observePlaylistIdsForSong(songId)
                    } else {
                        flowOf(emptySet())
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun setAddToPlaylistSong(song: Song?) {
            _addToPlaylistSong.value = song
        }

        private var controller: MediaController? = null
        private var currentQueue: List<Song> = emptyList()
        private var activeQueue: List<Song> = emptyList()
        private var currentSongId: Long? = null
        private var playbackStartPosition: Long = 0L
        private var currentQueueContext: QueueContext = QueueContext.AllSongs
        private var positionTracker: Job? = null
        private var positionSaveCounter = 0
        private var pendingInternalQueueSetCount = 0

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
                    if (isPlaying) {
                        startPositionTracking()
                    } else {
                        stopPositionTracking()
                        currentSongId?.let { id ->
                            viewModelScope.launch {
                                playbackSessionRepository.save(
                                    currentQueueContext,
                                    id,
                                    controller?.currentPosition ?: _state.value.position,
                                )
                            }
                        }
                    }
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
                    val newIdx = controller?.currentMediaItemIndex ?: return
                    activeQueue.getOrNull(newIdx)?.let { song ->
                        viewModelScope.launch {
                            playbackSessionRepository.save(currentQueueContext, song.id)
                        }
                    }
                    syncStateFromController()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        val ctrl = controller ?: return
                        _state.update { it.copy(duration = ctrl.duration.coerceAtLeast(0L)) }
                    }
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

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _state.update { it.copy(repeatMode = repeatMode) }
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    if (pendingInternalQueueSetCount > 0) {
                        pendingInternalQueueSetCount--
                        return
                    }
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        viewModelScope.launch { rebuildActiveQueueFromController() }
                    }
                }
            }

        // Must be called on the main thread — positionTracker is not thread-safe.
        private fun startPositionTracking() {
            positionTracker?.cancel()
            positionTracker =
                viewModelScope.launch {
                    positionSaveCounter = 0
                    while (isActive) {
                        val pos = controller?.currentPosition ?: _state.value.position
                        _state.update { s ->
                            s.copy(
                                position = pos,
                                duration = controller?.duration?.coerceAtLeast(0L) ?: s.duration,
                            )
                        }
                        positionSaveCounter++
                        if (positionSaveCounter >= 20) {
                            positionSaveCounter = 0
                            currentSongId?.let { id ->
                                playbackSessionRepository.save(currentQueueContext, id, pos)
                            }
                        }
                        delay(500)
                    }
                }
        }

        // Must be called on the main thread — positionTracker is not thread-safe.
        private fun stopPositionTracking() {
            positionTracker?.cancel()
            positionTracker = null
        }

        private fun syncStateFromController() {
            val ctrl = controller ?: return
            val idx = ctrl.currentMediaItemIndex
            val song = activeQueue.getOrNull(idx)
            currentSongId = song?.id
            _state.update {
                it.copy(
                    currentSong = song,
                    isPlaying = ctrl.isPlaying,
                    duration = ctrl.duration.coerceAtLeast(0L),
                    queue = currentQueue,
                    repeatMode = ctrl.repeatMode,
                )
            }
        }

        private suspend fun rebuildActiveQueueFromController() {
            val ctrl = controller ?: return
            val count = ctrl.mediaItemCount
            if (count == 0) return
            val ids = (0 until count).mapNotNull { i -> parseSongId(ctrl.getMediaItemAt(i).mediaId) }
            if (ids.isEmpty()) return
            val songsById = songRepository.getByIds(ids).associateBy { it.id }
            val rebuilt = ids.mapNotNull { songsById[it] }
            if (rebuilt.isEmpty()) return
            positionSaveCounter = 0
            activeQueue = rebuilt
            currentQueue = rebuilt
            playbackSessionRepository.load()?.let { triple -> currentQueueContext = triple.first }
            syncStateFromController()
        }

        fun playSongs(
            songs: List<Song>,
            startIndex: Int = 0,
            queueContext: QueueContext = QueueContext.AllSongs,
        ) {
            val ctrl = controller ?: return
            currentQueue = songs
            currentQueueContext = queueContext
            viewModelScope.launch {
                activeQueue =
                    if (_state.value.shuffleEnabled) {
                        val weights = songs.map { playWeightRepository.getWeight(it.id) }
                        shuffler.permute(songs, weights)
                    } else {
                        songs
                    }
                val startSong = songs.getOrNull(startIndex)
                val activeIndex =
                    if (startSong != null) {
                        activeQueue.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
                    } else {
                        0
                    }
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId("song:${song.id}")
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
                // Save before setMediaItems so cold-start resumption sees the session even if the process is killed during prepare
                activeQueue.getOrNull(activeIndex)?.let { song ->
                    playbackSessionRepository.save(queueContext, song.id)
                }
                currentSongId = null
                pendingInternalQueueSetCount++
                try {
                    ctrl.setMediaItems(items, activeIndex, 0L)
                } catch (e: Exception) {
                    pendingInternalQueueSetCount--
                    throw e
                }
                ctrl.prepare()
                ctrl.play()
                syncStateFromController()
            }
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
            viewModelScope.launch {
                val enabled = !_state.value.shuffleEnabled
                shuffleStateHolder.setShuffleEnabled(enabled)
                _state.update { it.copy(shuffleEnabled = enabled) }
                val ctrl = controller ?: return@launch
                val songId = currentSongId
                val position = ctrl.currentPosition
                val wasPlaying = ctrl.isPlaying
                activeQueue =
                    if (enabled) {
                        val weights = currentQueue.map { playWeightRepository.getWeight(it.id) }
                        shuffler.permute(currentQueue, weights)
                    } else {
                        currentQueue
                    }
                val newIndex = activeQueue.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                val items =
                    activeQueue.map { song ->
                        MediaItem.Builder()
                            .setMediaId("song:${song.id}")
                            .setUri(song.localPath ?: song.cachedPath ?: "")
                            .build()
                    }
                currentSongId = null
                activeQueue.getOrNull(newIndex)?.let { song ->
                    playbackSessionRepository.save(currentQueueContext, song.id, position)
                }
                pendingInternalQueueSetCount++
                try {
                    ctrl.setMediaItems(items, newIndex, position)
                } catch (e: Exception) {
                    pendingInternalQueueSetCount--
                    throw e
                }
                ctrl.prepare()
                if (wasPlaying) ctrl.play()
                syncStateFromController()
            }
        }

        fun cycleRepeatMode() {
            val next =
                when (_state.value.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            controller?.repeatMode = next
        }

        suspend fun nextWeightedSong(): Song? {
            val songs = currentQueue.ifEmpty { return null }
            val weights = songs.map { playWeightRepository.getWeight(it.id) }
            return shuffler.pick(songs, weights)
        }

        fun addSongToPlaylist(
            song: Song,
            playlist: Playlist,
        ) {
            viewModelScope.launch {
                val currentCount = playlistRepo.getSongsForPlaylist(playlist.id).size
                playlistRepo.addSong(playlist.id, song.id, currentCount)
            }
        }

        fun createPlaylistAndAddSong(
            name: String,
            song: Song,
        ) {
            viewModelScope.launch {
                val playlistId = playlistRepo.create(name)
                playlistRepo.addSong(playlistId, song.id, 0)
            }
        }

        override fun onCleared() {
            positionTracker?.cancel()
            controller?.release()
            super.onCleared()
        }
    }
