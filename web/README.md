# Omni Task web

The iPad version of Omni Task. It runs in the browser, signs in with Google and reads and writes the same
`TaskForge.md` in the owner's Obsidian vault on Google Drive. The task line format, queries and recurrence come
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
on the device for its hour; after that one quiet round trip to Google renews it. Nothing passes through a server of ours.

## Google Calendar on the Focus page (optional)

The Focus page can read your Google Calendar (events and shifts in the day plan, free time, the night's sleep). It is
off until you press "อนุญาต" on the Focus page, which asks Google for the read-only `calendar.readonly` scope on top
of `drive`. For it to work:

1. In the same Google Cloud project, enable the Google Calendar API (APIs & Services > Library).
2. On the consent screen (Data access), add the `.../auth/calendar.readonly` scope.

The Views page (Gantt, month and 7, 3 or 1 day calendars) reads the same calendar with the same permission. If
step 1 is missing, the Focus page says so instead of showing events. The tests use a stand-in for Google Calendar;
the real service has not been tried yet.

## On a computer

The same address works in any desktop browser. Chrome and Edge can also install it as an app (the install icon in
the address bar).

## Add to the iPad home screen

Open the site in Safari, Share > Add to Home Screen.

## How a write stays safe

Every edit reads the note again, lets the shared code change just the one line, checks the file's version in Drive
right before writing and starts over on the new text if it moved. If the line itself changed elsewhere, nothing is
written and the app says so.

## Projects and lists

The "โปรเจกต์/ลิสต์" page reads `TaskForge.md` and the list notes in the vault's `Omni/` folder (the notes whose
header says `omni-list: true`; the app finds only those directly in `Omni/`). Every text change (renaming a project or
a branch, adding to a list, pulling tasks into a list, "do in order" with 🆔 and ⛔, a new list note) is made by
`shared/.../web/WebProjects.kt`, the same code paths Android uses, and written with the same version check.

What the owner chooses on this page stays on this device (localStorage `omni.projects`): the order of projects, the
starred ones, the state of each branch (active, trying, chosen, parked), each project's task order. The web does not
read or write Android's `omni-settings.json` yet, so these choices do not travel between the phone and the iPad.
