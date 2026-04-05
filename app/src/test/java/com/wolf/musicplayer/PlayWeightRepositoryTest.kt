package com.wolf.musicplayer

import com.wolf.musicplayer.data.db.dao.PlayWeightDao
import com.wolf.musicplayer.data.db.entity.PlayWeightEntity
import com.wolf.musicplayer.data.repository.PlayWeightRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayWeightRepositoryTest {

    private lateinit var dao: PlayWeightDao
    private lateinit var repo: PlayWeightRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repo = PlayWeightRepository(dao)
    }

    @Test
    fun `recordSkip reduces weight by 25 percent`() = runTest {
        coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 1.0f)
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.recordSkip(1L)

        assertEquals(0.75f, slot.captured.weight, 0.001f)
        assertEquals(1, slot.captured.skipCount)
    }

    @Test
    fun `recordSkip does not go below 0_1`() = runTest {
        coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 0.1f)
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.recordSkip(1L)

        assertEquals(0.1f, slot.captured.weight, 0.001f)
    }

    @Test
    fun `recordFullPlay increases weight by 10 percent`() = runTest {
        coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 0.5f)
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.recordFullPlay(1L)

        assertEquals(0.55f, slot.captured.weight, 0.001f)
        assertEquals(1, slot.captured.playCount)
    }

    @Test
    fun `recordFullPlay does not exceed 1_0`() = runTest {
        coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 1.0f)
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.recordFullPlay(1L)

        assertEquals(1.0f, slot.captured.weight, 0.001f)
    }

    @Test
    fun `recordSkip creates default weight when no entry exists`() = runTest {
        coEvery { dao.getForSong(99L) } returns null
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.recordSkip(99L)

        // starts at 1.0, reduced to 0.75
        assertEquals(0.75f, slot.captured.weight, 0.001f)
    }

    @Test
    fun `resetWeight sets entry back to 1_0`() = runTest {
        val slot = slot<PlayWeightEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo.resetWeight(42L)

        assertEquals(42L, slot.captured.songId)
        assertEquals(1.0f, slot.captured.weight, 0.001f)
        assertEquals(0, slot.captured.skipCount)
        assertEquals(0, slot.captured.playCount)
    }
}
