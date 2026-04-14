# Android Auto Visibility Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app appear in Android Auto's media sources list and keep the `MusicService` alive during playback via a foreground notification.

**Architecture:** Three focused changes: (1) declare `POST_NOTIFICATIONS` in the manifest, (2) register the `MediaLibrarySession` with Media3's `MediaNotificationManager` by calling `addSession()` / `removeSession()` in `MusicService`, (3) request the `POST_NOTIFICATIONS` runtime permission in `MainActivity` on Android 13+. No changes to `AutoLibraryCallback`, `PlayerViewModel`, or any UI layer.

**Tech Stack:** Kotlin, Media3 (`MediaLibraryService`), AndroidX Activity Result API, Hilt

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `app/src/main/AndroidManifest.xml` | Declare `POST_NOTIFICATIONS` permission |
| Modify | `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt` | Call `addSession()` / `removeSession()` |
| Modify | `app/src/main/java/com/wolf/musicplayer/MainActivity.kt` | Request `POST_NOTIFICATIONS` runtime permission on API 33+ |

---

## Task 1: Manifest — declare `POST_NOTIFICATIONS` permission

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the permission declaration**

Open `app/src/main/AndroidManifest.xml`. Add the new `<uses-permission>` line immediately after the existing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` line:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Build to verify no errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: declare POST_NOTIFICATIONS permission for media notification"
```

---

## Task 2: `MusicService` — register session with `MediaNotificationManager`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt`

Media3's `MediaNotificationManager` only tracks sessions registered via `addSession()`. Without this call, no foreground notification is started and Android kills the service once the last binding ends.

- [ ] **Step 1: Add `addSession()` in `onCreate()`**

Open `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt`.

Replace the existing `onCreate()`:

```kotlin
override fun onCreate() {
    super.onCreate()
    mediaLibrarySession = MediaLibrarySession.Builder(this, player, autoLibraryCallback).build()
    addSession(mediaLibrarySession!!)
}
```

- [ ] **Step 2: Add `removeSession()` in `onDestroy()`**

Replace the existing `onDestroy()`:

```kotlin
override fun onDestroy() {
    mediaLibrarySession?.run {
        removeSession(this)
        player.release()
        release()
        mediaLibrarySession = null
    }
    autoLibraryCallback.release()
    super.onDestroy()
}
```

`removeSession()` must be called before `release()` so the notification manager can cleanly stop the foreground notification before the session is torn down.

- [ ] **Step 3: Build to verify no errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run unit tests to confirm nothing is broken**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests green. (The `AutoLibraryCallbackTest` suite is the relevant coverage; `MusicService` itself has no unit tests.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/MusicService.kt
git commit -m "feat: register MediaLibrarySession with MediaNotificationManager for foreground service"
```

---

## Task 3: `MainActivity` — request `POST_NOTIFICATIONS` runtime permission

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/MainActivity.kt`

On Android 13+ (API 33, `Build.VERSION_CODES.TIRAMISU`), `POST_NOTIFICATIONS` is a runtime permission that must be explicitly requested. Without the runtime grant, the foreground notification is silently suppressed on Android 13+ devices, though playback itself is unaffected.

`MainActivity` already uses `ActivityResultContracts.RequestPermission()` via a `permissionLauncher` for audio permissions. Add a second, separate launcher for the notification permission so the existing audio permission flow is not touched.

- [ ] **Step 1: Add a notification permission launcher and request call**

Replace the entire contents of `MainActivity.kt` with:

```kotlin
package com.wolf.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
```

- [ ] **Step 2: Build to verify no errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/MainActivity.kt
git commit -m "feat: request POST_NOTIFICATIONS permission on Android 13+ for media notification"
```

---

## Manual Verification

After installing the debug APK:

1. **Enable Android Auto developer mode** (one-time, device-side):
   - Open Android Auto app → tap version number **10 times** → Developer settings → enable **"Allow unknown sources for media sessions"**

2. Connect phone to car via USB.

3. Open Android Auto on the head unit → navigate to media sources → verify **MusicPlayer appears**.

4. Tap the app → browse Songs / Playlists / Albums / Artists → tap a song → verify **playback starts**.

5. Disconnect the car → verify **playback continues on the phone** (foreground service kept alive by the notification).

6. Verify the **phone UI plays songs normally** (existing functionality unchanged).
