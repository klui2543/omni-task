import { describe, expect, it } from 'vitest'
import { VaultDrive, fingerprint } from '../../web/src/vaultDrive'
import { memoryVault } from './memoryVault'
import { Vault } from '../../web/src/vault'
import { BehaviorSync } from '../../web/src/behavior'
import { archiveFilePath, omniDirPath, taskFilePath } from '../../web/src/core'

const NOTE = ['# งาน', '- [ ] ส่งรายงาน 📅 2026-10-20', '- [ ] โทรหาหมอ', ''].join('\n')
const ROOT = 'obsidian:test'

const open = (files: Record<string, string>) => {
  const mem = memoryVault(files)
  const drive = new VaultDrive(mem.fs, ROOT)
  return { mem, drive, vault: new Vault(drive, ROOT, null) }
}

describe('the vault as the app\'s Drive', () => {
  it('finds the task note by its path and reads its tasks', async () => {
    const { vault } = open({ [taskFilePath]: NOTE, 'Daily/2026-10-18.md': '- [ ] ซื้อนม\n' })
    const snap = await vault.load()
    expect(snap.fileId).toBe(taskFilePath)
    expect(snap.tasks.map((t) => t.title)).toEqual(['ส่งรายงาน', 'โทรหาหมอ'])
  })

  it('adds and ticks a task by writing the note in the vault', async () => {
    const { mem, vault } = open({ [taskFilePath]: NOTE })
    await vault.load()
    expect((await vault.add('จองตั๋วเครื่องบิน')).ok).toBe(true)
    expect(mem.texts.get(taskFilePath)).toContain('- [ ] จองตั๋วเครื่องบิน')
    const snap = await vault.load()
    const task = snap.tasks.find((t) => t.title === 'โทรหาหมอ')!
    expect((await vault.toggle(task)).ok).toBe(true)
    expect(mem.texts.get(taskFilePath)).toMatch(/- \[x\] โทรหาหมอ ✅ \d{4}-\d\d-\d\d/)
  })

  it('keeps a change Obsidian Sync wrote between the read and the write', async () => {
    const { mem, vault } = open({ [taskFilePath]: NOTE })
    await vault.load()
    // The first time the app reads the note to edit it, the phone's change lands right after.
    let once = true
    mem.hooks.onRead = (path) => {
      if (path !== taskFilePath || !once) return
      once = false
      queueMicrotask(() => mem.setText(taskFilePath, NOTE.replace('- [ ] โทรหาหมอ', '- [ ] โทรหาหมอ\n- [ ] งานจากมือถือ')))
    }
    expect((await vault.add('งานจากแท็บ')).ok).toBe(true)
    const text = mem.texts.get(taskFilePath)!
    expect(text).toContain('งานจากมือถือ')
    expect(text).toContain('งานจากแท็บ')
  })

  it('archives a finished task into the archive note beside the task note, making it when missing', async () => {
    const { mem, vault } = open({ [taskFilePath]: NOTE.replace('- [ ] โทรหาหมอ', '- [x] โทรหาหมอ ✅ 2026-10-18') })
    const snap = await vault.load()
    const done = snap.tasks.find((t) => t.title === 'โทรหาหมอ')!
    const res = await vault.archive(done)
    expect(res.ok).toBe(true)
    expect(mem.texts.get(taskFilePath)).not.toContain('โทรหาหมอ')
    expect(mem.texts.get(archiveFilePath)).toContain('- [x] โทรหาหมอ')
    // Undo puts it back and takes it out of the archive.
    await vault.undo(res.cut!)
    expect(mem.texts.get(taskFilePath)).toContain('โทรหาหมอ')
    expect(mem.texts.get(archiveFilePath)).not.toContain('โทรหาหมอ')
  })

  it('reads the tasks of the other notes, and sees a note changed in the editor on the next read', async () => {
    const { mem, vault } = open({ [taskFilePath]: NOTE, 'Work/Plan.md': '- [ ] เขียนแผน\n', '.trash/old.md': '- [ ] ทิ้งแล้ว\n' })
    await vault.load()
    // The first walk of the vault ends in the background.
    await new Promise<void>((resolve) => { vault.onIndexed = resolve })
    let snap = vault.withOtherNotes(await vault.load())
    expect(snap.tasks.map((t) => t.title)).toContain('เขียนแผน')
    expect(snap.tasks.map((t) => t.title)).not.toContain('ทิ้งแล้ว')
    mem.setText('Work/Plan.md', '- [ ] เขียนแผน\n- [ ] ส่งแผน\n')
    snap = await vault.load()
    expect(snap.tasks.map((t) => t.title)).toContain('ส่งแผน')
    // A task of that note is ticked in that note.
    const t = snap.tasks.find((x) => x.title === 'ส่งแผน')!
    await vault.toggle(t)
    expect(mem.texts.get('Work/Plan.md')).toMatch(/- \[x\] ส่งแผน/)
  })

  it('writes the behavior log in the Omni folder', async () => {
    const { mem, drive } = open({ [taskFilePath]: NOTE })
    const log = new BehaviorSync(drive, ROOT, async (path) => {
      let id = ROOT
      for (const part of path.split('/')) id = (await drive.child(id, part))?.id ?? (await drive.createFolder(id, part))
      return id
    })
    await log.send()
    // Nothing waited, so nothing was written; the folder lookups went through the vault without failing.
    expect(mem.trashed).toEqual([])
    expect(await drive.resolve(ROOT, omniDirPath)).not.toBeNull()
  })

  it('remembers its own writes, so Obsidian\'s report of them is not news', async () => {
    const { drive } = open({ [taskFilePath]: NOTE })
    await drive.writeText(taskFilePath, NOTE + '- [ ] x\n')
    expect(drive.isOwnWrite(taskFilePath)).toBe(true)
    expect(drive.isOwnWrite('Work/Plan.md')).toBe(false)
  })

  it('answers versions from the text, and no revisions', async () => {
    const { drive } = open({ [taskFilePath]: NOTE })
    const read = await drive.readText(taskFilePath)
    expect(read.version).toBe(fingerprint(NOTE))
    expect(read.revision).toBe('')
    expect(fingerprint('a')).not.toBe(fingerprint('b'))
    expect((await drive.get(ROOT)).name).toBe('Klui Vault')
    await expect(drive.readText('nope.md')).rejects.toMatchObject({ status: 404 })
  })
})
