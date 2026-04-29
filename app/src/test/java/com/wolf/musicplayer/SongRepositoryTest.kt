package com.wolf.musicplayer

import com.wolf.musicplayer.data.db.dao.SongDao
import com.wolf.musicplayer.data.db.entity.SongEntity
import com.wolf.musicplayer.data.repository.SongRepository
import com.wolf.musicplayer.data.source.LocalDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SongRepositoryTest {
    private val dao = mockk<SongDao>(relaxed = true)
    private val localDataSource = mockk<LocalDataSource>(relaxed = true)
    private val repo = SongRepository(dao, localDataSource)

    @Test
    fun `getByIds returns mapped songs in DAO order`() =
        runTest {
            coEvery { dao.getByIds(listOf(3L, 7L)) } returns
                listOf(
                    SongEntity(
                        id = 3,
                        title = "Three",
                        artist = "A",
                        album = "B",
                        duration = 1000,
                        sourceType = "LOCAL",
                    ),
                    SongEntity(
                        id = 7,
                        title = "Seven",
                        artist = "A",
                        album = "B",
                        duration = 2000,
                        sourceType = "LOCAL",
                    ),
                )

            val result = repo.getByIds(listOf(3L, 7L))

            assertEquals(2, result.size)
            assertEquals(3L, result[0].id)
            assertEquals("Three", result[0].title)
            assertEquals(7L, result[1].id)
            assertEquals("Seven", result[1].title)
        }
}
