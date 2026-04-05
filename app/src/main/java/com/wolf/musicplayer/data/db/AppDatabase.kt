package com.wolf.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.dao.PlayWeightDao
import com.wolf.musicplayer.data.db.dao.SongDao
import com.wolf.musicplayer.data.db.entity.*

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
