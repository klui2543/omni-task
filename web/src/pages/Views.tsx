import { useEffect, useMemo, useState } from 'preact/hooks'
import { today } from '../core'
import type { PageProps } from '../Home'
import { BUCKETS, DEFAULT_QUERY, KINDS, ListQuery, PRIORITIES, SORTS, STATUSES, filterCount, labelOf, storedQuery } from '../query'
import { URGENT_RULES, urgentRule } from '../settings'
import type { Task, ViewsIn } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { FilterPanel, SortDialog } from '../ui/ListTools'
import { CalendarViews } from '../ui/views/Calendar'
import { Gantt } from '../ui/views/Gantt'
import { Kanban } from '../ui/views/Kanban'
import { Matrix } from '../ui/views/Matrix'
import { StatusQuickAdd } from '../ui/views/StatusQuickAdd'
import type { ViewCtx } from '../ui/views/ctx'
import { addDays } from '../ui/views/dates'
import { useStored } from '../ui/views/hooks'
import { QuickAdd } from './Tasks'

export const MODES = ['kanban', 'matrix', 'gantt', 'calendar'] as const
export type ViewMode = (typeof MODES)[number]
export const MODE_LABEL: Record<ViewMode, string> = { kanban: 'Kanban', matrix: 'Matrix', gantt: 'Gantt', calendar: 'ปฏิทิน' }

/** A project's own views: the same four over only its tasks, with the way back to its overview (as on Android). */
export interface ViewsScope {
  project: string
  mode: ViewMode
  setMode: (mode: ViewMode) => void
  onOverview: () => void
}

/**
 * The Views tab: Kanban, Matrix, Gantt and the calendars over the same filters as the task list (the filter and
 * sort are shared with it, as on Android). Every change goes through the shared edit path like the other pages.
 */
