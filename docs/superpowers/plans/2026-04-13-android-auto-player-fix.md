# Android Auto + Standard Player Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two regressions introduced by the Android Auto integration: (1) the app is invisible in Android Auto because a manifest meta-data tag is in the wrong XML element, and (2) the standard phone player is silent because `AutoLibraryCallback.onSetMediaItems()` intercepts and discards queue items sent by `PlayerViewModel`.

**Architecture:** Two focused changes. The manifest fix moves one `<meta-data>` block. The callback fix adds a package-name guard at the top of `onSetMediaItems` so in-app `MediaController` calls pass through unchanged, while Android Auto calls continue through the existing queue-resolution logic. `Context` (for `appPackageName`) is injected into `AutoLibraryCallback` via Hilt's `@ApplicationContext` — no new Hilt modules needed.

**Tech Stack:** Kotlin, Media3 (MediaLibraryService), Hilt (`@ApplicationContext`), MockK, kotlinx-coroutines-test

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `app/src/main/AndroidManifest.xml` | Move `<meta-data>` from `<service>` to `<application>` |
| Modify | `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt` | Inject `Context`, add in-app pass-through guard in `onSetMediaItems` |
| Modify | `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt` | Add `Context` mock to setup; fix existing `onSetMediaItems` tests; add new pass-through test |

---

## Task 1: TDD — in-app pass-through for `onSetMediaItems`

**Files:**
- Modify: `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`
- Modify: `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`

### Why this test first

The current `onSetMediaItems` has no package-name awareness. We will add it. Writing the test first pins the exact expected contract before touching production code.

---

- [ ] **Step 1: Add `Context` mock to test setup and fix existing `onSetMediaItems` tests**

The existing six `onSetMediaItems` tests use `mockController` (relaxed), which returns `""` for `packageName` by default. After we add the guard, `"" == appPackageName` must be **false** so those tests still exercise the queue-resolution path.

Open `app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt`.

1. Add the import at the top of the import block:
   ```kotlin
   import android.content.Context
   ```

2. Add a field after `private val mockBrowser`:
   ```kotlin
   private val mockContext = mockk<Context>(relaxed = true)
   ```

