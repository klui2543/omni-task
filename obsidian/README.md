# Omni Ygdrasil for Obsidian

Every Omni project drawn as one tree on an Excalidraw page, kept in step with `Omni note.md`. The tree, the filters and
every change to the note come from the same Kotlin code as the Android and web apps (`shared/`); this plugin draws the
tree with the Excalidraw plugin's scripting API (ExcalidrawAutomate) and reads what the owner does on the drawing.

## What it does

- **The tree on its own page** (`Ygdrasil.excalidraw.md` in the Omni folder): the trunk, each project, its branches and
  tasks, and dashed lines between tasks that wait for one another (orange across projects).
- **Draws itself again** when the task note changes, from the phone, Drive sync or here. Only the boxes, lines and text
  Omni drew are replaced; everything the owner drew stays.
- **Side panel**: filters (status, dates, blocked, starred, parked branches, wait lines, search) and actions on what is
  selected on the drawing: add a task to a branch, tick tasks, move a task, fold a branch.
- **On the drawing**: an arrow from one task to another makes the second wait for the first (`🆔` and `⛔`); deleting a
  dashed wait line takes the wait out; a task dragged onto another branch's box moves there.
- **Text to task**: select any text or sticky note and run "แปลงข้อความที่เลือกเป็นงาน".

## Omni Task in a tab

The whole Omni Task web app (`../web/src`) opens in a tab of Obsidian: the ribbon's checklist icon, the command
"เปิด Omni Task", or by itself when Obsidian starts (a setting of the plugin). It reads and writes the notes in the vault
directly, so it works on every device that has Obsidian and needs no Google sign-in; Obsidian Sync takes each change to
the other devices. When Sync brings a change in, or a note is edited in Obsidian, the tab reads the notes again.

- `web/src/vaultDrive.ts` answers the app's Drive questions from the vault (a file's id is its path), so the app's
  vault logic, conflict checks, settings file and behavior log run unchanged. `src/vaultFs.ts` is the vault side.
- `web/src/host.ts` holds what differs from the web page: the address is kept in memory, the look goes on the tab, and
  Google sign-in, Drive and Calendar are left out (the Focus page has no calendar events in the tab yet).
- `src/omniView.ts` draws the app in a shadow root, so its styles and Obsidian's never meet. `scripts/scopeCss.mjs`
  turns the app's styles into the tab's: the page's root becomes the tab, a width rule asks the tab's width (a container
  query), and a screen height is the tab's height. The app's sheets and bars stay inside the tab.
- `npm run preview` builds `preview/index.html`, the tab in a page shaped like Obsidian over example notes, for looking
  at the layout in a browser (`?dark`, `?page=%23/tasks`, `?side=0`).

## Installing and updating (BRAT)

Releases are published to their own repository, `klui2543/omni-ygdrasil`, by `.github/workflows/obsidian.yml` on every
push to `main` that touches `obsidian/`, `shared/` or `web/src/`. Once, before the first release:

1. Create the repository `klui2543/omni-ygdrasil` on GitHub (public, with a README so it is not empty).
2. Make a fine-grained personal access token with **Contents: Read and write** on that repository only.
3. In `klui2543/omni-task` Settings > Secrets and variables > Actions, add it as `OMNI_PLUGIN_TOKEN`.

In Obsidian (desktop and phone): install **Excalidraw** and **BRAT** from Community plugins, then in BRAT
"Add beta plugin" with `klui2543/omni-ygdrasil`. BRAT checks for new versions when Obsidian starts and updates the plugin
by itself.

## Building here

```
(cd ../web && npm ci)   # the Omni Task tab is the web app's code, with its packages
npm ci
npm run core    # builds shared/ to JavaScript and copies it into src/kotlin and ../web/src/kotlin
npm test
npm run build   # main.js
```
