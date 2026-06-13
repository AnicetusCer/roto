# Roto Privacy Policy

Last updated: 2026-06-13

Roto is an offline, privacy‑first Android app for managing rotating schedules (for example school dinners, shifts, or collections). This policy explains what information the app accesses and how it is handled.

Roto is developed and maintained by **AnicetusCer** (“we”, “us”).

## 1. Data collection and processing

- We do **not** collect, store, or process any personal data on our own servers.  
- The app does **not** include analytics SDKs, advertising SDKs, or tracking libraries.  
- We do not use any third‑party crash reporting or telemetry services.

All rota data you load into the app stays on your device, under your control.

## 2. Data stored on your device

Roto stores the following information locally on your device:

- The rota JSON file you choose or import (for example a school menu or shift rota).  
- Your choice of source (local file or shared link), saved as simple preferences so the app can reload it on startup.  
- A mirrored copy of remote rota JSON (when you use a shared link), saved into a `Downloads/Roto` folder so it can be used offline and by the homescreen widget.

This data is stored only on the device and is not sent to us.

Because rota files are user‑provided, they may contain information such as names, classes, duties, or other schedule details. You are responsible for ensuring that the content of these files complies with any applicable privacy or safeguarding requirements for your organisation.

## 3. Network access and shared links

Roto requests the `INTERNET` permission for a single purpose:

- To download rota JSON from HTTPS links that you explicitly provide (for example a GitHub Gist or other HTTPS URL).

When you enter a shared link:

- The app fetches the JSON from that URL using a secure HTTPS connection.  
- The downloaded file is saved on your device and treated like a local rota file.  
- The app and homescreen widget use the saved local copy unless you press Fetch to download the link again.

We do not operate any backend server for Roto, and we do not receive or log the contents of your rota files.

> Note: The website or service that hosts your shared link (for example GitHub or another provider) may have its own privacy practices. Please review their policies separately.

## 4. Widgets and background work

Roto includes an optional homescreen widget. To show today’s or tomorrow’s rota, the widget:

- Reads the rota JSON stored on your device.  
- Refreshes shared-link rota JSON over HTTPS only when you press the widget Fetch button.

This background work happens on your device only. No data is sent to us.

## 5. Children’s data

Roto is often used for school or club rotas, which may relate to children’s schedules. Roto itself does not collect or transmit any personal data; it simply displays the rota information that you or your organisation supply.

If you include personal information about children in a rota file, you are responsible for:

- Obtaining any necessary consent.  
- Ensuring that the file is stored and shared in a way that complies with applicable laws and policies.

## 6. Third‑party libraries

Roto uses open‑source libraries provided by the Android platform (for example AndroidX, Jetpack Compose, Glance, WorkManager, and Kotlinx Serialization) and the OkHttp HTTP client. These run entirely on‑device and are not used to send data to us or to tracking services.

## 7. Your choices

You can:

- Choose a different rota file at any time.  
- Clear your saved selection within the app, which resets the app’s state.  
- Delete rota files and their mirrored copies from your device using your file manager.

Uninstalling the app removes all app‑specific data stored on your device. It does not delete files you created in shared folders like Downloads.

## 8. Contact

If you have questions about this policy or Roto’s privacy behaviour, please use the project issue tracker:

- Source code and issues: <https://github.com/AnicetusCer/roto>

If this policy changes, we will update the “Last updated” date above and describe the changes in the project’s release notes.
