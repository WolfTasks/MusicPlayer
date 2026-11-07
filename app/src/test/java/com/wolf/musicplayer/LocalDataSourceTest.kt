package com.wolf.musicplayer

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import com.wolf.musicplayer.data.source.LocalDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LocalDataSourceTest {
    private val columns =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

    private val resolver = mockk<ContentResolver>()
    private val context = mockk<Context> { every { contentResolver } returns resolver }
    private val selection = slot<String>()

    private fun givenCursor(vararg rows: Array<Any?>) {
        val cursor = MatrixCursor(columns).apply { rows.forEach { addRow(it) } }
        every { resolver.query(any(), any(), capture(selection), any(), any()) } returns cursor
    }

    @Test
    fun `maps MediaStore rows to local song entities`() {
        givenCursor(arrayOf(42L, "So What", "Miles Davis", "Kind of Blue", 545_000L))

        val song = LocalDataSource(context).scanLocalMusic().single()

        assertEquals("So What", song.title)
        assertEquals("Miles Davis", song.artist)
        assertEquals("Kind of Blue", song.album)
        assertEquals(545_000L, song.duration)
        assertEquals("LOCAL", song.sourceType)
        assertEquals("content://media/external/audio/media/42", song.localPath)
    }

    @Test
    fun `missing metadata falls back to unknown placeholders`() {
        givenCursor(arrayOf(7L, null, null, null, 0L))

        val song = LocalDataSource(context).scanLocalMusic().single()

        assertEquals("Unknown", song.title)
        assertEquals("Unknown Artist", song.artist)
        assertEquals("Unknown Album", song.album)
    }

    @Test
    fun `returns every row in cursor order`() {
        givenCursor(
            arrayOf(1L, "A", "x", "y", 1L),
            arrayOf(2L, "B", "x", "y", 2L),
            arrayOf(3L, "C", "x", "y", 3L),
        )

        val songs = LocalDataSource(context).scanLocalMusic()

        assertEquals(listOf("A", "B", "C"), songs.map { it.title })
        assertEquals("content://media/external/audio/media/3", songs.last().localPath)
    }

    @Test
    fun `only queries music files`() {
        givenCursor()

        LocalDataSource(context).scanLocalMusic()

        assertEquals("${MediaStore.Audio.Media.IS_MUSIC} != 0", selection.captured)
    }

    @Test
    fun `null cursor yields empty list`() {
        every { resolver.query(any(), any(), any<String>(), any(), any()) } returns null

        assertTrue(LocalDataSource(context).scanLocalMusic().isEmpty())
    }
}
