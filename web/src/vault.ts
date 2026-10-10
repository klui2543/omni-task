import {
  addTask, focus, archiveAppend, archiveFilePath, archiveRemove, cutTask, editTask, isConflictCopy, listTasks, loadTasks,
  restoreBlock, taskFilePath, toggle,
} from './core'
import { Drive, DriveFile, FOLDER, sameName } from './drive'
import type { EditOp, EditResult, FocusIn, FocusOut, Query, Task, TaskList } from './types'
import {
  addListItem, addTagged, branchChange, chainTasks, createListNote, includeInList, listStarters, projects, renameTag, updateListNote,
} from './core'
import type { BranchOp, BranchResult, ListNote, ProjectEditResult, ProjectsIn, ProjectsOut } from './types'

/** A task taken out of the note (deleted or archived), and where it was, so it can be put back. */
export interface Cut {
  title: string
  index: number
  lines: string[]
  archived: boolean
  /** The note the block was cut from, when it was not TaskForge (an item of a list note). */
  fileId?: string
}

export class VaultError extends Error {
  constructor(public code: 'notfound' | 'busy', message: string) {
    super(message)
  }
}

/** The owner's Obsidian vault in Google Drive, reduced to the live TaskForge note. */
export class Vault {
  /** [fileId] is the TaskForge note when it was found directly; otherwise it is looked up under [rootId]. */
  constructor(private drive: Drive, private rootId: string, private fileId: string | null = null) {}

  private async file(): Promise<string> {
    if (this.fileId) return this.fileId
    const file = await this.drive.resolve(this.rootId, taskFilePath)
    if (!file) throw new VaultError('notfound', `ไม่พบ ${taskFilePath} ในโฟลเดอร์ที่เลือก`)
    return (this.fileId = file.id)
  }

  private folderId: string | null = null

  /** The TaskForge folder, which also holds the archive note and any conflict copies. */
  private async folder(): Promise<string> {
    if (this.folderId) return this.folderId
    const parent = (await this.drive.get(await this.file(), 'parents')).parents?.[0]
    if (!parent) throw new VaultError('notfound', 'ไม่พบโฟลเดอร์ของ TaskForge.md')
    return (this.folderId = parent)
  }

  /**
   * The note, and the names of conflict copies a sync app left beside it (e.g. "TaskForge (conflict ...).md"):
   * those mean two versions met, so the owner should compare them before going on.
   */
  async load(): Promise<Snapshot> {
    const id = await this.file()
    const [{ text }, files] = await Promise.all([this.drive.readText(id), this.folder().then((f) => this.drive.children(f))])
    const conflicts = files.map((f) => f.name).filter((n) => n.startsWith('TaskForge') && isConflictCopy(n))
    return new Snapshot(id, text, loadTasks(id, taskFilePath, text), conflicts)
  }

  /**
   * Reads the note, lets the shared logic edit it, and writes it back. The note's version is checked again just
   * before writing: if the sync app or Obsidian changed it meanwhile, the edit starts over on the new text,
   * so a change made elsewhere is never overwritten.
   */
  private async edit(op: (text: string) => EditResult, fileId?: string): Promise<EditResult> {
    // A task of a list note carries that note's id in its key, so it is edited in its own note.
    const id = fileId ?? (await this.file())
    for (let attempt = 0; attempt < 3; attempt++) {
      const { text, version } = await this.drive.readText(id)
      const result = op(text)
      if (!result.ok) return result
      // Nothing to change (e.g. a rename that touched no line of this note): nothing is written.
      if (result.text === text) return result
      if ((await this.drive.version(id)) !== version) continue
      await this.drive.writeText(id, result.text!)
      return result
    }
    throw new VaultError('busy', 'ไฟล์ถูกแก้อยู่ตลอด ลองใหม่อีกครั้ง')
  }

  toggle(task: Task, withSubtasks = false) {
    return this.edit((text) => toggle(text, task, withSubtasks), noteOf(task))
  }

  add(sentence: string) {
    return this.edit((text) => addTask(text, sentence))
  }

  change(task: Task, op: EditOp) {
    return this.edit((text) => editTask(text, task, op), noteOf(task))
  }

  /** Deletes the task with its description and subtasks; the cut is kept for undo. */
  async remove(task: Task): Promise<EditResult & { cut?: Cut }> {
    const res = await this.edit((text) => cutTask(text, task), noteOf(task))
    return res.ok ? { ...res, cut: { title: task.title, index: res.cutIndex!, lines: res.cutLines!, archived: false, fileId: noteOf(task) } } : res
  }

