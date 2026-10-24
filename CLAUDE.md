# Omni Task

Native Android app (Kotlin, Jetpack Compose) that reads and writes the checkbox tasks in the owner's Obsidian vault.

## Writing rules (from the owner, always apply)

- Never use the em dash `—` or the middle dot `·` in any text the user reads: UI strings, notifications, mockups, release notes, and replies in chat. Use a line break, a comma, a colon, parentheses, or separate UI elements with spacing instead.
- Reply to the owner in Thai, concisely. Suggest a model and effort level for each task.
- Do not start work until the owner says to go ahead. Picking an option, or asking a question (such as why a model or effort level), is not a go-ahead: ask "start now?" and wait, so the owner can switch the model and effort first.
- When the owner reports a problem or asks for a change, think first: find the cause from evidence (code, logs, the vault reports), then propose 2 or 3 ways with a recommendation, model and effort, and wait. No code until the owner picks one and says go.
- Any change to how something looks (layout, size, position, colour) needs an HTML mock published as an artifact, and the owner's pick, before any code is written. A new detail from the owner (such as "well sized") is a new look: mock it again, do not reuse an earlier mock. Do not merge or open a PR unless the owner asks.

## Build

- No Android SDK locally; build and test only through GitHub Actions (`.github/workflows/build.yml`).
- Vault logic shared with the web app lives in `shared/` (Kotlin Multiplatform, JVM and JS). Keep it free of java.time and other JVM-only APIs; `gradle :shared:allTests` runs its tests on both and needs no Android SDK.
- The Obsidian plugin (Omni Ygdrasil, `obsidian/`) also opens the whole web app in a tab (`web/src/embed.tsx`, `host.ts`, `vaultDrive.ts`: the vault is read directly, no Google), so a change in `web/src` changes the plugin too; `npm run preview` in `obsidian/` shows the tab in a browser. Google Calendar in the tab signs in through the browser and comes back by `web/public/obsidian.html` (`obsidian/src/google.ts`, setup in `obsidian/README.md`). The plugin bundles the same shared code; `.github/workflows/obsidian.yml` builds it and publishes releases to the separate repository `klui2543/omni-ygdrasil` (secret `OMNI_PLUGIN_TOKEN`) for BRAT, so this repository's releases stay APK-only for Obtainium.
- When the owner asks for an APK: push the branch, run the Build APK workflow on it (workflow_dispatch; a branch run builds and tests but does not publish a release), download the `omni-task` artifact, send the .apk to the owner in chat with SendUserFile, and say what the version fixes. Never commit the APK; delete the downloaded folder afterwards. Tell the owner to test, and open a PR or merge only when asked.
- Release APKs are signed in CI from repository secrets (`OMNI_KEYSTORE_B64`, `OMNI_KEYSTORE_PASSWORD`, `OMNI_KEY_ALIAS`) and published as GitHub releases for Obtainium. Never commit a keystore.

## The owner's phone

- OPPO Find N6 (ColorOS), not Samsung. Older commits and comments that blame Samsung's freezer were a wrong guess.
- Omni's battery use is already set to unrestricted, and the month widget's taps were still delayed after that.

## Vault facts

- The app's own notes live in `📁 Folder/หลังบ้าน/Omni/`: the task note `Omni note.md` (live tasks, new tasks go here), its archive `Omni note Archive.md`, the profile `โปรไฟล์.md`, the list notes (Bucket list...) and `omni-settings.json` (Android). Before the move, the task note was `📁 Folder/หลังบ้าน/TaskForge/TaskForge.md` and the rest was in `Omni/` at the vault root; the apps still read those places until the owner moves them (Drive keeps a moved file's id, so nothing breaks).
- Tasks are in the Tasks-emoji format in TaskForge token order: tags, reminder (`#remind-at-due ⏰ HH:mm` or `#remind-at-scheduled 🎯 HH:mm`), priority, 🔁, ➕, ⏳, 📅, ✅.
- The secretary chat (new look) follows `📁 Folder/หลังบ้าน/Omni/เลขา คู่มือ.md` (`SecretaryGuide` in `shared/`): bubbles in the owner's order (and hidden ones), taught words, how answers are cut and ranked, and the secretary's learned lines (`เสนอ:` lines the app can accept). The app reads it at every question and keeps lines it cannot read. The secretary chat, the assistant and its brief use no Gemini (owner, 2026-10-24); Gemini is left for voice notes only.
- Project names live in `📁 Folder/หลังบ้าน/Claude/ชื่อโปรเจกต์.md` (`ProjectNames` in `shared/`): the owner groups projects and branches freely in Ygdrasil, and every move or rename is written there (the row, and a line under `## ประวัติการย้าย`) so the secretary writes the new names. Task lines are read with it: an other name without spaces (`#omni`, `#rxshift`) or an old place counts as the name it has now, and is written with the new name when a line is retagged.
- Claude log (`📁 Folder/หลังบ้าน/Claude/Claude log.md`) is the secretary's book (`SecretaryBook` in `shared/`): its งานค้าง holds only the owner's next step per project (`#kluido`, with `ที่:`, `เสร็จเมื่อ:` and `ต่อจากนี้:` lines under it), Claude's work is plain bullets under `# งานของ Claude`, and a task the secretary closed has a `ปิดโดยเลขา:` line. The secretary chat's morning brief reads it. Files kept in `Omni/ส่งเลขา` are copies handed to Claude: never read for tasks, deleted after 7 days.
- Image attachments go in `📁 Folder/หลังบ้าน/Attachments`, always saved as WebP.
- Why a task is still open is logged by the owner on dated lines under it: `    - ค้าง 2026-10-13 รอคนอื่น: รอรูปจากพี่เอ` (the word ค้าง, then the date) (`StuckLog` in `shared/`). They are not part of the description, move to the archive with the task, and are handed to Claude in the assistant's snapshot; read them to tell a long process from a task that is stuck.
