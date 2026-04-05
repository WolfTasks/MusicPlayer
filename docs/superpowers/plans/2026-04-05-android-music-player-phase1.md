# Android Music Player — Phase 1: Foundation & Local Playback

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working Android app that scans local music files, displays them in 5 tabs, plays audio via Media3/ExoPlayer with background service, and tracks per-song play weights for weighted shuffle.

**Architecture:** MVVM + Repository. UI (Compose) → ViewModel (StateFlow) → Repository → DataSources. `MusicService` (Media3 `MediaSessionService`) runs as a foreground service; all screens connect to it via `MediaController`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 Dark, Media3/ExoPlayer, Hilt, Room, Navigation Compose, Coil

**Scope:** Local playback only. Server integration is Plan 2. Offline caching is Plan 3.

---

## File Map

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   └── java/com/example/musicplayer/
│       ├── MusicPlayerApplication.kt          ← @HiltAndroidApp entry point
│       ├── MainActivity.kt                    ← single Activity, hosts NavHost
│       ├── di/
│       │   ├── AppModule.kt                   ← application-scoped bindings
│       │   ├── DatabaseModule.kt              ← Room DB + DAOs
│       │   └── PlayerModule.kt                ← ExoPlayer (ServiceScoped)
│       ├── data/
│       │   ├── db/
│       │   │   ├── AppDatabase.kt             ← Room DB definition
│       │   │   ├── entity/
│       │   │   │   ├── SongEntity.kt
│       │   │   │   ├── PlayWeightEntity.kt
│       │   │   │   ├── PlaylistEntity.kt
│       │   │   │   ├── PlaylistSongEntity.kt
│       │   │   │   └── ServerEntity.kt        ← placeholder for Phase 2
│       │   │   └── dao/
│       │   │       ├── SongDao.kt
│       │   │       ├── PlayWeightDao.kt
│       │   │       └── PlaylistDao.kt
│       │   ├── source/
│       │   │   └── LocalDataSource.kt         ← MediaStore scanner
│       │   └── repository/
│       │       ├── SongRepository.kt
│       │       ├── PlayWeightRepository.kt
│       │       └── PlaylistRepository.kt
│       ├── domain/
│       │   ├── model/
│       │   │   ├── Song.kt
│       │   │   ├── Playlist.kt
│       │   │   └── PlayWeight.kt
│       │   └── WeightedShuffler.kt            ← pure algorithm, no Android deps
│       ├── service/
│       │   └── MusicService.kt                ← MediaSessionService
│       └── ui/
│           ├── theme/
│           │   ├── Color.kt
│           │   ├── Theme.kt
│           │   └── Type.kt
│           ├── navigation/
│           │   └── AppNavigation.kt
│           ├── screen/
│           │   ├── library/
│           │   │   ├── LibraryScreen.kt
│           │   │   └── LibraryViewModel.kt
│           │   ├── playlist/
│           │   │   ├── PlaylistScreen.kt
│           │   │   └── PlaylistViewModel.kt
│           │   ├── artist/
│           │   │   ├── ArtistScreen.kt
│           │   │   └── ArtistViewModel.kt
│           │   ├── album/
│           │   │   ├── AlbumScreen.kt
│           │   │   └── AlbumViewModel.kt
│           │   └── player/
│           │       ├── PlayerScreen.kt
│           │       └── PlayerViewModel.kt
│           └── component/
│               ├── SongRow.kt                 ← reusable song list item
│               ├── AlbumArt.kt                ← Coil image with fallback
│               └── MiniPlayer.kt              ← persistent bottom bar
└── src/test/java/com/example/musicplayer/
    ├── WeightedShufflerTest.kt
    ├── PlayWeightRepositoryTest.kt
    └── SongRepositoryTest.kt
gradle/
└── libs.versions.toml
build.gradle.kts                               ← root
settings.gradle.kts
```

---

## Task 1: Create Android Project in Android Studio

**Files:** (created by Android Studio wizard)

- [ ] **Step 1: Create project**

  In Android Studio: **File → New → New Project → Empty Activity**

  Settings:
  - Name: `MusicPlayer` (or dein Wunschname)
  - Package: `com.example.musicplayer`
  - Save location: `C:\Users\Admin\IdeaProjects\MusicPlayer`
  - Language: Kotlin
  - Min SDK: API 26 (Android 8.0)
  - Build configuration language: Kotlin DSL

- [ ] **Step 2: Verify project builds**

  In Android Studio terminal or Gradle panel:
  ```
  ./gradlew assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit baseline**

  ```bash
  git init
  git add .
  git commit -m "chore: initial Android Studio project scaffold"
  ```

---

## Task 2: Configure Gradle — libs.versions.toml + build.gradle.kts

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Replace `gradle/libs.versions.toml` with full version catalog**

  ```toml
  [versions]
  agp = "8.3.2"
  kotlin = "1.9.23"
  ksp = "1.9.23-1.0.19"
  composeBom = "2024.05.00"
  activityCompose = "1.9.0"
  navigationCompose = "2.7.7"
  hilt = "2.51.1"
  hiltNavigationCompose = "1.2.0"
  room = "2.6.1"
  media3 = "1.3.1"
  retrofit = "2.11.0"
  okhttp = "4.12.0"
  coil = "2.6.0"
  workmanager = "2.9.0"
  hiltWork = "1.2.0"
  lifecycle = "2.8.0"
  coroutines = "1.8.1"
  junit = "4.13.2"
  junitExt = "1.1.5"
  mockk = "1.13.11"
  coroutinesTest = "1.8.1"
  robolectric = "4.12.1"
  roomTesting = "2.6.1"

  [libraries]
  # Compose BOM
  compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
  compose-ui = { group = "androidx.compose.ui", name = "ui" }
  compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
  compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
  compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
  compose-material3 = { group = "androidx.compose.material3", name = "material3" }
  compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
  activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
  # Navigation
  navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
  # Hilt
  hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
  hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
  hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
  hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
  hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
  # Room
  room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
  room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
  room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
  room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomTesting" }
  # Media3
  media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
  media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
  # Retrofit + OkHttp (used in Phase 2)
  retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
  retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
  okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
  # Coil
  coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
  # WorkManager (used in Phase 3)
  workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
  # Lifecycle
  lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
  lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
  # Testing
  junit = { group = "junit", name = "junit", version.ref = "junit" }
  junit-ext = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
  coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
  mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
  robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }

  [plugins]
  android-application = { id = "com.android.application", version.ref = "agp" }
  kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
  hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
  ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
  ```

- [ ] **Step 2: Update root `build.gradle.kts`**

  ```kotlin
  plugins {
      alias(libs.plugins.android.application) apply false
      alias(libs.plugins.kotlin.android) apply false
      alias(libs.plugins.hilt) apply false
      alias(libs.plugins.ksp) apply false
  }
  ```

- [ ] **Step 3: Replace `app/build.gradle.kts`**

  ```kotlin
  plugins {
      alias(libs.plugins.android.application)
      alias(libs.plugins.kotlin.android)
      alias(libs.plugins.hilt)
      alias(libs.plugins.ksp)
  }

  android {
      namespace = "com.example.musicplayer"
      compileSdk = 34

      defaultConfig {
          applicationId = "com.example.musicplayer"
          minSdk = 26
          targetSdk = 34
          versionCode = 1
          versionName = "1.0"
          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      buildTypes {
          release {
              isMinifyEnabled = false
          }
      }

      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_17
          targetCompatibility = JavaVersion.VERSION_17
      }

      kotlinOptions {
          jvmTarget = "17"
      }

      buildFeatures {
          compose = true
      }

      composeOptions {
          kotlinCompilerExtensionVersion = "1.5.13"
      }

      testOptions {
          unitTests.isReturnDefaultValues = true
      }
  }

  dependencies {
      implementation(platform(libs.compose.bom))
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.graphics)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.compose.material3)
      implementation(libs.compose.material.icons)
      implementation(libs.activity.compose)
      implementation(libs.navigation.compose)
      implementation(libs.hilt.android)
      ksp(libs.hilt.compiler)
      implementation(libs.hilt.navigation.compose)
      implementation(libs.room.runtime)
      implementation(libs.room.ktx)
      ksp(libs.room.compiler)
      implementation(libs.media3.exoplayer)
      implementation(libs.media3.session)
      implementation(libs.coil.compose)
      implementation(libs.lifecycle.viewmodel.compose)
      implementation(libs.lifecycle.runtime.compose)
      debugImplementation(libs.compose.ui.tooling)

      testImplementation(libs.junit)
      testImplementation(libs.coroutines.test)
      testImplementation(libs.mockk)
      testImplementation(libs.robolectric)
      testImplementation(libs.room.testing)
      androidTestImplementation(libs.junit.ext)
  }
  ```

