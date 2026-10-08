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

## On a computer

The same address works in any desktop browser. Chrome and Edge can also install it as an app (the install icon in
the address bar).

## Add to the iPad home screen

Open the site in Safari, Share > Add to Home Screen.

## How a write stays safe

Every edit reads the note again, lets the shared code change just the one line, checks the file's version in Drive
right before writing and starts over on the new text if it moved. If the line itself changed elsewhere, nothing is
written and the app says so.
