// The tree as shapes to draw, with no Obsidian or Excalidraw in it, so it can be tested on its own. Positions are the
// shared layout's, moved by [origin] (where the trunk was left on the drawing). Every shape carries a [role]: what
// part of the tree it is, which the plugin stores on the drawn element to find it again.
import type { Task, YgEdge, YgOut, YgSpot } from './types'

export type Role =
  | { kind: 'trunk' }
  | { kind: 'limb'; tag: string; project: string; path: string }
  | { kind: 'task'; key: string; tag: string }
  | { kind: 'more'; tag: string }
  | { kind: 'edge' }
  | { kind: 'link'; blocker: string; waiting: string }

interface Base { role: Role; group?: string }
export type Shape =
  | (Base & { type: 'rect'; x: number; y: number; w: number; h: number; stroke: string; fill: string; dashed: boolean; width: number })
  | (Base & { type: 'ellipse'; x: number; y: number; w: number; h: number; stroke: string; fill: string; width: number })
  | (Base & { type: 'text'; x: number; y: number; text: string; color: string; size: number })
  | (Base & { type: 'line'; points: [number, number][]; stroke: string; width: number; dashed: boolean; arrow: boolean })

/** Excalidraw's own palette, so the tree sits well beside what the owner draws (it is turned for dark mode). */
export const COLORS = {
  trunk: '#1971c2',
  trunkText: '#ffffff',
  world: '#d0ebff',
  worldText: '#1864ab',
  limb: '#495057',
  text: '#212529',
  muted: '#868e96',
  faint: '#ced4da',
  done: '#2f9e44',
  across: '#f08c00',
  state: { ACTIVE: '#1971c2', TRYING: '#f08c00', CHOSEN: '#2f9e44', PARKED: '#868e96' } as Record<string, string>,
  priority: { HIGHEST: '#e03131', HIGH: '#f08c00', MEDIUM: '#1971c2' } as Record<string, string>,
}

/** Points along an SVG cubic `M x y C a b c d e f`, for a line Excalidraw can draw. */
export function curve(d: string, steps = 10): [number, number][] {
  const n = d.replace(/[MC]/g, ' ').trim().split(/[\s,]+/).map(Number)
  if (n.length < 8 || n.some((v) => Number.isNaN(v))) return []
  const [x0, y0, x1, y1, x2, y2, x3, y3] = n
  const out: [number, number][] = []
  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    const u = 1 - t
    const x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
    const y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
    out.push([Math.round(x * 10) / 10, Math.round(y * 10) / 10])
  }
  return out
}

const edgeShape = (e: YgEdge, ox: number, oy: number): Shape | null => {
  const points = curve(e.d).map(([x, y]) => [x + ox, y + oy] as [number, number])
  if (points.length < 2) return null
  switch (e.kind) {
    case 'BRANCH': {
      const parked = e.state === 'PARKED'
      return { type: 'line', points, stroke: COLORS.state[e.state ?? 'ACTIVE'] ?? COLORS.trunk, width: 2, dashed: parked, arrow: false, role: { kind: 'edge' } }
    }
    case 'TASK':
      return { type: 'line', points, stroke: COLORS.faint, width: 1, dashed: false, arrow: false, role: { kind: 'edge' } }
    default: {
      const across = e.kind === 'LINK_ACROSS'
      const role: Role = e.blocker && e.waiting ? { kind: 'link', blocker: e.blocker, waiting: e.waiting } : { kind: 'edge' }
      return { type: 'line', points, stroke: across ? COLORS.across : COLORS.muted, width: across ? 2 : 1, dashed: true, arrow: true, role }
    }
  }
}

const spotShapes = (s: YgSpot, task: (key: string) => Task | undefined, ox: number, oy: number): Shape[] => {
  const x = s.x + ox
  const top = s.y - s.h / 2 + oy
  switch (s.kind) {
    case 'TRUNK': {
      const group = 'trunk'
      return [
        { type: 'rect', x, y: top, w: s.w, h: s.h, stroke: COLORS.trunk, fill: COLORS.trunk, dashed: false, width: 2, role: { kind: 'trunk' }, group },
        { type: 'text', x: x + 14, y: top + s.h / 2 - 12, text: `Ygdrasil  ${s.count}`, color: COLORS.trunkText, size: 20, role: { kind: 'trunk' }, group },
      ]
    }
    case 'LIMB': {
      const world = s.path === ''
      const role: Role = { kind: 'limb', tag: s.tag!, project: s.project!, path: s.path! }
      const group = `limb:${s.tag}`
      const count = s.folded ? `  +${s.count}` : s.total > 0 ? `  ${s.done}/${s.total}` : ''
      const parked = s.state === 'PARKED'
      return [
        {
          type: 'rect', x, y: top, w: s.w, h: s.h, dashed: parked, width: world ? 2 : 1.5, role, group,
          stroke: world ? COLORS.trunk : COLORS.state[s.state ?? 'ACTIVE'] ?? COLORS.limb,
          fill: world ? COLORS.world : 'transparent',
        },
        {
          type: 'text', x: x + 12, y: top + s.h / 2 - 10, size: world ? 18 : 16, role, group,
          text: (world ? '#' : '') + s.name + count, color: world ? COLORS.worldText : parked ? COLORS.muted : COLORS.text,
        },
      ]
    }
    case 'TASK': {
      const t = s.key ? task(s.key) : undefined
      if (!t) return []
      const role: Role = { kind: 'task', key: t.key, tag: s.tag! }
      const group = `task:${t.key}`
      const done = t.status === 'DONE'
      const ring = COLORS.priority[t.priority] ?? COLORS.muted
      return [
        { type: 'ellipse', x: x + 4, y: s.y + oy - 7, w: 14, h: 14, stroke: done ? COLORS.done : ring, fill: done ? COLORS.done : 'transparent', width: 1.5, role, group },
        { type: 'text', x: x + 26, y: s.y + oy - 9, text: t.title, color: done ? COLORS.muted : COLORS.text, size: 14, role, group },
      ]
    }
    case 'MORE':
      return [{ type: 'text', x: x + 6, y: s.y + oy - 8, text: `และอีก ${s.count} งาน`, color: COLORS.muted, size: 13, role: { kind: 'more', tag: s.tag! } }]
  }
}

/** Every shape of the tree: lines first, so boxes and text sit on top of them. */
export function shapes(out: YgOut, task: (key: string) => Task | undefined, origin = { x: 0, y: 0 }): Shape[] {
  const lines = out.edges.map((e) => edgeShape(e, origin.x, origin.y)).filter((s): s is Shape => s !== null)
  return [...lines, ...out.spots.flatMap((s) => spotShapes(s, task, origin.x, origin.y))]
}

/** Which branch box (by tag) a point falls in, for a task dropped on one. */
export function limbAt(out: YgOut, x: number, y: number, origin = { x: 0, y: 0 }): string | null {
  const s = out.spots.find((p) => p.kind === 'LIMB' && x >= p.x + origin.x && x <= p.x + origin.x + p.w && y >= p.y + origin.y - p.h / 2 && y <= p.y + origin.y + p.h / 2)
  return s?.tag ?? null
}
