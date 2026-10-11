// What the web keeps on this device so it can be used without a network: the task note as last read, and the edits
// made while Google could not be reached. The other notes are already kept by noteIndex.ts. The pending edits are
// sent, joined line by line with whatever changed on Drive meanwhile, the next time Drive answers (see Vault).

/** The task note as last read from Drive. */
export interface CachedNote {
  id: string
  path: string
  text: string
}

/** A note edited offline: Drive's text the edits started from, and the text with the edits. */
export interface PendingEdit {
  path: string
  base: string
  text: string
}

interface Saved {
  rootId: string
  note: CachedNote | null
  pending: Record<string, PendingEdit>
}

const KEY = 'omni.offline'

function read(rootId: string): Saved {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Saved | null
    if (s && s.rootId === rootId) return s
  } catch {
    /* nothing kept */
  }
  return { rootId, note: null, pending: {} }
}

function write(s: Saved) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* not kept; the page still works while it is open */
  }
}

export const offlineStore = {
  note: (rootId: string): CachedNote | null => read(rootId).note,
  setNote(rootId: string, note: CachedNote) {
    const s = read(rootId)
    write({ ...s, note })
  },
  pending: (rootId: string): Record<string, PendingEdit> => read(rootId).pending,
  setPending(rootId: string, id: string, edit: PendingEdit | null) {
    const s = read(rootId)
    if (edit) s.pending[id] = edit
    else delete s.pending[id]
    write(s)
  },
  /** Forgets everything (on sign out). */
  clear() {
    try { localStorage.removeItem(KEY) } catch { /* nothing kept */ }
  },
}

/** An edit that clashed with a change made elsewhere: the other side was kept, these are this device's lines. */
export interface Clash {
  id: string
  fileId: string
  /** The note's name, for the owner to know where it was. */
  name: string
  lines: string[]
  at: number
}

const CLASH_KEY = 'omni.clashes'

/** The clashes kept until the owner adds the lines back to the note or lets them go. */
export const clashes = {
  all(): Clash[] {
    try {
      return JSON.parse(localStorage.getItem(CLASH_KEY) ?? '[]') as Clash[]
    } catch {
      return []
    }
  },
  add(fileId: string, path: string, groups: string[][]) {
    if (groups.length === 0) return
    const now = Date.now()
    const name = (path.split('/').pop() ?? path).replace(/\.md$/i, '')
    const added = groups.map((lines, i) => ({ id: `${now}-${i}-${Math.random().toString(36).slice(2, 6)}`, fileId, name, lines, at: now }))
    try { localStorage.setItem(CLASH_KEY, JSON.stringify([...added, ...clashes.all()])) } catch { /* not kept */ }
  },
  remove(id: string) {
    try { localStorage.setItem(CLASH_KEY, JSON.stringify(clashes.all().filter((c) => c.id !== id))) } catch { /* not kept */ }
  },
}
