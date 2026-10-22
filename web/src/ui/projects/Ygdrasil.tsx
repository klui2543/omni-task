import { useEffect, useRef, useState } from 'preact/hooks'
import { host } from '../../host'
import { DEFAULT_QUERY, type ListQuery } from '../../query'
import type { Task, YgFilter, YgOut, YgSpot } from '../../types'
import { FilterBody, SaveFilter, moreInUse } from '../ListTools'
import { RingButton } from './bits'
import { STATE_COLOR, STATE_LABEL } from './MindMap'
import { usePanZoom } from './panZoom'
import { HomePicker, StrayTray } from './StrayTray'
import { today } from '../../core'

/** The tree's plain filter: the task list's default query, someday work shown, wait lines on. */
export const PLAIN_FILTER: YgFilter = {
  query: { ...DEFAULT_QUERY, text: '' }, blockedOnly: false, starredOnly: false, parked: false, someday: true, links: true,
}

/** The filter's query as a list query (the search box text aside), for the shared filter panel. */
const listQueryOf = (f: YgFilter): ListQuery => ({ ...DEFAULT_QUERY, ...f.query })

/** How many choices differ from the plain filter (Ygdrasil.Filter.changed). */
export const filterChanges = (f: YgFilter) =>
  moreInUse(listQueryOf(f)) + (f.query.tags ?? []).filter((t) => t === 'kluido' || t === 'claudedo').length +
  (f.query.buckets ?? []).filter((b) => b === 'TODAY' || b === 'OVERDUE').length + ((f.query.text ?? '').trim() ? 1 : 0) +
  [f.blockedOnly, f.starredOnly, f.parked, !f.someday, !f.links].filter(Boolean).length

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
 * A task on the tree: a tap on its ring ticks it, a tap anywhere else (or a right click) opens it. Held, it lifts:
 * drag it onto another project or branch to move it there, or let go where it is to open it.
 */
