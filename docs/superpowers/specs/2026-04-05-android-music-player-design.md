# Android Music Player — Design Spec

**Datum:** 2026-04-05
**Status:** Approved

---

## Überblick

Eine Android Music Player App (Kotlin) mit lokalem Datei-Support, Subsonic/Jellyfin-Server-Integration, Wiedergabelisten-Verwaltung, gewichtetem Shuffle-Modus und optionalem Offline-Caching.

---

## Architektur

### Ansatz
Single-Module Android App nach **MVVM + Repository Pattern** (Google-Architekturempfehlung).

### Schichtenmodell
```
UI (Jetpack Compose)
  └── ViewModel (StateFlow)
        └── Repository
              ├── LocalDataSource    (MediaStore + Room)
              ├── ServerDataSource   (Retrofit → Subsonic/Jellyfin API)
              └── CacheDataSource    (Room + FileSystem)
```

**Strikte Schichtenregel:** UI kommuniziert nur mit ViewModels. ViewModels rufen nur Repositories. Repositories verwalten DataSources.

### Tech-Stack
| Bereich | Bibliothek |
|---|---|
| Sprache | Kotlin |
| Min SDK | 26 (Android 8.0) |
| UI | Jetpack Compose + Material3 Dark |
| Navigation | Navigation Compose |
| Playback | Media3 / ExoPlayer |
| Background Service | MediaSessionService (Media3) als Foreground-Service |
| DI | Hilt |
| Datenbank | Room |
| Netzwerk | Retrofit + OkHttp |
| Async | Kotlin Coroutines + Flow |
| Bilder / Cover | Coil |
| Background Tasks | WorkManager (Caching) |
| Secrets | Android Keystore (Passwörter) |

---

## Datenmodell (Room)

### Song
```
id: Long
title: String
artist: String
album: String
duration: Long          (Millisekunden)
sourceType: Enum        LOCAL | SERVER
serverId: Long?         (FK → Server, nullable für lokale Songs)
serverSongId: String?   (ID beim Server)
localPath: String?      (Pfad auf Gerät)
cachedPath: String?     (Pfad zum gecachten Offline-File)
```

### PlayWeight
```
songId: Long            (FK → Song)
skipCount: Int          (wie oft übersprungen)
playCount: Int          (wie oft vollständig gespielt)
weight: Float           (berechneter Wert, 0.1–1.0, Default: 1.0)
```

### Playlist
```
id: Long
name: String
serverId: Long?         (nullable = lokale Playlist)
isLocal: Boolean
```

### PlaylistSong
```
playlistId: Long
songId: Long
position: Int
```

### Server
```
id: Long
name: String
url: String
username: String
passwordEncrypted: String   (Android Keystore)
type: Enum                  SUBSONIC | JELLYFIN
isActive: Boolean           (vom Nutzer aktiviert/deaktiviert; deaktivierte Server werden nicht angezeigt)
```

---

## Skip-Algorithmus (gewichtetes Shuffle)

### Gewichtungsregeln
- **Anfangswert:** `weight = 1.0`
- **Bei Skip:** `weight = max(0.1, weight × 0.75)`
- **Bei vollständigem Play:** `weight = min(1.0, weight × 1.1)`

### Auswahl-Mechanismus
Beim Shuffle wird der nächste Song per **gewichtetem Zufalls-Draw** ausgewählt: jeder Song hat eine Auswahlwahrscheinlichkeit proportional zu seinem `weight`. Songs mit `weight = 0.1` werden ca. 10× seltener gezogen als Songs mit `weight = 1.0`.

### Definition "vollständig gespielt"
Ein Song gilt als vollständig gespielt wenn ≥ 80% der Dauer abgespielt wurden, bevor er endet oder gewechselt wird.

### Was zählt als Skip?
Nur **manuelle Skips** (Nutzer drückt "Nächster" oder wischt den Song weg) zählen als Skip. Natürliches Ende eines Songs (Song läuft durch) zählt nie als Skip — unabhängig davon ob danach der Weight-Bonus für "vollständig gespielt" greift.

---

## Navigation & Screens

