import { addTask, loadTasks, taskFilePath, toggle } from './core'
import { Drive, DriveFile, sameName } from './drive'
import type { EditResult, Task } from './types'

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

  async load(): Promise<Task[]> {
    const id = await this.file()
    const { text } = await this.drive.readText(id)
    return loadTasks(id, taskFilePath, text)
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
