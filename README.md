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
- `app/src/main/java/app/omnitask/data/VaultRepository.kt` reads and writes vault files through the folder picker
- `app/src/main/java/app/omnitask/ui/` holds the screens

Code in `shared/` cannot use java.time, `String.format`, `(?i)` in a regex or an unescaped `]` outside a regex set;
its tests run on the JVM and on JS (`gradle :shared:allTests`), and `RegexPortabilityTest` checks the regexes.
