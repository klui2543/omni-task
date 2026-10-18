// A vault in memory, standing in for Obsidian's in the tests (and in the preview page, scripts/preview.mjs).
import type { VaultEntry, VaultFs } from '../../web/src/vaultDrive'

/** A vault in memory, as Obsidian's would answer; [onRead] lets a test change a note while the app is between steps. */
export function memoryVault(files: Record<string, string>) {
  const texts = new Map<string, string>()
  const bins = new Map<string, ArrayBuffer>()
  const folders = new Set<string>([''])
  const mtimes = new Map<string, number>()
  let clock = 1000
  const addFolders = (path: string) => {
    const parts = path.split('/')
    for (let i = 1; i < parts.length; i++) folders.add(parts.slice(0, i).join('/'))
  }
  const touch = (path: string) => mtimes.set(path, ++clock)
  for (const [path, text] of Object.entries(files)) {
    addFolders(path)
    texts.set(path, text)
    touch(path)
  }
  const name = (p: string) => p.slice(p.lastIndexOf('/') + 1)
  const parent = (p: string) => (p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : '')
  const entry = (p: string): VaultEntry | null => {
    if (folders.has(p) && p !== '') return { path: p, name: name(p), folder: true }
    if (texts.has(p)) return { path: p, name: name(p), folder: false, mtime: mtimes.get(p), size: texts.get(p)!.length }
    if (bins.has(p)) return { path: p, name: name(p), folder: false, mtime: mtimes.get(p), size: bins.get(p)!.byteLength }
    return null
  }
  const trashed: string[] = []
  const hooks = { onRead: null as ((path: string) => void) | null }
  const fs: VaultFs = {
    name: 'Klui Vault',
    async list(path) {
      if (!folders.has(path)) return null
      const all = [...folders, ...texts.keys(), ...bins.keys()].filter((p) => p !== '' && parent(p) === path)
      return all.map((p) => entry(p)!)
    },
    stat: async (path) => entry(path),
    async read(path) {
      if (!texts.has(path)) throw new Error('missing ' + path)
      const text = texts.get(path)!
      hooks.onRead?.(path)
      return text
    },
    async write(path, text) {
      if (!texts.has(path)) throw new Error('missing ' + path)
      texts.set(path, text)
      touch(path)
    },
    async create(path, text) {
      if (entry(path)) throw new Error('exists ' + path)
      if (!folders.has(parent(path))) throw new Error('no folder ' + parent(path))
      texts.set(path, text)
      touch(path)
    },
    readBinary: async (path) => bins.get(path)!,
    async createBinary(path, data) {
      bins.set(path, data)
      touch(path)
    },
    async mkdir(path) {
      addFolders(path + '/x')
    },
    async trash(path) {
      trashed.push(path)
      texts.delete(path)
      bins.delete(path)
    },
  }
  return { fs, texts, bins, folders, trashed, hooks, setText: (p: string, t: string) => { texts.set(p, t); touch(p) } }
}

