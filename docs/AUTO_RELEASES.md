# Automatic Android APK releases

## Publishing workflow

`.github/workflows/auto-release.yml` automatically produces **signed, publicly downloadable** Android APKs after an **application-code** update is merged or pushed to `main`. It also supports a manual **Run workflow** action on `main`.

Changes limited to documentation or GitHub workflow configuration **do not** trigger a new app release. Pull requests never get signing secrets. Each app-change push to `main` is eligible, even if it is a merge commit.

The first run after the existing **v1.0.2 Beta 1** should produce **v1.0.3-beta.1** (Android `versionCode=100003`). Each new run increments the run number, version code and version name. Failed runs may leave gaps. A re-run of the *same* run reuses its version; do not rerun a previously published release to create a new version.

## Release pipeline

1. Check out **only** the specific `main` commit that triggered the build. Confirm it identifies itself as MeowMeow and supports version overrides.
2. Run JVM unit tests, Android lint and `assembleRelease` to produce an **unsigned** APK.
3. Upload the unsigned file as a **standard zipped Actions artifact**. The signing job uses a separate clean runner without checking out source or running Gradle.
4. In the isolated signing job, decrypt `SIGNING_ENCRYPTED_KEYSTORE_BASE64` using `SIGNING_BACKUP_PASSPHRASE`; verify the file hash matches the original backup and unlock it using `SIGNING_STORE_PASSWORD`.
5. Sign with alias `meowmeow-release`. Check `apksigner` v2/v3 signatures, signing certificate SHA-256, app identity, version code and version name.
6. Publish a **GitHub prerelease** tagged `v<version>-beta.1`, with the signed `.apk` as an asset and install/update guidance in its release notes.

## Where to download

Use [GitHub Releases](https://github.com/Aki-Gra/pocket-folder-player/releases). **Releases are durable download pages**, unlike temporary GitHub Actions artifacts.

Each APK is signed with the **same original permanent release certificate** as v1.0.2 Beta 1. As long as the application ID is unchanged and the Android version code increases, future signed updates can normally be installed over previous signed builds. Users of old debug-signed APKs may have to uninstall once and reselect their folders/preferences.

The public releases are marked **Beta** until broader device testing is complete. There is **no in-app automatic update or Google Play publishing**; publishing a new APK automatically does not make Android install it automatically.

## Required GitHub Actions secrets

- `SIGNING_STORE_PASSWORD`
- `SIGNING_BACKUP_PASSPHRASE`
- `SIGNING_ENCRYPTED_KEYSTORE_BASE64`

The third secret is the Base64 of the original **already encrypted** `.p12.gpg` file, not of the plaintext keystore. Base64 is not encryption. Keep the independent backups and passwords.

## Maintenance hardening

**Single release lane:** `.github/workflows/auto-release.yml` has a non-cancelling concurrency group. It serialises release runs and avoids simultaneous publishing jobs. GitHub can replace a pending intermediate run when many pushes arrive quickly; this is a *latest-change* pipeline, not a promise to release every intermediate commit.

**Version and tag guard:** Before accessing secrets the signing/publishing job checks existing `v1.0.*-beta.1` Git tags, refuses tag reuse even without a Release, and requires the new Android `versionCode` and `versionName` patch to exceed the highest tagged beta. This specifically prevents old workflow reruns from publishing a downgrade. The current version scheme is for the `1.0.x` beta series; update the guard and baseline deliberately before moving to `1.1` or `2.0`.

**Shareable integrity checksum:** GitHub Releases now include both the signed APK and `<APK filename>.sha256`. The signature and original signing certificate remain verified before the Release is created.

**Archived workflows:** The historical key generator, manual signer and first-beta publisher have been moved from `.github/workflows/` into `docs/workflow-archive/` as inert `.yml.disabled` files. They require a reviewed PR and explicit restoration to become runnable again. **Do not regenerate the permanent signing key to update the app**: a new key generally cannot update earlier APKs. The active automatic signing job remains on a separate runner that does not check out source or execute Gradle.

**Ignore patterns:** `.gitignore` now blocks common keystore and encrypted backup filenames; still keep backups and passwords in independent secure locations and restrict GitHub secret access.

## Old workflows

- `build-apk.yml` continues running non-release debug CI, including PR tests.
- `build-signed-apk.yml` was a historical manual signed-build workflow with a separate version sequence and is now archived/disabled.
- `publish-preview-release.yml` created the historical v1.0.2 Beta 1 and is now archived/disabled.

No signing key or secret is committed to the repository.
