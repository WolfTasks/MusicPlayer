package com.wolf.musicplayer.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShuffleStateHolder
    @Inject
    constructor() {
        @Volatile
        var shuffleEnabled: Boolean = false
            private set

        fun setShuffleEnabled(enabled: Boolean) {
            shuffleEnabled = enabled
        }
    }