### Bottom Navigation (5 Tabs)
| Tab | Inhalt |
|---|---|
| Bibliothek | Alle Songs (lokal + Server), Suche, Filter-Chips: Alle / Lokal / Server |
| Wiedergabelisten | Lokale + Server-Playlists, erstellen / bearbeiten / löschen |
| Künstler | Gruppierte Ansicht nach Artist |
| Alben | Gruppierte Ansicht nach Album |
| Jetzt läuft | Player-Screen |

### Zusätzliche Screens (via Navigation, kein Tab)
- `ServerListScreen` — Server verwalten (erreichbar aus Settings)
- `ServerEditScreen` — Server hinzufügen / bearbeiten
- `SongDetailScreen` — Songinfo, PlayWeight anzeigen und zurücksetzen
- `SettingsScreen` — Theme, Cache-Größe, Auto-Sync
- `OfflineScreen` — gecachte Songs verwalten

### Player-Screen ("Jetzt läuft")
- Album-Art (groß, zentriert)
- Titel / Artist / Album
- Seekbar mit Zeitanzeige
- Steuerung: Vorheriger / Zurückspulen / Play-Pause / Vorspulen / Nächster
- Shuffle-Toggle → aktiviert gewichteten Zufallsmodus
- Repeat-Modi: Off / Eins / Alle
- Queue-Button → aufklappbare Liste der nächsten Songs
- Lock-Screen- und Notification-Controls via `MediaSessionService`

---

## Server-Integration

### Unterstützte APIs
- **Subsonic API v1.16+** — kompatibel mit Subsonic, Navidrome, Airsonic, Funkwhale
- **Jellyfin REST API**

### Verbindungsablauf
1. Nutzer gibt URL, Username, Passwort ein
2. App erkennt Server-Typ automatisch via Ping-Endpunkt
3. Passwort wird mit Android Keystore verschlüsselt gespeichert — nie im Klartext
4. Erfolgreicher Connect → Server als Quelle in der Bibliothek sichtbar

### Synchronisation
- **Erster Connect:** vollständige Bibliothek laden (Songs, Playlists, Cover-Art)
- **Danach:** manueller Refresh-Button + optionaler Auto-Sync beim App-Start (Settings)
- Server-Playlists: können gespiegelt (read-only) oder als lokale Kopie editiert werden

### Mehrere Server
- Jeder Server erscheint als eigene Quelle
- Filter-Chip oben in der Bibliothek: `Alle` | `Lokal` | `[Servername 1]` | `[Servername 2]`
- PlayWeights gelten pro Song; serverübergreifende Erkennung anhand Titel + Artist

---

## Offline-Caching

### Markierung
- Nutzer markiert einzelne Songs oder ganze Playlists als "Offline verfügbar"
- Download läuft im Hintergrund via `WorkManager`

### Speicherung
- Gecachte Dateien im App-privaten Verzeichnis: `/data/data/<package>/cache/music/`
- `cachedPath` im `Song`-Eintrag zeigt auf lokale Kopie

### Verwaltung (`OfflineScreen`)
- Liste aller gecachten Songs mit Dateigröße
- Gesamter Cache-Verbrauch sichtbar
- Einzelne Songs oder gesamten Cache löschen
- Maximale Cache-Größe konfigurierbar (Default: 2 GB)

### Offline-Verhalten
- Bei fehlender Netzwerkverbindung: Server-Songs ohne Cache werden ausgegraut
- Shuffle-Modus wählt bei Offline-Betrieb nur verfügbare (gecachte oder lokale) Songs
- Gecachte Songs spielen aus lokalem Pfad — kein UI-Unterschied zu lokalen Songs

---

## Dateiformat-Support

Alle von Android / ExoPlayer nativ unterstützten Formate: MP3, AAC, FLAC, OGG, WAV, M4A, OPUS.

---

## Sicherheit

- Server-Passwörter: Android Keystore (kein Klartext in SharedPrefs oder Room)
- Netzwerk: HTTPS empfohlen; HTTP wird mit Warnung akzeptiert
- Keine Nutzerdaten werden außerhalb des Geräts gespeichert

---

## Nicht im Scope (MVP)

- Spotify / YouTube Music Integration
- Lyrics-Anzeige
- Equalizer
- Crossfade
- Social Features / Sharing
- Widget auf dem Homescreen (kann später ergänzt werden)
