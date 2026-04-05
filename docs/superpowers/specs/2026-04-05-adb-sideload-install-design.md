# ADB Sideload Install — Design Spec

**Date:** 2026-04-05
**Goal:** Install the MusicPlayer app on a personal Android device via ADB (USB debugging), without using the Google Play Store.

---

## Context

The app (`com.wolf.musicplayer`, versionCode 1) is fully buildable as a debug APK via Gradle. No release signing or Play Store account is required for personal use via ADB.

---

## Approach: ADB installDebug

Use the Gradle task `installDebug` which builds the debug APK and installs it on any connected ADB-authorized device in a single command.

---

## Prerequisites

### On the Android device (one-time setup)
1. *Settings → About phone → Build number* — tap 7 times to unlock Developer Options
2. *Settings → Developer options → USB debugging* — enable
3. Connect phone via USB, select "File Transfer" (MTP) mode when prompted
4. On the phone: approve the "Allow USB debugging?" dialog for this PC

### On the PC
- Android Studio is already installed → ADB is available at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- Ensure ADB is on the system PATH, or use the full path

---

## Build & Install

```bash
# Verify device is recognized
adb devices
# Expected output: <serial>  device

# Build debug APK and install in one step
./gradlew installDebug
```

The app will appear in the device's app drawer immediately after.

---

## Updating the App

Re-run `./gradlew installDebug` after any code change. ADB overwrites the existing installation automatically (equivalent to `adb install -r`).

```bash
./gradlew installDebug
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `adb devices` shows empty list | Check USB cable, re-approve debugging dialog on phone |
| `adb devices` shows `unauthorized` | Tap "Allow" on the phone screen |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall old version first: `adb uninstall com.wolf.musicplayer` |
| ADB not found | Add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH |

---

## Out of Scope

- Release signing / Play Store publishing (future work)
- ADB over WiFi (optional convenience, same commands)