export function ViewsPage(p: PageProps & { scope?: ViewsScope }) {
  const [storedMode, setStoredMode] = useStored('omni.viewMode', 'kanban', MODES)
  const mode = p.scope?.mode ?? storedMode
  const setMode = p.scope?.setMode ?? setStoredMode
  const [hide, setHide] = useStored('omni.hideDone', 'yes', ['yes', 'no'] as const)
  const hideDone = hide === 'yes'
  const [stored, setQueryState] = useState<ListQuery>(storedQuery.get)
  const setQuery = (q: ListQuery) => { setQueryState(q); storedQuery.set(q) }
  // Inside a project the tag filter is the project itself (it also covers its branches, #project/branch).
  const query: ListQuery = p.scope ? { ...stored, tags: [p.scope.project] } : stored
  const [urgent, setUrgent] = useState(urgentRule.get)
  const [overlay, setOverlay] = useState<'filter' | 'sort' | 'urgent' | null>(null)
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const [adding, setAdding] = useState<null | { status?: Task['status'] }>(null)
  const [notice, setNotice] = useState('')
  const [ganttFirst, setGanttFirst] = useState(() => addDays(today(), -1))
  const day = today()

  useEffect(() => {
    if (!notice) return
    const t = setTimeout(() => setNotice(''), 7_000)
    return () => clearTimeout(t)
  }, [notice])

  const out = useMemo(
    () => p.snapshot?.views({ today: day, query, hideDone, urgent, ganttFirst } satisfies ViewsIn) ?? null,
    [p.snapshot, day, query, hideDone, urgent, ganttFirst],
  )
  const tags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 20).map(([g]) => g)
  }, [p.snapshot])

  const all = p.snapshot?.tasks ?? []
  const current: Task | null = selected
    ? (() => {
        const byKey = p.snapshot?.byKey.get(selected.key)
        return byKey?.title === selected.title ? byKey : all.find((t) => t.title === selected.title) ?? null
      })()
    : null
  const open = (t: Task) => setSelected({ key: t.key, title: t.title })

  // Each active filter as a chip that takes it off, as on the Tasks page.
  const active: [string, () => void][] = [
    ...query.kinds.map((k): [string, () => void] => [labelOf(KINDS, k), () => setQuery({ ...query, kinds: query.kinds.filter((x) => x !== k) })]),
    ...query.buckets.map((k): [string, () => void] => [labelOf(BUCKETS, k), () => setQuery({ ...query, buckets: query.buckets.filter((x) => x !== k) })]),
    ...query.priorities.map((k): [string, () => void] => [labelOf(PRIORITIES, k), () => setQuery({ ...query, priorities: query.priorities.filter((x) => x !== k) })]),
    ...(p.scope ? [] : query.tags.map((k): [string, () => void] => ['#' + k, () => setQuery({ ...query, tags: query.tags.filter((x) => x !== k) })])),
    ...(filterCount({ ...query, kinds: [], buckets: [], priorities: [], tags: [] })
      ? [[query.statuses.map((s) => labelOf(STATUSES, s)).join(', ') || 'ทุกสถานะ', () => setQuery({ ...query, statuses: DEFAULT_QUERY.statuses })] as [string, () => void]]
      : []),
  ]
  const sorts = query.sorts.length ? query.sorts : DEFAULT_QUERY.sorts
  const sortText = `${labelOf(SORTS, sorts[0].by)} ${sorts[0].ascending ? '↑' : '↓'}${sorts.length > 1 ? ` +${sorts.length - 1}` : ''}`

  const ctx: ViewCtx | null = out && {
    p, out, today: day, open, selectedKey: current?.key,
    task: (key) => p.snapshot?.byKey.get(key),
  }

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
      <main class="page views">
        <header class="head vhead">
          {p.scope && <button class="ghost" onClick={p.scope.onOverview}>‹ กลับ</button>}
          <h1>{p.scope ? p.scope.project : 'มุมมอง'}</h1>
          <div class="seg big" role="tablist" aria-label="ชนิดมุมมอง">
            {p.scope && <button role="tab" aria-selected={false} onClick={p.scope.onOverview}>ภาพรวม</button>}
            {MODES.map((m) => <button key={m} role="tab" aria-selected={m === mode} onClick={() => setMode(m)}>{MODE_LABEL[m]}</button>)}
          </div>
          <span class="grow" />
          <div class="head-tools">
            <button class={`chip tool${filterCount(query) ? ' on' : ''}`} onClick={() => setOverlay('filter')}>
              กรอง{filterCount(query) ? ` ${filterCount(query)}` : ''}
            </button>
            {mode === 'kanban' && <button class="chip tool" onClick={() => setOverlay('sort')}>เรียง: {sortText}</button>}
            {mode === 'matrix' && (
              <div class="menu-wrap">
                <button class="chip tool" aria-haspopup="menu" aria-expanded={overlay === 'urgent'} onClick={() => setOverlay(overlay === 'urgent' ? null : 'urgent')}>
                  ด่วน = {URGENT_RULES.find(([k]) => k === urgent)![1]}
                </button>
                {overlay === 'urgent' && (
                  <>
                    <div class="menu-scrim" onClick={() => setOverlay(null)} />
                    <div class="popmenu" role="menu">
                      {URGENT_RULES.map(([rule, label]) => (
                        <button key={rule} role="menuitemradio" aria-checked={urgent === rule} class={urgent === rule ? 'on' : ''}
                          onClick={() => { urgentRule.set(rule); setUrgent(rule); setOverlay(null) }}>ด่วน = {label}</button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
            <button class="ghost narrow-only" onClick={() => p.onNavigate('settings')}>ตั้งค่า</button>
          </div>
        </header>

        {active.length > 0 && (
          <div class="chips vchips">
            {active.map(([label, off]) => (
              <span key={label} class="chip on saved">
                <span class="chip-main">{label}</span>
                <button class="chip-x" aria-label={`เอา ${label} ออก`} onClick={off}>×</button>
              </span>
            ))}
          </div>
        )}
        {notice && (
          <section class="notice" role="alert">
            <span class="grow">{notice}</span>
            <button class="link quiet" onClick={() => setNotice('')}>ซ่อน</button>
          </section>
        )}

        <div class={`vbody ${mode}`}>
          {!ctx ? (
            <p class="muted pad">กำลังโหลด...</p>
          ) : mode === 'kanban' ? (
            <Kanban {...ctx} hideDone={hideDone} onShowDone={() => setHide('no')} onAdd={(status) => setAdding({ status })} />
          ) : mode === 'matrix' ? (
            <Matrix {...ctx} urgent={urgent} onNotice={setNotice} />
          ) : mode === 'gantt' ? (
            <Gantt {...ctx} first={ganttFirst} setFirst={setGanttFirst} onConnect={p.onConnectCalendar} />
          ) : (
            <CalendarViews {...ctx} onConnect={p.onConnectCalendar} />
          )}
        </div>

        <button class="fab narrow-only" aria-label="เพิ่มงาน" onClick={() => setAdding({})}>+</button>
        {adding && !adding.status && <QuickAdd {...p} onClose={() => setAdding(null)} />}
        {adding?.status && (
          <StatusQuickAdd {...p} status={adding.status} statusLabel={labelOf(STATUSES, adding.status)} onClose={() => setAdding(null)} />
        )}
        {overlay === 'filter' && (
          <FilterPanel query={query} tags={tags} shown={out?.shown ?? 0} onChange={setQuery} onClose={() => setOverlay(null)}
            hideDone={{ on: hideDone, set: (on) => setHide(on ? 'yes' : 'no') }} />
        )}
        {overlay === 'sort' && <SortDialog query={query} onChange={setQuery} onClose={() => setOverlay(null)} noGroup />}
      </main>
      {current && (
        <>
          <div class="pane-scrim" onClick={() => setSelected(null)} />
          <aside class="pane" role="dialog" aria-label="แก้ไขงาน">
            <EditPanel {...p} task={current} onOpen={open} onClose={() => setSelected(null)} />
          </aside>
        </>
      )}
    </div>
  )
}
