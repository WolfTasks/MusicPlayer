package com.wolf.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wolf.musicplayer.data.db.entity.PlaylistEntity
import com.wolf.musicplayer.data.db.entity.PlaylistSongEntity
import com.wolf.musicplayer.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY name ASC")
  fun observeAll(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists WHERE id = :id")
  suspend fun getById(id: Long): PlaylistEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(playlist: PlaylistEntity): Long

  @Update
  suspend fun update(playlist: PlaylistEntity)

  @Delete
  suspend fun delete(playlist: PlaylistEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSong(entry: PlaylistSongEntity)

  @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
  suspend fun removeSong(
    playlistId: Long,
    songId: Long,
  )

  @Query(
    """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """,
  )
  fun observeSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

  @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
  fun observePlaylistIdsForSong(songId: Long): Flow<List<Long>>

  // NOTE: SQL is intentionally identical to observeSongsForPlaylist. If the query changes, update both.
  @Query(
    """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """,
  )
  suspend fun getSongsForPlaylist(playlistId: Long): List<SongEntity>
}