- [ ] **Step 4: Sync project**

  In Android Studio: **File → Sync Project with Gradle Files**
  Expected: no red errors in Build output.

- [ ] **Step 5: Commit**

  ```bash
  git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
  git commit -m "chore: configure gradle version catalog and dependencies"
  ```

---

## Task 3: Domain Models

**Files:**
- Create: `app/src/main/java/com/example/musicplayer/domain/model/Song.kt`
- Create: `app/src/main/java/com/example/musicplayer/domain/model/Playlist.kt`
- Create: `app/src/main/java/com/example/musicplayer/domain/model/PlayWeight.kt`

- [ ] **Step 1: Create `Song.kt`**

  ```kotlin
  package com.example.musicplayer.domain.model

  enum class SourceType { LOCAL, SERVER }

  data class Song(
      val id: Long,
      val title: String,
      val artist: String,
      val album: String,
      val duration: Long,        // milliseconds
      val sourceType: SourceType,
      val serverId: Long?,
      val serverSongId: String?,
      val localPath: String?,
      val cachedPath: String?,
  )
  ```

- [ ] **Step 2: Create `Playlist.kt`**

  ```kotlin
  package com.example.musicplayer.domain.model

  data class Playlist(
      val id: Long,
      val name: String,
      val serverId: Long?,
      val isLocal: Boolean,
  )
  ```

- [ ] **Step 3: Create `PlayWeight.kt`**

  ```kotlin
  package com.example.musicplayer.domain.model

  data class PlayWeight(
      val songId: Long,
      val skipCount: Int = 0,
      val playCount: Int = 0,
      val weight: Float = 1.0f,
  )
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/domain/
  git commit -m "feat: add domain models Song, Playlist, PlayWeight"
  ```

---

## Task 4: Room Entities, DAOs, and AppDatabase

**Files:**
- Create: `data/db/entity/SongEntity.kt`
- Create: `data/db/entity/PlayWeightEntity.kt`
- Create: `data/db/entity/PlaylistEntity.kt`
- Create: `data/db/entity/PlaylistSongEntity.kt`
- Create: `data/db/entity/ServerEntity.kt`
- Create: `data/db/dao/SongDao.kt`
- Create: `data/db/dao/PlayWeightDao.kt`
- Create: `data/db/dao/PlaylistDao.kt`
- Create: `data/db/AppDatabase.kt`

All paths below are relative to `app/src/main/java/com/example/musicplayer/`.

- [ ] **Step 1: Create `SongEntity.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.entity

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  @Entity(tableName = "songs")
  data class SongEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val title: String,
      val artist: String,
      val album: String,
      val duration: Long,
      val sourceType: String,      // "LOCAL" or "SERVER"
      val serverId: Long? = null,
      val serverSongId: String? = null,
      val localPath: String? = null,
      val cachedPath: String? = null,
  )
  ```

- [ ] **Step 2: Create `PlayWeightEntity.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.entity

  import androidx.room.Entity
  import androidx.room.ForeignKey
  import androidx.room.PrimaryKey

  @Entity(
      tableName = "play_weights",
      foreignKeys = [ForeignKey(
          entity = SongEntity::class,
          parentColumns = ["id"],
          childColumns = ["songId"],
          onDelete = ForeignKey.CASCADE,
      )]
  )
  data class PlayWeightEntity(
      @PrimaryKey val songId: Long,
      val skipCount: Int = 0,
      val playCount: Int = 0,
      val weight: Float = 1.0f,
  )
  ```

- [ ] **Step 3: Create `PlaylistEntity.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.entity

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  @Entity(tableName = "playlists")
  data class PlaylistEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String,
      val serverId: Long? = null,
      val isLocal: Boolean = true,
  )
  ```

- [ ] **Step 4: Create `PlaylistSongEntity.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.entity

  import androidx.room.Entity
  import androidx.room.ForeignKey

  @Entity(
      tableName = "playlist_songs",
      primaryKeys = ["playlistId", "songId"],
      foreignKeys = [
          ForeignKey(
              entity = PlaylistEntity::class,
              parentColumns = ["id"],
              childColumns = ["playlistId"],
              onDelete = ForeignKey.CASCADE,
          ),
          ForeignKey(
              entity = SongEntity::class,
              parentColumns = ["id"],
              childColumns = ["songId"],
              onDelete = ForeignKey.CASCADE,
          ),
      ]
  )
  data class PlaylistSongEntity(
      val playlistId: Long,
      val songId: Long,
      val position: Int,
  )
  ```

- [ ] **Step 5: Create `ServerEntity.kt`** (placeholder, used fully in Phase 2)

  ```kotlin
  package com.example.musicplayer.data.db.entity

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  @Entity(tableName = "servers")
  data class ServerEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String,
      val url: String,
      val username: String,
      val passwordEncrypted: String,
      val type: String,            // "SUBSONIC" or "JELLYFIN"
      val isActive: Boolean = true,
  )
  ```

- [ ] **Step 6: Create `SongDao.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.dao

  import androidx.room.*
  import com.example.musicplayer.data.db.entity.SongEntity
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface SongDao {
      @Query("SELECT * FROM songs ORDER BY title ASC")
      fun observeAll(): Flow<List<SongEntity>>

      @Query("SELECT * FROM songs WHERE sourceType = 'LOCAL' ORDER BY title ASC")
      fun observeLocal(): Flow<List<SongEntity>>

      @Query("SELECT * FROM songs WHERE id = :id")
      suspend fun getById(id: Long): SongEntity?

      @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, title")
      fun observeByArtist(artist: String): Flow<List<SongEntity>>

      @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
      fun observeByAlbum(album: String): Flow<List<SongEntity>>

      @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
      fun observeArtists(): Flow<List<String>>

      @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
      fun observeAlbums(): Flow<List<String>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertAll(songs: List<SongEntity>)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insert(song: SongEntity): Long

      @Delete
      suspend fun delete(song: SongEntity)

      @Query("DELETE FROM songs WHERE sourceType = 'LOCAL'")
      suspend fun deleteAllLocal()
  }
  ```

- [ ] **Step 7: Create `PlayWeightDao.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.dao

  import androidx.room.*
  import com.example.musicplayer.data.db.entity.PlayWeightEntity
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface PlayWeightDao {
      @Query("SELECT * FROM play_weights WHERE songId = :songId")
      suspend fun getForSong(songId: Long): PlayWeightEntity?

      @Query("SELECT * FROM play_weights")
      fun observeAll(): Flow<List<PlayWeightEntity>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(entity: PlayWeightEntity)

      @Query("DELETE FROM play_weights WHERE songId = :songId")
      suspend fun deleteForSong(songId: Long)
  }
  ```

