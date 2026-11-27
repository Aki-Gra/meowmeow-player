# MeowMeow Android signing key — one-time creation

**Important:** This is a permanent signing identity for personally distributed Android APKs. A new keystore cannot normally update an installed APK that was signed with the original keystore. Keep the same key for every future signed release.

## 1. Prerequisites

In the repository, open **Settings → Secrets and variables → Actions** and create these repository secrets:

- `SIGNING_STORE_PASSWORD`: unique strong password, **at least 16 characters**.
- `SIGNING_BACKUP_PASSPHRASE`: **different** unique passphrase, **at least 20 characters**, on a single line.

Keep the values in a trusted password manager, **outside** GitHub. GitHub shows secret names but does not let you retrieve their plaintext values later. Never send keystores or passwords over chat or commit them to the repository.

## 2. Generate the key (on a phone is fine)

1. Open the GitHub repository on **main** and choose **Actions**.
2. Select **Generate MeowMeow Signing Key (one-time)**.
3. Choose **Run workflow**, ensure branch is **main**, and enter exactly `GENERATE_NEW_SIGNING_KEY` in the confirmation field.
4. Run it **once**. It never runs on pushes or pull requests.
5. After the job succeeds, open the run's **Artifacts** section and download **MeowMeow-ENCRYPTED-Signing-Key-Backup**. Depending on GitHub's interface, the artifact arrives inside a ZIP archive containing `meowmeow-release.p12.gpg`.

The job generates a private RSA signing key inside an ephemeral GitHub runner and immediately encrypts its PKCS#12 (`.p12`) keystore using GPG AES-256 with a strong passphrase. It **decrypts a copy and verifies the backup's contents** before publishing only the encrypted file. The plaintext keystore is never uploaded as an artifact or committed to Git.

**The artifact is temporary (7-day retention).** Download it promptly. Deleting the workflow run or allowing artifact expiry can remove GitHub's copy.

## 3. Back up safely before any release

- Keep **two independent copies** of `meowmeow-release.p12.gpg` outside GitHub, such as two secure storage devices/locations.
- Keep **both** secret values in your password manager and a recoverable backup. The keystore password and GPG passphrase serve different purposes.
- Do not publish the GPG passphrase or keystore password, even if the encrypted backup is accidentally exposed.
- The job's restore-test validates the encryption round-trip, but later you should independently verify the backup on your own machine before depending on it for years.

**Do not run the generator a second time for an app update.** Each run creates an unrelated signing key even with identical passwords and alias.

## 4. Later: restoring the signing keystore on your own PC

After installing GPG, decrypt your backed-up file with:

```sh
gpg --output meowmeow-release.p12 --decrypt meowmeow-release.p12.gpg
```

GPG prompts for your **backup passphrase**. The decrypted `.p12` is still protected by **SIGNING_STORE_PASSWORD**.

Verify the alias with Java keytool (enter your store password at the prompt):

```sh
keytool -list -v -keystore meowmeow-release.p12 -storetype PKCS12
```

Use the alias `meowmeow-release`. This PKCS#12 keystore uses the **same password for the private key and the keystore**.

Keep the decrypted `.p12` private; avoid copying it into a public repository. A separate **protected release workflow** will be needed to sign APKs. The current workflow **only generates and backs up the key**; it neither signs APKs nor automatically stores the key as a GitHub Secret.

## 5. Consequences of loss

- **Lose the encrypted backup, but retain another good backup:** recover from that copy.
- **Lose the backup passphrase:** you cannot decrypt the encrypted backup unless you have another usable copy of the keystore.
- **Lose the store password:** even a decrypted keystore may be unusable for signing without its password.
- **Lose the signing key and all usable backups:** Android normally rejects updates to an app signed by the lost key. For direct sideloaded APK distribution, you must reinstall with a new key, potentially losing the app's saved preferences.

Existing debug APKs are also signed with **different** debug certificates. The first permanent signed APK may require a one-time uninstall of the old debug app, which can erase saved application settings but not music files saved in external music folders.

For account recovery: **GitHub 2FA recovery codes** protect access to GitHub. They do **not** recover an Android signing key.