  private async archiveNote(): Promise<{ id: string | null; text: string; version: string }> {
    const name = archiveFilePath.split('/').pop()!
    const file = await this.drive.child(await this.folder(), name)
    if (!file) return { id: null, text: '', version: '' }
    return { id: file.id, ...(await this.drive.readText(file.id)) }
  }

  /**
   * Moves a finished task with its whole block to the archive note beside TaskForge, under this month's
   * heading, as Android does. The archive is written first; if the live note changed meanwhile, the archive
   * is put back as it was and the move starts over on the new text.
   */
  async archive(task: Task): Promise<EditResult & { cut?: Cut }> {
    const id = await this.file()
    for (let attempt = 0; attempt < 3; attempt++) {
      const { text, version } = await this.drive.readText(id)
      const res = cutTask(text, task)
      if (!res.ok) return res
      const archive = await this.archiveNote()
      const next = archiveAppend(archive.text, res.cutLines!)
      const archiveId = archive.id ?? (await this.drive.createText(await this.folder(), archiveFilePath.split('/').pop()!, next))
      if (archive.id) await this.drive.writeText(archive.id, next)
      if ((await this.drive.version(id)) !== version) {
        if (archive.id) await this.drive.writeText(archive.id, archive.text)
        else await this.drive.trash(archiveId)
        continue
      }
      await this.drive.writeText(id, res.text!)
      return { ...res, cut: { title: task.title, index: res.cutIndex!, lines: res.cutLines!, archived: true } }
    }
    throw new VaultError('busy', 'ไฟล์ถูกแก้อยู่ตลอด ลองใหม่อีกครั้ง')
  }

  /** The profile note the assistant keeps (wake and sleep times...), or null when the vault has none. */
  async profile(): Promise<string | null> {
    const file = await this.drive.resolve(this.rootId, 'Omni/โปรไฟล์.md')
    return file ? (await this.drive.readText(file.id)).text : null
  }

  /** Puts a deleted or archived task back where it was (and takes it out of the archive note). */
  async undo(cut: Cut): Promise<EditResult> {
    const res = await this.edit((text) => restoreBlock(text, cut.index, cut.lines), cut.fileId)
    if (res.ok && cut.archived) {
      const archive = await this.archiveNote()
      const back = archive.id ? archiveRemove(archive.text, cut.lines) : null
      if (archive.id && back !== null) await this.drive.writeText(archive.id, back)
    }
    return res
  }

  /* ---------- Projects ---------- */

  private omniId: string | null = null

  /** The vault's Omni folder, which holds the list notes; made when [create] is set and it is missing. */
  private async omni(create = false): Promise<string | null> {
    if (this.omniId) return this.omniId
    const found = await this.drive.child(this.rootId, 'Omni')
    if (found) return (this.omniId = found.id)
    return create ? (this.omniId = await this.drive.createFolder(this.rootId, 'Omni')) : null
  }

  /** The list notes (Bucket list, Watch list...) in the Omni folder, with their text. */
  async lists(): Promise<ListNote[]> {
    const folder = await this.omni()
    if (!folder) return []
    const files = (await this.drive.children(folder)).filter((f) => f.mimeType !== FOLDER && /\.md$/i.test(f.name) && !isConflictCopy(f.name))
    const read = await Promise.all(files.map(async (f) => ({ id: f.id, path: `Omni/${f.name}`, text: (await this.drive.readText(f.id)).text })))
    // The shared code decides what is a list; this only leaves out the notes that cannot be one.
    return read.filter((n) => n.text.startsWith('---') && n.text.includes('omni-list'))
  }

  /** Writes the starter lists (Bucket list, Watch list) that are not in the Omni folder yet. */
  async seedLists(): Promise<void> {
    const folder = (await this.omni(true))!
    for (const s of listStarters()) {
      const name = s.path.split('/').pop()!
      if (!(await this.drive.child(folder, name))) await this.drive.createText(folder, name, s.text)
    }
  }

  /** A new list note in the Omni folder, unless one with that name is there already. */
  async createList(name: string, icon: string, categories: string[]): Promise<ProjectEditResult> {
    const made = createListNote(name, icon, categories)
    if (!made.ok) return made
    const folder = (await this.omni(true))!
    const file = made.path!.split('/').pop()!
    if (await this.drive.child(folder, file)) return { ok: false, error: 'exists' }
    await this.drive.createText(folder, file, made.text!)
    return made
  }

  private editProject(op: (text: string) => ProjectEditResult, fileId?: string): Promise<ProjectEditResult> {
    return this.edit(op as (text: string) => EditResult, fileId) as Promise<ProjectEditResult>
  }

  updateList(note: ListNote, icon: string, categories: string[]) {
    return this.editProject((text) => updateListNote(text, note.path, icon, categories), note.id)
  }