- [ ] **Step 8: Create `PlaylistDao.kt`**

  ```kotlin
  package com.example.musicplayer.data.db.dao

  import androidx.room.*
  import com.example.musicplayer.data.db.entity.PlaylistEntity
  import com.example.musicplayer.data.db.entity.PlaylistSongEntity
  import com.example.musicplayer.data.db.entity.SongEntity
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface PlaylistDao {
      @Query("SELECT * FROM playlists ORDER BY name ASC")
      fun observeAll(): Flow<List<PlaylistEntity>>

      @Query("SELECT * FROM playlists WHERE id = :id")
      suspend fun getById(id: Long): PlaylistEntity?

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insert(playlist: PlaylistEntity): Long

      @Update
      suspend fun update(playlist: PlaylistEntity)

      @Delete
      suspend fun delete(playlist: PlaylistEntity)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertSong(entry: PlaylistSongEntity)

      @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
      suspend fun removeSong(playlistId: Long, songId: Long)

      @Query("""
          SELECT s.* FROM songs s
          INNER JOIN playlist_songs ps ON ps.songId = s.id
          WHERE ps.playlistId = :playlistId
          ORDER BY ps.position ASC
      """)
      fun observeSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>
  }
  ```

- [ ] **Step 9: Create `AppDatabase.kt`**

  ```kotlin
  package com.example.musicplayer.data.db

  import androidx.room.Database
  import androidx.room.RoomDatabase
  import com.example.musicplayer.data.db.dao.PlaylistDao
  import com.example.musicplayer.data.db.dao.PlayWeightDao
  import com.example.musicplayer.data.db.dao.SongDao
  import com.example.musicplayer.data.db.entity.*

  @Database(
      entities = [
          SongEntity::class,
          PlayWeightEntity::class,
          PlaylistEntity::class,
          PlaylistSongEntity::class,
          ServerEntity::class,
      ],
      version = 1,
      exportSchema = false,
  )
  abstract class AppDatabase : RoomDatabase() {
      abstract fun songDao(): SongDao
      abstract fun playWeightDao(): PlayWeightDao
      abstract fun playlistDao(): PlaylistDao
  }
  ```

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/data/
  git commit -m "feat: add Room entities, DAOs, and AppDatabase"
  ```

---

## Task 5: Hilt Dependency Injection Setup

**Files:**
- Create: `MusicPlayerApplication.kt`
- Create: `di/DatabaseModule.kt`
- Create: `di/AppModule.kt`
- Create: `di/PlayerModule.kt`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Create `MusicPlayerApplication.kt`**

  ```kotlin
  package com.example.musicplayer

  import android.app.Application
  import dagger.hilt.android.HiltAndroidApp

  @HiltAndroidApp
  class MusicPlayerApplication : Application()
  ```

- [ ] **Step 2: Register application in `AndroidManifest.xml`**

  In `<application>` tag, add:
  ```xml
  android:name=".MusicPlayerApplication"
  ```

  Also add permissions:
  ```xml
  <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
  <!-- For Android 9 and below: -->
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
      android:maxSdkVersion="28" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
  ```

- [ ] **Step 3: Create `di/DatabaseModule.kt`**

  ```kotlin
  package com.example.musicplayer.di

  import android.content.Context
  import androidx.room.Room
  import com.example.musicplayer.data.db.AppDatabase
  import com.example.musicplayer.data.db.dao.PlaylistDao
  import com.example.musicplayer.data.db.dao.PlayWeightDao
  import com.example.musicplayer.data.db.dao.SongDao
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.android.qualifiers.ApplicationContext
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  object DatabaseModule {

      @Provides
      @Singleton
      fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
          Room.databaseBuilder(context, AppDatabase::class.java, "music_player.db").build()

      @Provides
      fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

      @Provides
      fun providePlayWeightDao(db: AppDatabase): PlayWeightDao = db.playWeightDao()

      @Provides
      fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
  }
  ```

- [ ] **Step 4: Create `di/AppModule.kt`**

  ```kotlin
  package com.example.musicplayer.di

  import com.example.musicplayer.domain.WeightedShuffler
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  object AppModule {

      @Provides
      @Singleton
      fun provideWeightedShuffler(): WeightedShuffler = WeightedShuffler()
  }
  ```

- [ ] **Step 5: Create `di/PlayerModule.kt`**

  ```kotlin
  package com.example.musicplayer.di

  import android.content.Context
  import androidx.media3.exoplayer.ExoPlayer
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.android.qualifiers.ApplicationContext
  import dagger.hilt.components.ServiceComponent
  import dagger.hilt.android.scopes.ServiceScoped

  @Module
  @InstallIn(ServiceComponent::class)
  object PlayerModule {

      @Provides
      @ServiceScoped
      fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
          ExoPlayer.Builder(context).build()
  }
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/
  git commit -m "feat: add Hilt DI setup — Application, modules, manifest"
  ```

---

## Task 6: Material3 Dark Theme

**Files:**
- Modify: `ui/theme/Color.kt`
- Modify: `ui/theme/Theme.kt`
- Modify: `ui/theme/Type.kt`

- [ ] **Step 1: Replace `ui/theme/Color.kt`**

  ```kotlin
  package com.example.musicplayer.ui.theme

  import androidx.compose.ui.graphics.Color

  val Purple80 = Color(0xFFD0BCFF)
  val PurpleGrey80 = Color(0xFFCCC2DC)
  val Pink80 = Color(0xFFEFB8C8)

  // Dark theme primary palette
  val DarkBackground = Color(0xFF121212)
  val DarkSurface = Color(0xFF1E1E1E)
  val DarkSurfaceVariant = Color(0xFF2A2A2A)
  val GreenAccent = Color(0xFF1DB954)
  val GreenAccentDim = Color(0xFF158a3e)
  val OnDark = Color(0xFFFFFFFF)
  val OnDarkSecondary = Color(0xFFB3B3B3)
  val OnDarkDisabled = Color(0xFF535353)
  ```

- [ ] **Step 2: Replace `ui/theme/Theme.kt`**

  ```kotlin
  package com.example.musicplayer.ui.theme

  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.darkColorScheme
  import androidx.compose.runtime.Composable

  private val DarkColorScheme = darkColorScheme(
      primary = GreenAccent,
      onPrimary = OnDark,
      primaryContainer = GreenAccentDim,
      onPrimaryContainer = OnDark,
      background = DarkBackground,
      onBackground = OnDark,
      surface = DarkSurface,
      onSurface = OnDark,
      surfaceVariant = DarkSurfaceVariant,
      onSurfaceVariant = OnDarkSecondary,
      outline = OnDarkDisabled,
  )

  @Composable
  fun MusicPlayerTheme(content: @Composable () -> Unit) {
      MaterialTheme(
          colorScheme = DarkColorScheme,
          typography = Typography,
          content = content,
      )
  }
  ```

- [ ] **Step 3: Verify theme compiles**

  ```
  ./gradlew :app:compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/ui/theme/
  git commit -m "feat: configure Material3 dark theme"
  ```

---

## Task 7: WeightedShuffler — TDD

**Files:**
- Create: `domain/WeightedShuffler.kt`
- Create: `src/test/java/com/example/musicplayer/WeightedShufflerTest.kt`

- [ ] **Step 1: Write failing test**

  Create `src/test/java/com/example/musicplayer/WeightedShufflerTest.kt`:

  ```kotlin
  package com.example.musicplayer

  import com.example.musicplayer.domain.WeightedShuffler
  import org.junit.Assert.*
  import org.junit.Test

  class WeightedShufflerTest {

      private val shuffler = WeightedShuffler()

      @Test
      fun `picks single item when list has one element`() {
          val result = shuffler.pick(listOf("only"), listOf(1.0f))
          assertEquals("only", result)
      }

      @Test
      fun `throws when items list is empty`() {
          assertThrows(IllegalArgumentException::class.java) {
              shuffler.pick(emptyList<String>(), emptyList())
          }
      }

      @Test
      fun `throws when sizes mismatch`() {
          assertThrows(IllegalArgumentException::class.java) {
              shuffler.pick(listOf("a", "b"), listOf(1.0f))
          }
      }

      @Test
      fun `heavily weighted item is picked much more often`() {
          val items = listOf("rare", "common")
          val weights = listOf(0.1f, 1.0f)
          val picks = (1..1000).map { shuffler.pick(items, weights) }
          val rareCount = picks.count { it == "rare" }
          val commonCount = picks.count { it == "common" }
          // common should be picked ~10x more often
          assertTrue("common picked at least 5x more than rare",
              commonCount > rareCount * 4)
      }

      @Test
      fun `equal weights produce roughly equal distribution`() {
          val items = listOf("a", "b")
          val weights = listOf(1.0f, 1.0f)
          val picks = (1..1000).map { shuffler.pick(items, weights) }
          val aCount = picks.count { it == "a" }
          // With equal weights, each should be picked ~500 times. Allow 15% deviation.
          assertTrue("a picked between 425 and 575 times", aCount in 425..575)
      }
  }
  ```

- [ ] **Step 2: Run test — expect FAIL (class not found)**

  ```
  ./gradlew :app:testDebugUnitTest --tests "*.WeightedShufflerTest"
  ```
  Expected: `FAILED` — `error: unresolved reference: WeightedShuffler`

- [ ] **Step 3: Implement `WeightedShuffler.kt`**

  Create `domain/WeightedShuffler.kt`:

  ```kotlin
  package com.example.musicplayer.domain

  import javax.inject.Inject

  class WeightedShuffler @Inject constructor() {

      fun <T> pick(items: List<T>, weights: List<Float>): T {
          require(items.isNotEmpty()) { "Items list must not be empty" }
          require(items.size == weights.size) { "Items and weights must have equal size" }

          val total = weights.sum()
          var remaining = Math.random().toFloat() * total

          for (i in items.indices) {
              remaining -= weights[i]
              if (remaining <= 0f) return items[i]
          }

          return items.last()
      }
  }
  ```

- [ ] **Step 4: Run test — expect PASS**

  ```
  ./gradlew :app:testDebugUnitTest --tests "*.WeightedShufflerTest"
  ```
  Expected: `4 tests completed, 0 failures`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/domain/WeightedShuffler.kt \
          app/src/test/java/com/example/musicplayer/WeightedShufflerTest.kt
  git commit -m "feat: add WeightedShuffler with TDD (4 tests green)"
  ```

