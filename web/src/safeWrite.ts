// Writes that do not lose someone else's write. Every edit checks the file's version just before writing, but Drive
// has no conditional write, so a write from another device can still land between the check and ours and be
// replaced. Drive keeps each write as a revision: right after writing, the revision before ours tells whether
// someone slipped in, and if so their text is joined back line by line, as Android does (shared Merge3).
import { WebNotesApi } from './kotlin/OmniTask-shared.mjs'
import type { Drive, Versioned, Written } from './drive'

const api = WebNotesApi.getInstance()

/** Two edits of one note from the same starting text, joined; [lost] holds this device's lines that clashed. */
export interface Merged {
  text: string
  lost: string[][]
}

/** Joins [local] (this device's edit) and [remote] (Drive's text now), both made from [base]. */
export const merge3 = (base: string, local: string, remote: string): Merged => JSON.parse(api.merge3(base, local, remote))

/**
 * Writes [text] over the file [id], which was read as [base] and whose version was checked just before. Answers the
 * groups of this device's lines that clashed with a write made in that moment (the other side was kept).
 */
export async function writeChecked(drive: Drive, id: string, base: Versioned, text: string, mimeType?: string): Promise<string[][]> {
  return settle(drive, id, base, text, await drive.writeText(id, text, mimeType), mimeType)
}

async function settle(drive: Drive, id: string, base: Versioned, text: string, written: Written, mimeType?: string): Promise<string[][]> {
  const lost: string[][] = []
  let from = base
  let mine = text
  let w = written
  try {
    for (let attempt = 0; attempt < 3; attempt++) {
      if (!from.revision || !w.revision) break
      // Drive's version goes up with every change: one step up means nobody wrote in between, no need to ask.
      if (from.version !== '' && Number(w.version) === Number(from.version) + 1) break
      const before = await drive.revisionBefore(id, w.revision)
      if (!before || before === from.revision) break
      const merged = merge3(from.text, mine, await drive.revisionText(id, before))
      lost.push(...merged.lost)
      if (merged.text === mine) break
      // Written over again since: whoever wrote last checks the same way.
      if ((await drive.version(id)) !== w.version) break
      const again = await drive.writeText(id, merged.text, mimeType)
      from = { text: mine, version: w.version, revision: w.revision }
      mine = merged.text
      w = again
    }
  } catch {
    // Anything going wrong here leaves the write as it is.
  }
  return lost
}

/** [lines] added at the end of [text], keeping its line endings. */
export function appendLines(text: string, lines: string[]): string {
  const sep = text.includes('\r\n') ? '\r\n' : '\n'
  const body = text.replace(/[\r\n]+$/, '')
  return (body ? body + sep : '') + lines.join(sep) + sep
}
