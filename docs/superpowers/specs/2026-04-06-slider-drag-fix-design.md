# Slider Drag Fix — PlayerScreen

**Date:** 2026-04-06  
**Status:** Approved

## Problem

The seek slider in `PlayerScreen` does not respond to drag input when the app is in the foreground. The slider thumb snaps back to the current playback position immediately after the user tries to drag it.

**Root cause:** `positionTracker` in `PlayerViewModel` updates `state.position` every 500ms. The Slider's `value` is bound directly to `state.position / state.duration`. When the user drags the slider, `onValueChange` calls `seekTo()` optimistically, but the tracker fires before the MediaController seek commits, overwriting `state.position` with the old position — causing the thumb to snap back.

## Fix

Add two local Compose states to `PlayerScreen`:

- `isDragging: Boolean` — true while the user is actively dragging the thumb
- `dragValue: Float` — the in-progress drag value (0f..1f)

**Slider value logic:**
- While `isDragging = true`: show `dragValue`
- Otherwise: show `state.position.toFloat() / state.duration`

**Slider callbacks:**
- `onValueChange`: set `isDragging = true`, update `dragValue` — do NOT call `seekTo`
- `onValueChangeFinished`: call `viewModel.seekTo(dragValue * duration)`, set `isDragging = false`

## Scope

- **Changed:** `PlayerScreen.kt` (Slider block only)
- **Unchanged:** `PlayerViewModel`, `seekTo()`, position tracker logic
