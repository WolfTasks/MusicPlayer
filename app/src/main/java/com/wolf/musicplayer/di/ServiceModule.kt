package com.wolf.musicplayer.di

import com.wolf.musicplayer.service.AndroidPackageInspector
import com.wolf.musicplayer.service.PackageInspector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun bindPackageInspector(impl: AndroidPackageInspector): PackageInspector
}
