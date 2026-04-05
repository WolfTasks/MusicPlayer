# ADB Sideload Install Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install the MusicPlayer app (`com.wolf.musicplayer`) on a personal Android device via ADB, without a Play Store account.

**Architecture:** No code changes required. This is a one-time device + PC setup followed by a single Gradle command. Android Studio's bundled ADB handles communication between PC and phone over USB.

**Tech Stack:** Android SDK Platform Tools (ADB), Gradle wrapper (`./gradlew`)

---

### Task 1: Verify ADB is available on PATH

**Files:** none

- [ ] **Step 1: Check if ADB is already on PATH**

Open a terminal in the project root and run:
```bash
adb version
```
Expected output (version may differ):
```
Android Debug Bridge version 1.0.41
Version 35.x.x
```

- [ ] **Step 2: If ADB is not found — add it to PATH**

ADB ships with Android Studio. Find it at:
```
%LOCALAPPDATA%\Android\Sdk\platform-tools\
```

Add that folder to your Windows PATH:
1. Press `Win + R` → type `sysdm.cpl` → Enter
2. *Advanced* tab → *Environment Variables*
3. Under *User variables* → select `Path` → *Edit* → *New*
4. Paste: `C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools`
5. OK all dialogs, restart the terminal

Re-run to confirm:
```bash
adb version
```
Expected: version string as above (no "command not found")

---

### Task 2: Enable USB Debugging on the Android device (one-time)

**Files:** none — phone settings only

- [ ] **Step 1: Unlock Developer Options**

On the phone:
*Settings → About phone → Build number* — tap exactly **7 times** in quick succession.
You will see a toast: *"You are now a developer!"*

> On Samsung: *Settings → About phone → Software information → Build number*
> On Pixel: *Settings → About phone → Build number*

- [ ] **Step 2: Enable USB Debugging**

On the phone:
*Settings → Developer options* (now visible at the bottom of Settings) → toggle **USB debugging** ON.

Confirm the warning dialog that appears.

---

### Task 3: Connect phone and authorize the PC

**Files:** none

- [ ] **Step 1: Connect USB cable**

Plug phone into PC via USB. When prompted on the phone, select **"File Transfer" (MTP)** mode (not "Charging only").

- [ ] **Step 2: Authorize this PC**

A dialog appears on the phone:
*"Allow USB debugging? RSA key fingerprint: …"*

Tick **"Always allow from this computer"**, then tap **Allow**.

- [ ] **Step 3: Verify the device is recognized**

In the project terminal:
```bash
adb devices
```
Expected output:
```
List of devices attached
XXXXXXXXXXXXXXXX	device
```
The status must say `device` (not `unauthorized` or `offline`).

If it says `unauthorized`: unlock the phone screen and re-approve the dialog.
If the list is empty: try a different USB cable or USB port.

---

### Task 4: Build and install the app

**Files:** none changed — uses existing Gradle setup

- [ ] **Step 1: Run installDebug**

From the project root (`C:\Users\Admin\IdeaProjects\MusicPlayer\MusicPlayer`):
```bash
./gradlew installDebug
```

Expected output ends with:
```
Installing APK 'app-debug.apk' on ... for app:debug
BUILD SUCCESSFUL in Xs
```

- [ ] **Step 2: Launch the app on the phone**

Open the phone's app drawer. Find **MusicPlayer** and tap it. The app should open.

- [ ] **Step 3: Verify audio playback works**

Grant any permissions the app requests (storage/media access). Confirm that local music files are listed and playback works.

---

### Task 5: Establish update workflow

**Files:** none

- [ ] **Step 1: Make a trivial code change to verify update flow**

For example, change the app version name in `app/build.gradle.kts`:
```kotlin
versionName = "1.0.1"
```

- [ ] **Step 2: Re-run installDebug**

```bash
./gradlew installDebug
```

ADB automatically overwrites the existing install (`-r` flag is implicit). No need to uninstall first.

Expected: app updates in place, all data preserved.

- [ ] **Step 3: Revert the version change**

```kotlin
versionName = "1.0"
```

---

## Troubleshooting Reference

| Problem | Solution |
|---|---|
| `adb devices` empty | Different USB cable, re-approve debugging dialog |
| `adb devices` shows `unauthorized` | Approve dialog on phone screen |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Run `adb uninstall com.wolf.musicplayer` first |
| ADB not found | Add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH (Task 1, Step 2) |
| Build fails | Run `./gradlew assembleDebug` first to see compiler errors |
