# Omni Task web

The iPad version of Omni Task. It runs in the browser, signs in with Google and reads and writes the checkbox tasks of
every note in the owner's Obsidian vault on Google Drive, as the Android app does. New tasks go to `Omni note.md`
(`📁 Folder/หลังบ้าน/Omni/`, or the old `TaskForge.md` until it is moved). The task line format, queries and recurrence come
from `../shared` (Kotlin compiled to JavaScript), so a line is read and written exactly as the Android app does.

## Run

```
npm install
npm run core     # builds ../shared to JavaScript (needs JDK 17 and Gradle) and copies it to src/kotlin
npm run dev      # or: npm run build
npm test         # browser tests against a fake Google Drive
```

## Set up Google sign-in (once)

1. In Google Cloud Console, create a project and enable the Google Drive API.
2. OAuth consent screen: user type External, publishing status Testing, add your own Google account as a test user.
3. Credentials > Create credentials > OAuth client ID > Web application.
   - Authorized JavaScript origins: where the app is served from, e.g. `https://<user>.github.io`
     (and `http://localhost:5173` for `npm run dev`).
   - Authorized redirect URIs: the app's exact address, e.g. `https://<user>.github.io/omni-task/`
     (and `http://localhost:5173/`).
4. Open the app and paste the client ID when asked. It is kept on that device only.

The app asks for the `drive` scope, which lets it find the vault folder by name. Sign-in is a redirect to Google and
back, not a popup, since popups are often blocked in an app added to the iPad home screen. The access token is kept
on the device for its hour; after that one quiet round trip to Google renews it. A sign-in lasts 24 hours from the
owner's last sign-in (quiet renewals do not extend it); after that the sign-in page asks again and says why. Nothing passes through a server of ours.

## The page for Obsidian

`public/obsidian.html` is where Google sends the browser after a sign-in begun in the Omni Task tab inside Obsidian
(`obsidian/`): it only hands Google's answer back to Obsidian (`obsidian://omni-task-auth`) and keeps nothing. Its
address must be an authorized redirect URI of the OAuth client; see `obsidian/README.md`.

## Google Calendar on the Focus page (optional)

