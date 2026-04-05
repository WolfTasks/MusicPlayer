package com.wolf.musicplayer.domain.model

data class PlayWeight(
    val songId: Long,
    val skipCount: Int = 0,
    val playCount: Int = 0,
    val weight: Float = 1.0f,
)
