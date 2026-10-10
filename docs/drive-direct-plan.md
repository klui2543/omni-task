# Plan: Android reads and writes the Omni folder on Drive directly (2026-10-10)

Agreed with the owner after the second conflict (see `sync-handoff.md`). Suggested: Opus 5.5, effort high, plan mode first.

## Why
The web writes the task note on Drive (read, edit one line, check the version, write). Android writes the phone's copy and
leaves upload to DriveSync, whose background sync the owner keeps off to save battery. Two paths to one file is what
makes conflicts, and settings or habits only narrow the window. With Drive as the one place both apps write, the window
closes for Omni's files and nothing runs in the background.

## Scope (owner's choice)
- Through Drive: the files of the Omni folder only: `Omni note.md`, `Omni note Archive.md`, the list notes,
  `โปรไฟล์.md`, `omni-settings.json`, plus the old places the apps still read (`TaskForge/TaskForge.md`, root `Omni/`).
  Use the shared path rules (`VaultText.TASK_FILE`, `LEGACY_TASK_FILE`, `Archive`), as the web does.
- Unchanged: every other note is still read (and its tasks edited) through the SAF folder. Obsidian on the phone keeps
  using DriveSync for the owner's other notes.
- Attachments stay on SAF for now.

## Design
1. **Sign-in**: Google Identity Services `AuthorizationClient` (play-services-auth) with the `drive` scope. It hands back
   an access token, silently once granted, so background work (alarm actions, widgets) can get one too. Works on the
   release build only (its signing key is stable; CI debug keys change per run).
2. **DriveClient** (Kotlin, small, like `web/src/drive.ts`): find folder and file by name (`sameName` rules for emoji),
   read text with `version`, replace text, list changes from a page token. HttpURLConnection is enough; no new HTTP lib.
3. **OmniStore**: cache of each Omni file's text and version in app storage, for a fast start, offline reading and alarms.
   - Edit: read fresh text and version, apply the same `VaultText` / shared edit, check the version again, write, and
     retry a few times if it moved (the web's `vault.ts` loop). The line is found by its raw text, so an edit made
     elsewhere to another line is kept.
   - Offline: queue the edit as an intent (task raw, line index, operation), not as a whole file. On reconnect replay
     each one on the fresh text; a task whose line is gone becomes a message, never a silent overwrite.
   - Refresh on app open and on resume through the changes feed (cheap); no periodic background job.
4. **VaultRepository**: route reads and writes of Omni folder paths to OmniStore; the SAF listing skips those paths so a
   note is never read twice. `sweepIfDue`, `SettingsSync`, `AlarmReceiver` (done, snooze), `Widgets` and `Scheduler`
   go through the same routing.
5. **Fallback**: not signed in means today's folder mode, unchanged. Settings gets a card to connect Google Drive and
   to show the last sync and queued edits.
6. **Switch-over**: on first connect, compare the phone's copy with Drive; if they differ, Drive wins and the phone copy
   is kept as a note in the app's own storage for the owner to look at (no file written to the vault).

## Owner's one-time steps
1. Google Cloud Console (same project as the web): Credentials > Create OAuth client ID > Android, package
   `app.omnitask`, SHA-1 of the release key (CI prints it; add a `keytool -list` step that echoes only the fingerprint).
2. OAuth consent screen: publish to Production, so grants do not end after 7 days. The `drive` scope is restricted, so
   sign-in shows "Google hasn't verified this app"; for the owner's own use, Advanced > continue.
3. Optional later: in DriveSync, make the Omni folder download-only or leave it; Omni no longer writes the phone copy.

## Status (2026-10-10)
Built in one PR, since routing inside `VaultRepository` covers every caller, the background ones included (alarm
actions, widgets, the daily sweep, settings sync):
- `shared/.../drive/`: `DriveRest` (REST over a transport the app gives), `OmniDrive` (cache, version-checked edits,
  offline queue), `Merge3` (line-by-line join of an offline edit with Drive's text); tests against a fake Drive.
- `app/.../data/DriveLink.kt`: sign-in (`AuthorizationClient`), OkHttp transport, the cache file, one store per process.
- Settings > Google Drive: connect, sign in again, status, pending edits, disconnect. Before connecting, the phone's copy
  of the task note is compared with Drive's; if they differ, the owner is asked (sync in DriveSync first, or use Drive's).
- The daily sweep runs only when Drive answers.
- CI prints the release key's SHA-1 and publishes releases from main only.
- Conflict copies named "(older, before conflict ...)" (DriveSync's other pattern) are now recognised and not read.

Still open: a screen listing offline edits that clashed (today they show once as a message).

## Edges that remain
- Drive v3 has no conditional write, so two writers in the same second can still race; the version re-check and retry
  keep it to that second.
- Editing an Omni file in Obsidian on the PC while the app writes is still possible, but rare.