function TaskSpot(p: { task: Task; spot: YgSpot; busy: boolean; onTick: () => void; onOpen: () => void; onLift: (x: number, y: number) => void }) {
  const hold = useHold(p.onOpen, p.onLift, p.onOpen)
  const done = p.task.status === 'DONE'
  return (
    <div class={`pj-mtask-wrap pj-mtask${done ? ' done' : ''}${p.spot.someday ? ' someday' : ''}`} style={{ left: `${p.spot.x}px`, top: `${p.spot.y - p.spot.h / 2}px`, width: `${p.spot.w}px`, height: `${p.spot.h}px` }}>
      <RingButton task={p.task} busy={p.busy} onTick={p.onTick} />
      <button class="pj-mtask-open" aria-label={`เปิด ${p.task.title}`} {...hold}>
        <span class="ellipsis">{p.task.title}</span>
        {p.spot.someday && <span class="yg-someday">พักไว้</span>}
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

/** The tree's filter, above the tree: the task list's two-layer filter, and the tree's own choices under "more". */
function FilterPanel(p: { filter: YgFilter; tags: string[]; onFilter: (f: YgFilter) => void }) {
  const f = p.filter
  const q = listQueryOf(f)
  const [more, setMore] = useState(moreInUse(q) > 0)
  const set = (change: Partial<YgFilter>) => p.onFilter({ ...f, ...change })
  const sw = (on: boolean, title: string, note: string | null, change: Partial<YgFilter>) => (
    <label class="setting">
      <span class="stack">
        <span>{title}</span>
        {note && <span class="muted small">{note}</span>}
      </span>
      <input type="checkbox" class="switch" role="switch" aria-label={title} checked={on} onChange={() => set(change)} />
    </label>
  )
  return (
    <section class="yg-filter" aria-label="ตัวกรอง">
      <div class="panel-head">
        <span class="strong">กรอง</span>
        <button class="link danger" onClick={() => p.onFilter({ ...PLAIN_FILTER, links: f.links })}>ล้างทั้งหมด</button>
      </div>
      <label class="search">
        <input type="search" placeholder="ค้นชื่องานหรือรายละเอียด" aria-label="ค้นชื่องานหรือรายละเอียด" value={f.query.text ?? ''}
          onInput={(e) => set({ query: { ...f.query, text: (e.target as HTMLInputElement).value } })} />
      </label>
      <FilterBody query={q} onChange={(next) => set({ query: { ...next, text: f.query.text ?? '' } })} tags={p.tags} more={more} onMore={setMore}
        extraInUse={[f.blockedOnly, f.starredOnly, f.parked, !f.someday].filter(Boolean).length}
        extra={(
          <div class="filter-section" role="group" aria-label="เฉพาะ Ygdrasil">
            <span class="small accent-text strong">เฉพาะ Ygdrasil</span>
            {sw(f.blockedOnly, 'ติดรองานอื่น', 'เฉพาะงานที่รองานอื่นที่ยังไม่เสร็จ', { blockedOnly: !f.blockedOnly })}
            {sw(f.someday, 'แสดงงานที่พักไว้', 'งาน #สักวัน เป็นสีจางพร้อมป้าย', { someday: !f.someday })}
            {sw(f.parked, 'รวมกิ่งที่พับเก็บ', null, { parked: !f.parked })}
            {sw(f.starredOnly, 'เฉพาะโปรเจกต์ติดดาว', null, { starredOnly: !f.starredOnly })}
          </div>
        )} />
      {more && <SaveFilter query={q} />}
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
  /** Every tag on the tasks, for the filter's Tag section. */
  tags?: string[]
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
  /** A task with no project given one (from the tray): [tag] goes in front of its tags. */
  onHome: (t: Task, tag: string) => void
  /** A tag that is not a project made one, from the tray. */
  onMakeProject: (tag: string) => void
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
  // [stray]: a task from the tray, which has no project yet.
  const [carry, setCarry] = useState<{ task?: Task; stray?: boolean; from: string; label: string; x: number; y: number; moved: boolean } | null>(null)
  // The tray of tasks with no project, under the tree, and the task whose new home is being picked.
  const strayCount = p.out.strays.reduce((n, g) => n + g.due.length, 0)
  const [trayOpen, setTrayOpen] = useState(false)
  const [picking, setPicking] = useState<Task | null>(null)
  const tray = useRef<HTMLElement | null>(null)
  useEffect(() => { if (strayCount === 0) setTrayOpen(false) }, [strayCount])
  // Folded branches opened for a moment while something is held over them; folded again when it is let go.
  const sprung = useRef<string[]>([])
  const foldedNow = useRef(p.folded)
  foldedNow.current = p.folded
  const at = useRef({ x: 0, y: 0 })
  const [dropTag, setDropTag] = useState<string | null>(null)
  const carrying = useRef(carry)
  carrying.current = carry
  const dropping = useRef(dropTag)
  dropping.current = dropTag
  useEffect(() => {
    if (!carry) return
    map.freeze(true)
    const move = (e: PointerEvent) => {
      at.current = { x: e.clientX, y: e.clientY }
      const c = carrying.current!
      const moved = c.moved || Math.hypot(e.clientX - c.x, e.clientY - c.y) > 6
      setCarry({ ...c, x: e.clientX, y: e.clientY, moved })
      const under = host.at(e.clientX, e.clientY)?.closest('[data-yg-tag]')
      setDropTag(under?.getAttribute('data-yg-tag') ?? null)
    }
    const up = () => {
      const c = carrying.current!
      const to = dropping.current
      if (c.task && c.stray) {
        if (to) p.onHome(c.task, to)
        else if (!c.moved) setPicking(c.task)
      } else if (c.task) {
        if (to && to !== c.from) p.onMove(c.task, c.from, to)
        else if (!c.moved) p.onOpen(c.task)
      } else if (to !== null && graftable(c.from, to)) p.onGraft(c.from, to)
      setCarry(null)
      setDropTag(null)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', up)
    // Held near the top of the tree, or just above the tray, the tree slides to bring more of it in reach; not while
    // over a project or branch, so the one under the finger stays put.
    let frame = requestAnimationFrame(function slide() {
      const box = map.boxEl()
      if (box && dropping.current === null) {
        const r = box.getBoundingClientRect()
        const bottom = tray.current ? tray.current.getBoundingClientRect().top : r.bottom
        const { x, y } = at.current
        const dy = y > r.top - 20 && y < r.top + 48 ? 8 : y > bottom - 48 && y < bottom + 8 ? -8 : 0
        if (dy && x > r.left && x < r.right) {
          map.panBy(0, dy)
          setDropTag(host.at(x, y)?.closest('[data-yg-tag]')?.getAttribute('data-yg-tag') ?? null)
        }
      }
      frame = requestAnimationFrame(slide)
    })
    return () => {
      cancelAnimationFrame(frame)
      if (sprung.current.length) p.onFolded([...new Set([...foldedNow.current, ...sprung.current])])
      sprung.current = []
      map.freeze(false)
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', up)
    }
  }, [carry !== null])

  // Held over a folded project or branch for a moment, it opens to show its branches.
  useEffect(() => {
    if (!carry || !dropTag || !p.folded.includes(dropTag)) return
    const t = setTimeout(() => {
      sprung.current = [...sprung.current, dropTag]
      p.onFolded(foldedNow.current.filter((x) => x !== dropTag))
    }, 600)
    return () => clearTimeout(t)
  }, [dropTag, carry !== null])

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
        {strayCount > 0 && (
          <button class={`yg-stray-btn${trayOpen ? ' on' : ''}`} aria-expanded={trayOpen} onClick={() => setTrayOpen(!trayOpen)}>
            ยังไม่มีบ้าน <span class="yg-tray-count">{strayCount}</span>
          </button>
        )}
        <button class={changes ? 'primary' : 'ghost'} aria-expanded={filtering} onClick={() => setFiltering(!filtering)}>
          ตัวกรอง{changes ? ` (${changes})` : ''}
        </button>
      </header>

      {filtering && <FilterPanel filter={p.filter} tags={p.tags ?? []} onFilter={p.onFilter} />}

      <div class="yg-stage">
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
      {trayOpen && strayCount > 0 && (
        <StrayTray strays={p.out.strays} task={p.task} today={today()} low={!!carry} trayRef={tray} onClose={() => setTrayOpen(false)}
          onPick={setPicking} onMakeProject={p.onMakeProject}
          onLift={(t, x, y) => { at.current = { x, y }; setCarry({ task: t, stray: true, from: '', label: t.title, x, y, moved: false }) }} />
      )}
      </div>

      {picking && (
        <HomePicker task={picking} stray={picking.tags.find((g) => p.out.strays.some((s) => s.tag && (g === s.tag || g.startsWith(s.tag + '/'))))?.split('/')[0]}
          tags={p.out.tags} onCancel={() => setPicking(null)} onMove={(tag) => { const t = picking; setPicking(null); p.onHome(t, tag) }} />
      )}

      {carry && (
        <div class="yg-ghost" aria-hidden="true" style={{ left: `${carry.x - host.origin().left + 14}px`, top: `${carry.y - host.origin().top + 10}px` }}>
          {carry.label}
          <span class="small">
            {carry.task
              ? dropTag && dropTag !== carry.from ? `ย้ายไป #${dropTag}` : carry.stray ? 'ลากขึ้นไปวางบนกิ่ง' : 'ลากไปวางบนกิ่ง'
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
              <button class="ghost small-btn" aria-pressed={p.filter.links} onClick={() => p.onFilter({ ...p.filter, links: !p.filter.links })}>{p.filter.links ? 'ซ่อนเส้นงานที่รอกัน' : 'แสดงเส้นงานที่รอกัน'}</button>
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
