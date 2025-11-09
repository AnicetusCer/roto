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

## Release Artifacts

### APK (F-Droid / direct sideload)

```bash
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

### App Bundle (Google Play)

```bash
./gradlew :app:bundleRelease
apksigner verify --verbose --print-certs app/build/outputs/bundle/release/app-release.aab
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
