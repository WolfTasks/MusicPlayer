# Shuffle & Repeat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Shuffle and Repeat buttons in PlayerScreen functional — Shuffle reorders the queue using weighted-random permutation, Repeat cycles through OFF/ALL/ONE modes.

**Architecture:** `PlayerViewModel` maintains two lists: `currentQueue` (original order) and `activeQueue` (what ExoPlayer holds — permuted when shuffle is ON). `WeightedShuffler.permute()` builds the shuffled order by repeated weighted draws without replacement. ExoPlayer's native `shuffleModeEnabled` is never used. Repeat already sets `controller.repeatMode`; this plan adds missing state-sync on reconnect.

**Tech Stack:** Kotlin, Media3/ExoPlayer MediaController, Coroutines + StateFlow, Hilt, Room (via `PlayWeightRepository`)

---

## Branch Setup

- [ ] **Create feature branch**

```bash
git checkout -b feature/shuffle-repeat
```

---

## Task 1: Add `permute()` to WeightedShuffler

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/domain/WeightedShuffler.kt`
- Test: `app/src/test/java/com/wolf/musicplayer/WeightedShufflerTest.kt`

- [ ] **Step 1: Write the failing tests**

Open `app/src/test/java/com/wolf/musicplayer/WeightedShufflerTest.kt` and add these tests after the existing ones:

```kotlin
@Test
fun `permute returns all items exactly once`() {
    val items = listOf("a", "b", "c", "d")
    val weights = listOf(1.0f, 1.0f, 1.0f, 1.0f)
    val result = shuffler.permute(items, weights)
    assertEquals(items.size, result.size)
    assertEquals(items.toSet(), result.toSet())
}

@Test
fun `permute with single item returns that item`() {
    val result = shuffler.permute(listOf("only"), listOf(1.0f))
    assertEquals(listOf("only"), result)
}

@Test
fun `permute places high-weight item earlier on average`() {
    val items = listOf("rare", "common")
    val weights = listOf(0.1f, 1.0f)
    // Run 500 permutations, count how often "common" is at index 0
    val commonFirst = (1..500).count { shuffler.permute(items, weights)[0] == "common" }
    // With 10:1 ratio, common should be first >80% of the time
    assertTrue("common should be first more than 80% of permutations", commonFirst > 400)
}

