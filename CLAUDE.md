# Omni Task

Native Android app (Kotlin, Jetpack Compose) that reads and writes the checkbox tasks in the owner's Obsidian vault.

## Writing rules (from the owner, always apply)

Klui's rules for every project are in `.claude/rules/klui-core.md` (a copy; the source is in siriraj-rx-shift, do not edit it here). They cover replying in Thai, suggesting a model and effort, the em dash and middle dot ban everywhere, talking every task through before starting, which work must wait for the owner's OK, and how to answer a problem report. On top of that, in this repository:

- For the banned em dash and middle dot, write a line break, a comma, a colon, parentheses, or separate UI elements with spacing instead.
- When proposing ways to fix a problem, give each one a model and effort level.
- Any change to how something looks (layout, size, position, colour) needs an HTML mock published as an artifact, and the owner's pick, before any code is written. A new detail from the owner (such as "well sized") is a new look: mock it again, do not reuse an earlier mock.
- Do not merge or open a PR unless the owner asks.

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
- The secretary's questions to the owner are `📁 Folder/หลังบ้าน/Claude/เลขาถาม.md` (`SecretaryAsk` in `shared/`), rewritten whole every round: plain bullets that repeat an open task's line, with `ไฟล์:` and `เหตุ:` under each. The today page shows them as one line ("เลขาถาม N ข้อ") and a sheet: done ticks the task, not done writes a `ค้าง` line (`StuckLog`). A task answered today is asked again the next day until it is done (owner, 2026-10-26).
- Image attachments go in `📁 Folder/หลังบ้าน/Attachments`, always saved as WebP.
- Why a task is still open is logged by the owner on dated lines under it: `    - ค้าง 2026-10-13 รอคนอื่น: รอรูปจากพี่เอ` (the word ค้าง, then the date) (`StuckLog` in `shared/`). They are not part of the description, move to the archive with the task, and are handed to Claude in the assistant's snapshot; read them to tell a long process from a task that is stuck.
