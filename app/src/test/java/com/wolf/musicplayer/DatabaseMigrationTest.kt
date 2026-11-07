package com.wolf.musicplayer

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.wolf.musicplayer.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the Room migrations against the exported schemas (app/schemas) on the JVM, so that
 * every `./gradlew test` and CI run proves existing playlists survive schema upgrades.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = instrumentation,
            file = instrumentation.targetContext.getDatabasePath(TEST_DB),
            driver = AndroidSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    @Test
    fun `migrate 2 to 3 keeps playlists and their songs and adds the songId index`() {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, album, duration, sourceType) " +
                    "VALUES (10, 'A', 'Artist', 'Album', 1000, 'LOCAL'), (11, 'B', 'Artist', 'Album', 1000, 'LOCAL')",
            )
            execSQL("INSERT INTO playlists (id, name, serverId, isLocal) VALUES (1, 'Favorites', NULL, 1)")
            execSQL("INSERT INTO playlist_songs (playlistId, songId, position) VALUES (1, 11, 0), (1, 10, 1)")
            close()
        }

        // Validates the migrated schema against 3.json (incl. the new index).
        val db = helper.runMigrationsAndValidate(3)

        assertEquals(listOf("Favorites"), db.texts("SELECT name FROM playlists WHERE id = 1"))
        assertEquals(
            listOf("11", "10"),
            db.texts("SELECT songId FROM playlist_songs WHERE playlistId = 1 ORDER BY position"),
        )
        val indices = db.texts("SELECT name FROM pragma_index_list('playlist_songs')")
        assertTrue("index missing, found $indices", "index_playlist_songs_songId" in indices)
        db.close()
    }

    @Test
    fun `migrate 1 to 3 keeps playlists`() {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO playlists (name, serverId, isLocal) VALUES ('Favorites', NULL, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(3)

        assertEquals(listOf("Favorites"), db.texts("SELECT name FROM playlists"))
        db.close()
    }

    private fun SQLiteConnection.texts(sql: String): List<String> =
        prepare(sql).use { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }
        }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
