# Pause on Audio Output Disconnect — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically pause playback when wired headphones are unplugged or a Bluetooth device disconnects.

**Architecture:** Configure `ExoPlayer` in `PlayerModule` with `AudioAttributes` and `handleAudioBecomingNoisy = true`. ExoPlayer registers internally for `ACTION_AUDIO_BECOMING_NOISY` and pauses the player — no custom BroadcastReceiver needed. `handleAudioFocus = true` is included at no extra cost to handle pausing on incoming calls.

**Tech Stack:** Media3 ExoPlayer (`androidx.media3.exoplayer`), Media3 Common (`androidx.media3.common.AudioAttributes`, `androidx.media3.common.C`), Hilt DI

---

### Task 1: Configure ExoPlayer with AudioAttributes

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/di/PlayerModule.kt`

> Note: This behavior is enforced inside ExoPlayer and cannot be meaningfully unit-tested without a device/emulator. Manual testing steps are provided at the end.

- [ ] **Step 1: Open `PlayerModule.kt` and replace the `provideExoPlayer` function**

Replace the current content of `provideExoPlayer`:

```kotlin
package com.wolf.musicplayer.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
    ): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
                /* handleAudioBecomingNoisy = */ true,
            )
            .build()
}
```

- [ ] **Step 2: Build the project to verify no compile errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run unit tests to confirm nothing is broken**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with all tests passing

- [ ] **Step 4: Manual test — wired headphones**

  1. Install the debug APK on a device
  2. Start playback of any song
  3. Plug in wired headphones — music should continue playing through headphones
  4. Unplug headphones — playback must **pause** immediately
  5. Press Play — playback resumes from the same position

- [ ] **Step 5: Manual test — Bluetooth device**

  1. Connect a Bluetooth speaker or headphones
  2. Start playback — music plays through Bluetooth
  3. Turn off or disconnect the Bluetooth device — playback must **pause**
  4. Reconnect Bluetooth device and press Play — playback resumes

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/di/PlayerModule.kt
git commit -m "feat: pause playback when audio output disconnects"
```
