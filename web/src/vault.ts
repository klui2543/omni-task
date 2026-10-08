import { addTask, loadTasks, taskFilePath, toggle } from './core'
import { Drive } from './drive'
import type { EditResult, Task } from './types'

export class VaultError extends Error {
  constructor(public code: 'notfound' | 'busy', message: string) {
    super(message)
  }
}

/** The owner's Obsidian vault in Google Drive, reduced to the live TaskForge note. */
export class Vault {
  private fileId: string | null = null

  constructor(private drive: Drive, private rootId: string) {}

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