3. In `setup()`, add before the `callback = AutoLibraryCallback(...)` call:
   ```kotlin
   every { mockContext.packageName } returns "com.wolf.musicplayer"
   ```
   (MockK's `every` is already imported because other tests use `coEvery`; add `import io.mockk.every` if the compiler complains.)

4. Add `context = mockContext` as the first argument to the `AutoLibraryCallback(...)` constructor call in `setup()`:
   ```kotlin
   callback =
       AutoLibraryCallback(
           context = mockContext,
           songRepository = songRepo,
           playlistRepository = playlistRepo,
           playWeightRepository = playWeightRepo,
           shuffler = WeightedShuffler(),
           shuffleStateHolder = shuffleStateHolder,
           scope = testScope,
       )
   ```

5. The `mockController` and `mockMediaSession` are already relaxed. Add explicit stubs for package names on the **existing** `onSetMediaItems` section mocks so the guard evaluates to false (Auto path):
   ```kotlin
   private val mockMediaSession = mockk<MediaSession>(relaxed = true)
   private val mockController = mockk<MediaSession.ControllerInfo>(relaxed = true) {
       every { packageName } returns "com.google.android.projection.gearhead"
   }
   ```
   (Replace the existing two declarations in the `// --- onSetMediaItems ---` section with these.)

---

- [ ] **Step 2: Write the new failing test for in-app pass-through**

Add the following test at the end of `AutoLibraryCallbackTest`, inside the class, after the last `onSetMediaItems` test:

```kotlin
@Test
fun `onSetMediaItems from in-app controller passes items through unchanged`() =
    runTest {
        // Simulate PlayerViewModel: plain song IDs, URIs already set, no "song:" prefix
        val inAppController = mockk<MediaSession.ControllerInfo>(relaxed = true) {
            every { packageName } returns "com.wolf.musicplayer"
        }
        val item1 =
            androidx.media3.common.MediaItem.Builder()
                .setMediaId("1")
                .setUri("/music/1.mp3")
                .build()
        val item2 =
            androidx.media3.common.MediaItem.Builder()
                .setMediaId("2")
                .setUri("/music/2.mp3")
                .build()

        val future =
            callback.onSetMediaItems(
                mockMediaSession,
                inAppController,
                listOf(item1, item2),
                1,
                30_000L,
            )
        testScope.advanceUntilIdle()
        val result = future.get()

        // Items returned unchanged — no DB lookup, same IDs and count
        assertEquals(2, result.mediaItems.size)
        assertEquals("1", result.mediaItems[0].mediaId)
        assertEquals("2", result.mediaItems[1].mediaId)
        assertEquals(1, result.startIndex)
        assertEquals(30_000L, result.startPositionMs)
    }
```

---

- [ ] **Step 3: Run the new test to confirm it fails**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest.onSetMediaItems from in-app controller passes items through unchanged"
```

Expected: **FAIL** — test fails because the guard does not exist yet and `resolveQueue("1")` returns empty.

---

- [ ] **Step 4: Inject `Context` into `AutoLibraryCallback` and add the pass-through guard**

Open `app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt`.

1. Add imports (place with the existing imports, alphabetically):
   ```kotlin
   import android.content.Context
   import dagger.hilt.android.qualifiers.ApplicationContext
   ```

2. Add `@ApplicationContext private val context: Context` as the **first** constructor parameter:
   ```kotlin
   @Singleton
   class AutoLibraryCallback
       @Inject
       constructor(
           @ApplicationContext private val context: Context,
           private val songRepository: SongRepository,
           private val playlistRepository: PlaylistRepository,
           private val playWeightRepository: PlayWeightRepository,
           private val shuffler: WeightedShuffler,
           private val shuffleStateHolder: ShuffleStateHolder,
           @ApplicationScope private val scope: CoroutineScope,
       ) : MediaLibrarySession.Callback {
   ```

3. Replace the entire `onSetMediaItems` override with:
   ```kotlin
   override fun onSetMediaItems(
       mediaSession: MediaSession,
       controller: MediaSession.ControllerInfo,
       mediaItems: List<MediaItem>,
       startIndex: Int,
       startPositionMs: Long,
   ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
       // In-app controller (PlayerViewModel): queue already built; pass through unchanged.
       if (controller.packageName == context.packageName) {
           return Futures.immediateFuture(
               MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
           )
       }
       // Android Auto: resolve the single browse-tree mediaId into a full queue.
       val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
       callbackScope.launch {
           try {
               val requestedId = mediaItems.firstOrNull()?.mediaId
               if (requestedId == null) {
                   future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                   return@launch
               }
               val (songs, requestedSongId) = resolveQueue(requestedId)
               if (songs.isEmpty() || requestedSongId == null) {
                   future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                   return@launch
               }
               val queue =
                   if (shuffleStateHolder.shuffleEnabled) {
                       val weights = songs.map { playWeightRepository.getWeight(it.id) }
                       shuffler.permute(songs, weights)
                   } else {
                       songs
                   }
               val idx = queue.indexOfFirst { it.id == requestedSongId }.coerceAtLeast(0)
               val resolvedItems = queue.map { it.toMediaItem("song:${it.id}") }
               future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, idx, startPositionMs))
           } catch (e: Exception) {
               future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
           }
       }
       return future
   }
   ```

---

- [ ] **Step 5: Run all `AutoLibraryCallbackTest` tests**

```bash
./gradlew test --tests "com.wolf.musicplayer.AutoLibraryCallbackTest"
```

Expected: **All tests PASS.** If any fail, check that `mockController.packageName` returns `"com.google.android.projection.gearhead"` (not `"com.wolf.musicplayer"`) in the existing Auto tests.

---

- [ ] **Step 6: Run full unit-test suite**

```bash
./gradlew test
```

Expected: **BUILD SUCCESSFUL**, all tests green.

---

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/service/AutoLibraryCallback.kt
git add app/src/test/java/com/wolf/musicplayer/AutoLibraryCallbackTest.kt
git commit -m "fix: pass through setMediaItems from in-app controller unchanged"
```

---

## Task 2: Fix manifest — move meta-data to `<application>`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

No unit test exists for manifest structure. Verification is a successful build.

---

- [ ] **Step 1: Move the `<meta-data>` block**

Open `app/src/main/AndroidManifest.xml`.

Remove the `<meta-data>` block from inside `<service android:name=".service.MusicService" ...>`:
```xml
<!-- REMOVE this from inside <service> -->
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

Add it as a direct child of `<application>`, immediately before the `<activity>` tag:
```xml
<application
        android:name=".MusicPlayerApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MusicPlayer">

    <meta-data
        android:name="com.google.android.gms.car.application"
        android:resource="@xml/automotive_app_desc" />

    <activity
            android:name=".MainActivity"
            ...>
        ...
    </activity>

    <service
        android:name=".service.MusicService"
        android:foregroundServiceType="mediaPlayback"
        android:exported="true">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaLibraryService" />
        </intent-filter>
        <intent-filter>
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
        <!-- no meta-data here -->
    </service>
</application>
```

---

- [ ] **Step 2: Build the debug APK**

```bash
./gradlew assembleDebug
```

Expected: **BUILD SUCCESSFUL** with no errors.

---

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix: move Android Auto meta-data to <application> so app is discoverable"
```
