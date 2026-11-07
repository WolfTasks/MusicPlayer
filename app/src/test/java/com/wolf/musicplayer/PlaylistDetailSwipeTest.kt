package com.wolf.musicplayer

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.SavedStateHandle
import com.wolf.musicplayer.data.repository.PlaylistRepository
import com.wolf.musicplayer.domain.model.Playlist
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailScreen
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the swipe-to-remove behaviour of [PlaylistDetailScreen]:
 * only a full end-to-start swipe removes exactly the swiped song; start-to-end and
 * short swipes remove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlaylistDetailSwipeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repo = mockk<PlaylistRepository>(relaxed = true)

    private fun setScreen() {
        every { repo.observeAll() } returns flowOf(listOf(Playlist(PLAYLIST_ID, "Rock Mix", null, true)))
        every { repo.observeSongsForPlaylist(PLAYLIST_ID) } returns
            flowOf(listOf(testSong(1L, title = "First"), testSong(2L, title = "Second")))
        val vm = PlaylistDetailViewModel(repo, SavedStateHandle(mapOf("playlistId" to PLAYLIST_ID)))
        composeRule.setContent {
            PlaylistDetailScreen(
                contentPadding = PaddingValues(),
                onBack = {},
                onSongClick = { _, _ -> },
                onMoreClick = {},
                viewModel = vm,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `full end-to-start swipe removes exactly the swiped song once`() {
        setScreen()

        composeRule.onNodeWithText("Second").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        coVerify(exactly = 1) { repo.removeSong(PLAYLIST_ID, 2L) }
        coVerify(exactly = 0) { repo.removeSong(PLAYLIST_ID, 1L) }
    }

    @Test
    fun `start-to-end swipe does not remove`() {
        setScreen()

        composeRule.onNodeWithText("First").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        coVerify(exactly = 0) { repo.removeSong(any(), any()) }
    }

    @Test
    fun `short end-to-start swipe below the threshold does not remove`() {
        setScreen()

        composeRule.onNodeWithText("First").performTouchInput {
            // ~20 px slow drag: below the 56 dp positional threshold and the fling velocity.
            swipeLeft(startX = centerX, endX = centerX - 20f, durationMillis = 1_000)
        }
        composeRule.waitForIdle()

        coVerify(exactly = 0) { repo.removeSong(any(), any()) }
    }

    private companion object {
        const val PLAYLIST_ID = 3L
    }
}
