import {
  addTask, focus, archiveAppend, archiveFilePath, archiveRemove, cutTask, editTask, isConflictCopy, listTasks, loadTasks,
  restoreBlock, taskFilePath, toggle, addTaskInStatus, views,
} from './core'
import { Drive, DriveFile, sameName } from './drive'
import type { EditOp, EditResult, FocusIn, FocusOut, Query, Task, TaskList, ViewsIn, ViewsOut } from './types'

/** A task taken out of the note (deleted or archived), and where it was, so it can be put back. */
export interface Cut {
  title: string
  index: number
  lines: string[]
  archived: boolean
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
  private async edit(op: (text: string) => EditResult): Promise<EditResult> {
    const id = await this.file()
    for (let attempt = 0; attempt < 3; attempt++) {
      const { text, version } = await this.drive.readText(id)
      const result = op(text)
      if (!result.ok) return result
      if ((await this.drive.version(id)) !== version) continue
      await this.drive.writeText(id, result.text!)
      return result
    }
    throw new VaultError('busy', 'ไฟล์ถูกแก้อยู่ตลอด ลองใหม่อีกครั้ง')
  }

  toggle(task: Task, withSubtasks = false) {
    return this.edit((text) => toggle(text, task, withSubtasks))
  }

  add(sentence: string) {
    return this.edit((text) => addTask(text, sentence))
  }

  /** Adds a task that starts in [status] (the "+" on a Kanban column). */
  addInStatus(sentence: string, status: Task['status']) {
    return this.edit((text) => addTaskInStatus(text, sentence, status))
  }

  change(task: Task, op: EditOp) {
    return this.edit((text) => editTask(text, task, op))
  }

  /** Deletes the task with its description and subtasks; the cut is kept for undo. */
  async remove(task: Task): Promise<EditResult & { cut?: Cut }> {
    const res = await this.edit((text) => cutTask(text, task))
    return res.ok ? { ...res, cut: { title: task.title, index: res.cutIndex!, lines: res.cutLines!, archived: false } } : res
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
    const res = await this.edit((text) => restoreBlock(text, cut.index, cut.lines))
    if (res.ok && cut.archived) {
      const archive = await this.archiveNote()
      const back = archive.id ? archiveRemove(archive.text, cut.lines) : null
      if (archive.id && back !== null) await this.drive.writeText(archive.id, back)
    }
    return res
  }
}

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

  /** The Views page: Kanban, Matrix, Gantt and calendar tasks under the list's filters. */
  views(state: ViewsIn): ViewsOut {
    return views(this.fileId, taskFilePath, this.text, state)
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
