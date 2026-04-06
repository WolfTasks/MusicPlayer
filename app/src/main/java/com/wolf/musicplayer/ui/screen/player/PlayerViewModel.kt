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

        // Must be called on the main thread — positionTracker is not thread-safe.
        private fun startPositionTracking() {
            positionTracker?.cancel()
            positionTracker =
                viewModelScope.launch {
                    while (isActive) {
                        _state.update { s -> s.copy(position = controller?.currentPosition ?: s.position) }
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
