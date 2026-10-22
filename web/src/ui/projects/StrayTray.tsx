import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { cleanProjectName, homeLine } from '../../core'
import type { Task, YgStray } from '../../types'
import { shortDate } from '../views/dates'

/** How long a row in the tray is held before it lifts to be dragged onto the tree. */
const HOLD_MS = 400
/** Tasks a group shows before "ดูอีก". */
const SHOWN = 5

export type StraySort = 'due' | 'old' | 'az'

const SORTS: [StraySort, string][] = [['due', 'ใกล้กำหนด'], ['old', 'เก่าสุด'], ['az', 'ก ถึง ฮ']]

/**
 * A task in the tray. A tap opens the picker; held still for a moment, it lifts and follows the finger (or the mouse,
 * after a short move) onto the tree. While lifted the page must not scroll under the finger, so the touch is held here.
 */
function StrayRow(p: { task: Task; today: string; onPick: () => void; onLift: (x: number, y: number) => void }) {
  const row = useRef<HTMLButtonElement>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const start = useRef({ x: 0, y: 0 })
  const lifted = useRef(false)
  const moved = useRef(false)
  const clear = () => { if (timer.current) clearTimeout(timer.current); timer.current = null }
  useEffect(() => {
    const el = row.current!
    const hold = (e: TouchEvent) => { if (lifted.current) e.preventDefault() }
    el.addEventListener('touchmove', hold, { passive: false })
    return () => el.removeEventListener('touchmove', hold)
  }, [])
  const date = p.task.due ?? p.task.scheduled
  return (
    <button ref={row} class="yg-stray-row" aria-label={`ย้าย ${p.task.title}`}
      onPointerDown={(e) => {
        if (e.button) return
        lifted.current = false
        moved.current = false
        start.current = { x: e.clientX, y: e.clientY }
        clear()
        const { clientX, clientY } = e
        if (e.pointerType !== 'mouse') timer.current = setTimeout(() => { timer.current = null; lifted.current = true; p.onLift(clientX, clientY) }, HOLD_MS)
      }}
      onPointerMove={(e) => {
        const far = Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 8
        if (!far) return
        moved.current = true
        clear()
        // The mouse lifts as soon as it drags; a finger that moves first is scrolling the tray.
        if (e.pointerType === 'mouse' && e.buttons === 1 && !lifted.current) { lifted.current = true; p.onLift(e.clientX, e.clientY) }
      }}
      onPointerUp={clear}
      onPointerCancel={clear}
      onContextMenu={(e) => e.preventDefault()}
      onClick={(e) => {
        if (lifted.current || moved.current) { lifted.current = false; e.preventDefault(); return }
        p.onPick()
      }}>
      <span class="yg-stray-box" aria-hidden="true" />
      <span class="grow ellipsis left">{p.task.title}</span>
      {date && <span class={`micro${p.task.due && p.task.due < p.today ? ' yg-late' : ' faint'}`}>{shortDate(date)}</span>}
    </button>
  )
}

/**
 * The tray under Ygdrasil: the tasks with no project, by their first tag (no tag first). A tap opens the picker,
 * a held task is dragged up onto a project or branch; a whole tag can be made a project from its group's head.
 */
