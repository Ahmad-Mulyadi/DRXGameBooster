# DRX GAME TURBO PRO (v1.25.5)

**Developer:** DerreX  
**Application ID:** `com.derrex.gameturbo`  
**Current Version:** `1.25.5` (`versionCode = 8`)

DRX GAME TURBO PRO is a Kotlin & Jetpack Compose Material 3 gaming companion built for Android without root, without `AccessibilityService`, and without modifying external game files or accounts.

---

## What's New in v1.25.5

- **Elite Dark • iOS-Inspired • Electric Blue Overhaul:** Refined surface hierarchy (`#07090D`, `#0C1017`, `#121925`, `#192230`), Electric Blue (`#1677FF`), Electric Cyan (`#20D5FF`), Silver (`#DCE5F2`), hairline glass borders, 16–26dp cards, floating 5-tab capsule bar (`Home`, `Games`, `Tools`, `Activity`, `Profile`), and consolidated 5-tab Floating Control Center (`Home`, `Apps`, `Voice`, `Tools`, `Monitor`).
- **DRX Session Timeline & DRX Game Resource Report:** Room-persisted event timeline (`LAUNCHED`, `MONITORING_STARTED`, `TEMP_INCREASED`, `NETWORK_CHANGED`, `THERMAL_WARNING`, `BATTERY_DROP`, `SESSION_ENDED`), temperature/battery/RAM progression graphs, `.txt` export, and honest per-game resource reports with clear OS-restriction labels.
- **DRX Game Profile Automation:** Per-game automation profiles (HUD layout, FPS monitor, Floating Sidebar, Network monitor, Quick tools, Recorder/Screenshot shortcuts, Session timer, performance display mode) with optional auto-apply on game launch.
- **DRX Pre-Game Check:** Non-blocking readiness diagnostics (Storage, Battery & Temp, OS Thermal Status, Network, Overlay Permission, Recorder Readiness, Monitoring Status) that always allow immediate game launch.
- **DRX Touch Response Test & DRX Display Intelligence:** Dedicated Touch Response Test (`Tap Test`, `Multi-touch`, `Touch Tracking`, `Gesture Test`, `Accuracy Target Test`) separated from Display Intelligence (Resolution, Density, System-Supported `Display.Mode` Refresh Rate switcher, Orientation, Physical Display Size, HDR), with duplicate embedded screen recorder removed from Display.
- **DRX Gaming Focus Mode:** Focus timer, permission-gated DND Priority toggle (always preserving alarms and emergency calls), screen timeout extension, notification guidance, and session summary.

---

## Core Architecture & Features

- **UI & Theme:** Jetpack Compose + Material 3 (`DrxGameTurboTheme`), 7 theme presets, custom accent color & wallpaper, adaptive navigation (`Floating Capsule NavigationBar` on compact, `NavigationRail` on wide screens), English & Indonesian (`AppStrings`), full `testTag` coverage.
- **Persistence:** Room Database v6 (`DrxDatabase`) with migrations (`1->6`) storing tracked games, play sessions, network metrics, voice presets, diagnostic crash events, game notifications, event reminders, stability watch warnings, session timeline events, and per-game profile automations; plus Jetpack DataStore (`DrxPreferences`).
- **Telemetry & Services:**
  - `DeviceTelemetryMonitor`: real `ActivityManager`, `BatteryManager`, `StatFs`, `ConnectivityManager`, `Display.Mode`, and `Choreographer` frame-pacing UI FPS + thermal status.
  - `FloatingSidebarService` & `FloatingMiniWindowManager`: edge-swipe control center with live HUD strip, quick profile switcher, quick apps, capture controls, and floating utilities.
  - `ScreenRecorderService` & `SystemAudioVideoMuxerEngine`: hardware `MediaCodec` H.264 + AAC/MediaMuxer recording with real-time pause/resume timestamp continuity, internal audio capture on Android 10+ (graceful fallback when apps disallow `AudioPlaybackCapture`), and mic PCM RMS metering.
  - `DrxGameNotificationListenerService`: `NotificationListenerService` with OTP/PIN/token redaction & per-game grouping.
  - `DrxUpdateManager`: HTTPS manifest fetch, SHA-256 verification, PackageManager APK signature & `versionCode` validation, and system `PackageInstaller` intent flow (never silent installs).

---

## Build & Automatic APK Detection

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest --console=plain
```

### Run Full Auto-Build & APK Change Detector
```bash
bash scripts/drx_auto_build.sh
```
Outputs generated cleanly in `src/`:
- `src/apk/DRX-GAME-TURBO-PRO-v1.25.5.apk`
- `src/apk/DRX-GAME-TURBO-PRO.apk`
- `src/apk/DRX-GAME-TURBO-PRO-DEBUG.apk`
- `src/aab/DRX-GAME-TURBO-PRO.aab`
- `src/update-manifest.json` & `src/apk/update-manifest.json`
- `src/SHA256SUMS.txt`
- `src/logs/build-log.txt`

### Optional HTTPS OTA Bridge Configuration
```bash
export DRX_UPDATE_BASE_URL="https://updates.example.com/drx/apk"
export DRX_UPDATE_MANIFEST_URL="https://updates.example.com/drx/apk/update-manifest.json"
bash scripts/drx_auto_build.sh
```
For Play Store release signing:
```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
bash scripts/drx_auto_build.sh
```
