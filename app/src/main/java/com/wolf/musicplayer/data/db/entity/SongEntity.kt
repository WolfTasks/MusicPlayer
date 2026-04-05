package com.wolf.musicplayer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    // "LOCAL" or "SERVER"
    val sourceType: String,
    val serverId: Long? = null,
    val serverSongId: String? = null,
    val localPath: String? = null,
    val cachedPath: String? = null,
)
