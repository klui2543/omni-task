// Every note of the vault, as Android reads them: the folders are walked once, then kept up to date from Drive's
// change log, so a reload asks Drive one question and reads only the notes that changed. What was read is kept on
// the device (IndexedDB) for the next visit.
import { WebNotesApi } from './kotlin/OmniTask-shared.mjs'
import { isConflictCopy } from './core'
import { AuthExpired, Drive, DriveChange, FOLDER, sameName } from './drive'

const api = WebNotesApi.getInstance()

/** The file key that makes the shared page builders read a JSON list of notes (see WebNotes.MANY). */
export const MANY: string = api.many()
const ATTACHMENTS: string = api.attachmentDir()

/** Whether a note is read for tasks: not an archive, not a sync app's conflict copy. */
export const isRead = (path: string): boolean => api.isRead(path)

/** One note as the shared code reads it: its Drive id (the start of its tasks' keys), vault path and text. */
export interface NoteText {
  key: string
  path: string
  text: string
}

interface Entry {
  path: string
  version: string
  /** Undefined until read; null when the note has no checkbox line, so there is nothing to keep. */
  text?: string | null
}

const HAS_TASK = /^[ \t]*(?:[-*+]|\d+[.)])[ \t]+\[.\]/m
const MD = /\.md$/i

/** Two vault paths that are the same to a person (see sameName). */
export const samePath = (a: string, b: string) => {
  const x = a.split('/')
  const y = b.split('/')
  return x.length === y.length && x.every((s, i) => sameName(s, y[i]))
}

const join = (folder: string, name: string) => (folder ? `${folder}/${name}` : name)

/** Runs [fn] over [items], at most [limit] at a time, answering in order. */
async function pool<T, R>(items: T[], limit: number, fn: (t: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length)
  let next = 0
  const worker = async () => {
    while (next < items.length) {
      const i = next++
      out[i] = await fn(items[i])
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
  return out
}

const chunks = <T>(items: T[], size: number): T[][] =>
  Array.from({ length: Math.ceil(items.length / size) }, (_, i) => items.slice(i * size, i * size + size))

/* ---------- Kept on the device ---------- */

interface Saved {
  token: string
  folders: [string, string][]
  files: [string, Entry][]
}

const DB = 'omni-notes'
const STORE = 'index'

function openDb(): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    try {
      const req = indexedDB.open(DB, 1)
      req.onupgradeneeded = () => req.result.createObjectStore(STORE)
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => resolve(null)
      req.onblocked = () => resolve(null)
    } catch {
      resolve(null)
    }
  })
}

async function readSaved(rootId: string): Promise<Saved | null> {
  const db = await openDb()
  if (!db) return null
  return new Promise((resolve) => {
    try {
      const req = db.transaction(STORE).objectStore(STORE).get(rootId)
      req.onsuccess = () => resolve((req.result as Saved | undefined) ?? null)
      req.onerror = () => resolve(null)
    } catch {
      resolve(null)
    }
  })
}

async function writeSaved(rootId: string, saved: Saved | null): Promise<void> {
  const db = await openDb()
  if (!db) return
  await new Promise<void>((resolve) => {
    try {
      const tx = db.transaction(STORE, 'readwrite')
      if (saved) tx.objectStore(STORE).put(saved, rootId)
      else tx.objectStore(STORE).clear()
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
      tx.onabort = () => resolve()
    } catch {
      resolve()
    }
  })
}

/** Forgets every vault's notes kept on this device (on sign out). */
export const clearNoteIndex = () => writeSaved('', null)

/* ---------- The index ---------- */

export class NoteIndex {
  /** Folder id to its vault path ('' for the vault itself). Hidden folders and Attachments are not in it. */
  private folders = new Map<string, string>()
  private files = new Map<string, Entry>()
  /** Drive's change log position; null until the vault has been walked once. */
  private token: string | null = null
  private restored: Promise<void> | null = null
  private queue: Promise<void> = Promise.resolve()
  /** A note read elsewhere (the task note, always read fresh), so it is not fetched here too. */
  skip: string | null = null

  constructor(private drive: Drive, private rootId: string) {}

  private restore(): Promise<void> {
    return (this.restored ??= readSaved(this.rootId).then((s) => {
      if (!s || this.token) return
      this.token = s.token
      this.folders = new Map(s.folders)
      this.files = new Map(s.files)
    }))
  }

  /** Whether the vault was walked before (in this visit or an earlier one), so [refresh] only reads what changed. */
  async ready(): Promise<boolean> {
    await this.restore()
    return this.token !== null
  }

  /** Brings the index up to date with Drive. Calls run one after another, each seeing what the one before saw. */
  refresh(): Promise<void> {
    const run = this.queue.then(() => this.update())
    this.queue = run.catch(() => undefined)
    return run
  }

