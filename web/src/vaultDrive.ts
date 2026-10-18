// The vault itself, as the app's Drive. Inside Obsidian the app reads and writes the notes on the device (Obsidian
// Sync takes them to the other devices), so this answers every question the app asks Google Drive from the vault:
// a file's id is its path in the vault, a version is a fingerprint of its text, and nothing is ever offline.
// Everything above it (the vault, the note index, the settings file, the behavior log) works unchanged.
import { Drive, DriveError, FOLDER, sameName, type DriveChange, type DriveFile, type Versioned, type Written } from './drive'

/** One item of a folder in the vault. */
export interface VaultEntry {
  /** The full path in the vault ('📁 Folder/หลังบ้าน/Omni/Omni note.md'). */
  path: string
  name: string
  folder: boolean
  /** Last change, in milliseconds (files only). */
  mtime?: number
  size?: number
}

/** The little of Obsidian's vault the app needs (obsidian/src/vaultFs.ts; an in-memory one in the tests). */
export interface VaultFs {
  /** The vault's name, as Obsidian shows it. */
  name: string
  /** What is directly inside the folder at [path] ('' is the vault itself); null when there is no such folder. */
  list(path: string): Promise<VaultEntry[] | null>
  /** The file or folder at [path], or null. */
  stat(path: string): Promise<VaultEntry | null>
  read(path: string): Promise<string>
  /** Replaces the text of the file at [path]. */
  write(path: string, text: string): Promise<void>
  /** A new text file; fails when one is there. */
  create(path: string, text: string): Promise<void>
  readBinary(path: string): Promise<ArrayBuffer>
  createBinary(path: string, data: ArrayBuffer): Promise<void>
  mkdir(path: string): Promise<void>
  /** Moves the file to the trash the owner chose in Obsidian (the system's or the vault's .trash). */
  trash(path: string): Promise<void>
}

/** A short fingerprint of a text: two texts with the same one are taken as the same version. */
export function fingerprint(text: string): string {
  let a = 5381
  let b = 52711
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i)
    a = ((a << 5) + a + c) | 0
    b = ((b << 5) + b) ^ c
  }
  return `${text.length}.${(a >>> 0).toString(36)}.${(b >>> 0).toString(36)}`
}

const MIME: Record<string, string> = {
  md: 'text/markdown', json: 'application/json', webp: 'image/webp', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg',
  gif: 'image/gif', svg: 'image/svg+xml', txt: 'text/plain',
}
const mimeOf = (name: string) => MIME[name.slice(name.lastIndexOf('.') + 1).toLowerCase()] ?? 'application/octet-stream'

const parentPath = (path: string) => (path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '')

export class VaultDrive extends Drive {
  /** Paths this app wrote, with when: Obsidian tells about those changes too, and they are not news to the app. */
  private own = new Map<string, number>()

  /** [rootId] stands for the vault itself; it is unique to the vault, so what the app keeps per vault stays apart. */
  constructor(private fs: VaultFs, readonly rootId: string) {
    super(() => { throw new DriveError(401, 'no Google sign-in inside Obsidian') })
  }

  private pathOf(id: string): string {
    return id === this.rootId ? '' : id
  }

  private idOf(path: string): string {
    return path === '' ? this.rootId : path
  }

  private join(parentId: string, name: string): string {
    const parent = this.pathOf(parentId)
    return parent ? `${parent}/${name}` : name
  }

  private file(e: VaultEntry): DriveFile {
    return {
      id: e.path,
      name: e.name,
      mimeType: e.folder ? FOLDER : mimeOf(e.name),
      parents: [this.idOf(parentPath(e.path))],
      // Enough to tell that a file changed for the note index; reads and writes compare the text's own fingerprint.
      version: e.folder ? '' : `${e.mtime ?? 0}.${e.size ?? 0}`,
      modifiedTime: e.mtime ? new Date(e.mtime).toISOString() : undefined,
      trashed: false,
    }
  }

  private async entries(parentId: string): Promise<VaultEntry[]> {
    return (await this.fs.list(this.pathOf(parentId))) ?? []
  }

  private async must(id: string): Promise<VaultEntry> {
    const e = await this.fs.stat(this.pathOf(id))
    if (!e) throw new DriveError(404, `not in the vault: ${id}`)
    return e
  }

  /** Remembers that the app itself just changed [path]. */
  private mine(path: string) {
    this.own.set(path, Date.now())
  }

