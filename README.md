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

## With Obsidian Sync

When the vault is synced by Obsidian Sync instead of Google Drive:

- Android: the folder picker points at the vault folder Obsidian keeps on the phone, and Settings > Google Drive is
  disconnected (the app would otherwise write the Omni folder to Drive, where Obsidian Sync never looks). A Drive sync
  app may still copy the folder up to Drive one way, as a backup, but must not bring anything back down.
- Computer and iPad: the Omni Task tab of the Omni Ygdrasil plugin (`obsidian/`), the web app inside Obsidian, reading
  and writing the vault directly.

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
