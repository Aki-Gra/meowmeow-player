# MeowMeow Music Player — Signed APKs

## What is configured

- **Build signed MeowMeow APK (manual)** is a manually triggered workflow on the repository's default `main` branch. It never runs on push or pull requests.
- It builds **the approved, immutable player PR source** by default (rather than the older code still on `main`). After PR #1 has been reviewed and merged, choose **main-after-merge** to build the merged source.
- It runs the project's JVM unit tests and Android lint, then builds an **unsigned release APK**, decrypts the existing signing key inside the temporary runner, applies Android APK signing and verifies its signature.
- The private key is never checked into the repository or uploaded as an artifact. Only `MeowMeowMusicPlayer-Signed.apk` is published.
- `versionCode` automatically increases with the workflow run number, starting at 100001, and `versionName` is `1.0.<run number>`. Re-running the *same* workflow run does **not** increase the code; use a fresh Run workflow action for updates.
- The Android `applicationId` stays unchanged; this preserves the app identity and saved preferences **for compatible signed updates**.

## Run your first signed build

1. Open **Actions** in GitHub on the repository.
2. Select **Build signed MeowMeow APK (manual)**.
3. Choose **Run workflow** and confirm the selected branch is **main**.
4. Leave **source** as **approved-player-pr** while PR #1 remains unmerged.
5. After success, download **MeowMeowMusicPlayer-Signed.apk** from the run's Artifacts.

The first permanently signed APK likely cannot be installed over an earlier GitHub **debug** APK due to a different signing certificate. Uninstalling the debug app can erase its saved folder selections, equaliser settings and playback progress, though external music files are unaffected. After this one-time migration, signed builds made with the **same original key** and a higher versionCode can update in place.

## Your signing secrets

The workflow uses the two secrets created for signing-key generation:

- `SIGNING_STORE_PASSWORD` — password for the PKCS#12 keystore.
- `SIGNING_BACKUP_PASSPHRASE` — separate passphrase for decrypting the encrypted backup.

It also supports a third, **optional for now but required after artifact expiry**:

- `SIGNING_ENCRYPTED_KEYSTORE_BASE64` — the **Base64 encoding of the ORIGINAL encrypted backup file** `meowmeow-release.p12.gpg`, *not* the decrypted keystore.

Base64 is **not** encryption. This secret contains an already strongly encrypted file, and GitHub masks/stores it as an Actions secret. Never put the Base64 text into an issue, PR, repository file, chat, or workflow input.

Until **4 December 2026, 10:51 UTC**, the workflow can download the original encrypted artifact from generation run **45985538206**. After expiry you **must add** `SIGNING_ENCRYPTED_KEYSTORE_BASE64` or the build cannot sign APKs. Your separately saved encrypted backup is the source of truth. Do not generate a different key.

## Add the permanent encrypted-backup secret

Do this on a trusted device using your saved `meowmeow-release.p12.gpg` backup. **Do not upload the file to an online Base64 converter.**

On Linux or Android Termux (with the backup file in your working directory):

```sh
base64 -w 0 meowmeow-release.p12.gpg > encrypted-key-base64.txt
```

On macOS:

```sh
base64 -i meowmeow-release.p12.gpg | tr -d '\n' > encrypted-key-base64.txt
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("meowmeow-release.p12.gpg")) |
    Set-Content -NoNewline encrypted-key-base64.txt
```

Copy the entire resulting single-line string **directly** into a new GitHub repository Actions secret named `SIGNING_ENCRYPTED_KEYSTORE_BASE64`. After creating the secret, delete the temporary `encrypted-key-base64.txt` file. The release workflow verifies the encrypted file's SHA-256 against the original generation-run artifact before decrypting it.

## Verification and recovery

A successful release log reports `apksigner verify` and the app signing certificate's public fingerprint. **Every future signed APK must use the same certificate**. If the original signing key and all usable backups are lost, a new key normally cannot update previously installed signed APKs; users need to uninstall/reinstall and may lose app settings.

Keep two securely stored encrypted backup copies and both passwords independently recoverable. GitHub recovery codes are unrelated to Android app signing.

### Limitations

The signed build is still for **personal sideloading**. There is no Google Play publishing and no auto-update service. You must download and install newer signed APKs yourself.

Do not merge PR #1 automatically; preserve the existing acceptance and phone-testing checklist.
