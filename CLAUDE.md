# CLAUDE.md: Syok

Syok (Manglish for "so enjoyable") is an open-source Android music player for cars: a full-screen app for aftermarket Android head units and Android Auto on the phone, sharing one Media3 playback service. Sources: local files and USB/SD, internet radio, and a self-hosted Navidrome server (Subsonic API). Plan: `plans/syok-v1.md`.

## Stack

- Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer, MediaLibraryService, MediaSession), Room, WorkManager (later phases).
- One `:app` module. Manual dependency construction in `AppContainer`, no Hilt.
- Gradle Kotlin DSL with `gradle/libs.versions.toml`. Always build through `./gradlew`.
- minSdk 24.

## Environment

- `export ANDROID_HOME="$HOME/Library/Android/sdk"` and put `$ANDROID_HOME/platform-tools` and `$ANDROID_HOME/emulator` on PATH before running Gradle or the emulator.
- Use the cached Gradle distribution in `~/.gradle/wrapper/dists` to generate the wrapper; commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

## Rules

- Original name, UI, and artwork only. Never copy another player's design, assets, or branding.
- Never commit a Navidrome server URL, username, password, token, keystore, or `local.properties`.
- One playback service owns the only ExoPlayer. UI and Android Auto talk to it through MediaController or the session.
- Tests: JUnit and Robolectric unit tests, Compose UI tests. Every milestone runs `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- UI work builds to `design/car-player/DECISIONS.md` once that exists.
