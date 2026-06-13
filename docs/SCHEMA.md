# Roto JSON Schema (Version 0.3)

Roto stores schedules in a lightweight JSON format so you can edit rotas by hand, generate them via AI, or tweak the sample files. This document explains each field and offers a fully annotated example with inline comments.

> **Note:** JSON doesn’t support comments, so the annotated example below uses pseudo-comment lines (`// ...`) for clarity. Remove them in real files.

---

## Top-Level Structure

```jsonc
{
  "schema_version": "0.3",         // Required. Currently must be "0.3".
  "rota_name": "My Rota Name",     // Display name shown in the app (can be any label).
  "notes": [ "Optional global note" ], // Optional list shown on the home screen.
  "special_events": {              // Optional. Map of ISO date -> string OR array of strings.
    "2025-12-05": ["Event 1", "Event 2"]
  },
  "cycle": { ... },                // Required. Describes repeating weeks/days.
  "overrides": { ... }             // Optional map of date-specific overrides.
}
```

### `cycle`

`cycle` always contains a `weeks` array, and may optionally include a `repeat` block. You can use either explicit `week_commencing` dates, the `repeat` anchor, or both.

#### Option A – Explicit week dates

```jsonc
"cycle": {
  "weeks": [
    {
      "week_id": "Week 1",
      "week_commencing": [
        "2025-10-27",
        "2025-11-17"
      ],
      "days": {
        "monday": { ... }
      }
    }
  ]
}
```

Use `week_commencing` when you have specific calendar Mondays that should map to a given week template. This is ideal for finite term schedules or when you don’t want the rota to loop.

#### Option B – Repeating cycle

```jsonc
"cycle": {
  "repeat": {
    "start_date": "2025-10-27",    // ISO date (must be a Monday). Roto cycles relative to this Monday anchor.
    "start_week_id": "Week 1"
  },
  "weeks": [
    {
      "week_id": "Week 1",
      "days": {
        "monday": { ... }
      }
    },
    {
      "week_id": "Week 2",
      "days": {
        "monday": { ... }
      }
    }
  ]
}
```

`repeat` tells Roto to loop through the `weeks` array forever, starting from the given Monday. Specify `start_week_id` if the first week in the loop isn’t the first element in the array.

> **Heads-up:** for now Roto only supports Monday anchors. If your rota naturally flips mid-week (e.g., starts on a Wednesday), anchor the cycle to the nearest Monday and split the entries across two weeks (previous week covers Wednesday–Sunday, next week covers Monday–Tuesday). A future update may allow arbitrary anchor days, but this approach keeps today/tomorrow views accurate in the meantime.

> **Can I use both?** Yes. If a date lands in `week_commencing`, that explicit mapping wins. Otherwise Roto falls back to the repeating cycle.

> **Legacy field:** Older files may use `school_name`; Roto will still read this, but new rotas should prefer `rota_name`.

### Day definition

```jsonc
"days": {
  "monday": {
    "slots": [
      { "label": "Meat Main", "text": "Chicken Pasta Bake", "tags": ["allergen:gluten"] },
      { "label": "Veggie Main", "text": "Roasted Vegetable Bake" },
      { "label": "Desert", "text": "Chocolate Brownie", "tags": ["allergen:nuts", "Vegan"]}
    ],
    "notes": [
      "Fresh fruit and water available daily."
    ]
  }
}
```

- `slots` (required) – ordered list of blocks displayed in the UI. Each slot must have:
  - `label` (string) – e.g., “Option 1”, “Senior shift”, “Activity”.
  - `text` (string) – description for that slot.
  - `tags` (optional array) – arbitrary markers (“vegan”, “Team B”, “night shift”).
- `notes` (optional array) – extra reminders for that day only.


### `overrides`

Use overrides for one-off changes (closures, special events). Keys are ISO dates (`YYYY-MM-DD`).

```jsonc
"overrides": {
  "2025-12-19": {
    "closed": true,
    "reason": "Term ends – school closed",
    "notes": ["Wrap lunches available on request."]
  },
  "2025-12-20": {
    "slots": [
      { "label": "Winter Fair", "text": "Stalls open 10:00-16:00" }
    ],
    "notes": ["Carols at noon."]
  }
}
```

- `closed` (bool) – if `true`, the app shows the day as closed (empty slots).
- `reason` (string) – optional text explaining the closure.
- `slots` – optional replacement slots (if the day isn’t closed).
- `notes` – optional extra reminders.

### `special_events`

Use `special_events` at the top level to surface banners on specific dates without touching the underlying cycle/override. Keys are ISO dates. Values can be:

- A single string:
  ```jsonc
  "special_events": { "2025-12-05": "Christmas Disco Today!" }
  ```
- Or an array of strings when you want multiple lines on the same day:
  ```jsonc
  "special_events": { "2025-12-05": ["Christmas Disco Today!", "Wear festive jumpers"] }
  ```

Special events are displayed in both the app and the widget (today/tomorrow).

---

## Fully Annotated Example

```jsonc
{
  "schema_version": "0.3",
  "rota_name": "City Hospital Night Shift Sample",
  "notes": [
    "Rotation shows a repeating seven-night pattern.",
    "Each night includes a senior nurse, support nurse, and float."
  ],
  "cycle": {
    "repeat": {
      "start_date": "2025-10-27",    // Monday anchor for the cycle.
      "start_week_id": "Night Cycle"
    },
    "weeks": [
      {
        "week_id": "Night Cycle",
        "week_commencing": ["2025-10-27"],
        "days": {
          "monday": {
            "slots": [
              { "label": "Senior Nurse", "text": "S. Patel (22:00-07:30)" },
              { "label": "Support Nurse", "text": "E. Martin (22:00-07:30)" },
              { "label": "Admissions Float", "text": "J. Okafor (standby until 02:00)" }
            ],
            "notes": ["Med reconciliation focus night. Expect 6 admissions."]
          },
          "tuesday": {
            "slots": [
              { "label": "Senior Nurse", "text": "K. Wong (22:00-07:30)" },
              { "label": "Support Nurse", "text": "D. Voss (22:00-07:30)" }
            ],
            "notes": ["Night safety round at 01:30."]
          }
        }
      }
    ]
  },
  "overrides": {
    "2025-11-05": {
      "slots": [
        { "label": "Fire Drill", "text": "Midnight drill – all staff present." }
      ],
      "notes": ["Arrive 30 min early."]
    },
    "2025-12-25": {
      "closed": true,
      "reason": "Hospital holiday staffing – see emergency rota."
    }
  }
}
```

---

## Tips & Best Practices

- **Stick to ISO dates** (`YYYY-MM-DD`) for everything (`week_commencing`, `overrides` keys, etc.).
- **Only include days you need** – you can omit unused days entirely, but any day you declare must contain at least one slot.
- **Use tags creatively** – they can highlight allergens, teams, or locations. The UI simply renders them as badges.
- **One file per rota** – the app expects a single JSON file containing the entire cycle, plus overrides, whist you could have two rotas using the same day slots, it will probably just get messy and confusing.
- **Regenerate with AI safely** – the built-in prompt in the app is schema-aware; if something looks off, ask your assistant to fix the specific field, re-save the JSON, then tap **Reload** for local files or **Fetch** for shared links.

Need to test a change quickly? Edit one of the sample rotas in `app/src/main/assets/sample_rotas`, copy it to your device, and load it through the app.
