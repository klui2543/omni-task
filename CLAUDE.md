# Omni Task

Native Android app (Kotlin, Jetpack Compose) that reads and writes the checkbox tasks in the owner's Obsidian vault.

## Writing rules (from the owner, always apply)

- Never use the em dash `—` or the middle dot `·` in any text the user reads: UI strings, notifications, mockups, release notes, and replies in chat. Use a line break, a comma, a colon, parentheses, or separate UI elements with spacing instead.
- Reply to the owner in Thai, concisely. Suggest a model and effort level for each task.
- Do not start work until the owner says to go ahead. Picking an option, or asking a question (such as why a model or effort level), is not a go-ahead: ask "start now?" and wait, so the owner can switch the model and effort first.
- Any change to how something looks (layout, size, position, colour) needs an HTML mock published as an artifact, and the owner's pick, before any code is written. A new detail from the owner (such as "well sized") is a new look: mock it again, do not reuse an earlier mock. Do not merge or open a PR unless the owner asks.

## Build

- No Android SDK locally; build and test only through GitHub Actions (`.github/workflows/build.yml`).
- Vault logic shared with the web app lives in `shared/` (Kotlin Multiplatform, JVM and JS). Keep it free of java.time and other JVM-only APIs; `gradle :shared:allTests` runs its tests on both and needs no Android SDK.
- The Obsidian plugin (Omni Ygdrasil, `obsidian/`) also opens the whole web app in a tab (`web/src/embed.tsx`, `host.ts`, `vaultDrive.ts`: the vault is read directly, no Google), so a change in `web/src` changes the plugin too; `npm run preview` in `obsidian/` shows the tab in a browser. The plugin bundles the same shared code; `.github/workflows/obsidian.yml` builds it and publishes releases to the separate repository `klui2543/omni-ygdrasil` (secret `OMNI_PLUGIN_TOKEN`) for BRAT, so this repository's releases stay APK-only for Obtainium.
- When the owner asks for an APK: push the branch, run the Build APK workflow on it (workflow_dispatch; a branch run builds and tests but does not publish a release), download the `omni-task` artifact, send the .apk to the owner in chat with SendUserFile, and say what the version fixes. Never commit the APK; delete the downloaded folder afterwards. Tell the owner to test, and open a PR or merge only when asked.
- Release APKs are signed in CI from repository secrets (`OMNI_KEYSTORE_B64`, `OMNI_KEYSTORE_PASSWORD`, `OMNI_KEY_ALIAS`) and published as GitHub releases for Obtainium. Never commit a keystore.

## The owner's phone

- OPPO Find N6 (ColorOS), not Samsung. Older commits and comments that blame Samsung's freezer were a wrong guess.
- Omni's battery use is already set to unrestricted, and the month widget's taps were still delayed after that.

## Vault facts

- The app's own notes live in `📁 Folder/หลังบ้าน/Omni/`: the task note `Omni note.md` (live tasks, new tasks go here), its archive `Omni note Archive.md`, the profile `โปรไฟล์.md`, the list notes (Bucket list...) and `omni-settings.json` (Android). Before the move, the task note was `📁 Folder/หลังบ้าน/TaskForge/TaskForge.md` and the rest was in `Omni/` at the vault root; the apps still read those places until the owner moves them (Drive keeps a moved file's id, so nothing breaks).
- Tasks are in the Tasks-emoji format in TaskForge token order: tags, reminder (`#remind-at-due ⏰ HH:mm` or `#remind-at-scheduled 🎯 HH:mm`), priority, 🔁, ➕, ⏳, 📅, ✅.
- Image attachments go in `📁 Folder/หลังบ้าน/Attachments`, always saved as WebP.
- Why a task is still open is logged by the owner on dated lines under it: `    - ค้าง 2026-10-13 รอคนอื่น: รอรูปจากพี่เอ` (the word ค้าง, then the date) (`StuckLog` in `shared/`). They are not part of the description, move to the archive with the task, and are handed to Claude in the assistant's snapshot; read them to tell a long process from a task that is stuck.