  addListItem(note: ListNote, title: string, category: string | null) {
    return this.editProject((text) => addListItem(text, note.path, title, category), note.id)
  }

  /** Tags tasks of TaskForge with a list's tag (and category), so they show in the list where they are. */
  includeInList(tasks: Task[], tag: string, category: string | null) {
    return this.editProject((text) => includeInList(text, tasks, tag, category))
  }

  /** A task in a project or branch: the title is read like quick add and gets the tag. */
  addTagged(tag: string, title: string) {
    return this.editProject((text) => addTagged(text, tag, title))
  }

  /** "Do in order" on or off for the project's open tasks, written as 🆔 and ⛔ in the note. */
  chain(ordered: Task[], on: boolean) {
    return this.editProject((text) => chainTasks(text, ordered, on))
  }

  /**
   * Renames a project or branch tag in TaskForge and in every list note, each read again and checked before it
   * is written. Answers how many lines changed.
   */
  async renameTag(old: string, name: string): Promise<ProjectEditResult> {
    let changed = 0
    const main = await this.editProject((text) => {
      const r = renameTag(text, old, name)
      changed = r.changed ?? 0
      return r
    })
    if (!main.ok) return main
    let total = changed
    for (const note of await this.lists()) {
      await this.editProject((text) => {
        const r = renameTag(text, old, name)
        changed = r.changed ?? 0
        return r
      }, note.id)
      total += changed
    }
    return { ...main, changed: total }
  }
}

/** The id of the note a task lives in: its key is the note's id and the line (see Task.key). */
const noteOf = (task: Task) => task.key.slice(0, task.key.lastIndexOf('#'))

/** The note as last read: its tasks, and the text the shared logic groups and sorts them from. */
export class Snapshot {
  readonly byKey: Map<string, Task>

  constructor(private fileId: string, private text: string, readonly tasks: Task[], readonly conflicts: string[] = []) {
    this.byKey = new Map(tasks.map((t) => [t.key, t]))
  }

  list(query: Query): TaskList {
    return listTasks(this.fileId, taskFilePath, this.text, query)
  }

  focus(state: FocusIn): FocusOut {
    return focus(this.fileId, taskFilePath, this.text, state)
  }

  /** The Projects page: projects, branches and lists, from this note, the list notes and what this device chose. */
  projects(notes: ListNote[], state: ProjectsIn): ProjectsOut {
    return projects(this.fileId, taskFilePath, this.text, notes.map((n) => ({ key: n.id, path: n.path, text: n.text })), state)
  }

  /** What the shared code does to the branch states of a project, from the tasks as they stand. */
  branchChange(states: string[], op: BranchOp): BranchResult {
    return branchChange(this.fileId, taskFilePath, this.text, states, op)
  }
}

/** A vault found in Drive by its TaskForge note: where the note is, and the vault folder above it. */
export interface FoundVault {
  fileId: string
  rootId: string
  rootName: string
  /** The folders from the vault down to the note, for telling copies apart. */
  path: string
}

/**
 * Looks for TaskForge.md anywhere in Drive and climbs from each to its vault folder (the folder that holds
 * `📁 Folder/หลังบ้าน/TaskForge/`). Copies outside that layout are still offered, under their own folder.
 */
export async function findVaults(drive: Drive): Promise<FoundVault[]> {
  const name = taskFilePath.split('/').pop()!
  const folders = taskFilePath.split('/').slice(0, -1)
  const files = await drive.findFiles(name)
  const found = await Promise.all(
    files.map(async (file) => {
      const chain: DriveFile[] = []
      let parentId = file.parents?.[0]
      // Up through the expected folders and one more, the vault itself.
      for (let i = 0; i <= folders.length && parentId; i++) {
        const parent = await drive.get(parentId, 'id,name,parents').catch(() => null)
        if (!parent) break
        chain.unshift(parent)
        parentId = parent.parents?.[0]
      }
      const inLayout =
        chain.length === folders.length + 1 && folders.every((f, i) => sameName(chain[i + 1].name, f))
      const root = inLayout ? chain[0] : chain[chain.length - 1]
      if (!root) return null
      return { fileId: file.id, rootId: root.id, rootName: root.name, path: chain.map((c) => c.name).join(' / '), inLayout }
    }),
  )
  // Notes in the expected layout first: those are live vaults, the rest are copies or backups.
  return found
    .filter((v): v is FoundVault & { inLayout: boolean } => v !== null)
    .sort((a, b) => Number(b.inLayout) - Number(a.inLayout))
    .map(({ inLayout: _, ...v }) => v)
}
