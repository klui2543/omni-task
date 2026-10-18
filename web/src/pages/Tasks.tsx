import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { host } from '../host'
import { today } from '../core'
import type { PageProps } from '../Home'
import { BUCKETS, DEFAULT_QUERY, GROUPS, KINDS, ListQuery, PRIORITIES, SORTS, STATUSES, filterCount, labelOf, storedQuery } from '../query'
import type { Group, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { FilterPanel, SortDialog } from '../ui/ListTools'
import { LayoutBar, useLayout, type Layout } from '../ui/LayoutBar'
import { ViewsPage } from './Views'
import { PageHead } from '../ui/PageHead'
import { TaskRow } from '../ui/TaskRow'
import { useStored } from '../ui/views/hooks'

// Quick add moved to ui/QuickAdd.tsx (the side bar, the bottom bar and a Kanban column open it); the pages that
// import it from here still find it.
export { QuickAdd } from '../ui/QuickAdd'

/** Folded groups as "grouping:label", as Android remembers them per grouping. */
const FOLD_KEY = 'omni.foldedGroups'
/** Before, a fold was remembered by its label alone; those count as folds of the grouping in use. */
const OLD_FOLD_KEY = 'omni.folded'
const read = (k: string) => {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
// Kept even when empty, so the old key is not read again once every group is open.
const write = (k: string, v: string) => {
  try {
    localStorage.setItem(k, v)
  } catch {
    /* just not kept */
  }
}
const lines = (v: string | null) => (v ?? '').split('\n').filter(Boolean)
const storedFolds = (groupBy: string) => (read(FOLD_KEY) !== null ? lines(read(FOLD_KEY)) : lines(read(OLD_FOLD_KEY)).map((l) => `${groupBy}:${l}`))

const TONE: Record<Group['tone'], string> = { ALERT: 'alert', ACCENT: 'accent', PLAIN: '', MUTED: 'muted' }

/** The Tasks tab: the list, or one of the views over the same filters (Kanban, Matrix, Gantt, calendar) behind one switch. */
export function TasksPage(p: PageProps) {
  const [layout, setLayout] = useLayout()
  return layout === 'list' ? <TaskList {...p} layout={layout} onLayout={setLayout} /> : <ViewsPage {...p} layout={layout} onLayout={setLayout} />
}

function TaskList(p: PageProps & { layout: Layout; onLayout: (l: Layout) => void }) {
  const [text, setText] = useState('')
  const [hide, setHide] = useStored('omni.hideDone', 'yes', ['yes', 'no'] as const)
  const [showDone, setShowDone] = useState(false)
  const [query, setQueryState] = useState<ListQuery>(storedQuery.get)
  // Folded groups are remembered on this device for each grouping, as Android remembers them.
  const [folded, setFolded] = useState<string[]>(() => storedFolds(query.groupBy))
  const setQuery = (q: ListQuery) => { setQueryState(q); storedQuery.set(q) }
  const [overlay, setOverlay] = useState<'filter' | 'sort' | null>(null)
  // The open task, by key and title: a key is its line, so the title confirms it is still the same task.
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const search = useRef<HTMLInputElement>(null)

  // "/" jumps to the search box, as on the web versions of TickTick and Linear.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = host.target(e)
      const typing = target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
      // Not while a sheet is open over the page (quick add belongs to the shell now).
      if (e.key === '/' && !typing && !overlay && !host.find('[aria-modal=true]')) {
        e.preventDefault()
        search.current?.focus()
      }
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [overlay])

  const list = useMemo(() => p.snapshot?.list({ ...query, text }), [p.snapshot, query, text])
  const shown = list ? new Set(list.groups.flatMap((g) => g.keys)).size : 0
  const doneToday = query.statuses.includes('DONE') ? [] : p.snapshot?.tasks.filter((t) => t.status === 'DONE' && t.done === today() && !t.parent) ?? []
  const tags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 20).map(([g]) => g)
  }, [p.snapshot])

  const all = p.snapshot?.tasks ?? []
  const current: Task | null = selected
    ? (() => {
        const byKey = p.snapshot?.find(selected.key)
        return byKey?.title === selected.title ? byKey : all.find((t) => t.title === selected.title) ?? null
      })()
    : null
  const openTask = (t: Task) => setSelected({ key: t.key, title: t.title })

  // The notes and list notes the filter can narrow to, as on Android's filter sheet.
  const filterNotes = useMemo(() => [...new Set((p.snapshot?.tasks ?? []).map((t) => (t.note ?? '').split('/').pop()!.replace(/\.md$/i, '')).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'th')), [p.snapshot])
  const filterLists = useMemo(() => [...new Set((p.snapshot?.listItems() ?? []).map((t) => t.list ?? '').filter(Boolean))].sort((a, b) => a.localeCompare(b, 'th')), [p.snapshot])
  // What the filter row shows: each active filter as a chip that takes it off.
  const active: [string, () => void][] = [
    ...query.kinds.map((k): [string, () => void] => [labelOf(KINDS, k), () => setQuery({ ...query, kinds: query.kinds.filter((x) => x !== k) })]),
    ...query.buckets.map((k): [string, () => void] => [labelOf(BUCKETS, k), () => setQuery({ ...query, buckets: query.buckets.filter((x) => x !== k) })]),
    ...query.priorities.map((k): [string, () => void] => [labelOf(PRIORITIES, k), () => setQuery({ ...query, priorities: query.priorities.filter((x) => x !== k) })]),
    ...query.tags.map((k): [string, () => void] => ['#' + k, () => setQuery({ ...query, tags: query.tags.filter((x) => x !== k) })]),
    ...query.lists.map((k): [string, () => void] => [k, () => setQuery({ ...query, lists: query.lists.filter((x) => x !== k) })]),
    ...query.notes.map((k): [string, () => void] => [k, () => setQuery({ ...query, notes: query.notes.filter((x) => x !== k) })]),
    ...(filterCount({ ...query, kinds: [], buckets: [], priorities: [], tags: [], notes: [], lists: [] })
      ? [[query.statuses.map((s) => labelOf(STATUSES, s)).join(', ') || 'ทุกสถานะ', () => setQuery({ ...query, statuses: DEFAULT_QUERY.statuses })] as [string, () => void]]
      : []),
  ]
  const sorts = query.sorts.length ? query.sorts : DEFAULT_QUERY.sorts
  const sortText = `${labelOf(SORTS, sorts[0].by)} ${sorts[0].ascending ? '↑' : '↓'}${sorts.length > 1 ? ` +${sorts.length - 1}` : ''}`
  const fold = (key: string) => {
    const next = folded.includes(key) ? folded.filter((f) => f !== key) : [...folded, key]
    setFolded(next)
    write(FOLD_KEY, next.join('\n'))
  }
  // The search text counts as a filter (and "clear all" clears it too), as on Android.
  const filters = filterCount(query) + (text.trim() ? 1 : 0)

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
    <main class="page">
      <PageHead title="งาน">
          <label class="search">
            <span class="faint">ค้นหา</span>
            <input ref={search} aria-label="ค้นหาชื่องาน" placeholder="ค้นหาชื่องาน" value={text} onInput={(e) => setText(e.currentTarget.value)} />
            <span class="kbd wide-only">/</span>
          </label>
      </PageHead>

      <LayoutBar layout={p.layout} onLayout={p.onLayout} filter={{ count: filters, onOpen: () => setOverlay('filter') }}
        end={<button class="chip tool" aria-label="เรียงและจัดกลุ่ม" title={`กลุ่ม: ${labelOf(GROUPS, query.groupBy)}, เรียง: ${sortText}`} onClick={() => setOverlay('sort')}>เรียง ⇅</button>} />
      {active.length > 0 && (
        <div class="chips">
          {active.map(([label, off]) => (
            <span key={label} class="chip on saved">
              <span class="chip-main">{label}</span>
              <button class="chip-x" aria-label={`เอา ${label} ออก`} onClick={off}>×</button>
            </span>
          ))}
        </div>
      )}

      {!p.snapshot ? (
        <p class="muted pad">กำลังโหลด...</p>
      ) : (
        <div class="groups">
          {list!.groups.length === 0 && (
            <section class="group empty">{text ? 'ไม่มีงานตรงกับคำค้น' : filters ? 'ไม่มีงานตรงกับตัวกรอง' : 'ไม่มีงานค้าง'}</section>
          )}
          {list!.groups.map((g) => {
            const key = `${query.groupBy}:${g.label}`
            const open = !folded.includes(key)
            return (
              <section key={g.label} class="group">
                <h2>
                  <button class="group-head" aria-expanded={open} onClick={() => fold(key)}>
                    <span class={`caret${open ? ' open' : ''}`}>›</span>
                    <span class={`group-label ${TONE[g.tone]}`}>{g.label}</span>
                    <span class="count">{g.keys.length}</span>
                  </button>
                </h2>
                {open && (
                  <ul class="plain">
                    {g.keys.map((k) => {
                      const t = p.snapshot!.find(k)!
                      return (
                        <TaskRow key={k} task={t} progress={list!.progress[k]} waitingOn={list!.waiting?.[k]} busy={p.busy} selected={current?.key === k}
                          onToggle={() => p.tick(t)} onOpen={() => openTask(t)} />
                      )
                    })}
                  </ul>
                )}
              </section>
            )
          })}
          {doneToday.length > 0 && (
            <section class="group">
              <h2>
                <button class="group-head" aria-expanded={showDone} onClick={() => setShowDone(!showDone)}>
                  <span class={`caret${showDone ? ' open' : ''}`}>›</span>
                  <span class="group-label muted">เสร็จวันนี้</span>
                  <span class="count">{doneToday.length}</span>
                </button>
              </h2>
              {showDone && (
                <ul class="plain">
                  {doneToday.map((t) => (
                    <TaskRow key={t.key} task={t} busy={p.busy} selected={current?.key === t.key} onToggle={() => p.tick(t)} onOpen={() => openTask(t)} />
                  ))}
                </ul>
              )}
            </section>
          )}
        </div>
      )}

      {overlay === 'filter' && (
        // Android's one filter sheet has the "hide done" switch here too, though only the views honour it; the choice is shared with Views.
        <FilterPanel query={query} tags={tags} notes={filterNotes} lists={filterLists} shown={shown} onChange={setQuery} onClose={() => setOverlay(null)}
          onClearText={() => setText('')}
          hideDone={{ on: hide === 'yes', set: (on) => setHide(on ? 'yes' : 'no') }} />
      )}
      {overlay === 'sort' && <SortDialog query={query} onChange={setQuery} onClose={() => setOverlay(null)} />}
    </main>
    {current && (
      <>
        <div class="pane-scrim" onClick={() => setSelected(null)} />
        <aside class="pane" role="dialog" aria-label="แก้ไขงาน">
          <EditPanel {...p} task={current} onOpen={openTask} onClose={() => setSelected(null)} />
        </aside>
      </>
    )}
    </div>
  )
}
