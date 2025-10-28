# Android Emulator Cheat Sheet (Windows host)

## Typical Workflow

1. **Open PowerShell** in `C:\Users\Mile\GitHub\roto` (or wherever your repo lives on Windows).
2. **Start the emulator** and leave the window running while you test:
   ```powershell
   emulator -avd PixelLikeApi34
   ```
3. In another PowerShell window run Gradle builds or install APKs with `adb install -r`.

## Useful Commands

| Task | Command |
| --- | --- |
| List configured AVDs | `avdmanager list avd` |
| Re-create Pixel-like AVD | ```powershell
avdmanager create avd \`
  --name "PixelLikeApi34" \`
  --package "system-images;android-34;google_apis;x86_64" \`
  --device "pixel_5"
``` |
| Launch emulator | `emulator -avd PixelLikeApi34` |
| Launch with cold boot | `emulator -avd PixelLikeApi34 -no-snapshot-load` |
| Verify device online | `adb devices` |
| Install latest debug APK | `adb install -r app\build\outputs\apk\debug\app-debug.apk` |
| Clear app data | `adb shell pm clear org.schooldinners` |
| Push menu JSON into app scope | `adb push SchoolNomNomsMenu.json /sdcard/Android/data/org.schooldinners/files/Download/` |
| Pull crash logs | `adb logcat -d > logcat.txt` |
| Kill emulator | Close its window or run `adb emu kill` |

## Reminders

- If PowerShell can’t find `emulator` or `adb`, use the full paths (e.g. `"C:\Android\sdk\emulator\emulator.exe"`).
- Change the emulator date/time to match the week you’re testing (Settings → System → Date & time).
- Use the in-app **Clear menu** button or `adb shell pm clear org.schooldinners` before importing a new JSON.
- When returning from WSL, re-run `emulator -avd PixelLikeApi34` if the emulator isn’t already running.
