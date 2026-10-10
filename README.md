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
