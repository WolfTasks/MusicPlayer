package com.wolf.musicplayer

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wolf.musicplayer.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migrate1To2_preservesPlaylistData() {
        // Create a v1 database with a playlist row
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO playlists (name, serverId, isLocal) VALUES ('Favorites', NULL, 1)",
            )
            close()
        }

        // Run migration from 1 → 2 and validate schema
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        // Verify the playlist row survived
        val cursor = db.query("SELECT name FROM playlists WHERE name = 'Favorites'")
        try {
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Favorites", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        } finally {
            cursor.close()
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
