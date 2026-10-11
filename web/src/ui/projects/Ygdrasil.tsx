import { useEffect, useRef, useState } from 'preact/hooks'
import type { Task, YgFilter, YgOut, YgSpot } from '../../types'
import { RingMark } from './bits'
import { STATE_COLOR, STATE_LABEL } from './MindMap'
import { usePanZoom } from './panZoom'

export const PLAIN_FILTER: YgFilter = { show: 'OPEN', dates: 'ANY', priorities: [], blockedOnly: false, starredOnly: false, parked: false, links: true, text: '' }

const SHOWS: [YgFilter['show'], string][] = [['OPEN', 'ยังไม่เสร็จ'], ['ALL', 'ทั้งหมด'], ['DONE', 'เสร็จแล้ว']]
const DATES: [YgFilter['dates'], string][] = [['ANY', 'ทุกวัน'], ['OVERDUE', 'เลยกำหนด'], ['TODAY', 'ถึงวันนี้'], ['WEEK', 'ภายใน 7 วัน'], ['UNDATED', 'ไม่มีวัน']]
const PRIORITIES: [string, string][] = [['HIGHEST', 'สูงสุด'], ['HIGH', 'สูง'], ['MEDIUM', 'กลาง'], ['NONE', 'ปกติ'], ['LOW', 'ต่ำ'], ['LOWEST', 'ต่ำสุด']]

/** How many choices differ from the plain filter (Ygdrasil.Filter.changed). */
export const filterChanges = (f: YgFilter) =>
  [f.show !== 'OPEN', f.dates !== 'ANY', f.priorities.length > 0, f.blockedOnly, f.starredOnly, f.parked, !f.links, f.text.trim() !== ''].filter(Boolean).length

/** How long a press is held before the task or branch lifts to be dragged. */
const HOLD_MS = 250

/**
 * A tap, a right click, and a press held for [HOLD_MS] that lifts what is pressed (it then follows the pointer until
 * let go, see the tree's carry). A press that moves first is a drag of the map, not a lift.
 */
function useHold(onTap: () => void, onLift: (x: number, y: number) => void, onContext?: () => void) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const start = useRef({ x: 0, y: 0 })
  const lifted = useRef(false)
  const mouse = useRef(false)
  const clear = () => { if (timer.current) clearTimeout(timer.current); timer.current = null }
  return {
    onPointerDown: (e: PointerEvent) => {
      if (e.button) return
      mouse.current = e.pointerType === 'mouse'
      lifted.current = false
      start.current = { x: e.clientX, y: e.clientY }
      clear()
      const { clientX, clientY } = e
      timer.current = setTimeout(() => { timer.current = null; lifted.current = true; onLift(clientX, clientY) }, HOLD_MS)
    },
    onPointerMove: (e: PointerEvent) => { if (Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 6) clear() },
    onPointerUp: clear,
    onPointerCancel: clear,
    onContextMenu: (e: Event) => { e.preventDefault(); if (onContext && mouse.current && !lifted.current) { clear(); onContext() } },
    onClick: (e: Event) => { if (lifted.current) { lifted.current = false; e.preventDefault(); return } onTap() },
  }
}

/**
 * A task on the tree: a tap ticks it, a right click opens it. Held, it lifts: drag it onto another project or branch
 * to move it there, or let go where it is to open it.
 */
function TaskSpot(p: { task: Task; spot: YgSpot; busy: boolean; onTick: () => void; onOpen: () => void; onLift: (x: number, y: number) => void }) {
  const hold = useHold(p.onTick, p.onLift, p.onOpen)
  const done = p.task.status === 'DONE'
  return (
    <div class="pj-mtask-wrap" style={{ left: `${p.spot.x}px`, top: `${p.spot.y - p.spot.h / 2}px`, width: `${p.spot.w}px`, height: `${p.spot.h}px` }}>
      <button class={`pj-mtask${done ? ' done' : ''}`} disabled={p.busy} aria-label={`${done ? 'ยกเลิกการติ๊ก' : 'ติ๊กเสร็จ'} ${p.task.title}`} {...hold}>
        <RingMark task={p.task} />
        <span class="ellipsis">{p.task.title}</span>
      </button>
    </div>
  )
}

/**
 * A project or branch on the tree: a tap picks it. Held, it lifts with everything under it: drop it on another project
 * or branch to put it under that one, or on the trunk to make it a project of its own.
 */
