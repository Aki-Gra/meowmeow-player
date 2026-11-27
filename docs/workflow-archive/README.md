# Inactive signing and one-off publishing workflows

The files in this directory are archived for disaster recovery and historical reference. They are named `.yml.disabled` and live outside `.github/workflows/`, so GitHub Actions does **not** register or run them.

| File | Former role |
| --- | --- |
| `generate-signing-key.yml.disabled` | One-time creation and encryption of the Android signing keystore |
| `build-signed-apk.yml.disabled` | Historical manually signed APK (different version-number scheme) |
| `publish-preview-release.yml.disabled` | First one-off v1.0.2 Beta 1 publication |

**Active workflow:** `.github/workflows/auto-release.yml` already signs app releases with the existing permanent key in an isolated job and runner. It must stay enabled.

## Exceptional recovery only

If you need an archived workflow again, open a reviewable pull request copying the relevant file back into `.github/workflows/`. Re-evaluate any old source references, version codes, artifact run IDs, hash pins, retention windows, and secrets. Require human review and avoid running the restored generator on untrusted branches.

Do **not** regenerate the signing key just because you need an app update or have a new PC. Reuse your securely backed-up original key. If all usable copies of the original key are lost, generating a new key normally requires uninstall/reinstall instead of an in-place update, with possible loss of app settings.

Remove or disable the temporarily restored workflow after the exceptional operation.
