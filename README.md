# Omni Task

Android task app that reads and writes the checkbox tasks in an Obsidian vault folder directly.
Spec: https://claude.ai/code/artifact/4d587a77-093c-41da-b66c-97a3068baae5

## Build

Every push to `main` builds a debug APK with GitHub Actions; download it from the run's
`omni-task-debug` artifact. Locally: `gradle testDebugUnitTest assembleDebug` (Gradle 8.11, JDK 17).

## Layout

- `app/src/main/java/app/omnitask/data/TaskLine.kt` parses and edits one task line (Tasks emoji format)
- `app/src/main/java/app/omnitask/data/VaultRepository.kt` reads and writes vault files through the folder picker
- `app/src/main/java/app/omnitask/ui/` holds the List and Eisenhower views
