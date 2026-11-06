# Slider Drag Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the seek slider in `PlayerScreen` so dragging the thumb works correctly when the app is in the foreground.

**Architecture:** Add two local Compose states (`isDragging`, `dragValue`) to `PlayerScreen`. While the user drags, the slider shows the local drag value and ignores position tracker updates. The seek is committed to the ViewModel only when the user releases the thumb via `onValueChangeFinished`.

**Tech Stack:** Jetpack Compose (`remember`, `mutableStateOf`, `mutableFloatStateOf`), Material3 `Slider`

---

### Task 1: Fix the Slider in PlayerScreen.kt

**Files:**
- Modify: `app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerScreen.kt:77-91`

- [ ] **Step 1: Add the two local states and rewrite the Slider block**

Replace lines 77–91 in `PlayerScreen.kt`:

```kotlin
// Seekbar
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Slider(
        value = if (state.duration > 0) state.position.toFloat() / state.duration else 0f,
        onValueChange = { viewModel.seekTo((it * state.duration).toLong()) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(state.position.toMinSec(), style = MaterialTheme.typography.labelSmall)
        Text(state.duration.toMinSec(), style = MaterialTheme.typography.labelSmall)
    }
}
```

with:

```kotlin
// Seekbar
var isDragging by remember { mutableStateOf(false) }
var dragValue by remember { mutableFloatStateOf(0f) }
val sliderValue =
    if (isDragging) {
        dragValue
    } else if (state.duration > 0) {
        state.position.toFloat() / state.duration
    } else {
        0f
    }

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Slider(
        value = sliderValue,
        onValueChange = { v ->
            isDragging = true
            dragValue = v
        },
        onValueChangeFinished = {
            viewModel.seekTo((dragValue * state.duration).toLong())
            isDragging = false
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(state.position.toMinSec(), style = MaterialTheme.typography.labelSmall)
        Text(state.duration.toMinSec(), style = MaterialTheme.typography.labelSmall)
    }
}
```

Also add the missing imports at the top of the file (after the existing `import` block):

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass. (No new tests needed — this is a pure UI interaction fix with no testable logic; the only observable behaviour change is gesture response which requires a device.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wolf/musicplayer/ui/screen/player/PlayerScreen.kt
git commit -m "fix: slider drag no longer fights position tracker in PlayerScreen"
```
