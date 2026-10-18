// The vault as the Omni app reads it (web/src/vaultDrive.ts): Obsidian's own vault calls, so the editor, the file
// list and Obsidian Sync see every change the app makes as soon as it is made.
import { TFile, TFolder, type App, type TAbstractFile } from 'obsidian'
import type { VaultEntry, VaultFs } from '../../web/src/vaultDrive'

function entry(f: TAbstractFile): VaultEntry {
  if (f instanceof TFile) return { path: f.path, name: f.name, folder: false, mtime: f.stat.mtime, size: f.stat.size }
  return { path: f.path, name: f.name, folder: true }
}

export function vaultFs(app: App): VaultFs {
  const vault = app.vault
  const at = (path: string): TAbstractFile | null => (path === '' ? vault.getRoot() : vault.getAbstractFileByPath(path))
  const file = (path: string): TFile => {
    const f = at(path)
    if (!(f instanceof TFile)) throw new Error(`ไม่พบไฟล์ ${path}`)
    return f
  }
  return {
    name: vault.getName(),
    async list(path) {
      const f = at(path)
      return f instanceof TFolder ? f.children.map(entry) : null
    },
    async stat(path) {
      const f = at(path)
      return f ? entry(f) : null
    },
    read: (path) => vault.read(file(path)),
    write: (path, text) => vault.modify(file(path), text),
    async create(path, text) {
      await vault.create(path, text)
    },
    readBinary: (path) => vault.readBinary(file(path)),
    async createBinary(path, data) {
      await vault.createBinary(path, data)
    },
    async mkdir(path) {
      await vault.createFolder(path)
    },
    // The trash the owner chose in Obsidian's settings (the system's or the vault's .trash); an Obsidian older than
    // 1.6.6 has no such choice to follow, and gets the system's trash.
    trash: (path) => (app.fileManager.trashFile ? app.fileManager.trashFile(file(path)) : vault.trash(file(path), true)),
  }
}
