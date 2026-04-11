package com.wolf.musicplayer.data.repository

import com.wolf.musicplayer.data.db.dao.SongDao
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.data.source.LocalDataSource
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository
    @Inject
    constructor(
        private val dao: SongDao,
        private val localDataSource: LocalDataSource,
    ) {
        fun observeAll(): Flow<List<Song>> = dao.observeAll().map { it.map(SongEntity::toDomain) }

        fun observeArtists(): Flow<List<String>> = dao.observeArtists()

        fun observeAlbums(): Flow<List<String>> = dao.observeAlbums()

        fun observeByArtist(artist: String): Flow<List<Song>> = dao.observeByArtist(artist).map { it.map(SongEntity::toDomain) }

        fun observeByAlbum(album: String): Flow<List<Song>> = dao.observeByAlbum(album).map { it.map(SongEntity::toDomain) }

        suspend fun syncLocalMusic() {
            dao.deleteAllLocal()
            val scanned = localDataSource.scanLocalMusic()
            dao.insertAll(scanned)
        }

        suspend fun getById(id: Long): Song? = dao.getById(id)?.toDomain()

        suspend fun getAll(): List<Song> = dao.getAll().map(SongEntity::toDomain)

        suspend fun getByAlbum(album: String): List<Song> = dao.getByAlbum(album).map(SongEntity::toDomain)

        suspend fun getByArtist(artist: String): List<Song> = dao.getByArtist(artist).map(SongEntity::toDomain)

        suspend fun getAlbums(): List<String> = dao.getAlbums()

        suspend fun getArtists(): List<String> = dao.getArtists()
    }

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
