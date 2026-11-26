# MeowMeow Music Player — Pre-merge Technical Review

Reviewed: 26 November 2026  
Scope: the **whole Android app** on PR #1, not only its changed files.  
Evidence: all Kotlin application files, manifest, Gradle and CI configuration; GitHub Actions PR build #12 (success); inspection of the actual built debug APK.  
Decision: **Rebrand PR is suitable for merge after the owner accepts the documented test limitations.** The APK is **not yet a production-quality, upgradeable release**.

## Verified positives

- PR #1 changes product naming, launcher art and CI naming without changing `applicationId`, source packages, playback or equaliser logic.
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

### R5 — Medium/low: position persistence is periodic

`PlaybackService` persists playback every 3 seconds and on some player events. Seeking while paused followed by abrupt process termination before the next tick could lose the last seek position; the handler also keeps waking every 3 seconds when paused.

**Action:** persist on position discontinuity/seek and appropriate lifecycle transitions; schedule periodic writes only when necessary. This is a reliability/efficiency enhancement, not a newly introduced rebrand defect.

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
- [ ] Verify last track/seek position is restored without autoplay
- [ ] Verify empty/revoked folders and unsupported/corrupt files show useful errors
- [ ] Test on representative Android versions, particularly newer foreground-service / storage-permission behavior

## Merge recommendation

**Merge PR #1 after checking the new name/icon and accepting that the shipped artifact is a test/debug APK.** No rebrand-specific issue was identified that needs a code change before merging. Keep production signing, automated tests, controller-policy assessment and robustness improvements as separate issues/PRs to preserve the requested minimal-rebrand scope.

**This review is source/CI/APK inspection, not an on-device end-to-end test.**