---

## Task 8: PlayWeightRepository — TDD

**Files:**
- Create: `data/repository/PlayWeightRepository.kt`
- Create: `src/test/java/com/example/musicplayer/PlayWeightRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

  Create `src/test/java/com/example/musicplayer/PlayWeightRepositoryTest.kt`:

  ```kotlin
  package com.example.musicplayer

  import com.example.musicplayer.data.db.dao.PlayWeightDao
  import com.example.musicplayer.data.db.entity.PlayWeightEntity
  import com.example.musicplayer.data.repository.PlayWeightRepository
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import io.mockk.slot
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.*
  import org.junit.Before
  import org.junit.Test

  class PlayWeightRepositoryTest {

      private lateinit var dao: PlayWeightDao
      private lateinit var repo: PlayWeightRepository

      @Before
      fun setup() {
          dao = mockk(relaxed = true)
          repo = PlayWeightRepository(dao)
      }

      @Test
      fun `recordSkip reduces weight by 25 percent`() = runTest {
          coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 1.0f)
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.recordSkip(1L)

          assertEquals(0.75f, slot.captured.weight, 0.001f)
          assertEquals(1, slot.captured.skipCount)
      }

      @Test
      fun `recordSkip does not go below 0_1`() = runTest {
          coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 0.1f)
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.recordSkip(1L)

          assertEquals(0.1f, slot.captured.weight, 0.001f)
      }

      @Test
      fun `recordFullPlay increases weight by 10 percent`() = runTest {
          coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 0.5f)
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.recordFullPlay(1L)

          assertEquals(0.55f, slot.captured.weight, 0.001f)
          assertEquals(1, slot.captured.playCount)
      }

      @Test
      fun `recordFullPlay does not exceed 1_0`() = runTest {
          coEvery { dao.getForSong(1L) } returns PlayWeightEntity(songId = 1L, weight = 1.0f)
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.recordFullPlay(1L)

          assertEquals(1.0f, slot.captured.weight, 0.001f)
      }

      @Test
      fun `recordSkip creates default weight when no entry exists`() = runTest {
          coEvery { dao.getForSong(99L) } returns null
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.recordSkip(99L)

          // starts at 1.0, reduced to 0.75
          assertEquals(0.75f, slot.captured.weight, 0.001f)
      }

      @Test
      fun `resetWeight sets entry back to 1_0`() = runTest {
          val slot = slot<PlayWeightEntity>()
          coEvery { dao.upsert(capture(slot)) } returns Unit

          repo.resetWeight(42L)

          assertEquals(42L, slot.captured.songId)
          assertEquals(1.0f, slot.captured.weight, 0.001f)
          assertEquals(0, slot.captured.skipCount)
          assertEquals(0, slot.captured.playCount)
      }
  }
  ```

- [ ] **Step 2: Run test — expect FAIL**

  ```
  ./gradlew :app:testDebugUnitTest --tests "*.PlayWeightRepositoryTest"
  ```
  Expected: `FAILED` — `error: unresolved reference: PlayWeightRepository`

- [ ] **Step 3: Implement `PlayWeightRepository.kt`**

  Create `data/repository/PlayWeightRepository.kt`:

  ```kotlin
  package com.example.musicplayer.data.repository

  import com.example.musicplayer.data.db.dao.PlayWeightDao
  import com.example.musicplayer.data.db.entity.PlayWeightEntity
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class PlayWeightRepository @Inject constructor(
      private val dao: PlayWeightDao,
  ) {

      fun observeAll(): Flow<Map<Long, Float>> =
          dao.observeAll().map { list -> list.associate { it.songId to it.weight } }

      suspend fun recordSkip(songId: Long) {
          val current = dao.getForSong(songId) ?: PlayWeightEntity(songId = songId)
          dao.upsert(
              current.copy(
                  weight = (current.weight * 0.75f).coerceAtLeast(0.1f),
                  skipCount = current.skipCount + 1,
              )
          )
      }

      suspend fun recordFullPlay(songId: Long) {
          val current = dao.getForSong(songId) ?: PlayWeightEntity(songId = songId)
          dao.upsert(
              current.copy(
                  weight = (current.weight * 1.1f).coerceAtMost(1.0f),
                  playCount = current.playCount + 1,
              )
          )
      }

      suspend fun resetWeight(songId: Long) {
          dao.upsert(PlayWeightEntity(songId = songId))
      }

      suspend fun getWeight(songId: Long): Float =
          dao.getForSong(songId)?.weight ?: 1.0f
  }
  ```

- [ ] **Step 4: Run tests — expect PASS**

  ```
  ./gradlew :app:testDebugUnitTest --tests "*.PlayWeightRepositoryTest"
  ```
  Expected: `6 tests completed, 0 failures`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/data/repository/PlayWeightRepository.kt \
          app/src/test/java/com/example/musicplayer/PlayWeightRepositoryTest.kt
  git commit -m "feat: add PlayWeightRepository with skip/play weight logic (6 tests green)"
  ```

---

## Task 9: LocalDataSource + SongRepository

**Files:**
- Create: `data/source/LocalDataSource.kt`
- Create: `data/repository/SongRepository.kt`

- [ ] **Step 1: Create `LocalDataSource.kt`**

  ```kotlin
  package com.example.musicplayer.data.source

  import android.content.ContentUris
  import android.content.Context
  import android.provider.MediaStore
  import com.example.musicplayer.data.db.entity.SongEntity
  import dagger.hilt.android.qualifiers.ApplicationContext
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class LocalDataSource @Inject constructor(
      @ApplicationContext private val context: Context,
  ) {

      fun scanLocalMusic(): List<SongEntity> {
          val songs = mutableListOf<SongEntity>()
          val projection = arrayOf(
              MediaStore.Audio.Media._ID,
              MediaStore.Audio.Media.TITLE,
              MediaStore.Audio.Media.ARTIST,
              MediaStore.Audio.Media.ALBUM,
              MediaStore.Audio.Media.DURATION,
          )
          val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

          context.contentResolver.query(
              MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
              projection,
              selection,
              null,
              "${MediaStore.Audio.Media.TITLE} ASC",
          )?.use { cursor ->
              val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
              val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
              val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
              val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
              val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

              while (cursor.moveToNext()) {
                  val mediaId = cursor.getLong(idCol)
                  val contentUri = ContentUris.withAppendedId(
                      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                      mediaId,
                  ).toString()

                  songs += SongEntity(
                      title = cursor.getString(titleCol) ?: "Unknown",
                      artist = cursor.getString(artistCol) ?: "Unknown Artist",
                      album = cursor.getString(albumCol) ?: "Unknown Album",
                      duration = cursor.getLong(durationCol),
                      sourceType = "LOCAL",
                      localPath = contentUri,
                  )
              }
          }
          return songs
      }
  }
  ```

