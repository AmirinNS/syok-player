# Plan: Syok, a car music player for Android

## Goal

Build an original open-source music player with a landscape interface for aftermarket Android head units and Android Auto support from a phone. Both interfaces control one Media3 playback service per installation. Keep the first release small enough for a Python web developer learning Android to finish and demonstrate.

## Scope

- In scope across five releases: local audio, USB/SD folders, tags and artwork, internet radio URLs, Navidrome browsing and streaming, Wi-Fi downloads, offline playback, media keys, and Android Auto.
- Smallest first shipment: phase A, local MediaStore files, a head-unit Compose interface, queue controls, notification, and MediaSession media keys. It needs neither a server nor Google Play Services. This is a useful standalone player before adding integrations.
- Out of scope: a Navidrome server, cross-device playback synchronization, playlist editing on the server, casting, equalizer, crossfade, video, vendor CAN-bus integration, and a native Android Automotive OS distribution. ReplayGain is an optional follow-up, not a first-release promise.
- Working assumptions: a new Android project, one configured Navidrome account, user-selected music folders, and HTTPS source URLs. HTTP-only stations/servers initially show an unsupported-connection explanation. No source code, README, DESIGN.md, or existing tests were supplied. The assignment folder contains discussion files only. The supplied CLAUDE.md behavioral instructions are the conventions for this document; all product paths below are proposed, not inspected files.
- This is a large, multi-area feature. Each phase adds a releasable slice to the previous release. Do not start all five together. This assignment writes a plan only and performs no implementation, repository operations, or publishing.

## Approach

Use one `:app` module, Kotlin, Compose, Room, and manual dependency construction in `AppContainer`. Skip Hilt, extra modules, and a generic plugin architecture. Add each source when its phase starts.

Proposed source root `app/src/main/java/<chosen-package>/` is abbreviated as `src/` below. `test/` and `androidTest/` mean the matching package beneath `app/src/test/java/` and `app/src/androidTest/java/`. Choose the original app name and permanent package before scaffolding; this document does not claim a name is available.

Execution flow:

1. MediaStore/SAF/Navidrome repositories supply indexed tracks and stable source-prefixed IDs.
2. Compose uses `MediaController`; Android Auto uses the service's browse/session interface. Neither owns an ExoPlayer.
3. `LibraryCallback.onAddMediaItems` resolves known IDs into private playable URIs. It rejects unknown IDs and caller-supplied arbitrary URIs.
4. `PlaybackService : MediaLibraryService` owns one ExoPlayer and one `MediaLibrarySession`. Playback, queue, notification, audio focus, and hardware controls converge here.
5. Later, a Navidrome request resolves to a completed offline file first, otherwise a bounded stream cache and authenticated network source. Tokens are generated at the network boundary, not embedded in published media IDs.