function LimbSpot(p: { spot: YgSpot; selected: boolean; drop: boolean; onPick: () => void; onLift: (x: number, y: number) => void }) {
  const s = p.spot
  const hold = useHold(p.onPick, p.onLift)
  const world = s.path === ''
  return (
    <button data-yg-tag={s.tag}
      class={`pj-node${world ? ' yg-world' : ''}${p.selected ? ' sel' : ''}${s.state === 'PARKED' ? ' parked' : ''}${p.drop ? ' yg-drop' : ''}`}
      aria-pressed={p.selected} aria-label={world ? `#${s.name}` : s.name}
      style={{ left: `${s.x}px`, top: `${s.y - s.h / 2}px`, width: `${s.w}px`, height: `${s.h}px` }} {...hold}>
      {!world && <span class="pj-dot" style={{ background: STATE_COLOR[s.state ?? 'ACTIVE'] }} />}
      <span class="grow ellipsis left">{world ? '#' + s.name : s.name}</span>
      {s.folded ? <span class="yg-folded micro">+{s.count}</span> : s.total > 0 && <span class="micro muted">{s.done}/{s.total}</span>}
    </button>
  )
}

/** Where a carried project or branch may go: not on itself, not inside itself, not where it already hangs. */
const graftable = (from: string, to: string) => {
  const parent = from.includes('/') ? from.slice(0, from.lastIndexOf('/')) : ''
  return to !== from && !to.startsWith(from + '/') && to !== parent
}

function FilterPanel(p: { filter: YgFilter; onFilter: (f: YgFilter) => void }) {
  const f = p.filter
  const set = (change: Partial<YgFilter>) => p.onFilter({ ...f, ...change })
  const toggle = (on: boolean, label: string, change: Partial<YgFilter>) => (
    <button class={`chip${on ? ' on' : ''}`} aria-pressed={on} onClick={() => set(change)}>{label}</button>
  )
  return (
    <section class="yg-filter" aria-label="ตัวกรอง">
      <div class="seg" role="tablist" aria-label="สถานะงาน">
        {SHOWS.map(([v, label]) => (
          <button key={v} role="tab" aria-selected={f.show === v} onClick={() => set({ show: v })}>{label}</button>
        ))}
      </div>
      <div class="chips" role="group" aria-label="วันที่">
        {DATES.map(([v, label]) => toggle(f.dates === v, label, { dates: v }))}
      </div>
      <div class="chips" role="group" aria-label="ความสำคัญ">
        {PRIORITIES.map(([v, label]) => {
          const on = f.priorities.includes(v)
          return toggle(on, label, { priorities: on ? f.priorities.filter((x) => x !== v) : [...f.priorities, v] })
        })}
      </div>
      <div class="chips" role="group" aria-label="อื่น ๆ">
        {toggle(f.blockedOnly, 'ติดรองานอื่น', { blockedOnly: !f.blockedOnly })}
        {toggle(f.starredOnly, 'เฉพาะติดดาว', { starredOnly: !f.starredOnly })}
        {toggle(f.parked, 'รวมกิ่งที่พับเก็บ', { parked: !f.parked })}
        {toggle(f.links, 'เส้นงานที่รอกัน', { links: !f.links })}
      </div>
      <div class="row-gap">
        <label class="search grow">
          <input type="search" placeholder="ค้นชื่องานหรือ tag" aria-label="ค้นชื่องานหรือ tag" value={f.text}
            onInput={(e) => set({ text: (e.target as HTMLInputElement).value })} />
        </label>
        {filterChanges(f) > 0 && <button class="ghost small-btn" onClick={() => p.onFilter(PLAIN_FILTER)}>ล้างตัวกรอง</button>}
      </div>
    </section>
  )
}

/**
 * Project Ygdrasil: every project in one tree that fills the page, from the trunk through each project (a world) and
 * its branches to the tasks, with the tasks that wait for one another tied across. Drag to move, pinch or the buttons
 * to zoom; tap a world or branch for its actions underneath; tap a task to tick it, long-press to open it.
 */