@Test
fun `permute throws when sizes mismatch`() {
    assertThrows(IllegalArgumentException::class.java) {
        shuffler.permute(listOf("a", "b"), listOf(1.0f))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.wolf.musicplayer.WeightedShufflerTest"
```

Expected: 4 new tests FAIL with `Unresolved reference: permute`

- [ ] **Step 3: Implement `permute()` in WeightedShuffler**

Open `app/src/main/java/com/wolf/musicplayer/domain/WeightedShuffler.kt` and add `permute()` after `pick()`:

```kotlin
fun <T> permute(
    items: List<T>,
    weights: List<Float>,
): List<T> {
    require(items.size == weights.size) { "Items and weights must have equal size" }
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

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.wolf.musicplayer.WeightedShufflerTest"
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/domain/WeightedShuffler.kt
git add app/src/test/java/com/wolf/musicplayer/WeightedShufflerTest.kt
git commit -m "feat: add WeightedShuffler.permute() for weighted queue ordering"
```

---

## Task 2: Fix Repeat state sync

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

The `cycleRepeatMode()` function already sets `controller.repeatMode` correctly. Two things are missing:
1. `syncStateFromController()` never reads `ctrl.repeatMode` back, so after screen rotation (controller reconnect) the UI shows wrong state
2. `playerListener` has no `onRepeatModeChanged()`, so changes aren't reflected in `_state`

- [ ] **Step 1: Add `onRepeatModeChanged` to the `playerListener` object**

In `PlayerViewModel.kt`, the `playerListener` object currently ends after `onPlaybackStateChanged`. Add this override inside the `object : Player.Listener` block, after `onPlaybackStateChanged`:

```kotlin
override fun onRepeatModeChanged(repeatMode: Int) {
    _state.update { it.copy(repeatMode = repeatMode) }
}
```

- [ ] **Step 2: Read `repeatMode` back in `syncStateFromController()`**

In `PlayerViewModel.kt`, find `syncStateFromController()`. It currently reads:

```kotlin
private fun syncStateFromController() {
    val ctrl = controller ?: return
    val idx = ctrl.currentMediaItemIndex
    val song = currentQueue.getOrNull(idx)
    currentSongId = song?.id
    _state.update {
        it.copy(
            currentSong = song,
            isPlaying = ctrl.isPlaying,
            duration = ctrl.duration.coerceAtLeast(0L),
            queue = currentQueue,
        )
    }
}
```

Replace the `_state.update` block so it also reads `repeatMode`:

```kotlin
private fun syncStateFromController() {
    val ctrl = controller ?: return
    val idx = ctrl.currentMediaItemIndex
    val song = currentQueue.getOrNull(idx)
    currentSongId = song?.id
    _state.update {
        it.copy(
            currentSong = song,
            isPlaying = ctrl.isPlaying,
            duration = ctrl.duration.coerceAtLeast(0L),
            queue = currentQueue,
            repeatMode = ctrl.repeatMode,
        )
    }
}
```

- [ ] **Step 3: Build to verify no compile errors**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual test — Repeat**

Install on device/emulator and verify:
- Tap Repeat button → icon changes to `Repeat` (ALL mode), tinted primary color
- Tap again → icon changes to `RepeatOne` (ONE mode)
- Tap again → icon returns to `Repeat` (OFF mode), grey tint
- Rotate screen mid-song → repeat mode icon stays correct

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "fix: sync repeatMode from controller on reconnect and listener callback"
```

---

## Task 3: Implement Shuffle with weighted queue

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt`

This task adds `activeQueue` (what ExoPlayer holds), updates `playSongs()` to permute on shuffle, rewrites `toggleShuffle()` to rebuild the active queue, and updates `syncStateFromController()` to look up songs in `activeQueue` instead of `currentQueue`.

- [ ] **Step 1: Add `activeQueue` field**

In `PlayerViewModel.kt`, find these two lines:

```kotlin
private var controller: MediaController? = null
private var currentQueue: List<Song> = emptyList()
```

Add `activeQueue` right after `currentQueue`:

```kotlin
private var controller: MediaController? = null
private var currentQueue: List<Song> = emptyList()
private var activeQueue: List<Song> = emptyList()
```

- [ ] **Step 2: Update `syncStateFromController()` to use `activeQueue`**

`activeQueue` now mirrors what ExoPlayer holds, so `ctrl.currentMediaItemIndex` maps into `activeQueue`, not `currentQueue`. Update the song lookup:

```kotlin
private fun syncStateFromController() {
    val ctrl = controller ?: return
    val idx = ctrl.currentMediaItemIndex
    val song = activeQueue.getOrNull(idx)
    currentSongId = song?.id
    _state.update {
        it.copy(
            currentSong = song,
            isPlaying = ctrl.isPlaying,
            duration = ctrl.duration.coerceAtLeast(0L),
            queue = currentQueue,
            repeatMode = ctrl.repeatMode,
        )
    }
}
```

(`queue = currentQueue` stays — it exposes the original order for any future queue UI.)

- [ ] **Step 3: Update `playSongs()` to permute when shuffle is ON**

`playSongs()` currently sets media items synchronously. It now needs to fetch weights (suspend) when shuffle is enabled, so move ExoPlayer setup into `viewModelScope.launch`. Replace the entire `playSongs()` function:

```kotlin
fun playSongs(
    songs: List<Song>,
    startIndex: Int = 0,
) {
    currentQueue = songs
    val ctrl = controller ?: return
    viewModelScope.launch {
        activeQueue =
            if (_state.value.shuffleEnabled) {
                val weights = songs.map { playWeightRepository.getWeight(it.id) }
                shuffler.permute(songs, weights)
            } else {
                songs
            }
        val startSong = songs.getOrNull(startIndex)
        val activeIndex =
            if (startSong != null) {
                activeQueue.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
            } else {
                0
            }
        val items =
            activeQueue.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.localPath ?: song.cachedPath ?: "")
                    .build()
            }
        ctrl.setMediaItems(items, activeIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        syncStateFromController()
    }
}
```

- [ ] **Step 4: Rewrite `toggleShuffle()`**

Replace the current stub:

```kotlin
fun toggleShuffle() {
    _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
}
```

With the full implementation:

```kotlin
fun toggleShuffle() {
    viewModelScope.launch {
        val enabled = !_state.value.shuffleEnabled
        _state.update { it.copy(shuffleEnabled = enabled) }
        val ctrl = controller ?: return@launch
        val songId = currentSongId
        val position = ctrl.currentPosition
        val wasPlaying = ctrl.isPlaying
        activeQueue =
            if (enabled) {
                val weights = currentQueue.map { playWeightRepository.getWeight(it.id) }
                shuffler.permute(currentQueue, weights)
            } else {
                currentQueue
            }
        val newIndex = activeQueue.indexOfFirst { it.id == songId }.coerceAtLeast(0)
        val items =
            activeQueue.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.localPath ?: song.cachedPath ?: "")
                    .build()
            }
        ctrl.setMediaItems(items, newIndex, position)
        ctrl.prepare()
        if (wasPlaying) ctrl.play()
    }
}
```

- [ ] **Step 5: Build to verify no compile errors**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run all unit tests**

```bash
./gradlew test
```

Expected: All tests PASS

- [ ] **Step 7: Manual test — Shuffle**

Install on device/emulator and verify:
- Play a playlist with ≥5 songs in order
- Tap Shuffle → button turns primary color → song continues playing from same position
- Skip through songs → order is different from original
- Tap Shuffle again → button turns grey → subsequent songs resume original order
- Skip a song 3–5 times so its weight drops → toggle shuffle several times → the skipped song tends to appear later in the shuffled order

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerViewModel.kt
git commit -m "feat: implement shuffle with weighted queue permutation"
```

---

## Task 4: Merge to main

- [ ] **Run full test suite one last time**

```bash
./gradlew test
```

Expected: All tests PASS

- [ ] **Merge branch**

```bash
git checkout main
git merge feature/shuffle-repeat
```
