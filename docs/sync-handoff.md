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

## Update (2026-10-10 evening)
- Second conflict: `Omni note (older, before conflict 2026-10-10-18-43-58).md`, in `.trash`. This naming answers the open question: the newer version keeps the original name, the older one is renamed. The current `Omni note.md` is a new Drive file (created 5 s after the conflict); the old file id (since 2026-09-29) is now the conflict copy. The web finds notes by name and skips trashed files, so it is not affected.
- What it lost: the older side had "ปรับระบบให้เป็นระบบ "ป้าย"" ticked done (✅ 2026-10-10); the newer side (Routine moved to 10-10, `#อนาคต` added) won, so that tick is gone. No duplicated lines in this conflict.
- The nine `Routine ... ✅ 2026-10-08` lines in the archive did not come from sync. They are one done copy per tick, which is how Omni before v0.3 (`e813b26`, in-place repeats), the Tasks plugin and TaskForge complete a repeating task; their order (newest on top) matches. Omni now moves the line in place. The owner now ticks only in Omni, so these copies should not come back.
- Owner's sync app settings (screen recording): DriveSync Ultimate 7.7.4 (MetaCtrl, the Autosync family). **"Enable autosync" (monitor folders and sync in background) is off**; the schedule shows "Every 2 hours" greyed out. So the phone only syncs when the owner syncs by hand, and its copy can be hours old: this is the most likely cause of both conflicts.
- No "Automation" section (secret code for the documented `syncNow` broadcast, package `com.ttxapps.autosync`, class `com.ttxapps.autosync.Automation`) appears in Settings, Synchronization or Security in this version, so Omni cannot ask it to sync. Could be asked of drivesync@metactrl.com.

## Not known
- Whether conflicts stop once background sync is on.

## Agreed direction (proportional, step by step)
0. Owner: in Autosync turn on Instant upload, shortest acceptable interval, exempt it from battery optimisation, and sync manually before editing on the phone right after using the web. Owner may send screenshots of the Autosync settings. (2026-10-10 evening: background sync found off; owner asked to turn on "Enable autosync", pick the shortest schedule, turn on "Try again automatically", and set the app's battery use to Unrestricted. Then measure a week.)
1. Small code change (suggested Opus 5.5 medium): the web warns when a conflict copy exists (done 2026-10-10: a notice above every page names the copy); Android skips the daily archive sweep while a conflict copy exists (still to do).
2. Measure for one week, then decide.
3. Only if conflicts keep happening (suggested Opus 5.5 high): a conflict review screen comparing the two versions task by task, the owner confirms each choice, and the conflict copy moves to `.trash` instead of being deleted.

Not now: building our own full-vault sync ("Omni Sync") or moving to Obsidian Sync. Reasons are in the infographic.

## Links
- Infographic (private to the owner): https://claude.ai/artifact/Un9qtBRbX9xivzsTdiuzGr
- Web UI mockups: https://claude.ai/artifact/YaAv8JPuGSW4dcCQkAAuSi
- Web UI done 2026-10-10: edit panel (status, dates, reminder, repeat, priority, tags, description, subtasks, delete), filter panel with saved filters, group and sort, archive-or-delete prompt on done with undo. The web does no daily archive sweep of its own.
- Web UI done 2026-10-10 (PR #9): Focus, Views, Projects/Lists, assistant, Settings, task kinds manager, calendar links and calendar write, every note in the vault, the Omni folder move (`หลังบ้าน/Omni/Omni note.md`, old places still read). Still to do in the edit panel: note links and images (read-only), dragging subtasks into order.

## Open for the next session (owner deferred all three on 2026-10-10)
1. What "Omni setting เพียบเลย" means: too many files in the Omni folder, too many options in the Settings screen, or something else. Ask the owner; nothing was changed for it.
2. Settings sync: should the web read and write `Omni/omni-settings.json` (Android's synced settings)? Today the web keeps its choices in localStorage and never touches that file.
3. Sync and conflict work above (steps 0 to 3).
Also pending on the owner's side: move the files in Obsidian (Omni folder into `หลังบ้าน`, `TaskForge.md` to `Omni/Omni note.md`), add the `calendar.events` scope on the OAuth consent screen, and check CI on main after the PR #9 merge (Build APK emulator tests failed on the two runs before it).