  /** Whether a change Obsidian reports at [path] is one this app made in the last few seconds. */
  isOwnWrite(path: string, withinMs = 3000): boolean {
    const at = this.own.get(path)
    return at !== undefined && Date.now() - at < withinMs
  }

  /* ---------- Google's own APIs: not inside Obsidian ---------- */

  override async json(): Promise<any> {
    throw new DriveError(403, 'Google is not connected inside Obsidian')
  }

  override async postJson(): Promise<any> {
    throw new DriveError(403, 'Google is not connected inside Obsidian')
  }

  /* ---------- Finding ---------- */

  /** Every file and folder of the vault, top down. */
  private async everything(): Promise<VaultEntry[]> {
    const out: VaultEntry[] = []
    let level = ['']
    while (level.length) {
      const next: string[] = []
      for (const folder of level) {
        for (const e of (await this.fs.list(folder)) ?? []) {
          out.push(e)
          if (e.folder) next.push(e.path)
        }
      }
      level = next
    }
    return out
  }

  override async findFiles(name: string): Promise<DriveFile[]> {
    return (await this.everything()).filter((e) => !e.folder && sameName(e.name, name)).map((e) => this.file(e))
  }

  override async findFolders(name: string): Promise<DriveFile[]> {
    return (await this.everything()).filter((e) => e.folder && sameName(e.name, name)).map((e) => this.file(e))
  }

  override async folders(parentId: string): Promise<DriveFile[]> {
    const found = (await this.entries(parentId)).filter((e) => e.folder).map((e) => this.file(e))
    return found.sort((a, b) => a.name.localeCompare(b.name, 'th'))
  }

  override async get(id: string): Promise<DriveFile> {
    if (id === this.rootId) return { id, name: this.fs.name, mimeType: FOLDER, parents: [], version: '', trashed: false }
    return this.file(await this.must(id))
  }

  override async child(parentId: string, name: string): Promise<DriveFile | null> {
    const found = (await this.entries(parentId)).find((e) => sameName(e.name, name))
    return found ? this.file(found) : null
  }

  override async children(parentId: string): Promise<DriveFile[]> {
    return (await this.entries(parentId)).map((e) => this.file(e))
  }

  override async childrenOfMany(parentIds: string[]): Promise<DriveFile[]> {
    return (await Promise.all(parentIds.map((id) => this.children(id)))).flat()
  }

  /* ---------- Reading ---------- */

  override async readText(id: string): Promise<Versioned> {
    await this.must(id)
    const text = await this.fs.read(this.pathOf(id))
    // No revisions in a vault: a write is checked against the text just before it is made (see safeWrite.ts).
    return { text, version: fingerprint(text), revision: '' }
  }

  override async media(id: string): Promise<string> {
    return (await this.readText(id)).text
  }

  override async version(id: string): Promise<string> {
    return (await this.readText(id)).version
  }

  override async blob(id: string): Promise<Blob> {
    const e = await this.must(id)
    return new Blob([await this.fs.readBinary(e.path)], { type: mimeOf(e.name) })
  }

  /* ---------- Writing ---------- */

  override async writeText(id: string, text: string): Promise<Written> {
    const path = this.pathOf(id)
    await this.must(id)
    this.mine(path)
    await this.fs.write(path, text)
    return { version: fingerprint(text), revision: '' }
  }

  override async createText(parentId: string, name: string, text: string): Promise<string> {
    const path = this.join(parentId, name)
    this.mine(path)
    await this.fs.create(path, text)
    return path
  }

  override async createBlob(parentId: string, name: string, blob: Blob): Promise<string> {
    const path = this.join(parentId, name)
    this.mine(path)
    await this.fs.createBinary(path, await blob.arrayBuffer())
    return path
  }

  override async createFolder(parentId: string, name: string): Promise<string> {
    const path = this.join(parentId, name)
    if (!(await this.fs.stat(path))) await this.fs.mkdir(path)
    return path
  }

  override async trash(id: string): Promise<void> {
    const path = this.pathOf(id)
    this.mine(path)
    await this.fs.trash(path)
  }

  /* ---------- What Drive keeps that a vault does not ---------- */

  override async revisionBefore(): Promise<string | null> {
    return null
  }

  override async revisionText(): Promise<string> {
    throw new DriveError(404, 'a vault keeps no revisions')
  }

  /** The note index (noteIndex.ts) walks the vault again on every refresh: listing it is quick on the device. */
  override async startPageToken(): Promise<string> {
    return 'vault'
  }

  override async changes(): Promise<{ changes: DriveChange[]; token: string }> {
    throw new DriveError(410, 'a vault has no change log: walk it')
  }
}
