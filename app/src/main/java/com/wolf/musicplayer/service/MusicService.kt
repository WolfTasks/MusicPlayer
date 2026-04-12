package com.wolf.musicplayer.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaLibraryService() {
    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var autoLibraryCallback: AutoLibraryCallback

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, autoLibraryCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        autoLibraryCallback.release()
        super.onDestroy()
    }
}
