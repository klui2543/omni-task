import { describe, expect, it } from 'vitest'
import * as core from '../src/core'
import { PLAIN_FILTER } from '../src/types'

const NOTE = [
  '- [ ] ส่งรายงาน #peddose 🆔 rep ⏫',
  '- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ',
  '- [ ] ทบทวนเคส #Siriraj ⛔ rep',
  '- [ ] จองตั๋ว #bucketlist',
  '',
].join('\n')
const BUCKET = '---\nomni-list: true\nicon: 🏔️\ntag: bucketlist\n---\n# Bucket list\n'
const STATE = { order: [], starred: [], branches: [], kindTags: [] }
const path = core.taskFilePath

describe('the plugin through the shared core', () => {
  it('grows the tree from the note, leaving the list tag out', () => {
    const out = core.ygdrasil(path, NOTE, [{ key: 'b', path: `${core.omniDirPath}/Bucket list.md`, text: BUCKET }], STATE, PLAIN_FILTER, [])
    expect(out.worlds).toBe(2)
    expect(out.tags).toContain('peddose/แอป/เว็บ')
    const wait = out.edges.find((e) => e.kind === 'LINK_ACROSS')!
    expect([wait.blocker, wait.waiting]).toEqual([`${path}#0`, `${path}#2`])
  })

  it('writes an arrow as a wait, moves a task, adds one and ticks one', () => {
    const tasks = core.loadTasks(path, NOTE)
    const web = tasks.find((t) => t.title === 'ทำหน้าเว็บ')!
    const review = tasks.find((t) => t.title === 'ทบทวนเคส')!
    const linked = core.link(NOTE, web, review)
    expect(linked.ok).toBe(true)
    expect(linked.text!.split('\n')[1]).toContain(`🆔 ${linked.id}`)
    expect(linked.text!.split('\n')[2]).toContain(`⛔ rep,${linked.id}`)
    expect(core.link(NOTE, web, web).error).toBe('self')

    const moved = core.moveTo(NOTE, web, 'peddose/แอป/เว็บ', 'Siriraj/สอน')
    expect(moved.text!.split('\n')[1]).toBe('- [ ] ทำหน้าเว็บ #Siriraj/สอน')

    const added = core.addTagged(NOTE, 'peddose/แอป', 'ลองแอปบนมือถือ')
    expect(added.text).toContain('- [ ] ลองแอปบนมือถือ #peddose/แอป')

    const ticked = core.toggle(NOTE, web)
    expect(ticked.text!.split('\n')[1]).toMatch(/^- \[x\] ทำหน้าเว็บ #peddose\/แอป\/เว็บ ✅ \d{4}-\d{2}-\d{2}$/)
  })
})
