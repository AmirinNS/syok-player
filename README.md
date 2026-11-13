# Syok (Automotive Android Music Player)

Syok is a focused, driver-oriented local audio player designed specifically for automotive Android head-units (7" 1024×600 and 9"/10" 1280×720 widescreen displays). Built with Jetpack Compose and AndroidX Media3.

---

## Screenshots (Direction A: "Night Drive")

Captured directly from the API 34 emulator running at 1024×600 px head-unit resolution:

### Library Screen (4-Column Responsive Album Grid)
![Library Screen](docs/screenshots/library_1024x600.png)

### Now Playing Screen (Asymmetric Split Layout)
![Now Playing Screen](docs/screenshots/now_playing_1024x600.png)

---

## Key Ergonomic & UI Design Principles

Syok implements the approved **Direction A: "Night Drive"** automotive interface:
- **Asymmetric Split Layout**: In right-hand drive (RHD) vehicles, primary transport controls are situated on the right 60% of the display, keeping driving controls closest to the driver's hand while large artwork sits on the left 40%.
- **Zero-Glare Night Palette**: Pure OLED `#0A0D14` background with 590nm warm amber accents (`#FF9F0A`, 10.8:1 contrast ratio) to preserve dark-adapted vision during night driving.
- **Driving-Grade Touch Targets**:
  - Primary Play/Pause: 92 dp circular target.
  - Skip Prev / Next: 78 dp circular targets.
  - Secondary toggles (Shuffle, Repeat): 56 dp targets.
  - Seek Scrubber: Coarse 48 dp scrubbing strip with monospace elapsed/remaining time indicators.
- **Glanceable System Clock**: Header bar provides clean system time (`SYS TIME: HH:mm`) without developer jargon.

---

## Architectural Principles

Syok enforces strict single-service audio playback architecture:

```
┌────────────────────────────────────────────────────────┐
│                      MainActivity                      │
│            (Direction A Jetpack Compose UI)            │
└───────────────────────────┬────────────────────────────┘
                            │ observes StateFlow
┌───────────────────────────▼────────────────────────────┐
│                     PlayerViewModel                    │
│            (MediaController wrapper & state)           │
└───────────────────────────┬────────────────────────────┘
                            │ MediaController IPC
┌───────────────────────────▼────────────────────────────┐
│                    PlaybackService                     │
│                (: MediaLibraryService)                 │
│                                                        │
│  ┌────────────────────┐       ┌──────────────────────┐ │
│  │   LibraryCallback  │       │      QueueStore      │ │
│  │(Secured ID lookup) │       │(State & pos persist) │ │
│  └────────────────────┘       └──────────────────────┘ │
│                           │                            │
│                  ┌────────▼────────┐                   │
│                  │  Sole ExoPlayer │                   │
│                  └─────────────────┘                   │
└────────────────────────────────────────────────────────┘
```

1. **Sole Playback Service**: A single `PlaybackService` extending `MediaLibraryService` owns the only `ExoPlayer` instance. Activities and ViewModels connect strictly as `MediaController` clients and never instantiate their own players.
2. **Deterministic Audio Focus & Interruption**: Built with `PlayerFactory` configuring `C.USAGE_MEDIA`, `C.AUDIO_CONTENT_TYPE_MUSIC`, `handleAudioFocus = true`, and `handleAudioBecomingNoisy = true`.
3. **Queue & Resume State Persistence**: `QueueStore` saves current media item IDs, active index, and elapsed millisecond positions into persistent storage on state transitions, restoring the playback queue in a paused state across process restarts.
4. **Strict Media Item Resolution**: `LibraryCallback` resolves only known, source-qualified track IDs (`mediastore:external:<id>`) against Room database `LibraryDao`. Arbitrary caller-supplied URIs are rejected.
5. **Memory-Safe Artwork Loading**: `ArtworkLoader` extracts embedded artwork via background dispatchers, downsampling images to exact viewport dimensions (max 512px) with a bounded 32 MB in-memory `LruCache`.

---

## Android Auto (Projection Mode)

Syok supports Android Auto audio projection from a connected Android phone using the same unified `PlaybackService` and Media3 session architecture:

### What Works
- **App Discovery & Metadata**: Declares automotive app descriptor (`res/xml/automotive_app_desc.xml`) with media capability and `com.google.android.gms.car.application` metadata.
- **Hierarchical Media Browsing**: Driver-safe 4-level media tree (`Root -> Local -> Albums -> Tracks`) with bounded pagination and explicit browsability flags.
- **Secure Artwork Serving**: Narrow, read-only `ArtworkContentProvider` serving downsampled decoded artwork via content URIs without leaking local paths or secrets.
- **Search & Voice Intent**: Implements Media3 `onSearch` / `onGetSearchResult` matching indexed local tracks, and registers `android.media.action.MEDIA_PLAY_FROM_SEARCH`.
- **Playback Integration**: MediaBrowser client connections trigger playback directly on the existing `PlaybackService` ExoPlayer instance.

### Unverified (Pending Hardware/DHU Testing)
- Live Desktop Head Unit (DHU) projection and vehicle head unit display.
- Google Assistant voice search execution on a physical phone.
- Physical USB disconnection and vehicle ignition cycling.
*(See [docs/testing.md](docs/testing.md) for DHU setup and testing procedures).*

---

## Building & Testing

### Prerequisites
- JDK 17
- Android SDK (API 34 platform & platform-tools)
- Gradle Wrapper (included)

### Build APK
```bash
./gradlew :app:assembleDebug
```

### Run Unit Tests & Lint
```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

### Run Connected Tests on Head-Unit Emulator
```bash
# Launch API 34 AVD
emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -no-window -no-audio &

# Configure 1024x600 head-unit resolution and density
adb shell wm size 1024x600
adb shell wm density 160

# Run connected test suite
./gradlew :app:connectedDebugAndroidTest
```

---

## Fixture License Note

All test audio fixtures residing in `app/src/androidTest/assets/audio/` (`track1.mp3`, `track2.mp3`, `track3.mp3`) are synthetically generated test tones produced with `ffmpeg` (sine wave generators with ID3 metadata). No proprietary, commercial, or copyrighted music files are distributed with this repository.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2026 Mohamad Khairulamirin.

## USB/SD folders

While parked, open **Sources** in the Library header, tap **Add folder**, and choose a music folder in Android's picker. Syok persists read access so the folder can be used after restart. Available folder tracks appear alongside device music in Library and Android Auto's Local albums.

On eject or revoked access, Syok retains indexed rows but hides unavailable tracks. Reconnect the drive and use **Retry indexing**, or **Reconnect / reselect** to grant access again. **Remove folder** releases access and removes its tracks from playback without deleting original files. Folder access uses Android's Storage Access Framework and never requests all-files access. Firmware without a working document provider cannot offer this folder flow.

Verified on the API 34 emulator: add, play, restart without the picker, unavailable folder, retry, and remove. Physical USB eject on a real head unit is not yet tested. See [the testing record](docs/testing.md).

## Internet radio

While parked, open **Sources** in the Library header, scroll to **Stations**, and tap **Add station**. Syok plays direct HTTPS streams (MP3 and AAC) and HTTPS HLS (`.m3u8`) audio streams. Saved stations appear under the **Stations** tab in Library and under the Stations branch in Android Auto.

Stations play as live items. Now Playing hides the scrubber and shows a LIVE label. If a station fails, the app retries 3 times with backoff, then shows an error with a **Retry** button.

Not supported:
- HTTP-only stations. Every stream must use HTTPS, and HTTPS to HTTP redirects are blocked.
- Playlist files (`.m3u`, `.pls`). Use the direct stream URL or an HLS `.m3u8` URL.
- Credentials in station URLs (for example `https://user:pass@host/`).

See [the testing record](docs/testing.md) for verified results and known limits.