export function Ygdrasil(p: {
  out: YgOut
  task: (key: string) => Task | undefined
  busy: boolean
  filter: YgFilter
  folded: string[]
  onFilter: (f: YgFilter) => void
  onFolded: (tags: string[]) => void
  onBack: () => void
  onTick: (t: Task) => void
  onOpen: (t: Task) => void
  onAddTask: (tag: string, name: string) => void
  onAddBranch: (project: string, path: string, name: string) => void
  onOpenProject: (project: string) => void
  /** The task dropped on another project or branch: [tag] is where it goes, [from] the tag it had. */
  onMove: (t: Task, from: string, tag: string) => void
  /** A project or branch [from] dropped on [to] (empty: the trunk), to hang under it. */
  onGraft: (from: string, to: string) => void
}) {
  const [selTag, setSelTag] = useState('')
  const [filtering, setFiltering] = useState(false)
  const trunk = p.out.spots.find((s) => s.kind === 'TRUNK')!
  // On a narrow screen the tree opens readable, from the trunk, instead of shrunk to fit; "พอดี" shows all of it.
  const map = usePanZoom(p.out.width, p.out.height, 0.15, { scale: 0.62, anchorY: trunk.y })
  const sel = p.out.spots.find((s) => s.kind === 'LIMB' && s.tag === selTag)
  const changes = filterChanges(p.filter)
  const fold = (tag: string) => p.onFolded(p.folded.includes(tag) ? p.folded.filter((t) => t !== tag) : [...p.folded, tag])
  // A task, project or branch being carried: where the pointer is, and the branch (or the trunk, "") under it.
  const [carry, setCarry] = useState<{ task?: Task; from: string; label: string; x: number; y: number; moved: boolean } | null>(null)
  const [dropTag, setDropTag] = useState<string | null>(null)
  const carrying = useRef(carry)
  carrying.current = carry
  const dropping = useRef(dropTag)
  dropping.current = dropTag
  useEffect(() => {
    if (!carry) return
    map.freeze(true)
    const move = (e: PointerEvent) => {
      const c = carrying.current!
      const moved = c.moved || Math.hypot(e.clientX - c.x, e.clientY - c.y) > 6
      setCarry({ ...c, x: e.clientX, y: e.clientY, moved })
      const under = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-yg-tag]')
      setDropTag(under?.getAttribute('data-yg-tag') ?? null)
    }
    const up = () => {
      const c = carrying.current!
      const to = dropping.current
      if (c.task) {
        if (to && to !== c.from) p.onMove(c.task, c.from, to)
        else if (!c.moved) p.onOpen(c.task)
      } else if (to !== null && graftable(c.from, to)) p.onGraft(c.from, to)
      setCarry(null)
      setDropTag(null)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', up)
    return () => {
      map.freeze(false)
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', up)
    }
  }, [carry !== null])

  // Zoomed far out, tasks show as their rings only.
  const far = map.scale < 0.45

  return (
    <div class="pj-mapview">
      <header class="head">
        <button class="ghost" onClick={p.onBack}>‹ กลับ</button>
        <div class="grow">
          <h1 class="pj-h1-small">Project Ygdrasil</h1>
          <span class="small muted">{p.out.worlds} โปรเจกต์, {p.out.matched} งาน{p.out.links > 0 ? `, งานที่รอกัน ${p.out.links} เส้น` : ''}</span>
        </div>
        <button class={changes ? 'primary' : 'ghost'} aria-expanded={filtering} onClick={() => setFiltering(!filtering)}>
          ตัวกรอง{changes ? ` (${changes})` : ''}
        </button>
      </header>

      {filtering && <FilterPanel filter={p.filter} onFilter={p.onFilter} />}

      <div class="pj-map" {...map.boxProps}>
        <div class={`pj-map-layer${far ? ' far' : ''}`} data-testid="yg-layer" style={map.layerStyle}>
          <svg class="pj-edges" width={p.out.width} height={p.out.height} aria-hidden="true">
            {p.out.edges.map((e, i) => {
              if (e.kind === 'TASK') return <path key={i} d={e.d} fill="none" stroke="var(--border)" stroke-width="1.2" />
              if (e.kind === 'BRANCH') {
                const parked = e.state === 'PARKED'
                return <path key={i} d={e.d} fill="none" stroke-width="1.8" stroke={parked ? 'var(--control)' : STATE_COLOR[e.state ?? 'ACTIVE']}
                  stroke-opacity={parked ? 1 : 0.8} stroke-dasharray={parked ? '6 6' : undefined} />
              }
              return <path key={i} class={`yg-link${e.kind === 'LINK_ACROSS' ? ' across' : ''}`} d={e.d} fill="none" />
            })}
          </svg>
          {p.out.spots.map((s, i) => {
            if (s.kind === 'TRUNK') {
              return (
                <button key="trunk" data-yg-tag="" class={`pj-node root yg-trunk${selTag === '' ? ' sel' : ''}${carry && !carry.task && dropTag === '' && graftable(carry.from, '') ? ' yg-drop' : ''}`} aria-pressed={selTag === ''} aria-label="Ygdrasil"
                  style={{ left: `${s.x}px`, top: `${s.y - s.h / 2}px`, width: `${s.w}px`, height: `${s.h}px` }} onClick={() => setSelTag('')}>
                  <span class="grow ellipsis left">Ygdrasil</span>
                  <span class="micro">{s.count}</span>
                </button>
              )
            }
            if (s.kind === 'LIMB') {
              const drop = !!carry && s.tag === dropTag && (carry.task ? s.tag !== carry.from : graftable(carry.from, s.tag!))
              return (
                <LimbSpot key={`l${s.tag}`} spot={s} selected={s.tag === selTag} drop={drop} onPick={() => setSelTag(s.tag!)}
                  onLift={(x, y) => setCarry({ from: s.tag!, label: (s.path === '' ? '#' : '') + s.name, x, y, moved: false })} />
              )
            }
            if (s.kind === 'TASK') {
              const t = p.task(s.key!)
              return t ? (
                <TaskSpot key={`t${s.key}`} task={t} spot={s} busy={p.busy} onTick={() => p.onTick(t)} onOpen={() => p.onOpen(t)}
                  onLift={(x, y) => setCarry({ task: t, from: s.tag!, label: t.title, x, y, moved: false })} />
              ) : null
            }
            return <span key={`m${i}`} class="pj-more small faint" style={{ left: `${s.x + 6}px`, top: `${s.y - 9}px` }}>และอีก {s.count} งาน</span>
          })}
        </div>
        <div class="pj-zoom">
          <button aria-label="ซูมเข้า" onClick={() => map.zoomBy(1.25)}>+</button>
          <button aria-label="ซูมออก" onClick={() => map.zoomBy(0.8)}>−</button>
          <button aria-label="พอดีจอ" class="fit" onClick={map.fit}>พอดี</button>
        </div>
        {p.out.worlds === 0 && <p class="yg-empty muted">ไม่มีงานตรงกับตัวกรอง</p>}
      </div>

      {carry && (
        <div class="yg-ghost" aria-hidden="true" style={{ left: `${carry.x + 14}px`, top: `${carry.y + 10}px` }}>
          {carry.label}
          <span class="small">
            {carry.task
              ? dropTag && dropTag !== carry.from ? `ย้ายไป #${dropTag}` : 'ลากไปวางบนกิ่ง'
              : dropTag !== null && graftable(carry.from, dropTag)
                ? dropTag === '' ? 'แยกเป็นโปรเจกต์ของตัวเอง' : `ไปไว้ใต้ #${dropTag}`
                : 'ลากไปวางบนโปรเจกต์ กิ่ง หรือลำต้น'}
          </span>
        </div>
      )}

      <section class="pj-sel" aria-label="กิ่งที่เลือก">
        {!sel ? (
          <>
            <div class="row-gap">
              <span class="strong">Ygdrasil</span>
              <span class="small muted">ทุกโปรเจกต์ {trunk.count} งาน</span>
            </div>
            <div class="row-gap wrap">
              <button class="ghost small-btn" onClick={() => p.onFolded([])}>กางทั้งหมด</button>
              <button class="ghost small-btn" onClick={() => p.onFolded(p.out.tags.filter((t) => !t.includes('/')))}>พับทุกโปรเจกต์</button>
            </div>
            <span class="small muted">แตะโปรเจกต์หรือกิ่งเพื่อจัดการ กดค้างแล้วลากโปรเจกต์ไปวางบนอีกโปรเจกต์เพื่อรวมเป็นกิ่ง ลากกลับมาที่ลำต้นเพื่อแยกออก</span>
          </>
        ) : (
          <>
            <div class="row-gap">
              {sel.path !== '' && <span class="pj-dot big" style={{ background: STATE_COLOR[sel.state ?? 'ACTIVE'] }} />}
              <span class="strong">{sel.path === '' ? '#' + sel.name : sel.name}</span>
              <span class="small muted">#{sel.tag}{sel.path !== '' && sel.state ? `, ${STATE_LABEL[sel.state]}` : ''}</span>
            </div>
            <div class="row-gap wrap">
              <button class="primary small-btn" disabled={p.busy} onClick={() => p.onAddTask(sel.tag!, sel.name!)}>+ งาน</button>
              <button class="ghost small-btn" disabled={p.busy} onClick={() => p.onAddBranch(sel.project!, sel.path!, sel.name!)}>+ กิ่ง</button>
              <button class="ghost small-btn" onClick={() => fold(sel.tag!)}>{sel.folded ? 'กาง' : 'พับ'}</button>
              <button class="ghost small-btn" onClick={() => p.onOpenProject(sel.project!)}>เปิด Mind map</button>
            </div>
            <span class="small muted">แตะงานเพื่อติ๊ก กดค้างแล้วลากงาน กิ่ง หรือทั้งโปรเจกต์ไปวางบนกิ่งอื่นเพื่อย้าย</span>
          </>
        )}
      </section>
    </div>
  )
}
