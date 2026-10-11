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

## Installing and updating (BRAT)

Releases are published to their own repository, `klui2543/omni-ygdrasil`, by `.github/workflows/obsidian.yml` on every
push to `main` that touches `obsidian/` or `shared/`. Once, before the first release:

1. Create the repository `klui2543/omni-ygdrasil` on GitHub (public, with a README so it is not empty).
2. Make a fine-grained personal access token with **Contents: Read and write** on that repository only.
3. In `klui2543/omni-task` Settings > Secrets and variables > Actions, add it as `OMNI_PLUGIN_TOKEN`.

In Obsidian (desktop and phone): install **Excalidraw** and **BRAT** from Community plugins, then in BRAT
"Add beta plugin" with `klui2543/omni-ygdrasil`. BRAT checks for new versions when Obsidian starts and updates the plugin
by itself.

## Building here

```
npm ci
npm run core    # builds shared/ to JavaScript and copies it into src/kotlin
npm test
npm run build   # main.js
```