The Focus page can read your Google Calendar (events and shifts in the day plan, free time, the night's sleep). It is
off until you press "อนุญาต" on the Focus page, which asks Google for the read-only `calendar.readonly` scope on top
of `drive`. For it to work:

1. In the same Google Cloud project, enable the Google Calendar API (APIs & Services > Library).
2. On the consent screen (Data access), add the `.../auth/calendar.readonly` scope.

Settings has a card "ปฏิทินที่แสดง" to switch Google calendars off (only the ones Google Calendar shows are listed; a
calendar added later shows by default). The choice stays on this device, like the other web settings.

Tapping an event (in the Focus plan, Views or the assistant's agenda) opens its page in Google Calendar.

The assistant can also add events, as it does on Android: the switch "ลง Google Calendar ด้วย" on a plan card, and a
chosen time added from a suggestion (on by default once allowed). The first time it is turned on, the page explains
and then asks Google for the `calendar.events` scope (together with `calendar.readonly`, since `calendar.events`
cannot list calendars); nothing is asked at sign-in. Add that scope on the consent screen too. The page keeps the
conversation through the trip to Google and returns to the same page. Events go to the primary calendar.

The Views page (Gantt, month and 7, 3 or 1 day calendars) reads the same calendar with the same permission. If
step 1 is missing, the Focus page says so instead of showing events. The tests use a stand-in for Google Calendar;
the real service has not been tried yet.

## On a computer

The same address works in any desktop browser. Chrome and Edge can also install it as an app (the install icon in
the address bar).

## Add to the iPad home screen

Open the site in Safari, Share > Add to Home Screen.

## Every note in the vault

Focus, Tasks, Views, Projects and the Assistant show the tasks of every `.md` note in the vault together, as Android
does. Left out: the archive notes (`Omni note Archive.md`, the old `TaskForge Archive.md`), a sync app's conflict copies
(pointed out in a banner instead), hidden folders such as `.trash` and `.obsidian`, the Attachments folder, list
notes (their items belong to their list on the Projects page) and the old `TaskForge.md` once an `Omni note.md` is in
the new place. Each task's key starts with its note's Drive id, so ticking, editing, deleting, archiving, scheduling
from the Assistant, "do in order" and pulling into a list are written to the note the task is in. New tasks (quick
add, Kanban, a project's "+") still go to `Omni note.md`.

How the notes are read (`src/noteIndex.ts`):

- The first time, the task note is shown at once while the vault is walked in the background, level by level with
  many folders per Drive question; the notes are then read six at a time and the page fills in when they are done.
- After that a reload asks Drive's change log what changed since the last read and reads only those notes (a folder
  made, moved or renamed starts a new walk). `Omni note.md` itself is always read fresh.
- What was read is kept on the device (IndexedDB, only the notes with a checkbox line), so the next visit starts from
  it and asks only for the changes. Signing out forgets it.

The page builders in `shared/.../web` take one note as (key, path, text); with the key `WebNotes.MANY` the text is a
JSON list of notes instead, so every page reads one note or all of them with the same code.

## How a write stays safe

Every edit reads the note again, lets the shared code change just the one line, checks the file's version in Drive
right before writing and starts over on the new text if it moved. If the line itself changed elsewhere, nothing is
written and the app says so.

Drive has no conditional write, so another device can still write between that check and ours. Drive keeps each
write as a revision, so right after writing the app asks for the revision before its own (`src/safeWrite.ts`): when
it is not the one the edit started from, someone slipped in, and their text is joined back line by line with the
shared `Merge3` (the same code Android uses for offline edits). Where both changed the same line, the other side is
kept and this device's line waits in a banner ("การแก้ที่ชนกับที่อื่น") to be added to the end of the note or let go.

## Without a network

The site's own files are kept by a service worker (`public/sw.js`, network first, Google's services untouched), so the
app also opens with no network. The task note as last read is kept on the device (`src/offline.ts`; the other notes
are already kept by the note index). Opened without a network, even after the hour of the sign-in ran out, the page
shows them and says "ออฟไลน์" (in the side bar, or at the top of the page when the iPad is upright; a tap reads again). An edit to a note that was read
(ticks, edits, deletes, quick add) is made on the device's copy and waits; the badge counts the notes waiting. The
next read that reaches Drive (a reload, the network coming back) sends them: each note is read again and the edits
are joined with what changed meanwhile (`Merge3`), clashes going to the same banner. Moving to the archive, the daily
sweep, the profile and new list notes need Drive and say so. "Do in order" across several notes reads them all again and writes only when none of
them moved.

## Projects and lists

The "โปรเจกต์/ลิสต์" page reads the tasks of every note and the list notes in the vault's Omni folder (`หลังบ้าน/Omni/`, or the old `Omni/` at the vault root until it is moved; the notes whose
header says `omni-list: true`, found only directly in that folder). Every text change (renaming a project or
a branch, adding to a list, pulling tasks into a list, "do in order" with 🆔 and ⛔, a new list note) is made by
`shared/.../web/WebProjects.kt`, the same code paths Android uses, and written with the same version check.

A tag is a project only when the owner made it one on this page: the "แท็กอื่น" chips under the project cards make
one, and "เลิกเป็นโปรเจกต์" in a project's header turns it back into a plain tag (the task lines never change). The
first time, the projects every first tag named before are kept as the choice, so none disappears.

Which project, list, mind map, project view or Ygdrasil is open is kept in the address (`src/ui/projects/route.ts`,
e.g. `#/projects/project/<name>/map`), so the browser's back steps out in the order of Android's back button (a project
opened from Ygdrasil goes back to the tree) and each screen can be linked to.