- [ ] **Step 2: Create `SongRepository.kt`**

  ```kotlin
  package com.example.musicplayer.data.repository

  import com.example.musicplayer.data.db.dao.SongDao
  import com.example.musicplayer.data.db.entity.SongEntity
  import com.example.musicplayer.data.source.LocalDataSource
  import com.example.musicplayer.domain.model.Song
  import com.example.musicplayer.domain.model.SourceType
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class SongRepository @Inject constructor(
      private val dao: SongDao,
      private val localDataSource: LocalDataSource,
  ) {

      fun observeAll(): Flow<List<Song>> = dao.observeAll().map { it.map(SongEntity::toDomain) }

      fun observeArtists(): Flow<List<String>> = dao.observeArtists()

      fun observeAlbums(): Flow<List<String>> = dao.observeAlbums()

      fun observeByArtist(artist: String): Flow<List<Song>> =
          dao.observeByArtist(artist).map { it.map(SongEntity::toDomain) }

      fun observeByAlbum(album: String): Flow<List<Song>> =
          dao.observeByAlbum(album).map { it.map(SongEntity::toDomain) }

      suspend fun syncLocalMusic() {
          dao.deleteAllLocal()
          val scanned = localDataSource.scanLocalMusic()
          dao.insertAll(scanned)
      }

      suspend fun getById(id: Long): Song? = dao.getById(id)?.toDomain()
  }

  private fun SongEntity.toDomain() = Song(
      id = id,
      title = title,
      artist = artist,
      album = album,
      duration = duration,
      sourceType = if (sourceType == "LOCAL") SourceType.LOCAL else SourceType.SERVER,
      serverId = serverId,
      serverSongId = serverSongId,
      localPath = localPath,
      cachedPath = cachedPath,
  )
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/data/source/ \
          app/src/main/java/com/example/musicplayer/data/repository/SongRepository.kt
  git commit -m "feat: add LocalDataSource (MediaStore) and SongRepository"
  ```

---

## Task 10: PlaylistRepository

**Files:**
- Create: `data/repository/PlaylistRepository.kt`

- [ ] **Step 1: Create `PlaylistRepository.kt`**

  ```kotlin
  package com.example.musicplayer.data.repository

  import com.example.musicplayer.data.db.dao.PlaylistDao
  import com.example.musicplayer.data.db.entity.PlaylistEntity
  import com.example.musicplayer.data.db.entity.PlaylistSongEntity
  import com.example.musicplayer.data.db.entity.SongEntity
  import com.example.musicplayer.domain.model.Playlist
  import com.example.musicplayer.domain.model.Song
  import com.example.musicplayer.domain.model.SourceType
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class PlaylistRepository @Inject constructor(
      private val dao: PlaylistDao,
  ) {

      fun observeAll(): Flow<List<Playlist>> =
          dao.observeAll().map { it.map(PlaylistEntity::toDomain) }

      fun observeSongsForPlaylist(playlistId: Long): Flow<List<Song>> =
          dao.observeSongsForPlaylist(playlistId).map { it.map(SongEntity::toDomain) }

      suspend fun create(name: String): Long =
          dao.insert(PlaylistEntity(name = name))

      suspend fun rename(id: Long, name: String) {
          val existing = dao.getById(id) ?: return
          dao.update(existing.copy(name = name))
      }

      suspend fun delete(id: Long) {
          val existing = dao.getById(id) ?: return
          dao.delete(existing)
      }

      suspend fun addSong(playlistId: Long, songId: Long, position: Int) {
          dao.insertSong(PlaylistSongEntity(playlistId, songId, position))
      }

      suspend fun removeSong(playlistId: Long, songId: Long) {
          dao.removeSong(playlistId, songId)
      }
  }

  private fun PlaylistEntity.toDomain() = Playlist(
      id = id,
      name = name,
      serverId = serverId,
      isLocal = isLocal,
  )

  private fun SongEntity.toDomain() = Song(
      id = id,
      title = title,
      artist = artist,
      album = album,
      duration = duration,
      sourceType = if (sourceType == "LOCAL") SourceType.LOCAL else SourceType.SERVER,
      serverId = serverId,
      serverSongId = serverSongId,
      localPath = localPath,
      cachedPath = cachedPath,
  )
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/data/repository/PlaylistRepository.kt
  git commit -m "feat: add PlaylistRepository — CRUD for local playlists"
  ```

---

## Task 11: MusicService (Media3 MediaSessionService)

**Files:**
- Create: `service/MusicService.kt`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Create `MusicService.kt`**

  ```kotlin
  package com.example.musicplayer.service

  import androidx.media3.exoplayer.ExoPlayer
  import androidx.media3.session.MediaSession
  import androidx.media3.session.MediaSessionService
  import dagger.hilt.android.AndroidEntryPoint
  import javax.inject.Inject

  @AndroidEntryPoint
  class MusicService : MediaSessionService() {

      @Inject
      lateinit var player: ExoPlayer

      private var mediaSession: MediaSession? = null

      override fun onCreate() {
          super.onCreate()
          mediaSession = MediaSession.Builder(this, player).build()
      }

      override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
          mediaSession

      override fun onDestroy() {
          mediaSession?.run {
              player.release()
              release()
              mediaSession = null
          }
          super.onDestroy()
      }
  }
  ```

- [ ] **Step 2: Register service in `AndroidManifest.xml`**

  Inside `<application>`:
  ```xml
  <service
      android:name=".service.MusicService"
      android:foregroundServiceType="mediaPlayback"
      android:exported="true">
      <intent-filter>
          <action android:name="androidx.media3.session.MediaSessionService" />
      </intent-filter>
  </service>
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/service/MusicService.kt \
          app/src/main/AndroidManifest.xml
  git commit -m "feat: add MusicService — Media3 MediaSessionService with ExoPlayer"
  ```

---

## Task 12: PlayerViewModel + reusable components

**Files:**
- Create: `ui/screen/player/PlayerViewModel.kt`
- Create: `ui/component/AlbumArt.kt`
- Create: `ui/component/SongRow.kt`

