package com.wolf.musicplayer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,
    val passwordEncrypted: String,
    val type: String,            // "SUBSONIC" or "JELLYFIN"
    val isActive: Boolean = true,
)
