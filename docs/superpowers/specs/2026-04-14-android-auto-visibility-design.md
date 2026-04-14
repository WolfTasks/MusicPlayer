# Android Auto Visibility Fix — Design Spec

**Date:** 2026-04-14
**Status:** Approved

## Problem

The app does not appear in Android Auto's media source list when connected to a real car via USB or Wireless Android Auto.

## Root Causes

### Cause 1 — Device: developer mode not enabled (not a code issue)

Android Auto only shows apps from the Play Store by default. Debug builds (and sideloaded APKs) require the developer mode "Unknown sources for media sessions" to be enabled on the device before the app is discoverable.

**Fix:** One-time device configuration — no code change.

### Cause 2 — Code: `addSession()` never called in `MusicService`

`MusicService.onCreate()` builds the `MediaLibrarySession` but never calls `addSession()`. Media3's internal `MediaNotificationManager` is only activated by `addSession()`. Without it:

- No foreground-service notification is started.
- Android kills the service as soon as the last binding ends (e.g. when the car disconnects).
- Playback stops mid-session.

**Fix:** Add `addSession()` / `removeSession()` calls to `MusicService`.

### Cause 3 — Manifest: missing `POST_NOTIFICATIONS` permission

On Android 13+ (API 33+), posting any notification requires the `android.permission.POST_NOTIFICATIONS` permission. Without it, the `MediaNotificationManager` cannot post the foreground notification, which blocks `startForeground()` and leaves the service unprotected.

**Fix:** Declare the permission in `AndroidManifest.xml`.

---

## Design

### `MusicService.kt`

Add `addSession()` after building the session in `onCreate()`, and `removeSession()` at the top of the destroy block before `release()`.

```kotlin
override fun onCreate() {
    super.onCreate()
    mediaLibrarySession = MediaLibrarySession.Builder(this, player, autoLibraryCallback).build()
    addSession(mediaLibrarySession!!)
}

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

`addSession()` hands the session to Media3's `MediaNotificationManager`, which:
- Creates the notification channel automatically.
- Calls `startForeground()` when playback starts.
- Calls `stopForeground()` when playback stops or pauses (depending on Media3 version).

No `DefaultMediaNotificationProvider` configuration is required — Media3 provides a default.

### `AndroidManifest.xml`

Add one permission declaration alongside the existing `FOREGROUND_SERVICE` permissions:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

On Android 13+ (API 33+), `POST_NOTIFICATIONS` is also a runtime permission. `MainActivity` must request it via `ActivityCompat.requestPermissions()` (or the Activity Result API). Without the runtime grant, the notification is silently suppressed, though `startForeground()` for media playback continues to function.

---

## Device Setup (one-time, not a code change)

1. Open the **Android Auto** app on the phone.
2. Tap the **version number** 10 times to unlock developer options.
3. Open **Developer settings**.
4. Enable **"Allow unknown sources for media sessions"**.

This must be done once per device. After this, any locally installed media app with the correct `com.google.android.gms.car.application` meta-data will appear in Android Auto's media sources list.

---

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/wolf/musicplayer/service/MusicService.kt` | Add `addSession()` in `onCreate()`, `removeSession()` in `onDestroy()` |
| `app/src/main/AndroidManifest.xml` | Add `POST_NOTIFICATIONS` permission |
| `app/src/main/java/com/wolf/musicplayer/MainActivity.kt` | Request `POST_NOTIFICATIONS` runtime permission on Android 13+ |

## Files Not Changed

- `AutoLibraryCallback.kt` — browse tree and playback logic untouched
- `PlayerViewModel`, UI, Navigation — no changes
- All existing tests — no changes

---

## Testing

- Build and install debug APK.
- Enable developer mode + unknown sources in Android Auto (one-time).
- Connect phone to car via USB.
- Verify app appears in Android Auto media sources list.
- Tap a song → playback starts.
- Disconnect car → verify playback continues on phone (foreground service kept alive).
- Verify existing phone UI playback is unchanged.
