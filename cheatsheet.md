# Android Emulator + Build Cheat Sheet (Windows host + WSL build)

## Typical Workflow

1. **Build in WSL (Fedora 43)** from `/home/Mile/GitHub/roto`:
   ```bash
   ./gradlew assembleDebug; ./gradlew testDebugUnitTest
   ```
2. **Start the Windows emulator** (PowerShell or cmd):
   ```powershell
   C:\Android\sdk\emulator\emulator.exe -avd PixelLikeApi34Play
   ```
3. **Install the fresh debug APK from WSL path** (Windows shell):
   ```powershell
   adb install -r \\wsl.localhost\FedoraLinux-43\home\Mile\GitHub\roto\app\build\outputs\apk\debug\app-debug.apk
   ```
4. Test on the emulator. Clear app data when switching rotas:
   ```powershell
   adb shell pm clear org.roto
   ```
5. When done, produce release artifacts (WSL):
   ```bash
   ./gradlew :app:assembleRelease      # signed APK, see docs/BUILDING.md for keystore props
   ./gradlew :app:bundleRelease        # Play Store bundle
   ```

## Useful Commands

| Task | Command |
| --- | --- |
| List configured AVDs | `avdmanager list avd` |
| **Create Pixel-like AVD (Google Play Store)** | ```powershell
avdmanager create avd \`
  --name "PixelLikeApi34Play" \`
  --package "system-images;android-34;google_apis_playstore;x86_64" \`
  --device "pixel_5"
``` |
| Launch emulator | `emulator -avd PixelLikeApi34Play` |
| Launch with cold boot | `emulator -avd PixelLikeApi34Play -no-snapshot-load` |
| Verify device online | `adb devices` |
| Install latest debug APK | `adb install -r \\wsl.localhost\\FedoraLinux-43\\home\\Mile\\GitHub\\roto\\app\\build\\outputs\\apk\\debug\\app-debug.apk` |
| Clear app data | `adb shell pm clear org.roto` |
| Push rota JSON into app scope | `adb push RotoRota.json /sdcard/Android/data/org.roto/files/Download/` |
| Pull crash logs | `adb logcat -d > logcat.txt` |
| Kill emulator | Close its window or run `adb emu kill` |

## Reminders

- Gradle wrapper is used everywhere; it’s pinned with `distributionSha256Sum` for reproducible downloads (see `gradle/wrapper/gradle-wrapper.properties` and `docs/BUILDING.md`).
- Release signing pulls secrets from `~/.gradle/keystore-com.anicetuscer.roto.properties`; see `docs/BUILDING.md` for keystore layout and verification steps (the keystore can live under `~/.secrets/android/com.anicetuscer.roto/release.jks`).
- If PowerShell can’t find `emulator` or `adb`, use the full paths (e.g. `"C:\Android\sdk\emulator\emulator.exe"`).
- Change the emulator date/time to match the week you’re testing (Settings → System → Date & time).
- If `avdmanager` says the package can’t be found, install it first:
  ```powershell
  sdkmanager "system-images;android-34;google_apis_playstore;x86_64"
  ```
