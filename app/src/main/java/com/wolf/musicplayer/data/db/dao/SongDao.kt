package com.wolf.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wolf.musicplayer.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE sourceType = 'LOCAL' ORDER BY title ASC")
    fun observeLocal(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, title")
    fun observeByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
    fun observeByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    fun observeAlbums(): Flow<List<String>>

    // NOTE: Queries below are intentionally identical to their observe* counterparts.
    // If any observe* query changes, update the matching get* query and vice versa.
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, title")
    suspend fun getByArtist(artist: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
    suspend fun getByAlbum(album: String): List<SongEntity>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    suspend fun getArtists(): List<String>

    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    suspend fun getAlbums(): List<String>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs WHERE sourceType = 'LOCAL'")
    suspend fun deleteAllLocal()
}
