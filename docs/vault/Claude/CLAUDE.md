---
format: claude-rules/3
---
# Klui: rules for every Claude session

- Reply to Klui in Thai, short and simple. Suggest a model and effort for each task.
- Never use the em dash or the middle dot in text Klui reads.
- Do not start work until Klui says go. Picking an option is not a go: ask "เริ่มเลยไหม?" first.
- Never ask Klui to paste a token, key or password in chat. Secrets go only in environment settings.
- Never write a patient's name, hospital number (HN), national ID number, phone number or address into the vault or the logs; write "ผู้ป่วยรายหนึ่ง" or the role.

## The vault
- On the cloud the vault is at `~/vault`. Run `vsync` at the start of a session and again after every write. (`vsync` is `ob sync --path ~/vault` with the token built in.)
- On Windows the vault is the local Obsidian folder (`C:\Users\Klui\Desktop\Klui`). Obsidian Sync does the syncing while Obsidian is open, so do not run `vsync` or `ob` there.
- Tasks use the Tasks emoji format in TaskForge token order: tags, reminder, priority, 🔁, ➕, ⏳, 📅, ✅, then 🆔 and ⛔.

## Work log: `📁 Folder/หลังบ้าน/Claude/Claude log.md`
- Read its **ภาพรวม** and **งานค้าง** at the start of every session, plus any 💬 lines Klui added since the last session. If the session is about a project, check that project's line first.
- **ภาพรวม**: one line per project, rewritten each session: where it stands, then ➜ who acts next (Klui or Claude).
- **งานค้าง**: open work as checkboxes. First tag is the project (`#<project>/<topic>`), second tag is whose turn: `#kluido` (Klui must act) or `#claudedo` (Claude does it next session). Add ⏳ or 📅 when there is a plan for when. Tick (`[x]` with ✅ date) what is done; do not delete it.
- **ไทม์ไลน์**: newest day on top, plain bullets only (no checkboxes), so Omni does not count them as tasks. Keep only the last 14 days here.
- Older days and ticked items older than 14 days live in `Claude/log/<year>/<MM> <Mon>.md` (such as `log/2026/10 Oct.md`). The secretary moves them there once a day, closes each month, and writes one line per month in `Claude/log/index.md` (and each year's summary). Never write a day older than 14 days into the main file, and never edit the monthly notes except to move text exactly as it was.
- To find something older: read `Claude/log/index.md` and `Omni/Gemini log/index.md`, open the months that match, then search the words in both folders. Klui can type `/recall <topic>` for this. `Claude/README.md` says what is where and how to repair the secretary.
- Klui comments with `💬`; Claude answers under it with `🤖`. Never edit or delete Klui's lines.

## At the end of every piece of work
- Before saying the work is done (or when Klui types `/save`), write to Claude log: the project's line in ภาพรวม, any new or finished งานค้าง, and 2 to 4 plain bullets under today in ไทม์ไลน์ (what was done, what was decided and why, what waits and for whom). Then run `vsync` (cloud only).
- Write what Klui will need later, not a diary: decisions, what is blocked, what to do next. Keep each bullet to one line. Never edit or delete bullets of an earlier day; a correction is a new bullet today starting with `แก้ไข:`.
- A session that only answered a question and changed nothing does not need an entry.

## The secretary and /save
- A scheduled secretary runs on the cloud every 3 hours (08:07, 11:07, 14:07, 17:07, 20:07, 23:07 Bangkok time) and follows `Claude/secretary.md`, read again each round. It reads everything said in Klui's Claude Code sessions since its last run (and the Chat inbox) and updates ภาพรวม, งานค้าง and today's ไทม์ไลน์ in Claude log. It ends its file with `<!-- secretary-last: ... -->` after a round that fully worked, and writes every round to `Claude/system/health.md`; leave both alone. The Omni app warns Klui when that time gets older than a day. How the whole system fits together, with diagrams: `Claude/ระบบเลขา.md`.
- When Klui says the secretary stopped (or "ซ่อมเลขา"), follow "ซ่อมเลขา" in `Claude/README.md`.
- Klui types `/save` (optionally with a note) to make this session write its own state into Claude log at once, also when the work is not finished; otherwise the secretary picks it up within 3 hours. The command file is `Claude/commands/save.md` in the vault.
- When Klui says "เลขา สรุปงาน": do the secretary's round now, following `Claude/secretary.md`.
- Claude Chat and Cowork cannot be read directly. There Klui types "บันทึก": the `save-to-log` skill drops a summary into the Google Drive folder `Claude inbox`, and the secretary merges it into Claude log (bullets marked `(Chat)` or `(Cowork)`). If Klui expects something from a chat that is not in the log yet, say so and ask Klui to type "บันทึก" there.
- The first time Claude writes the log in a new place, write one test line and ask Klui to confirm it shows on the phone before writing the rest.

## Klui's own notes
- `📁 Folder/หลังบ้าน/Omni/Omni note.md` belongs to Klui. Claude only appends, and only a task Klui must do, tagged with its project first and `#kluido` second. Never edit or delete Klui's lines.

## Gemini in the Omni app (agreed 2026-10-19, widened 2026-10-21)
- The assistant in Omni (Gemini, typing) reads Klui's tasks and this Claude log, and can propose any change to tasks. Klui confirms each change with a tap, so nothing changes without a tap. Deleting is shown in red.
- Its conversations are kept in `📁 Folder/หลังบ้าน/Omni/Gemini log/<year>/<MM> <Mon>.md` (one note per month, newest day on top, each exchange under a heading with topic and tags), with `index.md` (one line per month, each year's summary) and `README.md` beside them; the app keeps them in order. Its lasting memory is `📁 Folder/หลังบ้าน/Omni/Gemini memory.md`. Read them when Klui refers to something said to Gemini. Do not edit them; Klui may.
- Big changes (several tasks, parking, dropping) are only proposals, sent here as text starting with `#claudecheck`. Claude reviews each one ("เห็นด้วย" or "ขอแก้" with the reason) and Klui approves; no AI changes the plan on its own.
