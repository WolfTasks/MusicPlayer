# Room Auto-Migration Design

**Date:** 2026-04-09
**Status:** Approved

## Problem

User-created playlists and play weights stored in Room were lost during app updates. Root cause: `AppDatabase` had `exportSchema = false` and no migration strategy. Any schema change (or version bump without migrations) caused Room to throw an `IllegalStateException` at runtime, which developers resolved by adding `fallbackToDestructiveMigration()` — silently wiping all user data.

## Goal

Prevent data loss on schema changes by establishing a proper migration infrastructure using Room's Auto-Migration feature.

## Approach: Auto-Migration (Room 2.4+)

Room generates migration SQL automatically at build time by diffing exported schema JSON files. Supports all additive schema changes (new tables, new columns with defaults) — which covers all planned features in this project (Server integration, OfflineCache, etc.).

## Changes

### `AppDatabase.kt`

- Set `exportSchema = true`
- Bump `version` from `1` to `2`
- Add `autoMigrations = [AutoMigration(from = 1, to = 2)]`

Version 2 introduces no schema changes — it is a baseline bump that establishes the migration infrastructure. All existing user data is preserved.

```kotlin
@Database(
    entities = [
        SongEntity::class,
        PlayWeightEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        ServerEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() { ... }
```

### `app/build.gradle.kts`

Add the Room schema export directory to KSP arguments:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### Schema files (committed to git)

After running `./gradlew assembleDebug`, Room writes:
- `app/schemas/com.wolf.musicplayer.data.db.AppDatabase/1.json` — baseline
- `app/schemas/com.wolf.musicplayer.data.db.AppDatabase/2.json` — version 2 (identical schema)

Both files must be committed. They serve as the diff source for all future auto-migrations.

## Workflow for Future Schema Changes

1. Modify entities (add table, add column with default value)
2. Increment `version` by 1
3. Add `@AutoMigration(from = N, to = N+1)` to the `autoMigrations` list
4. Run `./gradlew assembleDebug` — Room generates the new schema JSON and validates the migration
5. Commit all changed files including the new schema JSON

For complex changes (column rename, data transformation), implement `AutoMigrationSpec` and reference it via `@AutoMigration(from = N, to = N+1, spec = MySpec::class)`.

## Safety Net: No `fallbackToDestructiveMigration()`

`fallbackToDestructiveMigration()` is intentionally **not** added. If a migration is missing or broken, Room throws at startup — this surfaces the bug in development rather than silently destroying user data in production.

## Out of Scope

- Restoring data already lost in previous updates (not recoverable)
- Migrating from a corrupted database state
- Playback queue persistence (separate concern, separate feature)
