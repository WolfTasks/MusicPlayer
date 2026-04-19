# Android Auto Browse Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Android Auto showing empty categories (and non-functional Resume button) by adding `onSubscribe`, `onUnsubscribe`, and `onPlaybackResumption` overrides to `AutoLibraryCallback`.

**Architecture:** Three method overrides added to the existing `AutoLibraryCallback` singleton. `onSubscribe`/`onUnsubscribe` simply accept all requests (Media3's default rejects them, blocking `onGetChildren`). `onPlaybackResumption` loads the full library queue using existing repository + shuffle logic.

**Tech Stack:** Media3 1.3.x (`MediaLibrarySession.Callback`), Kotlin Coroutines + `SettableFuture`, Hilt, MockK + `kotlinx-coroutines-test`.

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt` | Add 3 method overrides |
| Modify | `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt` | Add 4 test cases |

---

## Task 1: Add `onSubscribe` and `onUnsubscribe`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

Media3's default `onSubscribe` returns `RESULT_ERROR_NOT_SUPPORTED`. Android Auto waits for a successful subscription before showing children — so the browse tree loads but drill-down hangs forever on a spinner.

- [ ] **Step 1: Write the failing tests**

In `AutoLibraryCallbackTest.kt`, append these two tests inside the class body, directly before the final closing `}` (currently line 448):

```kotlin
    // --- onSubscribe / onUnsubscribe ---

    @Test
    fun `onSubscribe returns success for any parentId`() = runTest {
        val future = callback.onSubscribe(mockSession, mockBrowser, "songs", null)
        assertEquals(0, future.get().resultCode)
    }

    @Test
    fun `onUnsubscribe returns success for any parentId`() = runTest {
        val future = callback.onUnsubscribe(mockSession, mockBrowser, "songs")
        assertEquals(0, future.get().resultCode)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onSubscribe returns success for any parentId" --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onUnsubscribe returns success for any parentId"
```

Expected: both tests fail — `onSubscribe` and `onUnsubscribe` are not yet overridden so the default returns `resultCode = RESULT_ERROR_NOT_SUPPORTED` (= 1001 in Media3, not 0).

- [ ] **Step 3: Add `onSubscribe` and `onUnsubscribe` to `AutoLibraryCallback`**

In `AutoLibraryCallback.kt`, locate the comment `// ── Playback` (the line immediately after the `onGetItem` closing brace). Insert the two overrides directly before that comment:

```kotlin
        // ── Subscription ────────────────────────────────────────────────────────

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
        ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

```

No new imports are needed — `Futures`, `LibraryResult`, `LibraryParams`, `MediaLibrarySession`, and `MediaSession` are already imported.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onSubscribe returns success for any parentId" --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onUnsubscribe returns success for any parentId"
```

Expected: both tests pass (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Run full test suite to confirm no regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "feat: override onSubscribe/onUnsubscribe to accept Android Auto browse subscriptions"
```

---

## Task 2: Add `onPlaybackResumption`

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`

Without this override, the head unit's "Resume" button (cold-start, no active queue) has no effect. The override loads the full library queue — shuffled if enabled — and starts from index 0.

- [ ] **Step 1: Write the failing tests**

In `AutoLibraryCallbackTest.kt`, append these two tests inside the class body, directly before the final closing `}`:

```kotlin
    // --- onPlaybackResumption ---

    @Test
    fun `onPlaybackResumption returns all songs at index 0`() =
        runTest {
            coEvery { songRepo.getAll() } returns listOf(song(1), song(2), song(3))

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(3, result.mediaItems.size)
            assertEquals(0, result.startIndex)
            assertEquals("song:1", result.mediaItems[0].mediaId)
            assertEquals("song:2", result.mediaItems[1].mediaId)
            assertEquals("song:3", result.mediaItems[2].mediaId)
        }

    @Test
    fun `onPlaybackResumption with shuffle enabled returns all songs with index 0`() =
        runTest {
            shuffleStateHolder.setShuffleEnabled(true)
            val songs = (1L..10L).map { song(it) }
            coEvery { songRepo.getAll() } returns songs
            coEvery { playWeightRepo.getWeight(any()) } returns 1.0f

            val future = callback.onPlaybackResumption(mockMediaSession, mockController)
            testScope.advanceUntilIdle()
            val result = future.get()

            assertEquals(10, result.mediaItems.size)
            assertEquals(0, result.startIndex)
        }
```

Note: `mockMediaSession` and `mockController` are already declared in the test class (added in the `onSetMediaItems` tests section).

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onPlaybackResumption returns all songs at index 0" --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onPlaybackResumption with shuffle enabled returns all songs with index 0"
```

Expected: compilation error — `onPlaybackResumption` is not yet overridden.

- [ ] **Step 3: Add `onPlaybackResumption` to `AutoLibraryCallback`**

In `AutoLibraryCallback.kt`, locate the `// ── Lifecycle` comment (the line after the closing brace of `onSetMediaItems`). Insert the new override directly before the `private suspend fun resolveQueue` method, inside the `// ── Playback` section:

```kotlin
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            callbackScope.launch {
                try {
                    val songs = songRepository.getAll()
                    val queue =
                        if (shuffleStateHolder.shuffleEnabled) {
                            val weights = songs.map { playWeightRepository.getWeight(it.id) }
                            shuffler.permute(songs, weights)
                        } else {
                            songs
                        }
                    val items = queue.map { it.toMediaItem("song:${it.id}") }
                    future.set(MediaSession.MediaItemsWithStartPosition(items, 0, 0L))
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

```

No new imports needed — all types (`SettableFuture`, `MediaSession`, `MediaSession.MediaItemsWithStartPosition`) are already imported.

- [ ] **Step 4: Run the new tests to verify they pass**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onPlaybackResumption returns all songs at index 0" --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onPlaybackResumption with shuffle enabled returns all songs with index 0"
```

Expected: both tests pass (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Run full test suite to confirm no regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt \
        app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "feat: add onPlaybackResumption to AutoLibraryCallback for Android Auto cold-start resume"
```

---

## Task 3: Manual verification

No automated tests cover the live head unit. Verify on real hardware or the Android Desktop Head Unit (DHU).

- [ ] **Step 1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Verify browse tree drill-down**

Open the app in Android Auto (or DHU). Tap **Songs** — confirm the song list appears (no infinite spinner). Tap a song — confirm it starts playing and car next/previous controls advance the queue.

- [ ] **Step 3: Verify playback resumption**

Kill the app process (`adb shell am force-stop com.wolf.musicplayer`). In Android Auto, tap the Resume/Play button. Confirm the queue loads and playback starts from the first song.