- [ ] **Step 1: Create `PlayerViewModel.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.player

  import android.content.ComponentName
  import android.content.Context
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import androidx.media3.common.MediaItem
  import androidx.media3.common.Player
  import androidx.media3.session.MediaController
  import androidx.media3.session.SessionToken
  import com.example.musicplayer.data.repository.PlayWeightRepository
  import com.example.musicplayer.data.repository.SongRepository
  import com.example.musicplayer.domain.WeightedShuffler
  import com.example.musicplayer.domain.model.Song
  import com.example.musicplayer.service.MusicService
  import com.google.common.util.concurrent.MoreExecutors
  import dagger.hilt.android.lifecycle.HiltViewModel
  import dagger.hilt.android.qualifiers.ApplicationContext
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch
  import javax.inject.Inject

  data class PlayerState(
      val currentSong: Song? = null,
      val isPlaying: Boolean = false,
      val position: Long = 0L,
      val duration: Long = 0L,
      val shuffleEnabled: Boolean = false,
      val repeatMode: Int = Player.REPEAT_MODE_OFF,
      val queue: List<Song> = emptyList(),
  )

  @HiltViewModel
  class PlayerViewModel @Inject constructor(
      @ApplicationContext private val context: Context,
      private val songRepository: SongRepository,
      private val playWeightRepository: PlayWeightRepository,
      private val shuffler: WeightedShuffler,
  ) : ViewModel() {

      private val _state = MutableStateFlow(PlayerState())
      val state: StateFlow<PlayerState> = _state.asStateFlow()

      private var controller: MediaController? = null
      private var currentQueue: List<Song> = emptyList()
      private var currentSongId: Long? = null
      private var playbackStartPosition: Long = 0L

      init {
          connectToService()
      }

      private fun connectToService() {
          val token = SessionToken(context, ComponentName(context, MusicService::class.java))
          val future = MediaController.Builder(context, token).buildAsync()
          future.addListener({
              controller = future.get()
              controller?.addListener(playerListener)
              syncStateFromController()
          }, MoreExecutors.directExecutor())
      }

      private val playerListener = object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) {
              _state.update { it.copy(isPlaying = isPlaying) }
          }

          override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
              val wasManualSkip = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
              if (wasManualSkip) {
                  currentSongId?.let { id ->
                      viewModelScope.launch { playWeightRepository.recordSkip(id) }
                  }
              }
              syncStateFromController()
          }

          override fun onPlaybackStateChanged(state: Int) {
              if (state == Player.STATE_ENDED) {
                  currentSongId?.let { id ->
                      val ctrl = controller ?: return
                      val pct = if (ctrl.duration > 0)
                          ctrl.currentPosition.toFloat() / ctrl.duration else 0f
                      if (pct >= 0.8f) {
                          viewModelScope.launch { playWeightRepository.recordFullPlay(id) }
                      }
                  }
              }
          }
      }

      private fun syncStateFromController() {
          val ctrl = controller ?: return
          val idx = ctrl.currentMediaItemIndex
          val song = currentQueue.getOrNull(idx)
          currentSongId = song?.id
          _state.update { it.copy(
              currentSong = song,
              isPlaying = ctrl.isPlaying,
              duration = ctrl.duration.coerceAtLeast(0L),
              queue = currentQueue,
          )}
      }

      fun playSongs(songs: List<Song>, startIndex: Int = 0) {
          currentQueue = songs
          val ctrl = controller ?: return
          val items = songs.map { song ->
              MediaItem.Builder()
                  .setMediaId(song.id.toString())
                  .setUri(song.localPath ?: song.cachedPath ?: "")
                  .build()
          }
          ctrl.setMediaItems(items, startIndex, 0L)
          ctrl.prepare()
          ctrl.play()
          syncStateFromController()
      }

      fun playPause() {
          controller?.let { if (it.isPlaying) it.pause() else it.play() }
      }

      fun skipToNext() {
          controller?.seekToNextMediaItem()
      }

      fun skipToPrevious() {
          controller?.seekToPreviousMediaItem()
      }

      fun seekTo(positionMs: Long) {
          controller?.seekTo(positionMs)
      }

      fun toggleShuffle() {
          _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
      }

      fun cycleRepeatMode() {
          val next = when (_state.value.repeatMode) {
              Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
              Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
              else -> Player.REPEAT_MODE_OFF
          }
          controller?.repeatMode = next
          _state.update { it.copy(repeatMode = next) }
      }

      suspend fun nextWeightedSong(): Song? {
          val songs = currentQueue.ifEmpty { return null }
          val weights = songs.map { playWeightRepository.getWeight(it.id) }
          return shuffler.pick(songs, weights)
      }

      override fun onCleared() {
          controller?.release()
          super.onCleared()
      }
  }
  ```

- [ ] **Step 2: Create `ui/component/AlbumArt.kt`**

  ```kotlin
  package com.example.musicplayer.ui.component

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.MusicNote
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.layout.ContentScale
  import androidx.compose.ui.unit.Dp
  import androidx.compose.ui.unit.dp
  import coil.compose.SubcomposeAsyncImage

  @Composable
  fun AlbumArt(
      artUrl: String?,
      size: Dp = 56.dp,
      cornerRadius: Dp = 8.dp,
      modifier: Modifier = Modifier,
  ) {
      SubcomposeAsyncImage(
          model = artUrl,
          contentDescription = "Album art",
          contentScale = ContentScale.Crop,
          modifier = modifier
              .size(size)
              .clip(RoundedCornerShape(cornerRadius)),
          error = {
              Box(
                  modifier = Modifier
                      .size(size)
                      .clip(RoundedCornerShape(cornerRadius))
                      .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center,
              ) {
                  Icon(
                      imageVector = Icons.Default.MusicNote,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
              }
          },
      )
  }
  ```

- [ ] **Step 3: Create `ui/component/SongRow.kt`**

  ```kotlin
  package com.example.musicplayer.ui.component

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.*
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.example.musicplayer.domain.model.Song

  @Composable
  fun SongRow(
      song: Song,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Row(
          modifier = modifier
              .fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
          AlbumArt(artUrl = null, size = 48.dp)
          Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = song.title,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
              Text(
                  text = "${song.artist} · ${song.album}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
          }
          Text(
              text = song.duration.toMinuteSeconds(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
      }
  }

  private fun Long.toMinuteSeconds(): String {
      val totalSeconds = this / 1000
      val minutes = totalSeconds / 60
      val seconds = totalSeconds % 60
      return "%d:%02d".format(minutes, seconds)
  }
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/ui/
  git commit -m "feat: add PlayerViewModel, AlbumArt, SongRow components"
  ```

---

## Task 13: Navigation + MainActivity + MiniPlayer

**Files:**
- Modify: `MainActivity.kt`
- Create: `ui/navigation/AppNavigation.kt`
- Create: `ui/component/MiniPlayer.kt`

- [ ] **Step 1: Create `ui/component/MiniPlayer.kt`**

  ```kotlin
  package com.example.musicplayer.ui.component

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.*
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Pause
  import androidx.compose.material.icons.filled.PlayArrow
  import androidx.compose.material.icons.filled.SkipNext
  import androidx.compose.material3.*
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.example.musicplayer.ui.screen.player.PlayerState

  @Composable
  fun MiniPlayer(
      state: PlayerState,
      onPlayPause: () -> Unit,
      onSkipNext: () -> Unit,
      onTap: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      if (state.currentSong == null) return

      Surface(
          modifier = modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.surfaceVariant,
          shadowElevation = 8.dp,
      ) {
          Row(
              modifier = Modifier
                  .clickable(onClick = onTap)
                  .padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
              AlbumArt(artUrl = null, size = 40.dp)
              Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = state.currentSong.title,
                      style = MaterialTheme.typography.bodyMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                      text = state.currentSong.artist,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                  )
              }
              IconButton(onClick = onPlayPause) {
                  Icon(
                      imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = if (state.isPlaying) "Pause" else "Play",
                  )
              }
              IconButton(onClick = onSkipNext) {
                  Icon(Icons.Default.SkipNext, contentDescription = "Next")
              }
          }
      }
  }
  ```