What the owner chooses on this page (which tags are projects, the order of projects, the starred ones, the state of
each branch, each project's task order, "do in order") is kept on this device (localStorage `omni.projects`) and travels with Android
through `omni-settings.json`; see "Settings shared with Android" below.

## Task kinds

Settings has the same "ประเภทงาน" manager as Android: hide the built-in kinds (#รอ, #อนาคต, #สักวัน; hiding one also removes its
card from Focus and its choice from the edit panel unless the task has that kind) and make kinds of your own (a name, an emoji
and the tag that marks it, which never names a project). The edit panel's "ประเภท" field and the Tasks filter use that list. The
logic is `shared/.../web/WebKinds.kt`. The choices are kept on this device (localStorage `omni.kinds`, in Android's own
tab-separated `name, emoji, tag` form) and travel with Android through `omni-settings.json`.

## Why a task is still open (the stuck log)

As on Android, the owner can write why a long-open task is still open, on a dated line under it
(`    - ค้าง 2026-10-13 รอคนอื่น: รอรูปจากพี่เอ`), with quick picks (รอคนอื่น, ไม่มีเวลา...) or their own words. The lines
are not part of the description and move to the archive with the task. The logic is the shared `StuckLog`, through
`shared/.../web/WebStuck.kt`; a note is the edit op `stuck`, written with the same version check as every edit, and it
counts as a review of the task.

- Focus: a late or long-open task (21 days or more) has a bar a week beside it (grey for a note, a tall accent bar for a
  finished subtask) and "ขยับล่าสุด N วันก่อน" on its line; either opens the task's path (made, every note, every
  finished subtask) with the box to write in. The review deck asks "ติดอะไรอยู่" before each choice (optional) and writes
  it first.
- Edit panel: such a task shows "ความเป็นไป" (subtasks done and the path, "+ จด" on today's point) under the description;
  other tasks have "ทำไมยังค้าง" folded at the bottom.
- "บันทึกงานค้าง" (from the edit panel, the review deck and the weekly questions) shows every task with notes as its path, the most recently
  explained first.
- The weekly review on the Assistant page says how many tasks have sat 21 days or more with nothing written; tapping
  that line asks about each, one at a time.

## Settings shared with Android

`src/settingsSync.ts` reads and writes Android's settings file, `omni-settings.json` in the Omni folder, for the choices
both apps have: the Projects page (which tags are projects, order, stars, branch states, task order, "do in order"), the
task kinds, what counts as urgent, the archive days and whether ticking done asks, the Focus page's choices (the
countdown, how much future work, suggestions waved away, reviews, tonight's bed and wake times and whether the wake
time came from the watch), saved filters, folded groups and Kanban columns, the look (font, size, theme, colour set,
in Android's upper-case names), task reminders on or off, and the dates each repeating task was done. The morning
summary's choices are read from the file and never written. The file is read when the page opens and on every
reload; it wins when its `savedAt` is newer than this device's last sync, as on Android, and the first time this
version meets a key it takes the phone's value even from an older file. A change here is written 1.5 seconds later
(quick changes become one write), with every key Android wrote left as it was and `savedAt` later than the file's
own, so a phone whose clock runs ahead still takes it. Nothing is written before the file was read once, so the iPad
never overwrites the phone's choices with its defaults. The calendars shown and the assistant's memory stay on each
device.

## Reminders

A browser on the iPad cannot schedule a notification for later, and sending one would need a server of ours, so the
web reminds only while it is open (`src/reminders.ts`, `src/ui/Reminders.tsx`). The times come from the shared
`AlarmPlan`, as on Android (a task's ⏰ or 🎯 time, parked branches left out). At each one a banner shows with เสร็จ,
อีก 1 ชั่วโมง and พรุ่งนี้ (a snooze is kept on this device for that reminder only), and a system notification too once
Settings > การแจ้งเตือน allowed it (on the iPad only for the app added to the Home Screen). Reminders that came due
while the page was away show on return, up to 12 hours back. The app icon carries the count of overdue work and work
due today where the browser allows a badge. Snoozing, letting go and opening a reminder go into the behavior log, as
on Android. The assistant shows the morning summary (Android's digest) as a card.

## Images

A picked photo is saved as WebP in the Attachments folder, as on Android, without its EXIF data and with a smaller
copy offered when it is large. Safari cannot write WebP from a canvas, so there the image is encoded with a
WebAssembly encoder (`@jsquash/webp`), loaded only when it is needed.
