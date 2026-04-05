package com.wolf.musicplayer.domain.model

enum class SourceType { LOCAL, SERVER }

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,        // milliseconds
    val sourceType: SourceType,
    val serverId: Long?,
    val serverSongId: String?,
    val localPath: String?,
    val cachedPath: String?,
)
