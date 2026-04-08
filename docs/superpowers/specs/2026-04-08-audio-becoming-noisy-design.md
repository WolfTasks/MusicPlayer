# Design Spec: Pause on Audio Output Disconnect

**Date:** 2026-04-08  
**Status:** Approved

## Problem

When the user disconnects headphones (wired or Bluetooth) or a Bluetooth speaker, the app continues playing. Audio then comes from the phone speaker unexpectedly — a common Android annoyance.

## Goal

Pause playback automatically when the audio output device disconnects. Position is preserved so the user can resume manually.

## Scope

Single change to `app/src/main/java/com/wolf/musicplayer/di/PlayerModule.kt`.

## Solution

Configure `ExoPlayer` with `AudioAttributes` in `PlayerModule`, passing two flags to `setAudioAttributes`:

- `handleAudioBecomingNoisy = true` — ExoPlayer registers internally for `AudioManager.ACTION_AUDIO_BECOMING_NOISY` and pauses when headphones are unplugged or a Bluetooth device disconnects.
- `handleAudioFocus = true` — ExoPlayer manages audio focus automatically: pauses on incoming calls or when another app takes audio focus, resumes when focus returns.

```kotlin
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
```

## Behavior

| Event | Result |
|---|---|
| Wired headphones unplugged | Playback pauses, position preserved |
| Bluetooth headphones/speaker disconnected | Playback pauses, position preserved |
| Incoming phone call | Playback pauses, resumes after call |
| User manually presses Play | Playback resumes normally |

## What Does NOT Change

- `MusicService.kt` — no changes
- `PlayerViewModel.kt` — no changes
- No new files, no new dependencies

## Testing

1. Play a song with wired headphones → unplug → confirm playback pauses
2. Play a song with Bluetooth device → disconnect → confirm playback pauses
3. Play a song → receive a phone call → confirm playback pauses during call
