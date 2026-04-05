package com.wolf.musicplayer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_weights",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlayWeightEntity(
    @PrimaryKey val songId: Long,
    val skipCount: Int = 0,
    val playCount: Int = 0,
    val weight: Float = 1.0f,
)
