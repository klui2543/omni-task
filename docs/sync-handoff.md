# Sync handoff (2026-10-10)

Where the conflict question stands, for the next session.

## Facts checked
- One conflict so far: `TaskForge (conflict 2026-10-09-05-55-31).md`, now in the vault's `.trash` folder on Drive. No other TaskForge conflict copies found.
- The sync app ("Drive Sync Ultimate") is most likely Autosync for Google Drive by MetaCtrl: the conflict name matches its documented pattern. Per MetaCtrl's 2015 post it compares MD5 (identical content is not a conflict) and, on a real conflict, keeps both versions: one under the original name, one as "(conflict ...)". Which side gets which name is not documented.
- Autosync offers 15 min to hourly intervals, Instant upload (local changes go up right away) and a manual sync. The owner's actual settings are not known yet.
- The Android app has no Drive access; it reads and writes the vault through the folder picked with SAF. On the first open of each day it runs the archive sweep (`TaskViewModel.sweepIfDue`), which edits `TaskForge.md` without any tap.
- The web writes Drive directly, checking the version just before writing. No documented conditional write (If-Match) was found for Drive v3, so a short race window remains.
- Obsidian Sync merges Markdown with diff-match-patch and can still duplicate text; its files are not in Drive, so the web could not read them.
- An Android Drive login under an OAuth app in "Testing" status gets refresh tokens that expire after 7 days.

## Not known
- Which version keeps the original name in a conflict.
- What caused the one conflict (suspected: a web or PC edit not yet downloaded to the phone, then a phone-side write).

## Agreed direction (proportional, step by step)
0. Owner: in Autosync turn on Instant upload, shortest acceptable interval, exempt it from battery optimisation, and sync manually before editing on the phone right after using the web. Owner may send screenshots of the Autosync settings.
1. Small code change (suggested Opus 5.5 medium): the web warns when a conflict copy exists (done 2026-10-10: a notice above every page names the copy); Android skips the daily archive sweep while a conflict copy exists (still to do).
2. Measure for one week, then decide.
3. Only if conflicts keep happening (suggested Opus 5.5 high): a conflict review screen comparing the two versions task by task, the owner confirms each choice, and the conflict copy moves to `.trash` instead of being deleted.

Not now: building our own full-vault sync ("Omni Sync") or moving to Obsidian Sync. Reasons are in the infographic.

## Links
- Infographic (private to the owner): https://claude.ai/artifact/Un9qtBRbX9xivzsTdiuzGr
- Web UI mockups: https://claude.ai/artifact/YaAv8JPuGSW4dcCQkAAuSi
- Web UI done 2026-10-10: edit panel (status, dates, reminder, repeat, priority, tags, description, subtasks, delete), filter panel with saved filters, group and sort, archive-or-delete prompt on done with undo. The web does no daily archive sweep of its own.
- Web UI still to do: Focus, Views (Kanban, Matrix, Gantt, calendar), Projects/Lists, assistant; in the edit panel the kind, note links and images (shown read-only for now) and dragging subtasks into order.
