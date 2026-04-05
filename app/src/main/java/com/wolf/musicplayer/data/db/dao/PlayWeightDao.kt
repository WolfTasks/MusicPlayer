package com.wolf.musicplayer.data.db.dao

import androidx.room.*
import com.wolf.musicplayer.data.db.entity.PlayWeightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayWeightDao {
    @Query("SELECT * FROM play_weights WHERE songId = :songId")
    suspend fun getForSong(songId: Long): PlayWeightEntity?

    @Query("SELECT * FROM play_weights")
    fun observeAll(): Flow<List<PlayWeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayWeightEntity)

    @Query("DELETE FROM play_weights WHERE songId = :songId")
    suspend fun deleteForSong(songId: Long)
}
