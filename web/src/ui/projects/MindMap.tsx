import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import type { BranchOut, BranchState, ProjectOut, Task } from '../../types'
import { RingMark, useTapOrHold } from './bits'

/** Sizes on the map, in pixels. Tasks sit in the next column, above the branch's own sub-branches. */
const COL = 200
const NODE_W = 156
const NODE_H = 40
const ROOT_H = 46
const TASK_W = 190
const TASK_H = 32
const TASKS_SHOWN = 8

export const STATE_COLOR: Record<BranchState, string> = { ACTIVE: 'var(--accent)', TRYING: 'var(--amber)', CHOSEN: 'var(--lime)', PARKED: '#9a9ea8' }
export const STATE_LABEL: Record<BranchState, string> = { ACTIVE: 'กำลังทำ', TRYING: 'กำลังลอง', CHOSEN: 'เลือกแล้ว', PARKED: 'พับเก็บ' }
const STATES: BranchState[] = ['ACTIVE', 'TRYING', 'CHOSEN', 'PARKED']

type Item =
  | { kind: 'branch'; node: BranchOut; x: number; y: number }
  | { kind: 'task'; task: Task; owner: BranchOut; x: number; y: number }
  | { kind: 'more'; count: number; owner: BranchOut; x: number; y: number }

/**
 * A tidy tree read left to right: each branch sits in the middle of the rows its tasks and sub-branches take
 * (Android's mapLayout). [y] is each item's centre line.
 */
export function mapLayout(nodes: Map<string, BranchOut>, tasksOf: (b: BranchOut) => Task[]): { items: Item[]; height: number; width: number } {
  const items: Item[] = []
  let deepest = 0
  const place = (n: BranchOut, depth: number, top: number): number => {
    deepest = Math.max(deepest, depth)
    let y = top
    const tasks = tasksOf(n)
    const shown = tasks.slice(0, TASKS_SHOWN)
    const x = (depth + 1) * COL
    shown.forEach((t) => { items.push({ kind: 'task', task: t, owner: n, x, y: y + TASK_H / 2 }); y += TASK_H })
    if (tasks.length > shown.length) { items.push({ kind: 'more', count: tasks.length - shown.length, owner: n, x, y: y + TASK_H / 2 }); y += TASK_H }
    if (shown.length && n.children.length) y += 6
    n.children.forEach((c, i) => {
      if (i > 0) y += 12
      y += place(nodes.get(c)!, depth + 1, y)
    })
    const h = Math.max(depth === 0 ? ROOT_H : NODE_H, y - top)
    items.push({ kind: 'branch', node: n, x: depth * COL, y: top + h / 2 })
    return h
  }
  const height = place(nodes.get('')!, 0, 0)
  return { items, height, width: (deepest + 1) * COL + TASK_W }
}

const curve = (x1: number, y1: number, x2: number, y2: number) => {
  const mx = (x1 + x2) / 2
  return `M${x1} ${y1} C${mx} ${y1} ${mx} ${y2} ${x2} ${y2}`
}

function TaskNode(p: { task: Task; x: number; y: number; busy: boolean; onTick: () => void; onOpen: () => void }) {
  const hold = useTapOrHold(p.onTick, p.onOpen)
  const done = p.task.status === 'DONE'
  return (
    <div class="pj-mtask-wrap" style={{ left: `${p.x}px`, top: `${p.y - TASK_H / 2}px`, width: `${TASK_W}px`, height: `${TASK_H}px` }}>
      <button class={`pj-mtask${done ? ' done' : ''}`} disabled={p.busy} aria-label={`${done ? 'ยกเลิกการติ๊ก' : 'ติ๊กเสร็จ'} ${p.task.title}`} {...hold}>
        <RingMark task={p.task} />
        <span class="ellipsis">{p.task.title}</span>
      </button>
    </div>
  )
}

/**
 * A project's branches as a mind map that fills the page: drag to move, pinch or the buttons to zoom. Tap a
 * branch for its actions underneath; tap a task to tick it, long-press (or right click) to open it.
 */