The service, session builder, browse callbacks, and controller arrangement follow [MediaLibraryService](https://developer.android.com/media/media3/session/serve-content) and [background playback](https://developer.android.com/media/media3/session/background-playback). The ID-to-URI callback is documented under [MediaSession.Callback.onAddMediaItems](https://developer.android.com/reference/androidx/media3/session/MediaSession.Callback#onAddMediaItems(androidx.media3.session.MediaSession,androidx.media3.session.MediaSession.ControllerInfo,java.util.List)).

For each named test below, write the assertion and run it first, read the expected failure, make the named edit, then rerun it. Record the result before advancing. Each numbered item names one file or a small configuration edit. Split any item that exceeds a short editing session by its named function. Verification and review finish each milestone; committing is outside this assignment.

### Phase A: local head-unit player

Ships a local-only APK. Prototype approval described in UI / Design precedes UI implementation.

#### Milestone A1: project and readable library

1. Create `settings.gradle.kts` with one `:app` module using the stable Android Studio Compose template.
2. Set `minSdk = 24` in `app/build.gradle.kts`; keep the template's compatible compile/target SDK and record their exact values in the future `README.md`.
3. Move dependency pins into `gradle/libs.versions.toml`, using the verified baselines in Dependencies; retain the template's Kotlin/Compose compiler configuration.
4. Add `src/data/TrackEntity.kt` with source ID, content URI, title, artist, album, track/disc number, duration, and availability.
5. Add `src/data/LibraryDao.kt` with track upsert and ordered album-track queries.
6. Add `src/data/LibraryDatabase.kt` with Room schema export enabled.
7. Add `test/local/MediaStoreSourceTest.kt` assertions for missing tags, duplicate scans, and album track ordering; run them red.
8. Add `src/local/MediaStoreSource.kt::scan` using content URIs and background queries; preserve the previous index if access fails.
9. Add `src/local/MediaStoreSource.kt::toTrack` with title/artist fallbacks and volume-qualified IDs; rerun the tests green.
10. Add audio read permissions to `app/src/main/AndroidManifest.xml`, requesting `READ_MEDIA_AUDIO` on API 33+ and `READ_EXTERNAL_STORAGE` through API 32 only. Add the corresponding permission action to `src/MainActivity.kt`.

→ Verify: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` succeeds. A fresh emulator scan indexes a tagged album, preserves track order on rescan, and treats permission denial as a visible state. Android's [shared-media guide](https://developer.android.com/training/data-storage/shared/media) documents MediaStore and audio permissions.

#### Milestone A2: one player, original UI, and CI

1. Add `src/AppContainer.kt` to construct the database and local source once per application.
2. Add `src/playback/PlaybackService.kt::onCreate` to build the sole ExoPlayer and `MediaLibrarySession`; release both in `onDestroy`.
3. Add service `onGetSession` and `src/playback/LibraryCallback.kt::onAddMediaItems` for known local IDs, with a rejection test in `test/playback/MediaIdResolutionTest.kt`.
4. Add `src/playback/PlayerFactory.kt` using media audio attributes, automatic audio-focus handling, and noisy-output pause. Use Media3 defaults before tuning buffer sizes.
5. Declare the service as exported with `foregroundServiceType="mediaPlayback"`, both `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService` actions, and the foreground playback permissions in the manifest. Limit connected controllers' commands; do not expose settings/download operations externally.
6. Add `src/ui/PlayerViewModel.kt` to connect/release a MediaController and observe session state, including buffering and errors.
7. Build `src/ui/LibraryScreen.kt` to the approved `design/car-player/DECISIONS.md`, with album selection and an empty/permission state.
8. Build `src/ui/NowPlayingScreen.kt` to the same spec, with previous, play/pause, next, seek, and queue selection through the controller.
9. Add `src/local/ArtworkLoader.kt` to load embedded artwork off the main thread, downsample it, and use a bounded memory cache; use placeholders on malformed art. Use platform [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever), closing it after extraction.
10. Add `androidTest/ui/LocalPlaybackTest.kt` for selection, play/pause semantics, and reconnection after activity recreation.
11. Add `src/playback/QueueStore.kt` to save only queue IDs and position in app-private preferences on queue changes and at bounded intervals; restore paused after process death. Add `test/playback/QueueStoreTest.kt` for interrupted playback and missing IDs.
12. Set the head-unit full-screen window behavior in `src/MainActivity.kt` without hiding essential controls behind system insets; preserve ordinary phone setup behavior.
13. Add `.github/workflows/android.yml` with wrapper validation, JDK setup, unit tests, lint, debug assembly, and emulator Compose tests. Pin action revisions when creating the workflow, not guessed SHAs here.
14. Write the future `README.md` local-install instructions, architecture explanation, original screenshots, fixture licenses, and chosen open-source license in `LICENSE`.

→ Verify: on an API 24 landscape AVD at 1024×600 and a current Android AVD, a full album plays with the activity closed; notification controls and injected media keys work. Focus loss pauses/ducks correctly; repeated controller connections do not create a second player. Listen to known gapless fixtures as described under Tests. CI executes the commands, not just compilation.

### Phase B: Android Auto from the phone

Ships the same APK with a useful local-music Auto browse tree. Aftermarket head-unit playback remains independent of Auto availability.

#### Milestone B1: discover, browse, and play

1. Add `app/src/main/res/xml/automotive_app_desc.xml` containing the media capability, and its `com.google.android.gms.car.application` metadata in the manifest, following [Android Auto media setup](https://developer.android.com/training/cars/media/auto).
2. Add `test/playback/BrowseTreeTest.kt` for stable root/album/track IDs, valid page boundaries, empty libraries, and unknown IDs; run red.
3. Add `src/playback/BrowseTree.kt::children` for Root → Local → Albums → Tracks with explicit browsable/playable flags and bounded pages.
4. Add `LibraryCallback.onGetLibraryRoot`, `onGetChildren`, and `onGetItem`; fetch Room data off the service thread and complete Media3's asynchronous results.
5. Add `LibraryCallback.onSearch` and `onGetSearchResult` for indexed local title/artist matches; rerun browse tests green.
6. Add `src/playback/MediaItemMapper.kt` to publish title, artist, album, duration, and small artwork through non-secret content URIs. Grant the host only the artwork access it needs.
7. Add `androidTest/playback/BrowserContractTest.kt` to connect a MediaBrowser and play a returned ID through the existing service.
8. Add `docs/testing.md` with the DHU procedure below and record browse, voice search, disconnect, and reconnect results.

→ Verify: a phone connected to the Desktop Head Unit displays the app, browses an album, starts the selected track, shows metadata/art, and supports controls without opening the phone activity. Test the current [car app quality requirements](https://developer.android.com/docs/quality-guidelines/car-app-quality?category=media). A browser contract test alone does not establish Auto compatibility.

### Phase C: removable folders and radio

Ships USB/SD playback and manually saved stations through both interfaces. Either addition can be released first without waiting for the other.

#### Milestone C1: persistent folder access

1. Add a parked setup action in `src/ui/SourcesScreen.kt` using `ACTION_OPEN_DOCUMENT_TREE` and persist the returned read grant.
2. Add `test/local/SafSourceTest.kt` for revoked grants, unavailable volumes, and a corrupt file; run red.
3. Add `src/local/SafSource.kt::scanFolder` to walk the selected tree in cancellable background batches and index document URIs without converting them to paths.
4. Add `SafSource.readTags` using the shared metadata/artwork path, filename fallback, and isolated per-file failures; rerun green.
5. Add source availability updates to `LibraryDao.kt`; after eject, retain rows as unavailable rather than deleting the library.
6. Add `SourcesScreen.kt` actions to reconnect/reselect a folder and retry indexing. Route available folder tracks into the existing Local browse branch.

→ Verify: select a USB/SD folder, restart the app/device, and play again without another picker. Eject during playback and revoke the grant; each produces a recoverable message. Use the [SAF guide](https://developer.android.com/training/data-storage/shared/documents-files) for persisted grants and restricted folders. If vendor firmware has no usable document provider, report that limitation rather than requesting all-files access.

#### Milestone C2: URL stations

1. Add `src/radio/StationEntity.kt` and station DAO methods; create the corresponding explicit Room migration.
2. Add `test/radio/StationUrlTest.kt` for invalid schemes, URL user-info, bad redirects, and missing station names; run red.
3. Add `src/radio/StationValidator.kt` accepting HTTPS MP3/AAC streams and HLS URLs, rejecting embedded credentials and unsupported schemes; rerun green.
4. Add a parked station add/edit form in `SourcesScreen.kt`, using the approved settings design.
5. Add `src/radio/RadioSource.kt` to create station MediaItems with live semantics and no seek/download commands; add the Media3 HLS artifact for HLS stations. Declare `INTERNET` and `ACCESS_NETWORK_STATE` permissions in the manifest for remote sources and later download constraints.
6. Add a Stations root branch to `BrowseTree.kt` and map connection failures to a visible retry action in `PlayerViewModel.kt`.

→ Verify: controlled MP3/AAC and HLS fixtures play on the AVD and DHU; an unreachable endpoint gives a bounded retry and a clear failure. A station URL is an actual stream, not a station webpage. HTTP-only stations and M3U/PLS playlist import remain unsupported in this slice and are stated in the README.

### Phase D: Navidrome streaming

Ships account setup, album browsing/search, stream caching, and playback through both clients. Offline guarantees arrive in phase E.

#### Milestone D1: secure connection and API contract

1. Add `test/navidrome/SubsonicAuthTest.kt` for the published MD5 test vector, UTF-8 passwords, fresh salts, and URI encoding; run red.
2. Add `src/navidrome/SubsonicAuth.kt::parameters` with `u`, fresh `s`, `t = MD5(password + salt)` in lowercase hexadecimal, plus client/protocol/JSON parameters; rerun green.
3. Add `src/security/CredentialStore.kt::save/load` using a Keystore-generated AES-GCM key and app-private encrypted data with a fresh IV on each write. Store server/account configuration privately too.
4. Add credential/index/download exclusions to `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`, referenced by the manifest. Keystore keys do not transfer with ciphertext; restoring secrets must not silently break playback.
5. Add `src/ui/ServerSettingsScreen.kt` for runtime HTTPS URL, username, password, connection test, sign out, and actionable TLS/auth errors. Never default to the owner's URL.
6. Add `src/navidrome/SubsonicClient.kt::ping` using platform HTTP/JSON facilities, timeouts, same-origin credential protection, and both HTTP and Subsonic-envelope error checks.
7. Add `test/navidrome/SubsonicClientTest.kt` with sanitized fixtures for bad credentials, server errors, malformed JSON, and an HTTP-success/API-failure response.

→ Verify: published token vector passes independently of the client; connection succeeds against a private test server configured only at runtime. Inspect logs, database rows, media metadata, and backup rules. No password, salted token, authenticated URL, or owner's server address appears in the public source, fixtures, screenshots, CI, or crash text.

The [OpenSubsonic authentication specification](https://opensubsonic.netlify.app/docs/api-reference/#authentication) describes this token scheme. It is not an expiring OAuth access token. A fresh salted request token needs the password again, so an isolated stored token is not a replacement for encrypted credential storage. API-key authentication is an alternative only when advertised by the actual server; it is not assumed or silently substituted for the requested token flow. [Android Keystore](https://developer.android.com/privacy-and-security/keystore) protects the encryption key; hardware backing is not guaranteed on cheap units. If key creation/decryption fails, require sign-in again or offer session-only use, never plaintext persistence.

#### Milestone D2: browse, stream, and cache

1. Add `SubsonicClient.getAlbumList2` with offset/size pagination, `getAlbum` for ordered tracks, and `search3` for remote search; store results with a private account ID prefix. Endpoint contracts are [getAlbumList2](https://opensubsonic.netlify.app/docs/endpoints/getalbumlist2/) and [search3](https://opensubsonic.netlify.app/docs/endpoints/search3/).
2. Add `src/navidrome/NavidromeRepository.kt` to persist fetched pages in Room, preserve last-good rows on errors, and refresh explicitly rather than scanning the entire server at launch.
3. Add `src/navidrome/StreamResolver.kt` to generate fresh authentication at network open/retry, not when a queue is first saved. Reject redirects that would forward credentials to a different origin.
4. Add `src/playback/PlaybackCache.kt` with one application-owned Media3 SimpleCache instance and a proposed 128 MiB eviction limit for progressive remote audio. Key entries by account, song, and rendition, never by changing token URLs.
5. Extend `LibraryCallback.onAddMediaItems` to resolve Navidrome IDs through that cache/network path; keep playback credentials out of browse metadata and the stored queue.
6. Add private downloaded artwork to `MediaItemMapper.kt`, exposing bounded local images rather than authenticated cover-art URLs.
7. Add a Navidrome root branch in `BrowseTree.kt` and its corresponding library source in `LibraryScreen.kt`.
8. Add `test/navidrome/StreamResolverTest.kt` for fresh auth, cache-key stability, account isolation, forbidden redirects, and XML/JSON error bodies returned instead of media bytes.

→ Verify: browse more than one server page, search, play an album, leave/reopen the activity, and repeat in DHU. Disable networking mid-stream and show a useful recoverable state. A cache hit reduces network traffic but does not claim a full song is downloaded. Verify raw/original streaming first; [stream](https://opensubsonic.netlify.app/docs/endpoints/stream/) documents optional transcoding, which requires separate seek/gapless testing.

### Phase E: Wi-Fi downloads and offline playback

Ships explicit completed downloads and an offline browse branch. Reuse the same player; a download worker is not a second playback service.

#### Milestone E1: durable completed files

1. Add `src/download/DownloadEntity.kt` for account/track ID, state, expected/received size, private file key, and failure category; add its Room migration.
2. Add `test/download/DownloadPolicyTest.kt` for Wi-Fi versus cellular, low storage, duplicate requests, and cancellation; run red.
3. Add `src/download/DownloadScheduler.kt` with unique WorkManager work per account/track, unmetered-network, battery-not-low, and storage-not-low constraints, and a cancel action.
4. Add `src/download/DownloadWorker.kt::doWork` to resolve credentials privately at execution time and check actual `TRANSPORT_WIFI`. Bind the transfer to the eligible Wi-Fi network and stop it when that network disappears, so fallback cannot silently consume cellular data. Unmetered alone does not mean Wi-Fi.
5. Add `DownloadWorker.copyToPartial` for one original track at a time, cancellable reads, timeouts, byte accounting, and application storage capacity checks.
6. Add `DownloadWorker.promoteCompletedFile` to reject API error bodies, verify successful completion, atomically rename a partial file, then mark the Room row complete. Reconcile a crash between rename and database update before advertising availability.
7. Add foreground progress/cancel support with `CoroutineWorker.setForeground`, the required WorkManager foreground-service `dataSync` declarations/permissions, and notification-permission handling on recent Android.
8. Add `test/download/DownloadCompletionTest.kt` for interrupted writes, API errors disguised as successful HTTP, disk-full, cancellation, rename/DB crash windows, and retry from a partial; rerun green.

→ Verify: a short album downloads over Wi-Fi and never becomes complete on a failed transfer. Disconnect Wi-Fi, switch to cellular, cancel, kill the process, and fill available storage. The UI retains a truthful pending/failed state and retry works. Initial retries restart a partial track unless the server's byte-range behavior has been verified; document that ceiling before promising resume. The [download endpoint](https://opensubsonic.netlify.app/docs/endpoints/download/) returns original media, not a transcoded stream.

#### Milestone E2: offline access and recovery

1. Extend `StreamResolver.kt` to prefer a complete existing download; recheck file existence before playback and fall back only when streaming is permitted.
2. Add `src/ui/DownloadsScreen.kt` with queued/progress/complete/failed states, explicit retry/cancel/remove, and storage use.
3. Add a Downloads root branch in `BrowseTree.kt` that resolves without contacting Navidrome; retain enough metadata/artwork locally.
4. Add `src/download/DownloadReconciler.kt` to repair missing-file rows and orphan partials at startup without deleting user music.
5. Extend `CredentialStore.kt::signOut` to cancel account work, close active network playback, remove credentials, and clear that account's private cached/downloaded content through an explicit settings action.
6. Add `androidTest/playback/OfflinePlaybackTest.kt` that disables networking, restarts the process, browses downloaded tracks, and plays a complete album.
7. Add the verified offline and failure demonstrations to `docs/testing.md` and the future public README; use licensed/generated sample audio and dummy account details.

→ Verify: airplane-mode cold start plays every completed download, including via the service browse tree, while incomplete/evicted cache entries are never listed as downloaded. Removing one download preserves other downloads and all original USB/MediaStore files.

WorkManager constraints and retry behavior follow [Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). Long workers follow [foreground worker guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running). Android 16+ job quotas and foreground-service limits can delay downloads even with notifications. Show waiting/retry state; do not promise immediate or indefinite background execution. Start with track/album downloads, not whole-server mirroring. A quota-exempt user-initiated transfer mechanism is a later alternative if measured restrictions make this inadequate.

## UI / Design

- Prototype: required before production UI work, not performed by this planning assignment. No design approval exists yet.
- Design question: at 1024×600, does an album list beside persistent playback controls or a dedicated now-playing screen make source selection and play/pause easier with large targets? Compare both with long titles, an empty library, and large text before choosing.
- Design spec: main session runs `prototype-ui` after plan review, records the original direction in `DESIGN.md` and `design/car-player/DECISIONS.md`, then marks this plan's prototype approved. No existing app's name, branding, screenshots, or layout is to be copied.
- Affected screens: library, now playing/queue, source/folder selection, server setup, stations, downloads, and recoverable error states. Support landscape/full-screen head units and a usable phone setup screen without imposing landscape on all phones.
- Aim for at least 64 dp primary car controls, with no interactive target below 48 dp. Use dp and actual display density rather than treating 1024×600 pixels as dp. Add clear contrast, accessible labels, predictable focus order, TalkBack descriptions, visible focus, and text scaling without clipped controls. Do not rely on color or gestures alone.
- Android Auto renders browsing/playback through its own driver-safe media UI. Compose is never projected as an arbitrary custom car screen. The service approach does not require adding a CarAppService or Car App Library dependency. Library-based media templates are another supported route, but unnecessary here. This distinction follows the [media apps overview](https://developer.android.com/training/cars/media).
- Keep credentials, folder pickers, URL typing, and bulk download management on the phone or a parked head unit. Honor Auto restrictions and supported host commands. Use no video, ads, text-heavy flows, or phone-unlock demands while driving. Review [distraction safeguards](https://developer.android.com/training/cars/media/distraction-safeguards) and the linked car quality checklist before distribution.
- A generic aftermarket Android app cannot reliably know the vehicle's motion state. Do not claim an automatic driving lock without trustworthy vehicle integration. Warn that setup is for parked use, keep routine playback simple, and document this limit.

## Affected Files

All paths are future product files. No existing product files are modified by this assignment.

| Area | Proposed files |
|---|---|
| Build and CI | `settings.gradle.kts`, root/app `build.gradle.kts`, `gradle/libs.versions.toml`, template wrapper files, `.github/workflows/android.yml` |
| App and UI | `src/AppContainer.kt`, `src/MainActivity.kt`, `src/ui/{PlayerViewModel,LibraryScreen,NowPlayingScreen,SourcesScreen,ServerSettingsScreen,DownloadsScreen}.kt`, Compose theme and string resources |
| Playback | `src/playback/{PlaybackService,PlayerFactory,LibraryCallback,BrowseTree,MediaItemMapper,PlaybackCache,QueueStore}.kt` |
| Local and radio | `src/local/{MediaStoreSource,SafSource,ArtworkLoader}.kt`, `src/radio/{StationEntity,StationValidator,RadioSource}.kt` |
| Server and downloads | `src/navidrome/{SubsonicAuth,SubsonicClient,NavidromeRepository,StreamResolver}.kt`, `src/security/CredentialStore.kt`, `src/download/{DownloadEntity,DownloadScheduler,DownloadWorker,DownloadReconciler}.kt` |
| Data and manifest | `src/data/{TrackEntity,LibraryDao,LibraryDatabase}.kt`, Room schemas/migrations, manifest, automotive/backup/artwork-provider XML |
| Tests and documentation | Named test files above, licensed fixtures, `README.md`, `LICENSE`, `.gitignore`, `DESIGN.md`, `design/car-player/DECISIONS.md`, `docs/testing.md` |

Files to remove: none. Runtime server settings, database exports containing user data, downloaded audio, signing keys, and local environment files do not belong in source control. Room schema definitions without user rows do belong in source control.

## Schema / Data Changes

- Phase A creates Room schema version 1 with Track rows. Source-qualified identity avoids collisions among MediaStore volumes, SAF documents, stations, and separate Navidrome accounts. URLs containing credentials are never identity keys.
- Later migrations add selected folder grants/source availability, Station rows, private Navidrome account associations, and Download rows. Store grants/settings privately; Android's persisted permission remains the authority for SAF access. Keep the table design flat until an actual query requires separate album/artist tables.
- Downloads use app-specific persistent storage, separate from the bounded disposable stream cache. Never use cache-directory eviction as the only copy of a promised download. Normal device uninstall removes app-specific downloads; state this explicitly.
- Export Room schemas and write `MigrationTest.kt` before every version change. Use additive migrations and preserve downloaded-file references. Do not enable destructive migration fallback. Rebuild a media index only after preserving configured sources and user download state.

## Acceptance Criteria

- [ ] Phase A plays a local album at 1024×600 on API 24 without Google Play Services; activity closure and recreation do not interrupt playback or duplicate players.
- [ ] Steering-wheel-style play/pause/next/previous events reach the MediaSession; real vendor routing limitations are documented rather than hidden.
- [ ] Both the standalone app and phone Auto client use the same service implementation. Selecting a browsed ID starts the correct track; metadata, artwork, queue, errors, and controls agree.
- [ ] MediaStore permission denial, revoked SAF access, missing USB/SD, corrupt files, and malformed artwork produce recoverable states without deleting source files.
- [ ] Stations play supported live stream URLs, expose no misleading seek/download controls, and explain unsupported URLs.
- [ ] Navidrome authentication, pagination, album order, search, streaming, and server errors work with runtime account configuration and sanitized test fixtures.
- [ ] Every authenticated request uses newly generated auth parameters at open/retry. Public files and media metadata contain neither the owner's URL nor secrets.
- [ ] Completed downloads survive process/device restart, play without a server connection, and never include partial or error-response files.
- [ ] Wi-Fi loss stops downloads before cellular fallback; low storage, cancellation, scheduler delay, and account sign-out remain truthful and recoverable.
- [ ] Focus interruptions, noisy-output events, gapless fixtures, font scaling, focus navigation, and screen-reader labels pass the test matrix.
- [ ] Public portfolio material uses an original identity and licensed sample media, and states device/API/format limits and checks actually performed.

## Tests

Test type: mixed JUnit, Robolectric, Compose instrumentation, service integration, DHU, and hardware checks. Existing tests to run: none were supplied. Proposed commands assume the single-module template above; verify task names with `./gradlew tasks` after scaffolding.

- Unit/Robolectric in `app/src/test/java/<chosen-package>/`: all named contract tests above, Room migrations, source identity, tag fallbacks, API parsing/auth, URL validation, file completion, cache-key isolation, and download constraints. Platform keystore and codec behavior still require real Android tests.
- Instrumentation in `app/src/androidTest/java/<chosen-package>/`: controller/service integration, browser selection, permission recovery, activity recreation, Compose accessibility semantics, and offline cold start. Run `./gradlew :app:connectedDebugAndroidTest` against API 24 and a current stable API emulator. CI also runs `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Gapless: use licensed adjacent FLAC tracks and MP3/AAC files with valid gapless metadata, one continuous ExoPlayer queue, and no player recreation between tracks. Compare with a known continuous reference on an AVD and the actual unit. Start with PCM output/offload disabled for a consistent baseline; test before enabling offload. Repeat on original Navidrome streams and offline files. Transcoding, unsupported metadata, network stalls, and vendor audio paths can prevent gapless playback. Crossfade is excluded. No blanket all-codec gapless claim.
- ReplayGain: automatic tag-to-gain playback has not been verified in the selected Media3 baseline. A later spike could read track/album gain and peak tags, apply bounded PCM gain with clipping protection, and test tagged FLAC/MP3 plus untagged/radio bypass. This may disable hardware offload and costs CPU. Do not advertise support or invent a Media3 ReplayGain switch. Prefer deferral to an untested volume adjustment.

Testing without a car:

1. Use an ordinary Android AVD, not AAOS, for the standalone app. Set landscape 1024×600 with a low-RAM profile, seed licensed music, and test with a 1 GiB-class memory budget. Also test current Android storage/foreground-service behavior. Inject media key events with ADB; this tests the session, not a vendor's steering-wheel wiring.
2. For Android Auto, install on an Auto-capable phone, enable Auto developer mode, and install the SDK Desktop Head Unit. Follow the documented USB accessory flow, `./desktop-head-unit --usb`, or start the phone's head-unit server and use `adb forward tcp:5277 tcp:5277` then `./desktop-head-unit`. Use the installed tool's documented debug-app settings when required. Exercise touch and rotary/focus input, search, browse, metadata, errors, and reconnect. These procedures come from the [DHU guide](https://developer.android.com/training/cars/testing/dhu).
3. An AAOS emulator is optional exploration of a different platform where the app runs in the vehicle. It is not a substitute for testing the phone-projection Android Auto target. Native AAOS packaging/release stays outside scope.
4. Before claiming head-unit support, borrow or buy a cheap unit with verified actual API level 24+, working SAF USB/SD access, and media-key routing. Check on a bench using the manufacturer's power requirements. Test cold boot, ignition-style power removal, deep sleep, SD remount, and competing launcher audio. No particular model, price, or vendor Android-version claim has been verified here.

## Rollback

- Keep each release useful on its own. If Auto, a source, or downloads regress, stop shipping that new slice and retain the last verified release while fixing it; do not rewrite the core service.
- Additive Room changes allow forward fixes without deleting libraries/downloads. Installing an older APK over a newer schema is not a safe rollback unless that downgrade is explicitly tested. Prefer a forward fix; a clean reinstall loses app-specific files and needs a clear warning.
- On partial downloads, retain a failed/pending row or delete only the app-owned partial. Never remove original MediaStore or SAF content. Reconcile completed files before rebuilding an index.
- If credentials leak, revoke/change them at the server and remove them from public artifacts/history through an owner-directed response. `.gitignore` cannot undo an existing leak. No real credentials are needed for public CI.

## Dependencies

Checked against primary documentation on 2026-11-11. These are planning baselines, not an executed compatibility build.

| Dependency | Verified evidence and choice |
|---|---|
| Media3 | Stable **1.11.1** is listed in the [release notes](https://developer.android.com/jetpack/androidx/releases/media3). Keep `media3-exoplayer`, `media3-session`, and later `media3-exoplayer-hls`/`media3-datasource` at one version. Do not use 1.12 alpha. |
| Compose | [Compose UI](https://developer.android.com/jetpack/androidx/releases/compose-ui) lists stable **1.12.1**. Use a stable BOM rather than independently mixing UI/foundation/test versions. The official [BOM guide](https://developer.android.com/develop/ui/compose/bom) explicitly shows **2026.09.00**, a verified usable baseline, not a verified latest November BOM. The [mapping page](https://developer.android.com/develop/ui/compose/bom/bom-mapping) must be checked during scaffold if selecting a newer BOM. |
| Room | Stable **2.8.5**, including KSP compiler/testing artifacts, appears in the [Room notes](https://developer.android.com/jetpack/androidx/releases/room). Room 2.8 raised its minimum to API 23. |
| WorkManager | Stable **2.12.0** requires API **24**, according to [WorkManager notes](https://developer.android.com/jetpack/androidx/releases/work). This determines the proposed app minimum. |
| Build | [AGP 9.5.0 documentation](https://developer.android.com/build/releases/agp-9-5-0-release-notes) specifies Gradle **9.6.0** and JDK **17**. Use the stable template's compatible toolchain, accounting for AGP's Kotlin integration. Exact Kotlin/KSP/compiler and Activity/Lifecycle/AndroidX Test pins are unverified here and must be resolved and recorded before A1 is complete. Do not guess a compatible combination. |
| Tests | The official [Robolectric setup](https://robolectric.org/getting-started/) shows **Robolectric 4.17** with **JUnit 4.13.2**. Compose test artifacts follow the selected BOM; use Room/WorkManager testing artifacts at their matching versions. |

Choose **minSdk 24, Android 7.0**, rather than pinning old core libraries to support Android 5/6. This covers an older Android generation while retaining the current WorkManager baseline. It excludes Android 6/API 23 and lower explicitly; actual head-unit Android versions must be checked before implementation. If the owner's unit is older, revisit this decision with a tested older dependency set or different hardware, not an unexplained manifest override. Compile/target SDK is a separate choice from minimum SDK and must follow the current toolchain and distribution requirements.

Use Kotlin's Compose compiler plugin arrangement from the template; [the compatibility guidance](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) explains that Kotlin 2+ uses the Compose compiler plugin rather than the historical compiler-extension compatibility table. Skip Hilt initially. Use platform HTTP/JSON and metadata APIs before adding a network client/tagging library. Media3, Room, and WorkManager do not require the app to depend on Play Services; validate the standalone path on a no-GMS image. Android Auto itself requires a compatible phone/Auto environment.

External services: the user's runtime-configured Navidrome, public/user-provided HTTPS station streams, and GitHub Actions for public builds. CI uses sanitized local responses and fixtures, never the private server. Online source tests use controlled endpoints and report their limitations.

For offline scheduling, choose WorkManager workers owning progressive original-file transfers. Media3's [DownloadService/DownloadManager](https://developer.android.com/media/media3/exoplayer/downloading-media) with `WorkManagerScheduler` is a valid alternative if segmented adaptive downloads become necessary. Do not run both systems for the same track; the current requirement is original Navidrome files, not offline radio/HLS.

## Decisions Log

| Decision | Chosen | Alternatives Considered | Why |
|---|---|---|---|
| Targets | Head-unit app and phone Android Auto; one playback service per installation | Head unit only; Auto only; separate players | Binding user decision. The two installations do not share a live queue over the network. |
| Sources | Navidrome, local/USB/SD, URL radio | A single-source app | Binding user decision; delivered incrementally. |
| Public identity | Original UI/name and public open-source project | Copy an existing player; private personal tool | Binding portfolio requirement. Name/license choice remains for the owner before publication. |
| First release | MediaStore local playback on a head unit | Navidrome first; Auto first; all features together | Fastest useful slice without server/auth/download dependencies. |
| Platform/stack | Kotlin, Compose, Media3, Room, Gradle Kotlin DSL/catalog | Java/views; separate playback engines | Binding baseline; one service avoids conflicting queues/focus. |
| Dependency construction | Single app module, manual AppContainer, no Hilt | Hilt; multiple architectural modules | Small learning project; add structure only when duplication proves a need. |
| Minimum SDK | API 24 | API 21/23 with older dependencies; API 26+ | Current WorkManager requires 24; older hardware compatibility remains an explicit exclusion. |
| Auto UI | Host-rendered media browse/session UI | Car App Library media templates; projecting Compose | Existing service meets the media need with fewer components; arbitrary Compose projection is unsuitable. |
| Download ownership | WorkManager per original track, private persistent files | Media3 DownloadService plus scheduler; cache-only offline; whole-server mirror | Matches requested WorkManager approach and keeps completed files distinct from evictable cache. |
| Network/security | HTTPS only initially, strict trust checks, fresh salted token auth, Keystore-encrypted password | HTTP opt-in; fixed stored salted token; API-key extension | Protect private server credentials and match requested auth; API-key availability is unverified. HTTP-only radio is a stated limitation. |
| Background network | Wi-Fi transport plus unmetered constraint, no default charging requirement | Unmetered alone; require charging; any network | Meets Wi-Fi-only downloads; some head units report charging poorly. Honor battery/storage constraints without blocking all head units. |
| Gapless/ReplayGain | Test original queue playback; defer ReplayGain pending spike | Claim universal gapless; apply generic volume scaling; ship custom DSP immediately | Codec/device behavior and normalization must be proven; avoid a false feature claim. |
| Testing | AVD + phone/DHU + eventual real head unit | AAOS emulator alone; car-only testing | Each exercises a different environment; no car is needed for most development. |

## Risks and remaining uncertainty

- Vendor firmware is the largest unknown. Displayed Android versions can be misleading, media keys can bypass the Android session, SAF providers can be absent, and launchers can kill background audio. Verify actual hardware before promising compatibility. No universal OEM workaround is planned.
- Low RAM, embedded artwork, huge USB trees, and server libraries can stall startup. Keep queries/scans off the main thread, load bounded pages, decode small art, and cancel obsolete work. Profile the first release before tuning buffers or adding libraries.
- Sudden power removal does not reliably call lifecycle cleanup. Phase A's QueueStore saves queue changes and position periodically with bounded writes; restore paused and require an explicit play action. A small amount of position loss after hard power removal remains possible. Do not depend on boot autoplay or a permanent wake lock.
- Use foreground playback and Media3's audio-focus handling for playback, not WorkManager. Hold any necessary network/CPU wake resources only during active playback/transfers, release on pause/end, and verify screen-off playback. New Android versions restrict background starts and focus requests; validate on the current stable OS. See [audio focus](https://developer.android.com/media/optimize/audio-focus).
- Navidrome version, reverse-proxy rules, certificate chain, download permissions, supported formats, API-key extensions, and byte-range support are unknown. Exercise a private staging account before phase D/E acceptance. Do not disable certificate verification or forward authenticated redirects to another host.
- ReplayGain support and full selected-toolchain compatibility are unverified. The version table separates verified published numbers from pins still to resolve. No build, emulator, DHU, or hardware check has run for this written plan.
- Wi-Fi hotspots can be metered, causing unmetered constraints to wait; explain that state rather than silently enabling cellular. The download cache and private files consume device storage and disappear on uninstall. Recent scheduler/service quotas can delay downloads.
- Original app name, package, license, final layout, exact head unit/API, and real performance remain open implementation decisions. None blocks reviewing this phased plan. After plan review, the next step is prototype approval, then phase A only. The implementer must read this plan and any appended review log before coding, preferably in a fresh implementation session.
