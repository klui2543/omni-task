# Omni Task

Android task app that reads and writes the checkbox tasks in an Obsidian vault folder directly.
Spec: https://claude.ai/code/artifact/4d587a77-093c-41da-b66c-97a3068baae5

## Build

Every push to `main` builds a debug APK with GitHub Actions; download it from the run's
`omni-task-debug` artifact. Locally: `gradle testDebugUnitTest :shared:allTests assembleDebug` (Gradle 8.11, JDK 17).

## Layout

- `shared/` is the vault logic the Android app and the web app (for the iPad) share, built for the JVM and for JS:
  - `data/TaskLine.kt` parses and edits one task line (Tasks emoji format)
  - `data/VaultText.kt` turns a note into tasks and makes each edit to its lines
  - `model/` holds the task model, queries, projects, planners and recurrence
  - `time/JavaTime.kt` gives kotlinx-datetime the java.time names the code calls
- `web/` is the iPad web app (Preact + TypeScript) on top of `shared/`; see `web/README.md`
- `app/src/main/java/app/omnitask/data/VaultRepository.kt` reads and writes vault files through the folder picker,
  and the Omni folder on Google Drive once it is connected (`DriveLink.kt`, with the logic in `shared/.../drive/`)
- `app/src/main/java/app/omnitask/ui/` holds the screens

Code in `shared/` cannot use java.time, `String.format`, `(?i)` in a regex or an unescaped `]` outside a regex set;
its tests run on the JVM and on JS (`gradle :shared:allTests`), and `RegexPortabilityTest` checks the regexes.

## What the assistant learns from

`📁 Folder/หลังบ้าน/Omni/omni-behavior.json` logs what the owner does on every device (a task added, done or moved
to another day, a reminder snoozed, swiped away or opened, a "no" to the assistant). The file holds one JSON event
per line and is only ever added to, so two devices writing at once join cleanly. Events from more than 90 days ago
are dropped. The logic is in `shared/.../model/Behavior.kt`; Android keeps events waiting in `BehaviorLog.kt`, and
the web in `web/src/behavior.ts`, until they are written to the file.

`shared/.../model/Learned.kt` reads the log with rules (no AI). It finds:

- tasks moved to a later day three times or more (in the daily and weekly summaries)
- work that usually slips, when it is due today or tomorrow (in the daily summary)
- what tasks like a new one usually came with (chips in quick add)
- the habits handed to Claude with "ask Claude"

A kind of work whose reminders keep being put off by an hour is asked about first. A yes is written to the profile
note (`- เตือนช้าลง:`), and only new tasks of that kind are then offered a later reminder.

## Gemini for the assistant (optional)

Settings > Gemini takes the owner's own Gemini API key (free at https://aistudio.google.com/apikey) and, optionally, a
model name (blank means `gemini-flash-latest`). With a key, questions typed to the assistant go to Gemini with the same
picture of the day "ask Claude" hands over, plus the open tasks numbered; the chip next to the heading switches back to
the rule-based answers. Gemini can suggest changes (done, due, scheduled, start, reminder, priority, a new task); they
come back as a list to tick, and only the ticked ones are written. The request and the reading of the answer are in
`shared/.../model/Gemini.kt`; Android sends it with `data/GeminiClient.kt`, the web with `web/src/gemini.ts`.

The key stays on the device (Android: preferences of its own, the web: this browser), never in the vault or
`omni-settings.json`. On the free tier Google may use what is sent to improve its models.

## Google Drive for the Omni folder (Android)

Settings > Google Drive connects the Omni folder (`📁 Folder/หลังบ้าน/Omni`) on Drive. From then on the app reads and
writes those files on Drive directly, as the web does: each edit reads the file, changes its line, checks the version
and writes. The sync app then never holds a second copy of them to clash over. Offline, edits stay on the phone and are
sent (joined line by line with any change made meanwhile) when Drive answers. Other notes are still read from the
picked folder. Why and how: `docs/drive-direct-plan.md`.

Set up once, in the same Google Cloud project as the web:

1. Credentials > Create credentials > OAuth client ID > Android. Package name `app.omnitask`; SHA-1 certificate
   fingerprint from the latest Build APK run (step "Release key fingerprint", also in the run's summary).
2. OAuth consent screen: publish the app (In production), so access does not end after 7 days. The `drive` scope is
   restricted, so Google shows "Google hasn't verified this app" when signing in; for your own account, choose
   Advanced, then continue.
3. Only the signed release build (the one Obtainium installs) can sign in: the fingerprint is that build's.
4. In DriveSync, sync once, then in Omni: Settings > Google Drive > Connect.
