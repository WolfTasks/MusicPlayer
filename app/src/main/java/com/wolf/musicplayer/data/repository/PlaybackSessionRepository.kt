package com.wolf.musicplayer.data.repository

import android.content.Context
import com.wolf.musicplayer.domain.model.QueueContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("playback_session", Context.MODE_PRIVATE)

        fun save(
            queueContext: QueueContext,
            songId: Long,
        ) {
            prefs.edit()
                .putString("context_type", queueContext.toTypeString())
                .putString("context_id", queueContext.toIdString())
                .putLong("last_song_id", songId)
                .apply()
        }

        fun load(): Pair<QueueContext, Long>? {
            val type = prefs.getString("context_type", null) ?: return null
            val id = prefs.getString("context_id", null) ?: ""
            val songId = prefs.getLong("last_song_id", -1L)
            if (songId == -1L) return null
            val ctx =
                when (type) {
                    "ALL" -> QueueContext.AllSongs
                    "PLAYLIST" -> QueueContext.PlaylistCtx(id.toLongOrNull() ?: return null)
                    "ALBUM" -> QueueContext.AlbumCtx(id)
                    "ARTIST" -> QueueContext.ArtistCtx(id)
                    else -> return null
                }
            return Pair(ctx, songId)
        }

        private fun QueueContext.toTypeString() =
            when (this) {
                is QueueContext.AllSongs -> "ALL"
                is QueueContext.PlaylistCtx -> "PLAYLIST"
                is QueueContext.AlbumCtx -> "ALBUM"
                is QueueContext.ArtistCtx -> "ARTIST"
            }

        private fun QueueContext.toIdString() =
            when (this) {
                is QueueContext.AllSongs -> ""
                is QueueContext.PlaylistCtx -> id.toString()
                is QueueContext.AlbumCtx -> name
                is QueueContext.ArtistCtx -> name
            }
    }
