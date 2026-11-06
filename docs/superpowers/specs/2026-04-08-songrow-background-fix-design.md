# Design: SongRow Background Fix

**Date:** 2026-04-08  
**Status:** Approved

## Problem

In `PlaylistDetailScreen`, each `SongRow` is wrapped in a `SwipeToDismissBox`. Material3's `SwipeToDismissBox` renders its foreground content on `MaterialTheme.colorScheme.surface` (`WolfNavySurface` = `#1A2744`), which is visibly lighter than the app's `background` color (`WolfNavy` = `#0D1830`) used in all other screens. The result is that song rows in the playlist detail look brighter and inconsistent with the rest of the dark theme.

## Solution

Add an explicit `.background(MaterialTheme.colorScheme.background)` to `SongRow`'s root `Row` modifier. This overrides whatever surface color the parent container (`SwipeToDismissBox` or otherwise) provides, and ensures `SongRow` always renders on the correct dark background.

The background is applied *before* the external `modifier` parameter so callers can still override it if needed.

## Change

**File:** `app/src/main/java/com/wolf/musicplayer/ui/component/SongRow.kt`

```kotlin
Row(
    modifier = Modifier
        .background(MaterialTheme.colorScheme.background)
        .then(modifier)
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    ...
)
```

## Scope

- One file changed: `SongRow.kt`
- No ViewModel, Repository, or data layer changes
- No new dependencies

## Testing

Visual inspection: song rows in `PlaylistDetailScreen` should match the background color of rows in `LibraryScreen`, `AlbumDetailScreen`, and `ArtistDetailScreen`.
