---
name: done-check
description: Gate before telling the owner that work on Omni Task is done, fixed, or passing. Use before ending any turn that changed app, shared, or web code, strings, or release notes. Collects the CLAUDE.md rules into one checklist.
---

# done-check: no "done" without evidence

One rule: **"done", "fixed" and "passing" need a check run in this turn that shows it.** If a check could not run here (no Android SDK locally), say so and point to the CI run instead. Never skip silently. Report to the owner in Thai.

## Checklist

1. **Shared module**: if `shared/` changed, run `gradle :shared:allTests` locally (JVM and JS, no Android SDK needed). Green, with the count.
2. **No JVM-only API in `shared/`**: `git diff -- shared/` has no `java.time` or other `java.*` import.
3. **Web**: if `web/` changed, `npm run build` in `web/` (typecheck and bundle) and `npm test` when Playwright can run.
4. **Android app**: cannot build here. After pushing, check the GitHub Actions run of `.github/workflows/build.yml` (unit tests plus emulator tests). Say "waiting on CI" rather than "done" until it is green.
5. **Writing rules** on everything the owner reads (UI strings, notifications, mockups, release notes, this turn's reply): no em dash and no middle dot.
   `git diff -U0 | grep '^+' | grep -nE '—|·'` must print nothing.
6. **Vault format**: if task parsing or writing changed, tokens stay in TaskForge order: tags, reminder, priority, 🔁, ➕, ⏳, 📅, ✅. New tasks go to `Omni note.md`; old paths are still read. Attachments save as WebP in `📁 Folder/หลังบ้าน/Attachments`.
7. **Secrets**: no keystore or signing secret in the diff.
8. **git**: commit and push to the assigned branch.

## Final report (Thai, short)

- ✅ Verified: command and result.
- ⚠️ Not verified or waiting on CI: which item and why.
