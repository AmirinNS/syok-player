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

## 4. Milestone C1 folder procedure and verification record

Set up while parked. Use the existing Pixel_3a API 34 AVD. Once the SDK tools and Gradle can run outside the worker sandbox:

1. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. KSP must export `app/schemas/io.github.amirinns.syok.data.LibraryDatabase/2.json`. Run `./gradlew :app:connectedDebugAndroidTest`, including `MigrationTest`. The migration must preserve every existing track column and ID.
2. Set `adb shell wm size 1024x600` and `adb shell wm density 160`. Generate a tagged fixture locally, then push it to a test folder. Do not download music.
   ```bash
   mkdir -p /tmp/syok-c1
   ffmpeg -f lavfi -i 'sine=frequency=440:duration=30' -metadata title='Folder tone' -metadata artist='Syok test' -metadata album='Folder fixtures' -metadata track='1' /tmp/syok-c1/folder-tone.mp3
   adb shell mkdir -p /sdcard/Music/SyokTest
   adb push /tmp/syok-c1/folder-tone.mp3 /sdcard/Music/SyokTest/
   ```
3. Install the debug APK, launch Syok, open Library's **Sources**, and tap **Add folder**. Choose `Music/SyokTest`, accept the system read grant, and wait for the indexed count. The folder should say **Available**. Capture Sources with one folder to `docs/screenshots/sources_1024x600.png`.
4. Return to Library and play Folder fixtures. Run `adb shell am force-stop io.github.amirinns.syok`, relaunch with `adb shell am start -n io.github.amirinns.syok/.MainActivity`, then play the album again. No picker should be required.
5. In Sources, **Remove folder** and confirm. Its SAF tracks should disappear from Library/Local browse and the active queue, while original documents remain. The screen should explain that Add folder reconnects. MediaStore may independently index the same device file, so distinguish the `saf:` identity when inspecting rows.
6. Add again, revoke the system grant independently if the device offers that action, and return to Sources. It should show **Access revoked** and a reconnect action. Exercise physical USB eject during playback on hardware; the emulator's primary storage is not a removable-volume substitute. Unavailable rows must remain in Room with `isAvailable = 0`.
7. Reconnect/reselect and retry. A malformed audio file must not prevent a good sibling file from indexing. Check that a phone held upright still shows Syok in landscape, then check large text, focus navigation, and TalkBack labels. Reset `adb shell wm size reset` and `adb shell wm density reset`, then shut down the AVD with `adb emu kill`.

### Verification record, 2026-11-12 (Pixel_3a API 34 AVD, 1024x600)

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Pass. SafSourceTest 9/9. Room v2 schema exported. |
| `./gradlew :app:connectedDebugAndroidTest` | Pass, 6/6, including MigrationTest. With the folders table removed from MIGRATION_1_2, MigrationTest fails, so it detects a broken migration. |
| Add folder through the system picker (Music/SyokTest, two generated tones) | Pass. "Indexed 2 tracks", status Available. |
| Play a folder track, force-stop, relaunch, play again | Pass. No picker needed after restart. |
| Folder moved away, then Sources opened | Pass. Status Unavailable with a reconnect message; Library hides the tracks. |
| Folder restored, Retry indexing | Pass. Available again, 2 tracks. |
| Remove folder (with confirmation) | Pass. Tracks leave Library; original files remain on storage. |
| Grant revoked by the system, physical USB eject during playback | Not run. Covered by SafSourceTest only; needs a real head unit or a device with a removable volume. |
| TalkBack, large text | Not run. |
| Phone held upright | Passed on API 34 at 1080x2220. `MainActivity` uses `sensorLandscape`, so the app stays landscape. Android 16 ignores fixed orientation on screens 600 dp or wider, which only affects tablets and head units that are already landscape. |

SAF scanning uses document URIs, 64-track write batches, cancellation checks, and the same metadata and artwork path as local playback. Library and the Local browse branch use the existing available-only queries. Android's persisted grants remain the access authority. Availability refreshes on app start, when Sources opens, and on storage mount, eject, and removal broadcasts. On mount, folders whose root is reachable again are rescanned automatically. MediaStore may also index the same device file, so a file in a primary-storage folder can appear twice; removable USB/SD volumes are the intended use.

## 5. Milestone C2 URL stations procedure and verification record

### Design notes
- Library reaches stations through two tabs under the header, `Albums` and `Stations`. This was the smallest change to the existing Library screen.
- Station media IDs are `station:<row id>`. `LibraryCallback.onAddMediaItems` loads the stored URL by ID and ignores any URI the caller sends.
- Redirects: `PlayerFactory` sets `DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)`, so an HTTPS stream cannot redirect to HTTP. HTTPS to HTTPS redirects are followed. With targetSdk 36, Android also blocks cleartext traffic by default, which covers `http` segment URLs inside an HLS playlist.
- Live items set `LiveConfiguration` and `MEDIA_TYPE_RADIO_STATION`. Now Playing hides the scrubber and shows LIVE labels. `LibraryCallback.onPlayerCommandRequest` refuses seek commands while a station plays.
- Retry: `PlayerViewModel` retries a failing station 3 times with 1 s, 2 s and 3 s delays, then shows an error banner with a Retry button.

### Manual stream test URLs
Checked with curl first. These are test fixtures only and never ship as default stations.
- HTTPS AAC: `https://stream.radioparadise.com/aac-128` (206, `audio/aac`).
- HTTPS HLS: `https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8` (206, `application/x-mpegURL`).
- Unreachable: `https://127.0.0.1:9/stream`.

### Verification record, 2026-11-13 (Pixel_3a API 34 AVD, 1024x600)

| Check | Result | Evidence |
|---|---|---|
| `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Pass | 43 unit tests, including StationUrlTest 10/10 and RadioSourceTest 4/4. Lint has 0 errors. StationUrlTest red first: a new case for `@` in the query failed (`10 tests completed, 1 failed`) before the validator fix. |
| `./gradlew :app:connectedDebugAndroidTest` | Pass | 8/8: MigrationTest 3 (1 to 2, 2 to 3, 1 to 3), BrowserContractTest 3, LocalPlaybackTest 2. |
| Station form | Pass | `http://example.com/x.pls` shows "Only HTTPS stream URLs are supported" inline. Add, edit (rename) and delete with confirmation all update the `stations` table. Screenshot `docs/screenshots/stations_1024x600.png`. |
| AAC station | Pass | Session state PLAYING. Now Playing shows RADIO SOURCE, LIVE, LIVE BROADCAST and no scrubber. Session actions include no seek, fast forward or rewind. Screenshot `docs/screenshots/now_playing_live_1024x600.png`. |
| HLS station | Pass | Session state PLAYING with HLS buffering. Same live UI. |
| Unreachable station | Pass | UI showed "Retrying (1/3)", "(2/3)", "(3/3)", then "Station unreachable after 3 retries. Tap Retry to reconnect." Logcat had 4 `ExoPlayerImplInternal: Playback error` lines (first try plus 3 retries), still 4 after 15 more seconds. Tapping Retry starts a new round. |

### Known limits
- Retry lives in `PlayerViewModel`, so it runs only while the head-unit app UI is alive. Playback started only from Android Auto shows the player error without automatic retry.
- A station whose stream is seekable (a video-on-demand HLS test stream, or live HLS with a DVR window) still advertises seek in the session, because ExoPlayer reports it seekable. Seek requests from controllers are refused while a station plays. Live radio streams such as the AAC test station advertise no seek.
- Android Auto browsing of the Stations branch is covered by BrowseTreeTest only. It is not yet checked on the Desktop Head Unit.
