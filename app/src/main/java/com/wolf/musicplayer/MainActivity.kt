package com.wolf.musicplayer

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.wolf.musicplayer.service.MusicService
import com.wolf.musicplayer.ui.navigation.AppNavigation
import com.wolf.musicplayer.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* permission granted or denied — library screen handles state */ }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* notification permission granted or denied — foreground service handles state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioPermission()
        requestNotificationPermission()
        setContent {
            MusicPlayerTheme {
                AppNavigation()
            }
        }
        if (savedInstanceState == null) handleVoiceSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceSearch(intent)
    }

    override fun onDestroy() {
        searchController?.let(MediaController::releaseFuture)
        searchController = null
        super.onDestroy()
    }

    private var searchController: ListenableFuture<MediaController>? = null

    /**
     * "Play <query> on MusicPlayer" from Google Assistant arrives as MEDIA_PLAY_FROM_SEARCH.
     * The query is forwarded to MusicService as a search-only MediaItem; AutoLibraryCallback
     * resolves it against the library (blank query = whole library) and starts playback.
     */
    private fun handleVoiceSearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        val future =
            searchController ?: MediaController
                .Builder(
                    this,
                    SessionToken(this, ComponentName(this, MusicService::class.java)),
                ).buildAsync()
                .also { searchController = it }
        future.addListener({
            val controller = future.get()
            val item =
                MediaItem
                    .Builder()
                    .setRequestMetadata(
                        MediaItem.RequestMetadata
                            .Builder()
                            .setSearchQuery(query)
                            .build(),
                    ).build()
            controller.setMediaItems(listOf(item))
            controller.prepare()
            controller.play()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestAudioPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
