package com.wolf.musicplayer.service

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
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.domain.ShuffleStateHolder
import com.wolf.musicplayer.domain.WeightedShuffler
import com.wolf.musicplayer.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLibraryCallback
    @Inject
    constructor(
        internal val songRepository: SongRepository,
        internal val playlistRepository: PlaylistRepository,
        internal val playWeightRepository: PlayWeightRepository,
        internal val shuffler: WeightedShuffler,
        internal val shuffleStateHolder: ShuffleStateHolder,
        internal val scope: CoroutineScope,
    ) : MediaLibrarySession.Callback {
        companion object {
            const val ROOT_ID = "root"
            const val SONGS_ID = "songs"
            const val PLAYLISTS_ID = "playlists"
            const val ALBUMS_ID = "albums"
            const val ARTISTS_ID = "artists"
        }

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
            scope.launch {
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
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            scope.launch {
                val songId = parseSongId(mediaId)
                val song = songId?.let { songRepository.getById(it) }
                if (song != null) {
                    future.set(LibraryResult.ofItem(song.toMediaItem(mediaId), null))
                } else {
                    future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
            }
            return future
        }

        // ── Playback stub — full implementation in Task 5 ────────────────────────

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
            )

        // ── Lifecycle ────────────────────────────────────────────────────────────

        fun release() {
            scope.coroutineContext[Job]?.cancel()
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
