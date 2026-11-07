package com.wolf.musicplayer.data.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.wolf.musicplayer.data.db.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun scanLocalMusic(): List<SongEntity> {
            val songs = mutableListOf<SongEntity>()
            val projection =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val contentUri =
                            ContentUris
                                .withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    mediaId,
                                ).toString()

                        songs +=
                            SongEntity(
                                title = cursor.getString(titleCol) ?: "Unknown",
                                artist = cursor.getString(artistCol) ?: "Unknown Artist",
                                album = cursor.getString(albumCol) ?: "Unknown Album",
                                duration = cursor.getLong(durationCol),
                                sourceType = "LOCAL",
                                localPath = contentUri,
                            )
                    }
                }
            return songs
        }
    }
