# MeowMeow Music Player — Pre-merge Technical Review

Reviewed: 26 November 2026  
Scope: the **whole Android app** on PR #1, including subsequent playback-reliability fixes.  
Evidence: all Kotlin application files, manifest, Gradle and CI configuration; GitHub Actions PR build #12 (success); inspection of the actual built debug APK.  
Decision: **The rebrand has passed CI, but newer playback-reliability code requires a fresh CI pass and physical-device acceptance before merge.** The APK is **not yet a production-quality, upgradeable release**.

## Verified positives

- PR #1 changes product naming, launcher art and CI naming without changing `applicationId`, source packages or equaliser logic. Subsequent commits improve playback restoration and error recovery.
- PR build #12 completed `:app:assembleDebug` successfully; the branded APK artifact was published.
- The built APK reports label **MeowMeow Music Player**, package `com.example.pocketfolderplayer`, versionName `0.1.0`, versionCode `1`, and contains the new `meowmeow_icon.webp` resource.
- The **merged APK manifest** has no `INTERNET`, broad storage, microphone or camera permission. Media libraries add `ACCESS_NETWORK_STATE` and `WAKE_LOCK`; media playback foreground-service permissions are expected. `allowBackup=false`.
- Folder access uses Android's user-controlled Storage Access Framework, persists only the read grant, and scans only immediate child files with `.mp3` or `.mp4` extensions.
- Playback uses a Media3 `MediaSessionService`, ExoPlayer audio-focus handling and audio-becoming-noisy handling. The player exposes play/pause, seek, previous/next, shuffle and repeat modes.
- The service stores the current track/position and shuffle/repeat state, while equaliser preferences remain separate. Preference names were intentionally preserved during rebranding.
- CI uses `pull_request` rather than `pull_request_target`, declares `contents: read`, and does not use release secrets.

## Findings and priorities

### R1 — High: CI debug APKs cannot reliably upgrade each other

The APKs from two successful GitHub Actions runs were inspected and their Android APK Signature Scheme v2 signer certificate SHA-256 digests **differ**. Both use the same application ID. Android requires a consistent app signing certificate for normal upgrades, so a newer debug APK may need a previous installation removed first. Uninstalling removes app-specific saved folders and settings, although music stored elsewhere remains in place.

**Action before real distribution:** establish a fixed signing key stored outside Git, provide it to a dedicated release build securely (for example encrypted GitHub Actions secrets), and increment `versionCode` for releases. Continue using ephemeral debug builds for experimentation.

Reference: [Android app signing](https://developer.android.com/studio/publish/app-signing).

### R2 — Medium: no automated test suite or lint gate

The repository contains only production code and a workflow that runs `gradle :app:assembleDebug`. A passing compile does not validate folder-provider behavior, resume correctness, media controls, UI interaction or device compatibility.

**Action:** introduce unit or instrumented tests for folder parsing / restore state and Media3 control behavior, and include `lintDebug` and tests in a separate maintenance PR. Verify on physical Android devices.

### R3 — Medium: controller-connection error has no recovery path

`MainActivity.kt` catches failure of `MediaController.Builder(...).buildAsync()` and displays a toast, but the controller remains `null`: the folder screen keeps showing a loading indicator and Add stays disabled. There is no retry UI.

**Action:** expose a failed state and Retry button in a later functional fix.

### R4 — Medium: media-session access policy should be reviewed

`PlaybackService.onGetSession()` returns the media session to all requesting Media3 controllers. This supports common external media controls, but the app does not implement its own controller access policy. Depending on requirements, third-party controllers may be able to observe track metadata or control playback.

**Action:** assess which callers and commands should be allowed without breaking Bluetooth, headphones, lock-screen controls or Android Auto; add explicit policy if needed. No exploit was demonstrated.

Reference: [Media3 background playback and controllers](https://developer.android.com/media/media3/session/background-playback).

### R5 — Addressed in PR: position restoration and error recovery

`PlaybackService` now persists playback on seeking, transitions and pause requests, as well as its existing 3-second checkpoint. The folder store also remembers the track index, allowing a deleted track to fall forward to the next available track. `MainActivity` opens the player with the saved track paused when a previous session exists. On playback failure the service attempts subsequent tracks, respecting shuffle, Repeat Off and normal repeat playback; a damaged Repeat One track is escaped rather than retried forever. An exhausted playlist is surfaced in the UI.

**Remaining limitations:** abrupt process death can still race asynchronous preference writes; the periodic handler still runs when paused; a corrupt track may need physical-device testing to verify platform-specific Media3 recovery.

### R6 — Low: large-folder scanning and error reporting

`FolderScanner` lists immediate children through `DocumentFile`, filters by extension and sorts by filename. This is correct for the declared feature, but large/cloud-backed folders may be slow; a revoked grant can lead to an empty or generic error message.

**Action:** test local and slow document providers, empty/revoked folders, and large playlists. Consider specific recovery messaging.

### R7 — Low: maintainability and dependency upkeep

- UI, player coordination and folder selection are concentrated in a roughly 480-line `MainActivity.kt`.
- CI logs warn about `setup-java@v4` / Node 20 action deprecation, Gradle `kotlinOptions.jvmTarget` and the `QueueMusic` icon API.
- Third-party Actions are version-tagged, not pinned to full commit SHA; review/pin for supply-chain hardening.
- The repository has no Gradle wrapper; cloud builds explicitly install Gradle 8.13.
- The launcher uses a single drawable WebP, not an adaptive/monochrome Android icon. This works but limits launcher theming support.

**Action:** follow-up maintenance PRs, not required for the current rebrand.

Reference: [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use).

## Manual acceptance checklist (not validated by automated CI)

- [ ] Install the new APK fresh; verify the launcher name and icon at normal launcher sizes
- [ ] Select a folder containing both MP3 and MP4 tracks; verify only direct children appear
- [ ] Verify add/remove favourite folders, and reopen the app to confirm persistence
- [ ] Verify playback, pause, seek-by-tap, drag-to-seek, previous, next, shuffle and repeat
- [ ] Verify background playback, notification controls, lock-screen, Bluetooth and headphone unplug
- [ ] Verify equaliser presets/manual bands on a supported device; graceful fallback on unsupported device
- [ ] Reopen app: last track and seek position restored **paused** on the player screen; no previous session opens Folders
- [ ] While music is already playing in background, reopen UI without pausing or interrupting it
- [ ] Seek while paused, force-close and reopen; verify saved position is recent
- [ ] Delete the previously selected track and reopen; verify the next track is selected, paused, with a brief notice
- [ ] Include corrupted tracks: verify automatic skip during playback and no infinite loops when every track fails
- [ ] Confirm Repeat Off, Repeat All, Repeat One and shuffle are respected, with safe error skipping
- [ ] Verify empty/revoked folders and unsupported/corrupt files show useful errors
- [ ] Test on representative Android versions, particularly newer foreground-service / storage-permission behavior

## Merge recommendation

**Merge PR #1 only after the latest playback-fix APK builds and the new recovery scenarios pass on a real Android device.** The rebrand itself is low risk, but the playback changes require runtime testing. Keep release signing, automated tests and controller-policy assessment as follow-ups. Long-lived signing is intentionally deferred until the owner has PC access.

**This review is source/CI/APK inspection, not an on-device end-to-end test.**
