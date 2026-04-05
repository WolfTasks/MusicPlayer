package com.wolf.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wolf.musicplayer.data.db.dao.PlayWeightDao
import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.dao.SongDao
import com.wolf.musicplayer.data.db.entity.PlayWeightEntity
import com.wolf.musicplayer.data.db.entity.PlaylistEntity
import com.wolf.musicplayer.data.db.entity.PlaylistSongEntity
import com.wolf.musicplayer.data.db.entity.ServerEntity
import com.wolf.musicplayer.data.db.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlayWeightEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        ServerEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun playWeightDao(): PlayWeightDao

    abstract fun playlistDao(): PlaylistDao
}
