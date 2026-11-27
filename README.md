# MeowMeow Music Player

A small, offline, folder-based Android music player. Select a folder and play MP3 / MP4 audio files directly from it. No catalogue import, artwork or internet connection.

## Features

- Android folder picker, with **only files directly inside the selected folder** (no subfolders).
- Multiple saved favourite folders. Music is read in place, not copied or modified.
- Play/pause, next/previous, seek, shuffle, repeat off/all/one and alphabetical filename sorting.
- System light/dark theme; no album-art or artist metadata display.
- Equaliser with Flat, Bass, Rock, Pop and Classical presets, plus adjustable bands on supported devices.
- Background playback and standard Android lock-screen / Bluetooth media controls.
- Restores the previous folder, song and position **paused** on the Player screen; remembers shuffle/repeat choices.
- Skips unplayable tracks during playback, handles deleted previous tracks, and offers retry if the player connection fails.

Supported audio containers are `.mp3` and `.mp4`; playback depends on the codecs supported by the phone. MP4 video is not displayed.

## Build

The project uses **Kotlin 2.2.20**, **Jetpack Compose**, **Media3**, **Android Gradle Plugin 8.11.1**, **Gradle 8.13**, **JDK 17**, Android compile SDK **36**, min SDK **26**.

**GitHub Actions:** Open the **Actions** tab and run **Build MeowMeow Music Player APK**, or push a commit to `main`. The workflow runs JVM unit tests, Android lint and a debug APK build, then publishes the APK as an artifact. Checks run for pull requests and pushes to `main`. Build actions are pinned to reviewed commit IDs, and the token has read-only repository permission.

**Local build:** Install JDK 17, Android SDK 36, Android build-tools 36.0.0 and Gradle 8.13. Run:

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

> This repository does not currently include the Gradle wrapper. The CI workflow installs Gradle 8.13 explicitly.

## Code layout

- `MainActivity.kt`: Compose screens, player controls and display state.
- `PlaybackService.kt`: Media3 playback and background media session.
- `FolderStore.kt`: Android Storage Access Framework folder access and saved favourites.
- `EqualizerEngine.kt`: Android audio-session equaliser and persisted presets.
- `PlaybackDecisions.kt`: testable selection/recovery rules; `PlaybackDecisionsTest.kt` contains JVM unit tests.

Music files stay on the device, with no internet permission. Folder access uses the system picker and Android's persisted read permissions. If a saved permission expires, the app asks for the folder to be selected again. The media session accepts trusted controllers (the app itself and Android's authorised media controllers); some unrelated third-party remote apps may be unable to connect.

## Status, releases and signing

The rebrand and playback fixes have been merged into `main`. GitHub Actions tests each PR and produces debug builds. Application-code changes pushed or merged to `main` automatically build, test, lint, sign and publish a public **Beta** APK at [GitHub Releases](../../releases), including a matching `.sha256` checksum.

The original release signing key and application ID remain unchanged, so newer signed versions can update older signed releases in place. Old separately signed debug APKs may require a one-time uninstall, losing saved app preferences. No private signing key is stored in Git.

The former one-time key generator, historical manual signer and first-beta release publisher are preserved but **disabled** in [the workflow archive](docs/workflow-archive/README.md). The active release pipeline signs on a separate runner without checking out or executing source in that job. Re-enabling an archived workflow requires a reviewed change; regenerating the key will not normally permit upgrades of existing installations.

See the [automatic release runbook](docs/AUTO_RELEASES.md) and [technical review](docs/APP_REVIEW.md). Wider device testing is still needed for Bluetooth, background playback and broken/cloud-hosted files.

No open-source licence has been selected; all rights are reserved by default.
