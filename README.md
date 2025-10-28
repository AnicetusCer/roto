# Roto

Roto is an offline, privacy-first Android app that answers one simple question: **“What’s on the rota tomorrow?”** It keeps a rotating timetable on-device, honours real calendar Mondays for Week 1/Week 2 style cycles, and supports one-off overrides without ever touching the network.

## Key Features

- **Tomorrow-first** – Launch straight into tomorrow’s rota with day-specific notes, tags, and override reasons.
- **Browse any day** – Pick any calendar date (weekends included) to see its slots or a friendly “No rota found” message.
- **Flexible slots** – Schema 0.3 stores labelled slots (Option 1, Grab & Go, Duty, etc.) plus optional tags for allergens or year groups.
- **Offline JSON import** – Load your rota via **Load rota (JSON)** or by placing `RotoRota.json` in the app’s scoped Downloads directory.
- **AI helper prompt** – The setup screen’s **Copy AI Instructions** button gives parents/carers a ready-made prompt to turn a PDF/photo into valid JSON with their own assistant.
- **Privacy by default** – No analytics, tracking, or proprietary dependencies; the app runs happily offline and is F-Droid friendly.

## Getting Started (Families)

1. **Install the app** – Build locally (see below) or side-load the provided APK.
2. **Generate the rota JSON**
   - On the setup screen tap **Copy AI Instructions**.
   - Paste the prompt into your preferred assistant (ChatGPT, Claude, Copilot, etc.) and share the rota PDF/photo/text.
   - The AI replies with JSON matching schema 0.3.
3. **Load the JSON**
   - Save the AI output as a `.json` file (for example `RotoRota.json`).
   - In the app tap **Load rota (JSON)** and choose the file, or place it at `Android/data/org.roto/files/Download/RotoRota.json` via `adb`.
4. **Browse the rota**
   - The home screen shows tomorrow and today.
   - Use **Browse rota weeks** to open any week pattern and inspect its days.
5. **Need to start over?** Tap **Clear rota** on the setup screen to forget the file and return to the instructions.

## JSON Format Summary (Schema 0.3)

```json
{
  "schema_version": "0.3",
  "school_name": "Example Primary School",
  "notes": ["Optional global notes"],
  "cycle": {
    "weeks": [
      {
        "week_id": "Week 1",
        "week_commencing": ["2025-11-03", "2025-11-24"],
        "days": {
          "monday": {
            "slots": [
              { "label": "Option 1", "text": "Chicken Pie" },
              { "label": "Option 2", "text": "Veggie Curry", "tags": ["vegetarian"] },
              { "label": "Grab & Go", "text": "Jacket Potato Bar" }
            ],
            "notes": ["Fruit cup alternative available."]
          },
          "saturday": {
            "slots": [
              { "label": "Weekend Club", "text": "Packed lunch hamper" }
            ]
          }
        }
      }
    ]
  },
  "overrides": {
    "2025-12-19": {
      "closed": true,
      "reason": "Term ends – school closed",
      "notes": ["Wrap lunches available on request."]
    }
  }
}
```

- `week_commencing` entries must be Mondays (ISO `YYYY-MM-DD`).
- Each day contains ordered `slots[]` objects with required `label` and `text`, plus optional `tags[]` and `notes[]`.
- Use `overrides{}` for one-off closures or special days rather than editing the base cycle.

The full AI helper prompt lives in `app/src/main/assets/ai_llm_instructions.txt`.

## Development

### Requirements

- Android Studio or command-line tools with Android SDK 34
- JDK 21
- Gradle (wrapper included)

### Building & Testing

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest
```

Install to a running emulator or device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Project Structure Highlights

- `app/src/main/java/org/roto/data` – Roto models, repository, and DataStore-backed preferences.
- `app/src/main/java/org/roto/domain` – Rotation logic that resolves overrides, notes, and slot lists.
- `app/src/main/java/org/roto/ui` – Compose screens plus the view model that handles imports and date browsing.
- `app/src/main/assets/ai_llm_instructions.txt` – The prompt surfaced by **Copy AI Instructions**.

## Roadmap Snapshot

- Harden JSON validation and surface clearer inline errors.
- Remember the last-opened week/day to speed up return visits.
- Polish accessibility copy and spacing on the setup flow.
- Longer term: explore a homescreen widget powered by the same offline logic.

## Contributing

Pull requests and issue reports are welcome. Please keep changes focused and ensure unit tests pass (`./gradlew testDebugUnitTest`) before submitting.
