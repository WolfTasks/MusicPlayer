# Shuffle & Repeat — Design Spec

**Date:** 2026-04-09  
**Status:** Approved

## Problem

The Shuffle and Repeat buttons in `PlayerScreen` are visually wired but non-functional:
- `toggleShuffle()` only flips a local state flag — never calls `controller.shuffleModeEnabled`
- `cycleRepeatMode()` sets `controller.repeatMode` correctly, but `syncStateFromController()` never reads it back, so the displayed state drifts after reconnect
- `playerListener` has no `onRepeatModeChanged()` callback

## Goals

- Shuffle ON → weighted-random playback order (songs skipped more often appear later)
- Shuffle OFF → original playlist order restored, current song continues
- Repeat cycles: OFF → ALL → ONE → OFF
- State survives controller reconnect

## Out of Scope

- Persisting shuffle/repeat preference across app restarts
- Server-sourced queues (future work)

---

## Architecture

### Queue Management in PlayerViewModel

Two lists are maintained at all times:

| Variable | Description |
|---|---|
| `currentQueue: List<Song>` | Original order — never mutated while the same playlist is active |
| `activeQueue: List<Song>` | What ExoPlayer currently holds — equals `currentQueue` (Shuffle OFF) or a weighted permutation (Shuffle ON) |

ExoPlayer's native `shuffleModeEnabled` is **never used**. The ViewModel owns shuffle state exclusively.

### playSongs()

```
playSongs(songs, startIndex):
  currentQueue = songs
  activeQueue = if (shuffleEnabled) weightedPermute(songs) else songs
  ctrl.setMediaItems(activeQueue, startIndex, 0L)
  ctrl.prepare() + ctrl.play()
```

When shuffle is already ON when `playSongs()` is called, the incoming list is permuted before being handed to ExoPlayer.

### toggleShuffle()

```
toggleShuffle():
  shuffleEnabled = !shuffleEnabled
  currentSong = state.currentSong (by ID)
  
  if enabling:
    activeQueue = weightedPermute(currentQueue)   // suspend: reads weights from DB
    newIndex = activeQueue.indexOf(currentSong)
    ctrl.setMediaItems(activeQueue, newIndex, ctrl.currentPosition)
  
  if disabling:
    activeQueue = currentQueue
    newIndex = currentQueue.indexOf(currentSong)
    ctrl.setMediaItems(currentQueue, newIndex, ctrl.currentPosition)
  
  wasPlaying = ctrl.isPlaying
  ctrl.setMediaItems(...)
  ctrl.prepare()
  if (wasPlaying) ctrl.play()
```

`isPlaying` is saved before `setMediaItems()` and restored afterwards so the song continues if it was playing, or stays paused if it wasn't.

### Repeat

`cycleRepeatMode()` already sets `controller.repeatMode` — this works. Two fixes needed:

1. `syncStateFromController()` must read `ctrl.repeatMode` into `_state`
2. `playerListener` must implement `onRepeatModeChanged(repeatMode: Int)` to handle external changes

Cycle order: `REPEAT_MODE_OFF → REPEAT_MODE_ALL → REPEAT_MODE_ONE → REPEAT_MODE_OFF`

### Shuffle State After Reconnect

`shuffleEnabled` lives in `_state` (ViewModel), not in ExoPlayer. It survives reconnects automatically. On reconnect, `syncStateFromController()` reads `ctrl.repeatMode` back. No action needed for shuffle.

---

## WeightedShuffler Changes

Add `permute()` — a new method alongside the existing `pick()`:

```kotlin
fun <T> permute(items: List<T>, weights: List<Float>): List<T> {
    val remaining = items.toMutableList()
    val remainingWeights = weights.toMutableList()
    val result = mutableListOf<T>()
    while (remaining.isNotEmpty()) {
        val picked = pick(remaining, remainingWeights)
        val idx = remaining.indexOf(picked)
        result.add(picked)
        remaining.removeAt(idx)
        remainingWeights.removeAt(idx)
    }
    return result
}
```

`pick()` is unchanged. `permute()` calls it repeatedly without replacement until all items are placed. High-weight songs tend to appear earlier in the result.

---

## Files Changed

| File | Change |
|---|---|
| `domain/WeightedShuffler.kt` | Add `permute()` |
| `ui/screen/player/PlayerViewModel.kt` | Add `activeQueue`, fix `toggleShuffle()`, fix `syncStateFromController()`, add `onRepeatModeChanged()` listener |
| `ui/screen/player/PlayerScreen.kt` | No changes — already correctly wired |
| `service/MusicService.kt` | No changes |

---

## Testing

- `WeightedShufflerTest`: Add unit tests for `permute()` — verify all items present, high-weight items appear earlier on average
- `PlayerViewModel`: Manual test: toggle shuffle mid-song → song continues, order changes; toggle off → original order restored
- Repeat: Verify all three modes cycle correctly and survive screen rotation (controller reconnect)
