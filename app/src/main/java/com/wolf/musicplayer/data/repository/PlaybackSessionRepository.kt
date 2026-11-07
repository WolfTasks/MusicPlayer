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
            positionMs: Long = 0L,
        ) {
            prefs
                .edit()
                .putString("context_type", queueContext.toTypeString())
                .putString("context_id", queueContext.toIdString())
                .putLong("last_song_id", songId)
                .putLong("position_ms", positionMs)
                .apply()
        }

        fun load(): Triple<QueueContext, Long, Long>? {
            val type = prefs.getString("context_type", null) ?: return null
            val id = prefs.getString("context_id", null) ?: ""
            val songId = prefs.getLong("last_song_id", -1L)
            if (songId == -1L) return null
            val positionMs = prefs.getLong("position_ms", 0L)
            val ctx =
                when (type) {
                    "ALL" -> QueueContext.AllSongs

                    "PLAYLIST" -> QueueContext.PlaylistCtx(id.toLongOrNull() ?: return null)

                    // Names are used as keys and may not survive a media rescan/tag edit — fallback handled by caller
                    "ALBUM" -> QueueContext.AlbumCtx(id)

                    "ARTIST" -> QueueContext.ArtistCtx(id)

                    else -> return null
                }
            return Triple(ctx, songId, positionMs)
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