export function StrayTray(p: {
  strays: YgStray[]
  task: (key: string) => Task | undefined
  today: string
  low: boolean
  trayRef: { current: HTMLElement | null }
  onClose: () => void
  onPick: (t: Task) => void
  onLift: (t: Task, x: number, y: number) => void
  onMakeProject: (tag: string) => void
}) {
  const [sort, setSort] = useState<StraySort>('due')
  const [shut, setShut] = useState<string[]>([])
  const [all, setAll] = useState<string[]>([])
  const total = p.strays.reduce((n, g) => n + g.due.length, 0)
  return (
    <section ref={(el) => { p.trayRef.current = el }} class={`yg-tray${p.low ? ' low' : ''}`} aria-label="งานที่ยังไม่มีบ้าน">
      <div class="yg-tray-grip" aria-hidden="true" />
      <div class="yg-tray-head">
        <span class="strong">ยังไม่มีบ้าน</span>
        <span class="yg-tray-count">{total}</span>
        <span class="grow" />
        <button class="yg-tray-x" aria-label="ปิดถาด" onClick={p.onClose}>×</button>
      </div>
      <span class="small faint yg-tray-hint">แตะงานเพื่อเลือกที่ไป หรือกดค้างแล้วลากขึ้นไปวางบนกิ่ง</span>
      <div class="yg-tray-sort" role="group" aria-label="เรียง">
        {SORTS.map(([k, label]) => <button key={k} class={sort === k ? 'on' : ''} aria-pressed={sort === k} onClick={() => setSort(k)}>{label}</button>)}
      </div>
      <div class="yg-tray-list">
        {p.strays.map((g) => {
          const id = g.tag ?? ''
          const closed = shut.includes(id)
          const keys = g[sort]
          const shown = all.includes(id) ? keys : keys.slice(0, SHOWN)
          return (
            <div key={id} class="yg-tray-group">
              <div class="yg-tray-ghead">
                <button class="yg-tray-gtoggle" aria-expanded={!closed} onClick={() => setShut(closed ? shut.filter((x) => x !== id) : [...shut, id])}>
                  <span class="micro faint">{closed ? '▸' : '▾'}</span>
                  <span class="strong">{g.tag ? '#' + g.tag : 'ไม่มี tag'}</span>
                  <span class="small faint">{keys.length}</span>
                </button>
                {g.tag && <button class="yg-tray-make" onClick={() => p.onMakeProject(g.tag!)}>ตั้งเป็นโปรเจกต์</button>}
              </div>
              {!closed && shown.map((key) => {
                const t = p.task(key)
                return t ? <StrayRow key={key} task={t} today={p.today} onPick={() => p.onPick(t)} onLift={(x, y) => p.onLift(t, x, y)} /> : null
              })}
              {!closed && keys.length > shown.length && (
                <button class="yg-tray-more" onClick={() => setAll([...all, id])}>ดูอีก {keys.length - shown.length} งาน</button>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

type Tab = 'pick' | 'main' | 'sub'

/**
 * Where a task with no project goes: a project or branch already on the tree, a new project, or a new branch under
 * any project or branch. The line is shown as it is and as it will be before anything is written.
 */
export function HomePicker(p: { task: Task; stray?: string; tags: string[]; onCancel: () => void; onMove: (tag: string) => void }) {
  const [tab, setTab] = useState<Tab>('pick')
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState<string | null>(null)
  const [name, setName] = useState('')
  const clean = (v: string) => (v.trim() ? cleanProjectName(v.replace(/^#+/, '')) : '')
  const target = tab === 'pick' ? picked : tab === 'main' ? clean(name) || null : picked && clean(name) ? `${picked}/${clean(name)}` : null
  const isNew = !!target && !p.tags.includes(target)
  const found = useMemo(() => {
    const q = search.trim().toLowerCase().replace(/^#/, '')
    return (q ? p.tags.filter((t) => t.toLowerCase().includes(q)) : p.tags).slice(0, 40)
  }, [search, p.tags])
  const go = (t: Tab) => { setTab(t); setPicked(null); setName(''); setSearch('') }
  const chips = (
    <>
      <input type="search" class="yg-pick-search" placeholder="ค้นโปรเจกต์หรือกิ่ง" aria-label="ค้นโปรเจกต์หรือกิ่ง" value={search}
        onInput={(e) => setSearch((e.target as HTMLInputElement).value)} />
      <div class="yg-pick-chips">
        {found.map((t) => <button key={t} class={picked === t ? 'on' : ''} aria-pressed={picked === t} onClick={() => setPicked(t)}>#{t}</button>)}
        {found.length === 0 && <span class="small faint">ไม่พบ ลองสร้างใหม่</span>}
      </div>
    </>
  )
  return (
    <div class="scrim" onClick={(e) => { if (e.target === e.currentTarget) p.onCancel() }}>
      <div class="dialog yg-pick" role="dialog" aria-label="ย้ายไปไว้ที่">
        <span class="small muted">ย้ายไปไว้ที่</span>
        <span class="strong">{p.task.title}</span>
        <div class="yg-pick-tabs" role="tablist">
          {([['pick', 'มีอยู่แล้ว'], ['main', 'สร้างโปรเจกต์หลัก'], ['sub', 'สร้างกิ่งย่อย']] as [Tab, string][]).map(([k, label]) => (
            <button key={k} role="tab" aria-selected={tab === k} class={tab === k ? 'on' : ''} onClick={() => go(k)}>{label}</button>
          ))}
        </div>
        {tab === 'pick' && chips}
        {tab === 'main' && (
          <>
            <input placeholder="ชื่อโปรเจกต์ใหม่" aria-label="ชื่อโปรเจกต์ใหม่" value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
            {p.stray && <button class="yg-pick-use" onClick={() => setName(p.stray!)}>ใช้ชื่อ tag เดิม #{p.stray}</button>}
          </>
        )}
        {tab === 'sub' && (
          <>
            <span class="small muted">1. เลือกว่าจะอยู่ใต้อะไร</span>
            {chips}
            <div class="yg-pick-sub">
              <span class="small accent-text">{picked ? `#${picked}/` : ''}</span>
              <input placeholder="2. ชื่อกิ่งใหม่" aria-label="ชื่อกิ่งใหม่" disabled={!picked} value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
            </div>
          </>
        )}
        <div class="yg-pick-preview">
          <span class="micro faint">ก่อน</span>
          <code>{p.task.raw.trim()}</code>
          <span class="micro faint">หลัง</span>
          <code>{target ? homeLine(p.task.raw, target).trim() : 'เลือกที่ไปก่อน'}</code>
          {isNew && <span class="micro yg-pick-new">{target!.includes('/') ? `กิ่งใหม่ใต้ #${target!.slice(0, target!.lastIndexOf('/'))}` : 'โปรเจกต์ใหม่ในต้นไม้'}</span>}
        </div>
        <div class="yg-pick-acts">
          <button class="ghost" onClick={p.onCancel}>ยกเลิก</button>
          <button class="primary" disabled={!target} onClick={() => target && p.onMove(target)}>{isNew ? 'สร้างแล้วย้าย' : 'ย้าย'}</button>
        </div>
      </div>
    </div>
  )
}
