# Syok Testing Guide & Verification Records

This document details verification procedures for both standalone head-unit operation and Android Auto projection, following the specifications in `plans/syok-v1.md`.

---

## 1. Testing Without a Car

Syok is designed for two complementary operational modes sharing one Media3 audio playback service:
1. **Standalone Aftermarket Android Head-Unit**: Runs directly on landscape Android hardware (e.g. 1024×600 or 1280×720 widescreen displays).
2. **Android Auto via Phone Projection**: Controlled from a companion Android phone connected via USB or wireless to an Android Auto-compatible head unit.

---

## 2. Desktop Head Unit (DHU) Procedure

To test Android Auto media browsing and playback without physical vehicle hardware, use Google's Desktop Head Unit (DHU) testing tool.

### Prerequisites
- An Android phone running Android 8.0+ with the Android Auto app installed.
- Android SDK Platform-Tools (`adb`) on your system PATH.
- Android SDK Desktop Head Unit package (or standalone `desktop-head-unit` binary).

### Procedure
1. **Enable Android Auto Developer Mode**:
   - On the phone, navigate to **Settings > Apps > Android Auto** (or open the Android Auto companion app).
   - Scroll to the bottom and tap **Version** 10 times until developer mode is unlocked.
   - Tap the overflow menu (three dots) in the top-right corner and select **Start head unit server**.

2. **Connect and Forward Port**:
   - Connect the phone to your computer via USB.
   - Verify device connection with `adb devices`.
   - Forward the head unit server port:
     ```bash
     adb forward tcp:5277 tcp:5277
     ```

3. **Launch Desktop Head Unit**:
   - Run the DHU utility from your Android SDK directory:
     ```bash
     # macOS / Linux
     $ANDROID_HOME/extras/google/auto/desktop-head-unit
     ```
   - The DHU window will open, simulating a car infotainment display.

4. **Verify Android Auto Media Workflows**:
   - **Discovery**: Ensure the Syok app icon appears in the car's app launcher.
   - **Browse**: Open Syok on the DHU. Navigate through `Root > Local > Albums > Tracks`. Verify that albums list properly, track metadata displays correctly, and artwork is fetched through `ArtworkContentProvider`.
   - **Playback**: Select a track from an album. Confirm audio begins, transport controls (play/pause/next/prev) update state, and Now Playing displays on the car screen.
   - **Voice Search**: Tap the microphone icon or say "Hey Google, play [track title] on Syok". Confirm search resolves and initiates playback.
   - **Disconnect & Reconnect**: Unplug the USB cable while audio is playing. Verify that playback pauses immediately without crashing. Reconnect the cable, restart the DHU session, and verify that Syok resumes cleanly.

---

## 3. Milestone B1 Verification Status

| Workflow | Status | Notes |
|---|---|---|
| MediaBrowser Service Contract (`BrowserContractTest`) | **PASS** | Automated instrumentation test on Pixel_3a API 34 AVD (`emulator-5554`). Validated root discovery, browse tree navigation, track playback, and search. |
| Automotive App Metadata & Manifest | **PASS** | Verified via `aapt2 dump xmltree`: `com.google.android.gms.car.application` metadata references `@xml/automotive_app_desc` and `PlaybackService` declares `MediaLibraryService`, `MediaBrowserService`, and `MEDIA_PLAY_FROM_SEARCH`. |
| DHU Browse | **not run (no DHU/phone on this machine)** | Requires physical phone with Android Auto app and Desktop Head Unit binary. |
| DHU Voice Search | **not run (no DHU/phone on this machine)** | Requires physical phone with Google Assistant and Android Auto. |
| DHU Disconnect | **not run (no DHU/phone on this machine)** | Requires physical phone USB cable disconnect simulation. |
| DHU Reconnect | **not run (no DHU/phone on this machine)** | Requires physical phone USB reconnect simulation. |

> [!NOTE]
> As specified in Milestone B1, no claim of complete Android Auto vehicle compatibility is made until tested against physical phone/DHU and automotive hardware.
