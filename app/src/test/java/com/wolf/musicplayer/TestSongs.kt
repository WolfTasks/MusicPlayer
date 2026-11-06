package com.wolf.musicplayer

import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.domain.model.SourceType

internal fun testSong(
    id: Long,
    title: String = "Song $id",
    artist: String = "Artist",
    album: String = "Album",
    localPath: String? = "content://media/external/audio/media/$id",
) = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = 180_000L,
    sourceType = SourceType.LOCAL,
    serverId = null,
    serverSongId = null,
    localPath = localPath,
    cachedPath = null,
)
