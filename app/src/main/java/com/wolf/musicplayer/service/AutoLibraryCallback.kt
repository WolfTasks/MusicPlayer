package com.wolf.musicplayer.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.di.ApplicationScope
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.QueueContext
import com.wolf.musicplayer.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLibraryCallback
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songRepository: SongRepository,
        private val playlistRepository: PlaylistRepository,
        private val playWeightRepository: PlayWeightRepository,
        private val shuffler: WeightedShuffler,
        private val shuffleStateHolder: ShuffleStateHolder,
        private val playbackSessionRepository: PlaybackSessionRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : MediaLibrarySession.Callback {
        companion object {
            const val ROOT_ID = "root"
            const val SONGS_ID = "songs"
            const val PLAYLISTS_ID = "playlists"
            const val ALBUMS_ID = "albums"
            const val ARTISTS_ID = "artists"
        }

        private var callbackJob = SupervisorJob(scope.coroutineContext[Job])
        private var callbackScope = CoroutineScope(scope.coroutineContext + callbackJob)

        // ── Browse ──────────────────────────────────────────────────────────────

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root =
                MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build(),
                    )
                    .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            callbackScope.launch {
                try {
                    val items: List<MediaItem> =
                        when {
                            parentId == ROOT_ID -> buildRootCategories()
                            parentId == SONGS_ID -> buildAllSongItems()
                            parentId == PLAYLISTS_ID -> buildPlaylistItems()
                            parentId == ALBUMS_ID -> buildAlbumItems()
                            parentId == ARTISTS_ID -> buildArtistItems()
                            parentId.startsWith("playlist:") -> {
                                val id = parentId.removePrefix("playlist:").toLongOrNull()
                                if (id != null) buildPlaylistSongItems(parentId, id) else emptyList()
                            }
                            parentId.startsWith("album:") ->
                                buildAlbumSongItems(parentId, parentId.removePrefix("album:"))
                            parentId.startsWith("artist:") ->
                                buildArtistSongItems(parentId, parentId.removePrefix("artist:"))
                            else -> emptyList()
                        }
                    future.set(LibraryResult.ofItemList(items, null))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            callbackScope.launch {
                try {
                    val songId = parseSongId(mediaId)
                    val song = songId?.let { songRepository.getById(it) }
                    if (song != null) {
                        future.set(LibraryResult.ofItem(song.toMediaItem(mediaId), null))
                    } else {
                        future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
                    }
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        // ── Subscription ────────────────────────────────────────────────────────────

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
        ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

        // ── Playback ────────────────────────────────────────────────────────────

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            callbackScope.launch {
                try {
                    val savedSession = playbackSessionRepository.load()
                    val rawSongs =
                        if (savedSession != null) {
                            val (ctx, _, _) = savedSession
                            val ctxSongs =
                                when (ctx) {
                                    is QueueContext.AllSongs -> songRepository.getAll()
                                    is QueueContext.PlaylistCtx -> playlistRepository.getSongsForPlaylist(ctx.id)
                                    is QueueContext.AlbumCtx -> songRepository.getByAlbum(ctx.name)
                                    is QueueContext.ArtistCtx -> songRepository.getByArtist(ctx.name)
                                }
                            // context exists but has no songs (e.g. deleted playlist) — fall back to full library
                            ctxSongs.ifEmpty { songRepository.getAll() }
                        } else {
                            songRepository.getAll()
                        }
                    val queue =
                        if (shuffleStateHolder.shuffleEnabled) {
                            val weights = rawSongs.map { playWeightRepository.getWeight(it.id) }
                            shuffler.permute(rawSongs, weights)
                        } else {
                            rawSongs
                        }
                    val startIndex =
                        if (savedSession != null) {
                            queue.indexOfFirst { it.id == savedSession.second }.coerceAtLeast(0)
                        } else {
                            0
                        }
                    val items = queue.map { it.toMediaItem("song:${it.id}") }
                    val startPositionMs = savedSession?.third ?: 0L
                    future.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs))
                } catch (e: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // In-app controller (PlayerViewModel): queue already built; pass through unchanged.
            if (controller.packageName == context.packageName) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
                )
            }
            // Android Auto: resolve the single browse-tree mediaId into a full queue.
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            callbackScope.launch {
                try {
                    val requestedId = mediaItems.firstOrNull()?.mediaId
                    if (requestedId == null) {
                        future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                        return@launch
                    }
                    val (songs, requestedSongId) = resolveQueue(requestedId)
                    if (songs.isEmpty() || requestedSongId == null) {
                        future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                        return@launch
                    }
                    val queue =
                        if (shuffleStateHolder.shuffleEnabled) {
                            val weights = songs.map { playWeightRepository.getWeight(it.id) }
                            shuffler.permute(songs, weights)
                        } else {
                            songs
                        }
                    val idx = queue.indexOfFirst { it.id == requestedSongId }.coerceAtLeast(0)
                    val resolvedItems = queue.map { it.toMediaItem("song:${it.id}") }
                    future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, idx, startPositionMs))
                } catch (e: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return future
        }

        private suspend fun resolveQueue(mediaId: String): Pair<List<Song>, Long?> {
            return when {
                mediaId.startsWith("album:") -> {
                    val rest = mediaId.removePrefix("album:")
                    val slashIdx = rest.indexOf("/song:")
                    if (slashIdx < 0) {
                        Pair(emptyList(), null)
                    } else {
                        val albumName = rest.substring(0, slashIdx)
                        val songId = rest.substring(slashIdx + 6).toLongOrNull()
                        if (songId == null) {
                            Pair(emptyList(), null)
                        } else {
                            Pair(songRepository.getByAlbum(albumName), songId)
                        }
                    }
                }
                mediaId.startsWith("artist:") -> {
                    val rest = mediaId.removePrefix("artist:")
                    val slashIdx = rest.indexOf("/song:")
                    if (slashIdx < 0) {
                        Pair(emptyList(), null)
                    } else {
                        val artistName = rest.substring(0, slashIdx)
                        val songId = rest.substring(slashIdx + 6).toLongOrNull()
                        if (songId == null) {
                            Pair(emptyList(), null)
                        } else {
                            Pair(songRepository.getByArtist(artistName), songId)
                        }
                    }
                }
                mediaId.startsWith("playlist:") -> {
                    val rest = mediaId.removePrefix("playlist:")
                    val slashIdx = rest.indexOf("/song:")
                    if (slashIdx < 0) {
                        Pair(emptyList(), null)
                    } else {
                        val playlistId = rest.substring(0, slashIdx).toLongOrNull()
                        if (playlistId == null) {
                            Pair(emptyList(), null)
                        } else {
                            val songId = rest.substring(slashIdx + 6).toLongOrNull()
                            if (songId == null) {
                                Pair(emptyList(), null)
                            } else {
                                Pair(playlistRepository.getSongsForPlaylist(playlistId), songId)
                            }
                        }
                    }
                }
                mediaId.startsWith("song:") -> {
                    val songId = mediaId.removePrefix("song:").toLongOrNull()
                    if (songId == null) {
                        Pair(emptyList(), null)
                    } else {
                        Pair(songRepository.getAll(), songId)
                    }
                }
                else -> Pair(emptyList(), null)
            }
        }

        // ── Lifecycle ────────────────────────────────────────────────────────────

        fun release() {
            callbackJob.cancel()
            callbackJob = SupervisorJob(scope.coroutineContext[Job])
            callbackScope = CoroutineScope(scope.coroutineContext + callbackJob)
        }

        // ── Private helpers ──────────────────────────────────────────────────────

        private fun buildRootCategories(): List<MediaItem> =
            listOf(
                buildBrowsableItem(SONGS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                buildBrowsableItem(PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                buildBrowsableItem(ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                buildBrowsableItem(ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            )

        private suspend fun buildAllSongItems(): List<MediaItem> = songRepository.getAll().map { it.toMediaItem("song:${it.id}") }

        private suspend fun buildPlaylistItems(): List<MediaItem> =
            playlistRepository.getAll().map { playlist ->
                buildBrowsableItem("playlist:${playlist.id}", playlist.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }

        private suspend fun buildAlbumItems(): List<MediaItem> =
            songRepository.getAlbums().map { album ->
                buildBrowsableItem("album:$album", album, MediaMetadata.MEDIA_TYPE_ALBUM)
            }

        private suspend fun buildArtistItems(): List<MediaItem> =
            songRepository.getArtists().map { artist ->
                buildBrowsableItem("artist:$artist", artist, MediaMetadata.MEDIA_TYPE_ARTIST)
            }

        private suspend fun buildPlaylistSongItems(
            parentId: String,
            playlistId: Long,
        ): List<MediaItem> =
            playlistRepository.getSongsForPlaylist(playlistId)
                .map { it.toMediaItem("$parentId/song:${it.id}") }

        private suspend fun buildAlbumSongItems(
            parentId: String,
            albumName: String,
        ): List<MediaItem> = songRepository.getByAlbum(albumName).map { it.toMediaItem("$parentId/song:${it.id}") }

        private suspend fun buildArtistSongItems(
            parentId: String,
            artistName: String,
        ): List<MediaItem> = songRepository.getByArtist(artistName).map { it.toMediaItem("$parentId/song:${it.id}") }

        private fun buildBrowsableItem(
            mediaId: String,
            title: String,
            mediaType: Int,
        ): MediaItem =
            MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build(),
                )
                .build()

        internal fun parseSongId(mediaId: String): Long? =
            when {
                mediaId.startsWith("song:") -> mediaId.removePrefix("song:").toLongOrNull()
                mediaId.contains("/song:") -> mediaId.substringAfterLast("/song:").toLongOrNull()
                else -> null
            }
    }

internal fun Song.toMediaItem(mediaId: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(localPath ?: cachedPath ?: "")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
