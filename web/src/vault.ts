import {
  addTask, focus, archiveAppend, archiveFilePath, legacyArchiveFilePath, legacyOmniDirPath, legacyProfileFilePath, legacyTaskFilePath, omniDirPath, profileFilePath, archiveRemove, cutTask, editTask, isConflictCopy, listTasks, loadTasks,
  restoreBlock, taskFilePath, toggle, addTaskInStatus, views,
} from './core'
import { Drive, DriveFile, FOLDER, sameName } from './drive'
import { branchStates } from './ui/projects/projectState'
import { config } from './config'
import type { EditOp, EditResult, FocusIn, FocusOut, Query, Task, TaskList, ViewsIn, ViewsOut } from './types'
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

/** The owner's Obsidian vault in Google Drive, reduced to the live task note (Omni note.md, or TaskForge.md until it is moved). */
export class Vault {
  /** [fileId] is the task note when it was found directly; otherwise it is looked up under [rootId]. */
  constructor(private drive: Drive, private rootId: string, private fileId: string | null = null) {}

  /** The task note's path in the vault as found: the new place, or the old TaskForge.md until it is moved. */
  private notePath = taskFilePath
  private located = false

  private async file(): Promise<string> {
    if (this.located && this.fileId) return this.fileId
    if (this.fileId) {
      // Found by a search or remembered: its name says which layout it is in. An old TaskForge.md gives way to
      // an Omni note.md once the owner has made one in the new place.
      const name = (await this.drive.get(this.fileId, 'name')).name
      this.notePath = sameName(name, 'TaskForge.md') ? legacyTaskFilePath : taskFilePath
      if (this.notePath === legacyTaskFilePath) {
        const fresh = await this.drive.resolve(this.rootId, taskFilePath)
        if (fresh) {
          this.fileId = fresh.id
          this.notePath = taskFilePath
          config.taskFileId = fresh.id
        }
      }
    } else {
      const fresh = await this.drive.resolve(this.rootId, taskFilePath)
      const old = fresh ? null : await this.drive.resolve(this.rootId, legacyTaskFilePath)
      const file = fresh ?? old
      if (!file) throw new VaultError('notfound', `ไม่พบ ${taskFilePath} ในโฟลเดอร์ที่เลือก`)
      this.notePath = fresh ? taskFilePath : legacyTaskFilePath
      this.fileId = file.id
    }
    this.located = true
    return this.fileId!
  }

  /** The note's name with the part that conflict copies and the archive start with. */
  private get isLegacy() {
    return this.notePath === legacyTaskFilePath
  }

  private archiveName() {
    return (this.isLegacy ? legacyArchiveFilePath : archiveFilePath).split('/').pop()!
  }

  private folderId: string | null = null

  /** The note's own folder, which also holds the archive note and any conflict copies. */
  private async folder(): Promise<string> {
    if (this.folderId) return this.folderId
    const parent = (await this.drive.get(await this.file(), 'parents')).parents?.[0]
    if (!parent) throw new VaultError('notfound', 'ไม่พบโฟลเดอร์ของไฟล์งาน')
    return (this.folderId = parent)
  }