- [ ] **Step 2: Create `ui/navigation/AppNavigation.kt`**

  ```kotlin
  package com.example.musicplayer.ui.navigation

  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.*
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.graphics.vector.ImageVector
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import androidx.navigation.NavDestination.Companion.hierarchy
  import androidx.navigation.NavGraph.Companion.findStartDestination
  import androidx.navigation.compose.NavHost
  import androidx.navigation.compose.composable
  import androidx.navigation.compose.currentBackStackEntryAsState
  import androidx.navigation.compose.rememberNavController
  import com.example.musicplayer.ui.component.MiniPlayer
  import com.example.musicplayer.ui.screen.album.AlbumScreen
  import com.example.musicplayer.ui.screen.artist.ArtistScreen
  import com.example.musicplayer.ui.screen.library.LibraryScreen
  import com.example.musicplayer.ui.screen.player.PlayerScreen
  import com.example.musicplayer.ui.screen.player.PlayerViewModel
  import com.example.musicplayer.ui.screen.playlist.PlaylistScreen

  sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
      object Library : Screen("library", "Bibliothek", Icons.Default.LibraryMusic)
      object Playlists : Screen("playlists", "Playlists", Icons.Default.QueueMusic)
      object Artists : Screen("artists", "Künstler", Icons.Default.Person)
      object Albums : Screen("albums", "Alben", Icons.Default.Album)
      object Player : Screen("player", "Jetzt läuft", Icons.Default.PlayCircle)
  }

  private val tabs = listOf(
      Screen.Library, Screen.Playlists, Screen.Artists, Screen.Albums, Screen.Player
  )

  @Composable
  fun AppNavigation() {
      val navController = rememberNavController()
      val playerViewModel: PlayerViewModel = hiltViewModel()
      val playerState by playerViewModel.state.collectAsStateWithLifecycle()
      val navBackStackEntry by navController.currentBackStackEntryAsState()
      val currentDestination = navBackStackEntry?.destination

      Scaffold(
          bottomBar = {
              NavigationBar {
                  tabs.forEach { screen ->
                      NavigationBarItem(
                          icon = { Icon(screen.icon, contentDescription = screen.label) },
                          label = { Text(screen.label) },
                          selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                          onClick = {
                              navController.navigate(screen.route) {
                                  popUpTo(navController.graph.findStartDestination().id) {
                                      saveState = true
                                  }
                                  launchSingleTop = true
                                  restoreState = true
                              }
                          }
                      )
                  }
              }
          },
      ) { innerPadding ->
          NavHost(navController = navController, startDestination = Screen.Library.route) {
              composable(Screen.Library.route) {
                  LibraryScreen(
                      contentPadding = innerPadding,
                      onSongClick = { songs, index ->
                          playerViewModel.playSongs(songs, index)
                          navController.navigate(Screen.Player.route)
                      }
                  )
              }
              composable(Screen.Playlists.route) {
                  PlaylistScreen(contentPadding = innerPadding)
              }
              composable(Screen.Artists.route) {
                  ArtistScreen(contentPadding = innerPadding)
              }
              composable(Screen.Albums.route) {
                  AlbumScreen(contentPadding = innerPadding)
              }
              composable(Screen.Player.route) {
                  PlayerScreen(
                      viewModel = playerViewModel,
                      contentPadding = innerPadding,
                  )
              }
          }
      }
  }
  ```

- [ ] **Step 3: Replace `MainActivity.kt`**

  ```kotlin
  package com.example.musicplayer

  import android.Manifest
  import android.os.Build
  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.result.contract.ActivityResultContracts
  import com.example.musicplayer.ui.navigation.AppNavigation
  import com.example.musicplayer.ui.theme.MusicPlayerTheme
  import dagger.hilt.android.AndroidEntryPoint

  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {

      private val permissionLauncher = registerForActivityResult(
          ActivityResultContracts.RequestPermission()
      ) { /* permission granted or denied — library screen handles state */ }

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          requestAudioPermission()
          setContent {
              MusicPlayerTheme {
                  AppNavigation()
              }
          }
      }

      private fun requestAudioPermission() {
          val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Manifest.permission.READ_MEDIA_AUDIO
          } else {
              Manifest.permission.READ_EXTERNAL_STORAGE
          }
          permissionLauncher.launch(permission)
      }
  }
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/
  git commit -m "feat: add navigation scaffold, MiniPlayer, MainActivity with permission request"
  ```

---

## Task 14: LibraryScreen + LibraryViewModel

**Files:**
- Create: `ui/screen/library/LibraryViewModel.kt`
- Create: `ui/screen/library/LibraryScreen.kt`

- [ ] **Step 1: Create `LibraryViewModel.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.library

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.example.musicplayer.data.repository.SongRepository
  import com.example.musicplayer.domain.model.Song
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.combine
  import kotlinx.coroutines.flow.stateIn
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch
  import javax.inject.Inject

  data class LibraryState(
      val songs: List<Song> = emptyList(),
      val query: String = "",
      val isLoading: Boolean = false,
  ) {
      val filtered: List<Song>
          get() = if (query.isBlank()) songs
          else songs.filter {
              it.title.contains(query, ignoreCase = true) ||
                  it.artist.contains(query, ignoreCase = true) ||
                  it.album.contains(query, ignoreCase = true)
          }
  }

  @HiltViewModel
  class LibraryViewModel @Inject constructor(
      private val songRepository: SongRepository,
  ) : ViewModel() {

      private val _query = MutableStateFlow("")
      private val _isLoading = MutableStateFlow(false)

      val state = combine(songRepository.observeAll(), _query, _isLoading) { songs, query, loading ->
          LibraryState(songs = songs, query = query, isLoading = loading)
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())

      fun setQuery(query: String) { _query.update { query } }

      fun syncLocalMusic() {
          viewModelScope.launch {
              _isLoading.update { true }
              songRepository.syncLocalMusic()
              _isLoading.update { false }
          }
      }
  }
  ```

- [ ] **Step 2: Create `LibraryScreen.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.library

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.itemsIndexed
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Refresh
  import androidx.compose.material.icons.filled.Search
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.example.musicplayer.domain.model.Song
  import com.example.musicplayer.ui.component.SongRow

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun LibraryScreen(
      contentPadding: PaddingValues,
      onSongClick: (songs: List<Song>, index: Int) -> Unit,
      viewModel: LibraryViewModel = hiltViewModel(),
  ) {
      val state by viewModel.state.collectAsStateWithLifecycle()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text("Bibliothek") },
                  actions = {
                      IconButton(onClick = { viewModel.syncLocalMusic() }) {
                          Icon(Icons.Default.Refresh, contentDescription = "Sync")
                      }
                  }
              )
          },
          contentPadding = contentPadding,
      ) { padding ->
          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(padding)
          ) {
              OutlinedTextField(
                  value = state.query,
                  onValueChange = viewModel::setQuery,
                  placeholder = { Text("Suchen…") },
                  leadingIcon = { Icon(Icons.Default.Search, null) },
                  singleLine = true,
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 8.dp),
              )

              if (state.isLoading) {
                  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                      CircularProgressIndicator()
                  }
              } else {
                  LazyColumn(modifier = Modifier.fillMaxSize()) {
                      itemsIndexed(state.filtered, key = { _, song -> song.id }) { index, song ->
                          SongRow(
                              song = song,
                              onClick = { onSongClick(state.filtered, index) },
                          )
                          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                      }
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/ui/screen/library/
  git commit -m "feat: add LibraryScreen with search and sync"
  ```

---

## Task 15: PlayerScreen

**Files:**
- Create: `ui/screen/player/PlayerScreen.kt`

- [ ] **Step 1: Create `PlayerScreen.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.player

  import androidx.compose.foundation.layout.*
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.*
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import androidx.media3.common.Player
  import com.example.musicplayer.ui.component.AlbumArt

  @Composable
  fun PlayerScreen(
      viewModel: PlayerViewModel,
      contentPadding: PaddingValues,
  ) {
      val state by viewModel.state.collectAsStateWithLifecycle()
      val song = state.currentSong

      Column(
          modifier = Modifier
              .fillMaxSize()
              .padding(contentPadding)
              .padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
          Spacer(modifier = Modifier.height(24.dp))

          AlbumArt(
              artUrl = null,
              size = 280.dp,
              cornerRadius = 16.dp,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text = song?.title ?: "Kein Song",
              style = MaterialTheme.typography.headlineSmall,
              textAlign = TextAlign.Center,
              maxLines = 2,
          )
          Text(
              text = song?.artist ?: "",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )

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

          // Controls
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
          ) {
              IconButton(onClick = { viewModel.skipToPrevious() }) {
                  Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
              }
              FilledIconButton(
                  onClick = { viewModel.playPause() },
                  modifier = Modifier.size(64.dp),
              ) {
                  Icon(
                      imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = if (state.isPlaying) "Pause" else "Play",
                      modifier = Modifier.size(36.dp),
                  )
              }
              IconButton(onClick = { viewModel.skipToNext() }) {
                  Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
              }
          }

          // Shuffle + Repeat
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
              IconButton(onClick = { viewModel.toggleShuffle() }) {
                  Icon(
                      imageVector = Icons.Default.Shuffle,
                      contentDescription = "Shuffle",
                      tint = if (state.shuffleEnabled)
                          MaterialTheme.colorScheme.primary
                      else
                          MaterialTheme.colorScheme.onSurfaceVariant,
                  )
              }
              IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                  Icon(
                      imageVector = when (state.repeatMode) {
                          Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                          Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                          else -> Icons.Default.Repeat
                      },
                      contentDescription = "Repeat",
                      tint = if (state.repeatMode != Player.REPEAT_MODE_OFF)
                          MaterialTheme.colorScheme.primary
                      else
                          MaterialTheme.colorScheme.onSurfaceVariant,
                  )
              }
          }
      }
  }

  private fun Long.toMinSec(): String {
      val totalSeconds = this / 1000
      val min = totalSeconds / 60
      val sec = totalSeconds % 60
      return "%d:%02d".format(min, sec)
  }
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/ui/screen/player/PlayerScreen.kt
  git commit -m "feat: add PlayerScreen with playback controls, seekbar, shuffle, repeat"
  ```

