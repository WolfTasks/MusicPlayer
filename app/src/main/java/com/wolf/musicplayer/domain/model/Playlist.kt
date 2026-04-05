package com.wolf.musicplayer.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val serverId: Long?,
    val isLocal: Boolean,
)
