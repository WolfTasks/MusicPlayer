package com.wolf.musicplayer

import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.SourceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRepositoryTest {
    private val dao = mockk<PlaylistDao>()
    private val repo = PlaylistRepository(dao)

    @Test
    fun `observePlaylistIdsForSong returns set of playlist ids`() =
        runTest {
            every { dao.observePlaylistIdsForSong(42L) } returns flowOf(listOf(1L, 3L))

            val result = repo.observePlaylistIdsForSong(42L).first()

            assertEquals(setOf(1L, 3L), result)
        }

    @Test
    fun `getSongsForPlaylist maps entities to domain songs`() =
        runTest {
            val entity =
                SongEntity(
                    id = 7L,
                    title = "Test",
                    artist = "A",
                    album = "B",
                    duration = 1000L,
                    sourceType = "LOCAL",
                    serverId = null,
                    serverSongId = null,
                    localPath = "/p",
                    cachedPath = null,
                )
            coEvery { dao.getSongsForPlaylist(10L) } returns listOf(entity)

            val songs = repo.getSongsForPlaylist(10L)

            assertEquals(1, songs.size)
            assertEquals("Test", songs[0].title)
            assertEquals(7L, songs[0].id)
            assertEquals(SourceType.LOCAL, songs[0].sourceType)
        }
}
