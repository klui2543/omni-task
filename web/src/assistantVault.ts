// What the Assistant and Settings pages add to the vault: the profile note, task lines added or dated in one write,
// and the archive sweep. Added from outside the classes (declaration merging) so vault.ts is left as it is; import
// this file for its effect before calling any of the methods below.
import { legacyProfileFilePath, omniDirPath, profileFilePath } from './core'
import { Drive } from './drive'
import type { EditResult } from './types'
import { Snapshot, Vault, VaultError } from './vault'

declare module './drive' {
  interface Drive {
    /** A new folder in [parentId]; returns its id. */
    createFolder(parentId: string, name: string): Promise<string>
  }
}

declare module './vault' {
  interface Snapshot {
    /** What the shared assistant logic reads: the task note's text, or every note with tasks as a JSON list. */
    noteText(): string
    /** The task note's key (the start of every task key in it), or the key that says "every note". */
    noteKey(): string
  }

  interface Vault {
    /**
     * Changes the profile note: the note is read again, [change] turns its text (null when the vault has none yet)
     * into the new text, and it is written back, with the note and its Omni folder made first when they are missing.
     * Returns the new text. Starts over when the note changed meanwhile.
     */
    changeProfile(change: (text: string | null) => string): Promise<string>
    /** Applies a change of the task note's text (or the note [fileId]) as one safe write (read again, version checked). */
    editNote<T extends EditResult>(op: (text: string) => T, fileId?: string): Promise<T>
    /** Moves finished tasks to the archive note, as [sweepOp] decides; returns the titles moved. */
    sweep(sweepOp: (live: string, archive: string) => { text: string; archive: string; titles: string[] } | null): Promise<string[]>
  }
}

Drive.prototype.createFolder = async function (this: any, parentId: string, name: string): Promise<string> {
  const res = await this.call('https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, parents: [parentId], mimeType: 'application/vnd.google-apps.folder' }),
  })
  return (await res.json()).id
}

Snapshot.prototype.noteText = function (this: Snapshot) {
  return this.source().text
}
Snapshot.prototype.noteKey = function (this: Snapshot) {
  return this.source().key
}

Vault.prototype.changeProfile = async function (this: any, change: (text: string | null) => string): Promise<string> {
  for (let attempt = 0; attempt < 3; attempt++) {
    // The profile in the Omni folder, or the old place at the vault root until the folder is moved.
    const file = (await this.drive.resolve(this.rootId, profileFilePath)) ?? (await this.drive.resolve(this.rootId, legacyProfileFilePath))
    if (!file) {
      const text = change(null)
      await this.drive.createText(await this.ensureFolders(omniDirPath), profileFilePath.split('/').pop()!, text)
      return text
    }
    const { text: current, version } = await this.drive.readText(file.id)
    const text = change(current)
    if ((await this.drive.version(file.id)) !== version) continue
    await this.drive.writeText(file.id, text)
    return text
  }
  throw new VaultError('busy', 'ไฟล์โปรไฟล์ถูกแก้อยู่ตลอด ลองใหม่อีกครั้ง')
}

Vault.prototype.editNote = function (this: any, op: (text: string) => EditResult, fileId?: string) {
  return this.edit(op, fileId)
}

Vault.prototype.sweep = async function (
  this: any,
  sweepOp: (live: string, archive: string) => { text: string; archive: string; titles: string[] } | null,
): Promise<string[]> {
  const id = await this.file()
  for (let attempt = 0; attempt < 3; attempt++) {
    const { text, version } = await this.drive.readText(id)
    const archive = await this.archiveNote()
    const res = sweepOp(text, archive.text)
    if (!res) return []
    const archiveId = archive.id ?? (await this.drive.createText(await this.folder(), this.archiveName(), res.archive))
    if (archive.id) await this.drive.writeText(archive.id, res.archive)
    if ((await this.drive.version(id)) !== version) {
      // The note moved on meanwhile: put the archive back as it was and start over on the new text.
      if (archive.id) await this.drive.writeText(archive.id, archive.text)
      else await this.drive.trash(archiveId)
      continue
    }
    await this.drive.writeText(id, res.text)
    return res.titles
  }
  throw new VaultError('busy', 'ไฟล์ถูกแก้อยู่ตลอด ลองใหม่อีกครั้ง')
}
