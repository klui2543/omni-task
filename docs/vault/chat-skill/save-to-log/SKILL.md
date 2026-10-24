---
name: save-to-log
description: Save this conversation into Klui's work log. Use when Klui types "บันทึก", "save", "/save", "เก็บลง log", or asks to keep what was decided here. Writes one new summary file into the Google Drive folder "Claude inbox"; Klui's secretary merges it into the Obsidian work log every 3 hours.
---

# Save to Klui's work log

Klui keeps one work log in Obsidian (`Claude log.md`). Claude Chat and Cowork cannot write there, so they drop a summary into the Google Drive folder "Claude inbox", and a scheduled secretary merges it into the log. Only ever create a new file; never edit, move or delete any file.

Reply in Thai, short. Never use the em dash or the middle dot. Never write a patient's name, hospital number (HN), national ID number, phone number or address; write "ผู้ป่วยรายหนึ่ง" instead.

## Steps

1. From this conversation work out: which project (a short tag such as `omni-task` or `siriraj/ป้าย`), where it stands, what was decided and why, what is stuck and on whom, and the next step and whose it is (Klui or Claude). If Klui added a note after the command, include it.
2. Write the summary in exactly this form (plain text, no checkboxes outside งานค้าง):

```
omni-inbox/1
from: chat
saved: <now, ISO 8601 with timezone, such as 2026-10-21T20:40:00+07:00>
project: <project tag>

## ภาพรวม
- <project>: <where it stands> ➜ <Klui or Claude acts next>

## งานค้าง
- [ ] #<project>/<topic> #kluido <what Klui must do>
- [ ] #<project>/<topic> #claudedo <what Claude should do next time>

## ไทม์ไลน์
- <2 to 4 one-line bullets: what was done, decided and why, what waits and for whom>

## รู้จักเพิ่ม
- <only when this talk showed something lasting about how Klui works or lives: a habit, when his energy is good or bad, what blocks or helps him; one line each; leave the section out when there is none>
```

   Use `from: cowork` when running in Cowork. Leave งานค้าง empty when nothing waits. Several projects: one ภาพรวม line each, and tag each item with its own project.
3. Find the folder: Google Drive search `title = 'Claude inbox' and mimeType = 'application/vnd.google-apps.folder'` (its id is usually `1w6DBgYGGgvGrV-B8zrsbMYcSxfVohQGC`). Create it in My Drive only if it does not exist.
4. Create one file in it with Google Drive create_file: title `<YYYY-MM-DD HHmm> <chat or cowork> <topic, a few words>`, parentId the folder id, contentMimeType `text/plain`, textContent the summary.
5. Tell Klui in 2 lines what was saved and that it reaches Claude log within 3 hours.

If there is no Google Drive tool, show the summary in a code block and tell Klui to connect Google Drive in Settings > Connectors, then say "บันทึก" again.
