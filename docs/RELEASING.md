# Releasing Voltra

Releases are built and published automatically by [`.github/workflows/release.yml`](../.github/workflows/release.yml) when a semantic version tag (`vX.Y.Z`) pointing at a commit on `main` is pushed.

## Required Repository Secrets

Production releases fail fast if any of these GitHub Actions secrets are missing:

| Secret | Description |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded PKCS12/JKS release keystore (`base64 -w0 release.jks`) |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_PASSWORD` | Key password |
| `ANDROID_KEY_ALIAS` | Key alias (defaults to `upload` if omitted) |

Never commit keystore files or passwords to the repository.

## Release Checklist

1. Bump `versionCode` (monotonically increasing integer) and `versionName` (`X.Y.Z`) in `app/build.gradle.kts`.
2. Update `CHANGELOG.md` and add release notes in:
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   - `fastlane/metadata/android/ru-RU/changelogs/<versionCode>.txt`
3. Verify locally:
   ```sh
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease -Pvoltra.signReleaseWithDebugKey=true :app:assembleDebugAndroidTest
   ```
4. Merge the release preparation PR into `main` and wait for Android CI to pass.
5. Tag and push from `main`:
   ```sh
   git checkout main && git pull --ff-only
   git tag -a v1.0.0 -m "Voltra v1.0.0"
   git push origin v1.0.0
   ```

## What the Release Workflow Does

1. Validates that the tag matches `vX.Y.Z` (or prerelease `vX.Y.Z-rc.1`), matches `versionName` in `app/build.gradle.kts`, and is an ancestor of `origin/main`.
2. Decodes the signing keystore into `$RUNNER_TEMP` (`umask 077`), failing immediately if secrets are missing.
3. Gates on `:app:testDebugUnitTest` and `:app:lintDebug`.
4. Builds signed `:app:assembleRelease` and `:app:bundleRelease` with `--no-configuration-cache`.
5. Verifies the APK with `apksigner verify --verbose` and the AAB with `jarsigner -verify`.
6. Uploads the R8 `mapping.txt` as a workflow artifact (90-day retention).
7. Computes `SHA256SUMS` and deletes the temporary keystore in `if: always()`.
8. Publishes `voltra-<version>.apk`, `voltra-<version>.aab`, and `SHA256SUMS` to GitHub Releases.
