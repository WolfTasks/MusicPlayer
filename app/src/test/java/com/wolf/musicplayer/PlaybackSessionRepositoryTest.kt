package com.wolf.musicplayer

import android.content.Context
import android.content.SharedPreferences
import com.wolf.musicplayer.data.repository.PlaybackSessionRepository
import com.wolf.musicplayer.domain.model.QueueContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackSessionRepositoryTest {
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private lateinit var repo: PlaybackSessionRepository

    @Before
    fun setup() {
        every { mockContext.getSharedPreferences("playback_session", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockPrefs.getLong("position_ms", 0L) } returns 0L
        repo = PlaybackSessionRepository(mockContext)
    }

    @Test
    fun `load returns null when no session saved`() {
        every { mockPrefs.getString("context_type", null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when song id is minus 1`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns -1L
        assertNull(repo.load())
    }

    @Test
    fun `load returns AllSongs context`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns 42L

        val result = repo.load()
        assertEquals(QueueContext.AllSongs, result?.first)
        assertEquals(42L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns PlaylistCtx with correct id`() {
        every { mockPrefs.getString("context_type", null) } returns "PLAYLIST"
        every { mockPrefs.getString("context_id", null) } returns "7"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 99L

        val result = repo.load()
        assertEquals(QueueContext.PlaylistCtx(7L), result?.first)
        assertEquals(99L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns AlbumCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ALBUM"
        every { mockPrefs.getString("context_id", null) } returns "Abbey Road"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 5L

        val result = repo.load()
        assertEquals(QueueContext.AlbumCtx("Abbey Road"), result?.first)
        assertEquals(5L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns ArtistCtx with correct name`() {
        every { mockPrefs.getString("context_type", null) } returns "ARTIST"
        every { mockPrefs.getString("context_id", null) } returns "The Beatles"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 3L

        val result = repo.load()
        assertEquals(QueueContext.ArtistCtx("The Beatles"), result?.first)
        assertEquals(3L, result?.second)
        assertEquals(0L, result?.third)
    }

    @Test
    fun `load returns saved positionMs`() {
        every { mockPrefs.getString("context_type", null) } returns "ALL"
        every { mockPrefs.getString("context_id", null) } returns ""
        every { mockPrefs.getLong("last_song_id", -1L) } returns 10L
        every { mockPrefs.getLong("position_ms", 0L) } returns 45_000L

        val result = repo.load()
        assertEquals(45_000L, result?.third)
    }

    @Test
    fun `save AllSongs writes correct type, empty id, song id, and position`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        val songIdSlot = slot<Long>()
        val positionSlot = slot<Long>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor
        every { mockEditor.putLong("last_song_id", capture(songIdSlot)) } returns mockEditor
        every { mockEditor.putLong("position_ms", capture(positionSlot)) } returns mockEditor

        repo.save(QueueContext.AllSongs, 10L, 30_000L)

        assertEquals("ALL", typeSlot.captured)
        assertEquals("", idSlot.captured)
        assertEquals(10L, songIdSlot.captured)
        assertEquals(30_000L, positionSlot.captured)
        verify { mockEditor.apply() }
    }

    @Test
    fun `save defaults positionMs to 0 when not provided`() {
        val positionSlot = slot<Long>()
        every { mockEditor.putLong("position_ms", capture(positionSlot)) } returns mockEditor

        repo.save(QueueContext.AllSongs, 10L)

        assertEquals(0L, positionSlot.captured)
    }

    @Test
    fun `save PlaylistCtx writes playlist id as string`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.PlaylistCtx(3L), 55L)

        assertEquals("PLAYLIST", typeSlot.captured)
        assertEquals("3", idSlot.captured)
    }

    @Test
    fun `save AlbumCtx writes album name as context id`() {
        val idSlot = slot<String>()
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.AlbumCtx("Thriller"), 1L)

        assertEquals("Thriller", idSlot.captured)
    }

    @Test
    fun `save ArtistCtx writes artist name as context id`() {
        val typeSlot = slot<String>()
        val idSlot = slot<String>()
        every { mockEditor.putString("context_type", capture(typeSlot)) } returns mockEditor
        every { mockEditor.putString("context_id", capture(idSlot)) } returns mockEditor

        repo.save(QueueContext.ArtistCtx("The Beatles"), 2L)

        assertEquals("ARTIST", typeSlot.captured)
        assertEquals("The Beatles", idSlot.captured)
    }

    @Test
    fun `load returns null when context id is null and type is PLAYLIST`() {
        every { mockPrefs.getString("context_type", null) } returns "PLAYLIST"
        every { mockPrefs.getString("context_id", null) } returns null
        every { mockPrefs.getLong("last_song_id", -1L) } returns 10L

        val result = repo.load()
        assertNull(result)
    }

    @Test
    fun `load returns null when type is unknown`() {
        every { mockPrefs.getString("context_type", null) } returns "UNKNOWN_TYPE"
        every { mockPrefs.getString("context_id", null) } returns "some_id"
        every { mockPrefs.getLong("last_song_id", -1L) } returns 20L

        val result = repo.load()
        assertNull(result)
    }
}
