# Building Roto

This document covers the developer build process end-to-end: environment prerequisites, debug builds, and release-signed artifacts for Play Store and F-Droid.

---

## Prerequisites

- Android Studio or Android command-line tools with SDK Platform 34 and Build-Tools 35 (or newer)
- JDK 21
- Gradle Wrapper (included in repo)
- `adb` and `apksigner` on your `PATH`

## Debug Builds

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> If the manifest changes (for example when new permissions like `android.permission.INTERNET` are added for shared-link rotas), reinstall the app on your emulator/device so Android picks up the updated permissions.

## Release Signing Setup

### 1. Generate or reuse a keystore (one-time)

```bash
mkdir -p ~/.secrets/android/com.anicetuscer.roto
keytool -genkeypair \
  -alias release \
  -keyalg RSA -keysize 2048 \
  -validity 10950 \
  -storetype PKCS12 \
  -keystore ~/.secrets/android/com.anicetuscer.roto/release.jks \
  -dname "CN=Your Name, C=GB"
```

### 2. Store secrets for Gradle

Create `~/.gradle/keystore-com.anicetuscer.roto.properties`:

```
storeFile=/home/<you>/.secrets/android/com.anicetuscer.roto/release.jks
storePassword=yourKeystorePassword
keyAlias=release
keyPassword=yourKeystorePassword
```

Gradle automatically picks this up via `loadSigningProps()` in `app/build.gradle.kts`.

> **Windows note:** mirror the same layout under `C:\Users\<you>\.secrets\android\com.anicetuscer.roto\release.jks` and `C:\Users\<you>\.gradle\keystore-com.anicetuscer.roto.properties`, updating the `storeFile` path to the Windows location.

## Release Artifacts

### APK (F-Droid / direct sideload)

```bash
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

For F-Droid reproducibility and scans:

- The Gradle wrapper is pinned with `distributionSha256Sum` to verify the Gradle download.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` is set in `app/build.gradle.kts` to disable the dependency metadata signing block.
- Release APKs uploaded to GitHub are named `roto-v<versionName>.apk` (e.g. `roto-v1.0.8.apk`) to match the `Binaries` URL pattern used in fdroiddata.
- Build the GitHub release APK from the exact commit used in the fdroiddata `Builds` entry. Android embeds VCS metadata in `META-INF/version-control-info.textproto`, and F-Droid compares the signed GitHub APK against its rebuilt APK.
- After uploading a release APK, check both the SHA256 and embedded revision:

```bash
sha256sum app/build/outputs/apk/release/versioned/roto-v<versionName>.apk
unzip -p app/build/outputs/apk/release/versioned/roto-v<versionName>.apk META-INF/version-control-info.textproto
```

For fdroiddata update MRs:

- Keep the version update diff focused on `metadata/org.roto.yml`.
- Keep `subdir: app`, `gradle: yes`, `Binaries`, and `AllowedAPKSigningKeys`.
- Prefer full commit hashes in new `Builds` entries.
- Create the MR branch from current `fdroid/fdroiddata` `master`, not from a stale fork branch.
- GitLab's Code Quality widget is used by fdroiddata as an APK report channel; informational entries for permissions, signing key, APK size/ABI, and reproducible build artifacts are expected.
- GitLab's "No security scans enabled" and "License Compliance failed loading results" widgets are GitLab-native security/compliance report notices. They are not F-Droid blockers unless a CI job fails or a maintainer comments.

### Dependency profiles (F-Droid vs. “latest”)

The default dependencies in `app/build.gradle.kts` are pinned for F-Droid reproducibility. To build with newer libraries (e.g., for Play or local testing), enable the toggle:

```bash
./gradlew -PuseLatestDeps=true :app:assembleRelease
```

With `useLatestDeps=true`, common libraries (core-ktx, lifecycle, activity-compose, material, datastore, glance, work, desugar) switch to newer versions. Leave the flag off for F-Droid-compatible, pinned builds.

### App Bundle (Google Play)

```bash
./gradlew :app:bundleRelease
```

Note: The Play Console only needs the `.aab`. F-Droid rebuilds their own copy from source, but you can upload the signed APK to checkpoints or share it for testing.

## Troubleshooting

- **`apksigner: command not found`** – add `$ANDROID_HOME/build-tools/<version>` to your `PATH`.
- **`storePassword`/`keyPassword` missing** – ensure the properties file is readable and Gradle was restarted after editing.
- **Gradle can’t find the keystore** – the helper expects an absolute path; double-check `storeFile`.

## Useful Paths

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

---

Need a refresher on the app architecture or JSON schema? See `README.md` and the prompts in `app/src/main/assets/ai_llm_instructions.txt`.
