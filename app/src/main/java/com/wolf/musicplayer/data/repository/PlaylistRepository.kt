package com.wolf.musicplayer.data.repository

import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.entity.PlaylistEntity
import com.wolf.musicplayer.data.db.entity.PlaylistSongEntity
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository
    @Inject
    constructor(
        private val dao: PlaylistDao,
    ) {
        fun observeAll(): Flow<List<Playlist>> = dao.observeAll().map { it.map(PlaylistEntity::toDomain) }

        fun observeSongsForPlaylist(playlistId: Long): Flow<List<Song>> =
            dao.observeSongsForPlaylist(playlistId).map { it.map(SongEntity::toDomain) }

        fun observePlaylistIdsForSong(songId: Long): Flow<Set<Long>> = dao.observePlaylistIdsForSong(songId).map { it.toSet() }

        suspend fun getSongsForPlaylist(playlistId: Long): List<Song> = dao.getSongsForPlaylist(playlistId).map(SongEntity::toDomain)

        suspend fun getAll(): List<Playlist> = dao.getAll().map(PlaylistEntity::toDomain)

        suspend fun create(name: String): Long = dao.insert(PlaylistEntity(name = name))

        suspend fun rename(
            id: Long,
            name: String,
        ) {
            val existing = dao.getById(id) ?: return
            dao.update(existing.copy(name = name))
        }

        suspend fun delete(id: Long) {
            val existing = dao.getById(id) ?: return
            dao.delete(existing)
        }

        suspend fun addSong(
            playlistId: Long,
            songId: Long,
            position: Int,
        ) {
            dao.insertSong(PlaylistSongEntity(playlistId, songId, position))
        }

        suspend fun removeSong(
            playlistId: Long,
            songId: Long,
        ) {
            dao.removeSong(playlistId, songId)
        }
    }

private fun PlaylistEntity.toDomain() =
    Playlist(
        id = id,
        name = name,
        serverId = serverId,
        isLocal = isLocal,
    )

private fun SongEntity.toDomain() =
    Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        sourceType = if (sourceType == "LOCAL") SourceType.LOCAL else SourceType.SERVER,
        serverId = serverId,
        serverSongId = serverSongId,
        localPath = localPath,
        cachedPath = cachedPath,
    )
