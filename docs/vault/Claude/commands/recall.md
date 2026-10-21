---
description: Find something from Klui's past work in the Claude log and the Gemini log
---

Find what Klui asks about in their past work logs. Reply in Thai, short. Never use the em dash or the middle dot. Do not change any file.

What to find: $ARGUMENTS

1. Cloud only: run `vsync`. On Windows do not (Obsidian syncs). The vault is `~/vault` on the cloud, the local Obsidian folder on Windows.
2. Read `📁 Folder/หลังบ้าน/Claude/Claude log.md` (the last 14 days and every project's line).
3. Read `📁 Folder/หลังบ้าน/Claude/log/index.md` and `📁 Folder/หลังบ้าน/Omni/Gemini log/index.md`; pick the months whose summary or tags match, and read those month notes.
4. Search the words too, in Thai and English and as tags: `grep -ril` over `📁 Folder/หลังบ้าน/Claude/` and `📁 Folder/หลังบ้าน/Omni/Gemini log/` (on Windows use the Grep tool), then read the matching days.
5. Answer with what was found, newest first: the date, the file, and one or two lines each. Say what was decided and what is still open. If nothing matches, say so and list the words you tried.
