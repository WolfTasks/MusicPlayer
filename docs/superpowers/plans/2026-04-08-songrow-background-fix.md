# SongRow Background Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `SongRow` rows appearing with a lighter background in `PlaylistDetailScreen` by giving `SongRow` an explicit `background(MaterialTheme.colorScheme.background)`.

**Architecture:** `SwipeToDismissBox` in M3 renders its foreground content on `colorScheme.surface`, which is visibly lighter than `colorScheme.background` used elsewhere. Adding an explicit background to `SongRow` before the external `modifier` parameter ensures the correct dark background in all contexts while remaining overridable by callers.

**Tech Stack:** Jetpack Compose, Material3

---

### Task 1: Fix SongRow background

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/component/SongRow.kt`

- [ ] **Step 1: Apply the fix**

In `SongRow.kt`, change the `Row`'s modifier chain from:

```kotlin
Row(
    modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
```

to:

```kotlin
Row(
    modifier =
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .then(modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
```

Also add the missing import at the top of the file (after the existing imports):

```kotlin
import androidx.compose.foundation.background
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Run unit tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/component/SongRow.kt
git commit -m "fix: set explicit background on SongRow to prevent SwipeToDismissBox surface bleed"
```
