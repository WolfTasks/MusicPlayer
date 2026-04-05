package com.wolf.musicplayer.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.components.ServiceComponent

@Module
@InstallIn(ServiceComponent::class)
object PlayerModule {
    @Provides
    @ServiceScoped
    fun provideExoPlayer(
        @ApplicationContext context: Context,
    ): ExoPlayer = ExoPlayer.Builder(context).build()
}