  private async update(): Promise<void> {
    await this.restore()
    let dirty = false
    if (this.token) {
      try {
        const { changes, token } = await this.drive.changes(this.token)
        const outcome = this.apply(changes)
        if (outcome === 'walk') await this.walk()
        dirty = outcome !== 'same' || token !== this.token
        this.token = token
      } catch (e) {
        if (e instanceof AuthExpired) throw e
        // The change log cannot be read (an old position, say): walk the vault again.
        await this.walkFromNow()
        dirty = true
      }
    } else {
      await this.walkFromNow()
      dirty = true
    }
    if ((await this.readTexts()) > 0) dirty = true
    if (dirty) await writeSaved(this.rootId, { token: this.token!, folders: [...this.folders], files: [...this.files] })
  }

  private async walkFromNow() {
    // The position is taken first, so a change made during the walk is still seen next time.
    const token = await this.drive.startPageToken()
    await this.walk()
    this.token = token
  }

  /** Lists the vault level by level, many folders per question; a note's text is kept when its version has not moved. */
  private async walk() {
    const folders = new Map<string, string>([[this.rootId, '']])
    const files = new Map<string, Entry>()
    let level = [this.rootId]
    while (level.length) {
      const found = (await pool(chunks(level, 40), 4, (ids) => this.drive.childrenOfMany(ids))).flat()
      const next: string[] = []
      for (const f of found) {
        // .obsidian, .trash and other hidden folders hold no live tasks.
        if (f.name.startsWith('.')) continue
        const parent = f.parents?.find((p) => folders.has(p))
        if (parent === undefined) continue
        const path = join(folders.get(parent)!, f.name)
        if (f.mimeType === FOLDER) {
          if (folders.has(f.id) || samePath(path, ATTACHMENTS)) continue
          folders.set(f.id, path)
          next.push(f.id)
        } else if (MD.test(f.name)) {
          files.set(f.id, this.entry(f.id, path, f.version ?? ''))
        }
      }
      level = next
    }
    this.folders = folders
    this.files = files
  }

  private entry(id: string, path: string, version: string): Entry {
    const old = this.files.get(id)
    return { path, version, text: old && old.version === version && version ? old.text : undefined }
  }

  /** Takes in Drive's changes: 'same' when none touch the vault, 'walk' when its folders changed. */
  private apply(changes: DriveChange[]): 'same' | 'changed' | 'walk' {
    let outcome: 'same' | 'changed' = 'same'
    for (const c of changes) {
      const f = c.file
      if (c.removed || !f || f.trashed) {
        if (this.folders.has(c.fileId)) return 'walk'
        if (this.files.delete(c.fileId)) outcome = 'changed'
        continue
      }
      const parent = f.parents?.find((p) => this.folders.has(p))
      if (f.mimeType === FOLDER) {
        const known = this.folders.get(f.id)
        // A folder made, moved or renamed inside the vault (or moved out of it): its notes move with it.
        if (known !== undefined ? parent === undefined || known !== join(this.folders.get(parent)!, f.name) : parent !== undefined) return 'walk'
        continue
      }
      if (parent === undefined || f.name.startsWith('.') || !MD.test(f.name)) {
        if (this.files.delete(f.id)) outcome = 'changed'
        continue
      }
      const next = this.entry(f.id, join(this.folders.get(parent)!, f.name), f.version ?? '')
      const old = this.files.get(f.id)
      if (!old || old.path !== next.path || old.version !== next.version) outcome = 'changed'
      this.files.set(f.id, next)
    }
    return outcome
  }

  /** Reads the notes not read yet, a few at a time; answers how many were read. */
  private async readTexts(): Promise<number> {
    const todo = [...this.files].filter(([id, e]) => e.text === undefined && id !== this.skip && isRead(e.path))
    await pool(todo, 6, async ([id, e]) => {
      try {
        const text = await this.drive.media(id)
        e.text = HAS_TASK.test(text) ? text : null
      } catch (err) {
        if (err instanceof AuthExpired) throw err
        // A file Drive will not hand over (not text after all) holds no tasks for us.
        e.text = null
      }
    })
    return todo.length
  }

  /** The notes with tasks, except the one read elsewhere, archives and conflict copies. */
  notes(): NoteText[] {
    const out: NoteText[] = []
    for (const [id, e] of this.files) {
      if (e.text && id !== this.skip && isRead(e.path)) out.push({ key: id, path: e.path, text: e.text })
    }
    return out.sort((a, b) => a.path.localeCompare(b.path))
  }

  /** The names of the conflict copies a sync app left anywhere in the vault. */
  conflicts(): string[] {
    return [...this.files.values()].map((e) => e.path.split('/').pop()!).filter(isConflictCopy)
  }
}
