package com.wolf.musicplayer.domain.model

sealed class QueueContext {
    object AllSongs : QueueContext()

    data class PlaylistCtx(
        val id: Long,
    ) : QueueContext()

    data class AlbumCtx(
        val name: String,
    ) : QueueContext()

    data class ArtistCtx(
        val name: String,
    ) : QueueContext()
}
