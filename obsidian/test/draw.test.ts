import { describe, expect, it } from 'vitest'
import { curve, limbAt, shapes } from '../src/draw'
import type { Task, YgOut } from '../src/types'

const task = (key: string, title: string, extra: Partial<Task> = {}): Task =>
  ({ key, raw: `- [ ] ${title}`, title, status: 'TODO', open: true, priority: 'NONE', tags: [], lineIndex: 0, ...extra })

const out: YgOut = {
  spots: [
    { kind: 'TRUNK', x: 0, y: 100, w: 156, h: 52, done: 0, total: 0, count: 3, folded: false },
    { kind: 'LIMB', x: 200, y: 60, w: 156, h: 40, tag: 'peddose', project: 'peddose', path: '', name: 'peddose', state: 'ACTIVE', done: 1, total: 4, count: 0, folded: false },
    { kind: 'LIMB', x: 400, y: 60, w: 156, h: 40, tag: 'peddose/แอป', project: 'peddose', path: 'แอป', name: 'แอป', state: 'PARKED', done: 0, total: 2, count: 0, folded: false },
    { kind: 'TASK', x: 400, y: 20, w: 190, h: 32, tag: 'peddose', key: 'f#0', done: 0, total: 0, count: 0, folded: false },
    { kind: 'TASK', x: 400, y: 140, w: 190, h: 32, tag: 'peddose', key: 'f#9', done: 0, total: 0, count: 0, folded: false },
    { kind: 'MORE', x: 400, y: 172, w: 190, h: 32, tag: 'peddose', done: 0, total: 0, count: 2, folded: false },
  ],
  edges: [
    { kind: 'BRANCH', d: 'M156 100 C178 100 178 60 200 60', state: 'ACTIVE' },
    { kind: 'LINK_ACROSS', d: 'M400 20 C360 20 360 140 400 140', blocker: 'f#0', waiting: 'f#9' },
    { kind: 'TASK', d: 'bad' },
  ],
  width: 800, height: 200, matched: 3, worlds: 1, links: 1, tags: ['peddose', 'peddose/แอป'],
}

describe('the tree as shapes', () => {
  it('samples a cubic from its ends, and drops a path it cannot read', () => {
    const pts = curve('M0 0 C10 0 10 10 20 10', 4)
    expect(pts[0]).toEqual([0, 0])
    expect(pts[4]).toEqual([20, 10])
    expect(pts).toHaveLength(5)
    expect(curve('bad')).toEqual([])
  })

  it('draws lines under boxes, marks every part, and moves it all to the origin', () => {
    const tasks = new Map([['f#0', task('f#0', 'ส่งรายงาน', { priority: 'HIGH' })], ['f#9', task('f#9', 'ทบทวนเคส', { status: 'DONE' })]])
    const s = shapes(out, (k) => tasks.get(k), { x: 1000, y: 500 })
    // Two lines (the broken path is dropped), then the boxes and text.
    expect(s.slice(0, 2).every((x) => x.type === 'line')).toBe(true)
    expect(s.filter((x) => x.type === 'line')).toHaveLength(2)
    const link = s.find((x) => x.role.kind === 'link')!
    expect(link.role).toEqual({ kind: 'link', blocker: 'f#0', waiting: 'f#9' })
    expect(link.type === 'line' && link.dashed && link.arrow).toBe(true)
    const world = s.find((x) => x.type === 'text' && x.role.kind === 'limb' && x.text.startsWith('#'))!
    expect(world.type === 'text' && world.text).toBe('#peddose  1/4')
    const parked = s.find((x) => x.type === 'rect' && x.role.kind === 'limb' && 'path' in x.role && x.role.path === 'แอป')!
    expect(parked.type === 'rect' && parked.dashed).toBe(true)
    const ring = s.find((x) => x.type === 'ellipse' && x.role.kind === 'task' && x.role.key === 'f#0')!
    expect(ring.type === 'ellipse' && ring.stroke).toBe('#f08c00')
    const trunk = s.find((x) => x.type === 'rect' && x.role.kind === 'trunk')!
    expect(trunk.type === 'rect' && [trunk.x, trunk.y]).toEqual([1000, 574])
    expect(s.some((x) => x.type === 'text' && x.text === 'และอีก 2 งาน')).toBe(true)
    // A task and its ring are one group, so they move together.
    expect(new Set(s.filter((x) => x.role.kind === 'task' && x.role.key === 'f#0').map((x) => x.group))).toEqual(new Set(['task:f#0']))
  })

  it('finds the branch box under a dropped task', () => {
    expect(limbAt(out, 450, 60)).toBe('peddose/แอป')
    expect(limbAt(out, 1250, 560, { x: 1000, y: 500 })).toBe('peddose')
    expect(limbAt(out, 50, 500)).toBeNull()
  })
})