export function MindMap(p: {
  project: ProjectOut
  task: (key: string) => Task | undefined
  busy: boolean
  onBack: () => void
  onTick: (t: Task) => void
  onOpen: (t: Task) => void
  onState: (b: BranchOut, s: BranchState) => void
  onAddBranch: (parent: BranchOut) => void
  onAddTask: (b: BranchOut) => void
  onRename: (b: BranchOut) => void
  onDelete: (b: BranchOut) => void
  /** The path to select first, e.g. a branch just added. */
  select?: string
}) {
  const nodes = useMemo(() => new Map(p.project.branches.map((b) => [b.path, b])), [p.project])
  const [selPath, setSelPath] = useState('')
  useEffect(() => { if (p.select !== undefined) setSelPath(p.select) }, [p.select])
  const sel = nodes.get(selPath) ?? nodes.get('')!
  const tasksOf = (b: BranchOut) =>
    b.own.map(p.task).filter((t): t is Task => !!t).sort((a, c) => Number(a.status === 'DONE') - Number(c.status === 'DONE') || a.lineIndex - c.lineIndex)
  const layout = mapLayout(nodes, tasksOf)
  const at = new Map(layout.items.filter((i): i is Extract<Item, { kind: 'branch' }> => i.kind === 'branch').map((i) => [i.node.path, i]))

  const box = useRef<HTMLDivElement>(null)
  const [scale, setScale] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const view = useRef({ scale: 1, pan: { x: 0, y: 0 } })
  view.current = { scale, pan }
  const apply = (s: number, pn: { x: number; y: number }) => {
    view.current = { scale: s, pan: pn }
    setScale(s)
    setPan(pn)
  }
  const size = useRef({ w: 0, h: 0 })
  const fit = () => {
    const { w, h } = size.current
    if (!w || !h) return
    const pad = 24
    const s = Math.min(1.2, (w - pad * 2) / layout.width, (h - pad * 2) / layout.height)
    const k = Math.min(2.5, Math.max(0.3, s))
    apply(k, { x: (w - layout.width * k) / 2, y: (h - layout.height * k) / 2 })
  }
  const fitRef = useRef(fit)
  fitRef.current = fit
  const zoomBy = (f: number, atPoint = { x: size.current.w / 2, y: size.current.h / 2 }) => {
    const { scale: s0, pan: p0 } = view.current
    const s = Math.min(2.5, Math.max(0.3, s0 * f))
    apply(s, { x: atPoint.x - (atPoint.x - p0.x) * (s / s0), y: atPoint.y - (atPoint.y - p0.y) * (s / s0) })
  }

  // The map starts fitted to its box, and wheel gestures are read here since the browser's own are passive.
  useEffect(() => {
    const el = box.current!
    let first = true
    const measure = () => {
      size.current = { w: el.clientWidth, h: el.clientHeight }
      if (first && size.current.w) { first = false; fitRef.current() }
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    const wheel = (e: WheelEvent) => {
      e.preventDefault()
      const r = el.getBoundingClientRect()
      if (e.ctrlKey || e.metaKey) zoomBy(Math.exp(-e.deltaY * 0.01), { x: e.clientX - r.left, y: e.clientY - r.top })
      else apply(view.current.scale, { x: view.current.pan.x - e.deltaX, y: view.current.pan.y - e.deltaY })
    }
    el.addEventListener('wheel', wheel, { passive: false })
    return () => { ro.disconnect(); el.removeEventListener('wheel', wheel) }
  }, [])

  // Dragging moves the map; two fingers also zoom. A drag never counts as a tap on what is under it.
  const pointers = useRef(new Map<number, { x: number; y: number; sx: number; sy: number }>())
  const moved = useRef(false)
  const pinch = useRef(0)
  const down = (e: PointerEvent) => {
    if (e.button) return
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY })
    moved.current = false
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()]
      pinch.current = Math.hypot(a.x - b.x, a.y - b.y)
    }
  }
  const move = (e: PointerEvent) => {
    const cur = pointers.current.get(e.pointerId)
    if (!cur) return
    const r = box.current!.getBoundingClientRect()
    const dx = e.clientX - cur.x
    const dy = e.clientY - cur.y
    cur.x = e.clientX
    cur.y = e.clientY
    if (!moved.current && Math.hypot(cur.x - cur.sx, cur.y - cur.sy) > 4) {
      moved.current = true
      box.current!.setPointerCapture(e.pointerId)
    }
    if (!moved.current) return
    if (pointers.current.size >= 2) {
      const [a, b] = [...pointers.current.values()]
      const d = Math.hypot(a.x - b.x, a.y - b.y)
      if (pinch.current > 0) zoomBy(d / pinch.current, { x: (a.x + b.x) / 2 - r.left, y: (a.y + b.y) / 2 - r.top })
      pinch.current = d
    } else {
      apply(view.current.scale, { x: view.current.pan.x + dx, y: view.current.pan.y + dy })
    }
  }
  const up = (e: PointerEvent) => {
    pointers.current.delete(e.pointerId)
    pinch.current = 0
  }

  const parent = sel.path ? nodes.get(sel.parentPath) : undefined
  const hint =
    sel.path === '' ? 'แตะ + กิ่ง เพื่อแตกทางใหม่ ซ้อนได้ไม่จำกัดชั้น'
    : sel.state === 'TRYING' ? 'เลือกแล้ว ทางที่กำลังลองข้างกันจะพับเก็บ'
    : sel.state === 'PARKED' ? 'พับเก็บ: งานซ่อนจากโฟกัส ปฏิทิน และรายการงาน แต่ยังอยู่ในไฟล์'
    : 'แตะงานเพื่อติ๊ก กดค้างเพื่อเปิด'

  return (
    <div class="pj-mapview">
      <header class="head">
        <button class="ghost" onClick={p.onBack}>‹ กลับ</button>
        <div class="grow">
          <h1 class="pj-h1-small">#{p.project.name}</h1>
          <span class="small muted">ลากเพื่อเลื่อน บีบหรือ Ctrl + ล้อเมาส์เพื่อซูม แตะกิ่งเพื่อจัดการ</span>
        </div>
      </header>

      <div class="pj-map" ref={box} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
        onClickCapture={(e) => { if (moved.current) { e.stopPropagation(); e.preventDefault(); moved.current = false } }}>
        <div class="pj-map-layer" data-testid="map-layer" style={{ width: `${layout.width}px`, height: `${layout.height}px`, transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})` }}>
          <svg class="pj-edges" width={layout.width} height={layout.height} aria-hidden="true">
            {layout.items.map((m, i) => {
              if (m.kind === 'more') return null
              if (m.kind === 'task') {
                const from = at.get(m.owner.path)
                return from ? <path key={i} d={curve(from.x + NODE_W, from.y, m.x, m.y)} fill="none" stroke="var(--border)" stroke-width="1.2" /> : null
              }
              if (!m.node.path) return null
              const from = at.get(m.node.parentPath)
              if (!from) return null
              const parked = m.node.state === 'PARKED'
              return (
                <path key={i} d={curve(from.x + NODE_W, from.y, m.x, m.y)} fill="none" stroke-width="1.8"
                  stroke={parked ? 'var(--control)' : STATE_COLOR[m.node.state]} stroke-opacity={parked ? 1 : 0.8} stroke-dasharray={parked ? '6 6' : undefined} />
              )
            })}
          </svg>
          {layout.items.map((m, i) => {
            if (m.kind === 'branch') {
              const n = m.node
              const root = n.path === ''
              const h = root ? ROOT_H : NODE_H
              return (
                <button key={`b${n.path}`} class={`pj-node${root ? ' root' : ''}${n.path === sel.path ? ' sel' : ''}${n.state === 'PARKED' ? ' parked' : ''}`}
                  aria-pressed={n.path === sel.path} aria-label={root ? `#${n.name}` : n.name}
                  style={{ left: `${m.x}px`, top: `${m.y - h / 2}px`, width: `${NODE_W}px`, height: `${h}px` }} onClick={() => setSelPath(n.path)}>
                  {!root && <span class="pj-dot" style={{ background: STATE_COLOR[n.state] }} />}
                  <span class="grow ellipsis left">{root ? '#' + n.name : n.name}</span>
                  {n.count > 0 && <span class="micro muted">{n.done}/{n.count}</span>}
                </button>
              )
            }
            if (m.kind === 'task') {
              return <TaskNode key={`t${m.task.key}`} task={m.task} x={m.x} y={m.y} busy={p.busy} onTick={() => p.onTick(m.task)} onOpen={() => p.onOpen(m.task)} />
            }
            return <span key={`m${i}`} class="pj-more small faint" style={{ left: `${m.x + 6}px`, top: `${m.y - 9}px` }}>และอีก {m.count} งาน</span>
          })}
        </div>
        <div class="pj-zoom">
          <button aria-label="ซูมเข้า" onClick={() => zoomBy(1.25)}>+</button>
          <button aria-label="ซูมออก" onClick={() => zoomBy(0.8)}>−</button>
          <button aria-label="พอดีจอ" class="fit" onClick={fit}>พอดี</button>
        </div>
      </div>

      <section class="pj-sel" aria-label="กิ่งที่เลือก">
        <div class="row-gap">
          {sel.path !== '' && <span class="pj-dot big" style={{ background: STATE_COLOR[sel.state] }} />}
          <span class="strong">{sel.path === '' ? '#' + sel.name : sel.name}</span>
          <span class="small muted">#{sel.tag}</span>
        </div>
        {sel.path !== '' && (
          <div class="chips">
            {STATES.map((s) => (
              <button key={s} class={`chip pj-state${sel.state === s ? ' on' : ''}`} aria-pressed={sel.state === s} disabled={p.busy} onClick={() => p.onState(sel, s)}>
                <span class="pj-dot" style={{ background: STATE_COLOR[s] }} />{STATE_LABEL[s]}
              </button>
            ))}
          </div>
        )}
        <div class="row-gap wrap">
          <button class="primary small-btn" disabled={p.busy} onClick={() => p.onAddBranch(sel)}>+ กิ่ง</button>
          <button class="ghost small-btn" disabled={p.busy} onClick={() => p.onAddTask(sel)}>+ งาน</button>
          {sel.path !== '' && <button class="ghost small-btn" disabled={p.busy} onClick={() => p.onRename(sel)}>เปลี่ยนชื่อ</button>}
          {sel.path !== '' && sel.count === 0 && <button class="ghost small-btn danger" disabled={p.busy} onClick={() => { p.onDelete(sel); setSelPath(parent?.path ?? '') }}>ลบกิ่ง</button>}
        </div>
        <span class="small muted">{hint}</span>
      </section>
    </div>
  )
}
