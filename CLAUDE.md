# Omni Task

Native Android app (Kotlin, Jetpack Compose) that reads and writes the checkbox tasks in the owner's Obsidian vault.

## Writing rules (from the owner, always apply)

- Never use the em dash `—` or the middle dot `·` in any text the user reads: UI strings, notifications, mockups, release notes, and replies in chat. Use a line break, a comma, a colon, parentheses, or separate UI elements with spacing instead.
- Reply to the owner in Thai, concisely. Suggest a model and effort level for each task.

## Build

- No Android SDK locally; build and test only through GitHub Actions (`.github/workflows/build.yml`).
- Vault logic shared with the web app lives in `shared/` (Kotlin Multiplatform, JVM and JS). Keep it free of java.time and other JVM-only APIs; `gradle :shared:allTests` runs its tests on both and needs no Android SDK.
- Release APKs are signed in CI from repository secrets (`OMNI_KEYSTORE_B64`, `OMNI_KEYSTORE_PASSWORD`, `OMNI_KEY_ALIAS`) and published as GitHub releases for Obtainium. Never commit a keystore.

## Vault facts

- The app's own notes live in `📁 Folder/หลังบ้าน/Omni/`: the task note `Omni note.md` (live tasks, new tasks go here), its archive `Omni note Archive.md`, the profile `โปรไฟล์.md`, the list notes (Bucket list...) and `omni-settings.json` (Android). Before the move, the task note was `📁 Folder/หลังบ้าน/TaskForge/TaskForge.md` and the rest was in `Omni/` at the vault root; the apps still read those places until the owner moves them (Drive keeps a moved file's id, so nothing breaks).
- Tasks are in the Tasks-emoji format in TaskForge token order: tags, reminder (`#remind-at-due ⏰ HH:mm` or `#remind-at-scheduled 🎯 HH:mm`), priority, 🔁, ➕, ⏳, 📅, ✅.
- Image attachments go in `📁 Folder/หลังบ้าน/Attachments`, always saved as WebP.
