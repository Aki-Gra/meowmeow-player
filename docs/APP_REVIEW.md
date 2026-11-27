# MeowMeow Music Player — Pre-merge Technical Review

Reviewed: 27 November 2026  
Scope: the **whole Android app**, rebrand and playback/maintenance fixes on PR #1.  
Evidence: Android/Kotlin sources, Gradle, manifest, GitHub Actions logs and prior successful debug APK builds.  
Status: **Signing is intentionally deferred. All new functional changes require a current green CI build and phone testing before merge.**

## Verified design and security properties

- Existing Android application ID and saved preferences remain unchanged. The approved cat/vinyl launcher icon is retained.
- The app operates offline; there is no `INTERNET`, broad-storage, camera or microphone permission in the previously inspected APK. Android media dependencies may request `ACCESS_NETWORK_STATE` and `WAKE_LOCK`.
- The Storage Access Framework is used for user-selected read-only folders, scanned without recursion, limited to MP3 and MP4 file extensions.
- Media3 ExoPlayer is hosted by a `MediaSessionService` with audio focus and noisy-output handling.
- The player restores the last song and position paused when a saved session exists, preserving active background playback on UI reopen.
- The service attempts to skip broken tracks, prevents error cycles and observes normal repeat/shuffle behaviour.
- CI uses `pull_request` (not `pull_request_target`), `contents: read`, immutable commit-pinned Actions and no signing secrets.

## Review findings and disposition

### R1 — Deferred: permanent signing and in-place APK updates

Separate cloud debug builds were verified to use different signing certificates. Android will not reliably install a newer one over an existing debug install with a different certificate.

**Decision:** keep GitHub debug builds for testing. When PC access is available, generate and securely back up a long-lived signing key, protect release signing from PRs, and introduce monotonically increasing release `versionCode` values. Do not put keys or passwords in source control. This is **not resolved by this PR**.

### R2 — Partially addressed: automated tests and lint

Added `PlaybackDecisions.kt` for pure playlist restoration and safe error navigation, with nine JVM unit tests covering saved tracks, deletion, wrapping, empty playlists, skip chains, Repeat Off and cyclic shuffle. CI now runs `testDebugUnitTest`, `lintDebug` and `assembleDebug`. The lint gate must remain enforced rather than suppressed or baselined.

**Remaining:** tests do not exercise Android's Storage Access Framework providers, real audio decoding, device notifications, Bluetooth, Android Auto or runtime lifecycle. Instrumented and physical-device testing are still needed.

### R3 — Addressed in code: audio-controller connection failure

The activity now keeps a connection-error state and presents a visible **Retry connection** button instead of showing an indefinite spinner after initial connection failure. Stale asynchronous controller callbacks are ignored when a retry has replaced the previous connection.

**Remaining:** reproduce a service-start failure on an Android device to test the recovery interaction.

### R4 — Addressed with compatibility caveat: MediaSession access

The service now returns its session only to controllers that Media3 reports as trusted. Android's documented trust model includes the app itself, system controllers and authorised media controllers. This reduces exposure to arbitrary external controllers.

**Remaining:** verify standard notifications, Bluetooth and lock-screen controls on a phone. Some unrelated third-party remote-control apps may no longer connect; extending access would require an explicit compatibility policy.

Reference: https://developer.android.com/media/media3/session/background-playback

### R5 — Addressed in code: playback persistence and checkpoint efficiency

Seek and track-change callbacks and pause events persist position. The existing three-second checkpoint is now scheduled **only while audio is actively playing** rather than firing when paused. Restore/error-skip decisions are isolated into testable functions.

**Remaining:** SharedPreferences `apply()` is asynchronous, so an abrupt process death may still race disk persistence; verify restart and force-close scenarios on-device.

### R6 — Partially addressed: folder-provider failures

Folder scans now verify that a persisted read permission exists before listing files. A missing permission produces a readable error instead of being silently interpreted as an empty music folder.

**Remaining:** large folders and slow/cloud-backed providers still depend on `DocumentFile.listFiles()`; loading state exists, but performance and provider quirks should be tested on devices.

### R7 — Partially addressed: maintainability and build security

- The legacy Kotlin `kotlinOptions.jvmTarget` configuration is replaced with `compilerOptions`.
- The deprecated QueueMusic icon is replaced by its AutoMirrored variant.
- GitHub Actions checkout, Java setup, SDK setup, Gradle setup and artifact upload are pinned to full verified commit SHAs; Node-20-era actions are upgraded where supported.
- Workflow concurrency cancels superseded PR builds; tests and lint now gate APK publication.

**Remaining:** there is no Gradle wrapper; the CI installs Gradle 8.13 explicitly. Launcher art is a single WebP, not an adaptive/monochrome icon. The Compose UI is concentrated in a large activity file. These are low-priority follow-ups, not reasons to change user-approved visual design.

Reference: https://docs.github.com/en/actions/reference/security/secure-use

## On-device acceptance checklist (still required)

- [ ] Install a fresh test APK; confirm the MeowMeow launcher icon and label
- [ ] Restore the saved song and seek position paused; no prior session starts on Folders
- [ ] Keep music playing in background while reopening the UI
- [ ] Seek while paused and restart; confirm position persists
- [ ] Remove the previously selected song; verify the next selection, paused and starting at 0:00
- [ ] Play a playlist with corrupted/unsupported files; verify skip and no infinite retry
- [ ] Verify Repeat Off, Repeat All, Repeat One, Shuffle and end-of-playlist behaviour
- [ ] Check standard notification, lock-screen, Bluetooth and headphone-disconnect controls
- [ ] Simulate controller connection failure and use Retry connection
- [ ] Remove folder permission; verify the re-select error, then reselect it successfully
- [ ] Test local folders and slow/cloud-backed folders where available
- [ ] Check equaliser behaviour on supported and unsupported devices
- [ ] Confirm phone-specific runtime behaviour on Android 10 through current versions where possible

## Merge recommendation

After a green run for the **current PR head** and essential phone checks, PR #1 is appropriate for review and merge as a debug-build project. It is **not yet a production release**, principally because of nonpersistent debug signing and incomplete device-level coverage. Do not merge automatically.