  /**
   * The note, and the names of conflict copies a sync app left beside it (e.g. "TaskForge (conflict ...).md"):
   * those mean two versions met, so the owner should compare them before going on.
   */
  async load(): Promise<Snapshot> {
    const id = await this.file()
    const [{ text }, files] = await Promise.all([this.drive.readText(id), this.folder().then((f) => this.drive.children(f))])
    const base = this.isLegacy ? 'TaskForge' : 'Omni note'
    const conflicts = files.map((f) => f.name).filter((n) => n.startsWith(base) && isConflictCopy(n))
    return new Snapshot(id, text, loadTasks(id, this.notePath, text), conflicts, this.notePath)
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

  /** Adds a task that starts in [status] (the "+" on a Kanban column). */
  addInStatus(sentence: string, status: Task['status']) {
    return this.edit((text) => addTaskInStatus(text, sentence, status))
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
    const file = await this.drive.child(await this.folder(), this.archiveName())
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
      const archiveId = archive.id ?? (await this.drive.createText(await this.folder(), this.archiveName(), next))
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
    const file = (await this.drive.resolve(this.rootId, profileFilePath)) ?? (await this.drive.resolve(this.rootId, legacyProfileFilePath))
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

  private omniCache: { id: string; path: string }[] | null = null

  /** Creates the folders of [path] under the vault root where they are missing (names matched loosely); returns the last one. */
  async ensureFolders(path: string): Promise<string> {
    let current = this.rootId
    for (const part of path.split('/')) current = (await this.drive.child(current, part))?.id ?? (await this.drive.createFolder(current, part))
    return current
  }

  /** The Omni folders that exist: the one inside the back-office folder, and the old one at the vault root until it is moved. */
  private async omniFolders(): Promise<{ id: string; path: string }[]> {
    if (this.omniCache) return this.omniCache
    const found: { id: string; path: string }[] = []
    for (const path of [omniDirPath, legacyOmniDirPath]) {
      const folder = await this.drive.resolve(this.rootId, path)
      if (folder && folder.mimeType !== 'text/markdown') found.push({ id: folder.id, path })
    }
    return (this.omniCache = found)
  }

  /** The Omni folder new notes go in; made (in the back-office folder) when [create] is set and there is none. */
  private async omni(create = false): Promise<string | null> {
    const first = (await this.omniFolders())[0]
    if (first) return first.id
    if (!create) return null
    const id = await this.ensureFolders(omniDirPath)
    this.omniCache = [{ id, path: omniDirPath }]
    return id
  }

  /** The list notes (Bucket list, Watch list...) in the Omni folder (or the old one), with their text. */
  async lists(): Promise<ListNote[]> {
    const read: ListNote[] = []
    const seen = new Set<string>()
    for (const folder of await this.omniFolders()) {
      const files = (await this.drive.children(folder.id)).filter((f) => f.mimeType !== FOLDER && /\.md$/i.test(f.name) && !isConflictCopy(f.name) && !seen.has(f.name))
      files.forEach((f) => seen.add(f.name))
      const notes = await Promise.all(files.map(async (f) => ({ id: f.id, path: `${folder.path}/${f.name}`, text: (await this.drive.readText(f.id)).text })))
      read.push(...notes)
    }
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

  constructor(readonly fileId: string, private text: string, readonly tasks: Task[], readonly conflicts: string[] = [], readonly path: string = taskFilePath) {
    this.byKey = new Map(tasks.map((t) => [t.key, t]))
  }

  list(query: Query): TaskList {
    return listTasks(this.fileId, this.path, this.text, { ...query, branches: branchStates() })
  }

  focus(state: FocusIn): FocusOut {
    return focus(this.fileId, this.path, this.text, { ...state, branches: branchStates() })
  }

  /** The Views page: Kanban, Matrix, Gantt and calendar tasks under the list's filters. */
  views(state: ViewsIn): ViewsOut {
    return views(this.fileId, this.path, this.text, { ...state, query: { ...state.query, branches: branchStates() } })
  }

  /** The Projects page: projects, branches and lists, from this note, the list notes and what this device chose. */
  projects(notes: ListNote[], state: ProjectsIn): ProjectsOut {
    return projects(this.fileId, this.path, this.text, notes.map((n) => ({ key: n.id, path: n.path, text: n.text })), state)
  }

  /** What the shared code does to the branch states of a project, from the tasks as they stand. */
  branchChange(states: string[], op: BranchOp): BranchResult {
    return branchChange(this.fileId, this.path, this.text, states, op)
  }
}

/** A vault found in Drive by its task note: where the note is, and the vault folder above it. */
export interface FoundVault {
  fileId: string
  rootId: string
  rootName: string
  /** The folders from the vault down to the note, for telling copies apart. */
  path: string
}

/**
 * Looks for Omni note.md (and the old TaskForge.md) anywhere in Drive and climbs from each to its vault folder
 * (the folder that holds `📁 Folder/หลังบ้าน/Omni/`, or `📁 Folder/หลังบ้าน/TaskForge/` for the old one). Copies outside
 * that layout are still offered, under their own folder.
 */
export async function findVaults(drive: Drive): Promise<FoundVault[]> {
  const found = (
    await Promise.all(
      [taskFilePath, legacyTaskFilePath].map(async (layout) => {
        const name = layout.split('/').pop()!
        const folders = layout.split('/').slice(0, -1)
        const files = await drive.findFiles(name)
        return Promise.all(
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
            const inLayout = chain.length === folders.length + 1 && folders.every((f, i) => sameName(chain[i + 1].name, f))
            const root = inLayout ? chain[0] : chain[chain.length - 1]
            if (!root) return null
            return { fileId: file.id, rootId: root.id, rootName: root.name, path: chain.map((c) => c.name).join(' / '), inLayout, current: layout === taskFilePath }
          }),
        )
      }),
    )
  ).flat()
  // Notes in the expected layout first, the new place before the old: those are live vaults, the rest are copies or backups.
  return found
    .filter((v): v is FoundVault & { inLayout: boolean; current: boolean } => v !== null)
    .sort((a, b) => Number(b.inLayout) - Number(a.inLayout) || Number(b.current) - Number(a.current))
    .map(({ inLayout: _, current: __, ...v }) => v)
}