---

## Task 16: ArtistScreen, AlbumScreen, PlaylistScreen

**Files:**
- Create: `ui/screen/artist/ArtistViewModel.kt`
- Create: `ui/screen/artist/ArtistScreen.kt`
- Create: `ui/screen/album/AlbumViewModel.kt`
- Create: `ui/screen/album/AlbumScreen.kt`
- Create: `ui/screen/playlist/PlaylistViewModel.kt`
- Create: `ui/screen/playlist/PlaylistScreen.kt`

- [ ] **Step 1: Create `ArtistViewModel.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.artist

  import androidx.lifecycle.ViewModel
  import com.example.musicplayer.data.repository.SongRepository
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.stateIn
  import androidx.lifecycle.viewModelScope
  import javax.inject.Inject

  @HiltViewModel
  class ArtistViewModel @Inject constructor(repo: SongRepository) : ViewModel() {
      val artists = repo.observeArtists()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  }
  ```

- [ ] **Step 2: Create `ArtistScreen.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.artist

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun ArtistScreen(
      contentPadding: PaddingValues,
      viewModel: ArtistViewModel = hiltViewModel(),
  ) {
      val artists by viewModel.artists.collectAsStateWithLifecycle()

      Scaffold(
          topBar = { TopAppBar(title = { Text("Künstler") }) },
          contentPadding = contentPadding,
      ) { padding ->
          LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
              items(artists, key = { it }) { artist ->
                  ListItem(
                      headlineContent = { Text(artist) },
                      modifier = Modifier.padding(horizontal = 8.dp),
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 3: Create `AlbumViewModel.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.album

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.example.musicplayer.data.repository.SongRepository
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.stateIn
  import javax.inject.Inject

  @HiltViewModel
  class AlbumViewModel @Inject constructor(repo: SongRepository) : ViewModel() {
      val albums = repo.observeAlbums()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  }
  ```

- [ ] **Step 4: Create `AlbumScreen.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.album

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlbumScreen(
      contentPadding: PaddingValues,
      viewModel: AlbumViewModel = hiltViewModel(),
  ) {
      val albums by viewModel.albums.collectAsStateWithLifecycle()

      Scaffold(
          topBar = { TopAppBar(title = { Text("Alben") }) },
          contentPadding = contentPadding,
      ) { padding ->
          LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
              items(albums, key = { it }) { album ->
                  ListItem(
                      headlineContent = { Text(album) },
                      modifier = Modifier.padding(horizontal = 8.dp),
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 5: Create `PlaylistViewModel.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.playlist

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.example.musicplayer.data.repository.PlaylistRepository
  import com.example.musicplayer.domain.model.Playlist
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.stateIn
  import kotlinx.coroutines.launch
  import javax.inject.Inject

  @HiltViewModel
  class PlaylistViewModel @Inject constructor(
      private val repo: PlaylistRepository,
  ) : ViewModel() {

      val playlists = repo.observeAll()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

      fun createPlaylist(name: String) {
          viewModelScope.launch { repo.create(name) }
      }

      fun deletePlaylist(playlist: Playlist) {
          viewModelScope.launch { repo.delete(playlist.id) }
      }
  }
  ```

- [ ] **Step 6: Create `PlaylistScreen.kt`**

  ```kotlin
  package com.example.musicplayer.ui.screen.playlist

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Add
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PlaylistScreen(
      contentPadding: PaddingValues,
      viewModel: PlaylistViewModel = hiltViewModel(),
  ) {
      val playlists by viewModel.playlists.collectAsStateWithLifecycle()
      var showDialog by remember { mutableStateOf(false) }
      var newName by remember { mutableStateOf("") }

      if (showDialog) {
          AlertDialog(
              onDismissRequest = { showDialog = false },
              title = { Text("Neue Wiedergabeliste") },
              text = {
                  OutlinedTextField(
                      value = newName,
                      onValueChange = { newName = it },
                      label = { Text("Name") },
                      singleLine = true,
                  )
              },
              confirmButton = {
                  TextButton(onClick = {
                      if (newName.isNotBlank()) viewModel.createPlaylist(newName)
                      newName = ""
                      showDialog = false
                  }) { Text("Erstellen") }
              },
              dismissButton = {
                  TextButton(onClick = { showDialog = false }) { Text("Abbrechen") }
              },
          )
      }

      Scaffold(
          topBar = { TopAppBar(title = { Text("Wiedergabelisten") }) },
          floatingActionButton = {
              FloatingActionButton(onClick = { showDialog = true }) {
                  Icon(Icons.Default.Add, contentDescription = "Neue Playlist")
              }
          },
          contentPadding = contentPadding,
      ) { padding ->
          LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
              items(playlists, key = { it.id }) { playlist ->
                  ListItem(
                      headlineContent = { Text(playlist.name) },
                      modifier = Modifier.padding(horizontal = 8.dp),
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              }
          }
      }
  }
  ```

- [ ] **Step 7: Full build + run**

  ```
  ./gradlew assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`
  Deploy to emulator or physical device and verify:
  - App opens with dark theme
  - 5 bottom nav tabs visible
  - Refresh in Bibliothek triggers scan
  - Songs appear in list
  - Tapping a song starts playback
  - Player screen shows controls

- [ ] **Step 8: Final commit**

  ```bash
  git add app/src/main/java/com/example/musicplayer/
  git commit -m "feat: add Artist, Album, Playlist screens — Phase 1 complete"
  ```

---

## Self-Review Checklist (completed)

| Spec Requirement | Covered in Task |
|---|---|
| Kotlin, min SDK 26 | Task 2 |
| Material3 Dark | Task 6 |
| Jetpack Compose | Task 2, all UI tasks |
| Navigation Compose (5 tabs) | Task 13 |
| Media3/ExoPlayer + background service | Task 11, 12 |
| Lock screen/notification controls | Task 11 (MediaSession) |
| Room (Song, PlayWeight, Playlist, PlaylistSong, Server) | Task 4 |
| Hilt DI | Task 5 |
| LocalDataSource (MediaStore) | Task 9 |
| SongRepository | Task 9 |
| PlaylistRepository (local CRUD) | Task 10 |
| WeightedShuffler (TDD) | Task 7 |
| PlayWeightRepository — skip/play rules | Task 8 |
| skip = manual only | Task 12 (PlayerViewModel) |
| ≥80% play = full play | Task 12 (PlayerViewModel) |
| SongRow + AlbumArt components | Task 12 |
| MiniPlayer | Task 13 |
| LibraryScreen + search + sync | Task 14 |
| PlayerScreen + controls | Task 15 |
| Artist + Album + Playlist screens | Task 16 |
| Android permission handling | Task 5 + Task 13 |

**Not in Phase 1 (covered in Phase 2/3):**
- Server integration (Subsonic/Jellyfin)
- Offline caching (WorkManager)
- SettingsScreen
- OfflineScreen
- SongDetailScreen (weight reset UI)
- Album art from MediaStore
