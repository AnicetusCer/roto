# School Nom Noms

School Nom Noms is an offline, privacy-respecting Android app that helps parents answer one simple question: **“What’s for lunch tomorrow at school?”** The app stores a school’s rotating menu locally, shows tomorrow’s meals up front, and lets parents browse any published week—even if it’s in the past or still upcoming.

## Key Features

- **Tomorrow & Today at a glance** – Launch straight into tomorrow’s menu, with today shown underneath for quick double-checks.
- **Week browser** – Open any published week to see the full Monday–Friday breakdown, including alternative and deli options.
- **JSON menu import** – Upload your own menu JSON file, or drop it into the app’s scoped Downloads folder. The app remembers your selection and can be cleared at any time.
- **AI helper prompt** – The setup screen includes a “Copy AI Instructions” button so parents can paste the prompt into any AI assistant and have it convert a PDF/photo/Text menu into the required JSON format.
- **Fully offline** – No network calls, logins, trackers, or analytics; ideal for F-Droid distribution.

## Getting Started (Parents)

1. **Install the app** – Build locally (see developer section) or install a provided APK.
2. **Generate the menu JSON**
   - On the setup screen tap **Copy AI Instructions**.
   - Paste the prompt into your preferred AI (Copilot, ChatGPT, Claude, etc.).
   - Provide the school’s current menu (PDF, photo, or text). The AI will return JSON matching the required schema.
3. **Upload the JSON**
   - Save the AI output as a `.json` file (for example `SchoolNomNomsMenu.json`).
   - In the app tap **Upload menu (JSON)** and choose the file, or place it at `Android/data/org.schooldinners/files/Download/SchoolNomNomsMenu.json` via `adb`.
4. **Browse meals**
   - The app immediately shows tomorrow and today.
   - Use **Browse weeks** to open any published week, even if it’s before or after today.
5. **Need to start over?** Tap **Clear menu** on the setup screen to forget the file and return to the instructions.

## JSON Format Summary

The app expects a structure that matches schema version `0.2`:

```json
{
  "schema_version": "0.2",
  "school_name": "Example Primary School",
  "notes": ["Optional notes"],
  "cycle": {
    "weeks": [
      {
        "week_id": "Week 1",
        "week_commencing": ["2025-09-01"],
        "days": {
          "monday": {
            "main": "Chicken Pie",
            "alt_hot": "Vegetable Curry",
            "deli_option": "Jacket Potato",
            "dessert": "Fruit Crumble"
          }
          // tuesday … friday entries
        }
      }
    ]
  }
}
```

- Dates in `week_commencing` must be Mondays (ISO `YYYY-MM-DD`).
- Each weekday can include `main`, `alt_hot`, `deli_option`, and `dessert` strings. Notes are optional.
- The AI prompt in `app/src/main/assets/ai_llm_instructions.txt` guides assistants to emit exactly this layout.

## Development

### Requirements

- Android Studio or command-line tools with Android SDK 34
- JDK 21
- Gradle (wrapper included)

### Building & Testing

```bash
./gradlew assembleDebug    # build the debug APK
./gradlew testDebugUnitTest
```

Install to a running emulator or device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Project Structure Highlights

- `app/src/main/java/org/schooldinners/data` – Menu models, repository, and DataStore-backed preferences.
- `app/src/main/java/org/schooldinners/ui` – Compose UI screens and view model handling imports, coverage messages, and week browsing.
- `app/src/main/assets/ai_llm_instructions.txt` – The AI helper prompt surfaced in the setup screen.

## Roadmap Snapshot

- Harden JSON validation and error surfaces.
- Remember last-browsed week for quicker navigation.
- Expand onboarding materials and consider accessibility polish.
- Longer term: optional widget or quick notifications once the core flow has settled.

## Contributing

Pull requests and issue reports are welcome. Please keep changes focused and ensure unit tests pass (`./gradlew testDebugUnitTest`) before submitting.
