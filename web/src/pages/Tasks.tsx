import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { today } from '../core'
import type { PageProps } from '../Home'
import { BUCKETS, DEFAULT_QUERY, GROUPS, KINDS, ListQuery, PRIORITIES, SORTS, STATUSES, filterCount, labelOf, storedQuery } from '../query'
import type { Group, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { FilterPanel, SortDialog } from '../ui/ListTools'
import { PageHead } from '../ui/PageHead'
import { TaskRow } from '../ui/TaskRow'

const DRAFT_KEY = 'omni.draft'
const FOLD_KEY = 'omni.folded'
const read = (store: () => Storage, k: string) => {
  try {
    return store().getItem(k) ?? ''
  } catch {
    return ''
  }
}
const write = (store: () => Storage, k: string, v: string) => {
  try {
    if (v) store().setItem(k, v)
    else store().removeItem(k)
  } catch {
    /* just not kept */
  }
}

const TONE: Record<Group['tone'], string> = { ALERT: 'alert', ACCENT: 'accent', PLAIN: '', MUTED: 'muted' }

export function TasksPage(p: PageProps) {
  const [text, setText] = useState('')
  // Folded groups are remembered on this device, as Android remembers them.
  const [folded, setFolded] = useState<string[]>(() => read(() => localStorage, FOLD_KEY).split('\n').filter(Boolean))
  const [adding, setAdding] = useState(false)
  const [showDone, setShowDone] = useState(false)
  const [query, setQueryState] = useState<ListQuery>(storedQuery.get)
  const setQuery = (q: ListQuery) => { setQueryState(q); storedQuery.set(q) }
  const [overlay, setOverlay] = useState<'filter' | 'sort' | null>(null)
  // The open task, by key and title: a key is its line, so the title confirms it is still the same task.
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const search = useRef<HTMLInputElement>(null)

  // "/" jumps to the search box, as on the web versions of TickTick and Linear.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
      if (e.key === '/' && !typing && !adding && !overlay) {
        e.preventDefault()
        search.current?.focus()
      }
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [adding, overlay])

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
        const byKey = p.snapshot?.byKey.get(selected.key)
        return byKey?.title === selected.title ? byKey : all.find((t) => t.title === selected.title) ?? null
      })()
    : null
  const openTask = (t: Task) => setSelected({ key: t.key, title: t.title })

  // What the filter row shows: each active filter as a chip that takes it off.
  const active: [string, () => void][] = [
    ...query.kinds.map((k): [string, () => void] => [labelOf(KINDS, k), () => setQuery({ ...query, kinds: query.kinds.filter((x) => x !== k) })]),
    ...query.buckets.map((k): [string, () => void] => [labelOf(BUCKETS, k), () => setQuery({ ...query, buckets: query.buckets.filter((x) => x !== k) })]),
    ...query.priorities.map((k): [string, () => void] => [labelOf(PRIORITIES, k), () => setQuery({ ...query, priorities: query.priorities.filter((x) => x !== k) })]),
    ...query.tags.map((k): [string, () => void] => ['#' + k, () => setQuery({ ...query, tags: query.tags.filter((x) => x !== k) })]),
    ...(filterCount({ ...query, kinds: [], buckets: [], priorities: [], tags: [] })
      ? [[query.statuses.map((s) => labelOf(STATUSES, s)).join(', ') || 'ทุกสถานะ', () => setQuery({ ...query, statuses: DEFAULT_QUERY.statuses })] as [string, () => void]]
      : []),
  ]
  const sorts = query.sorts.length ? query.sorts : DEFAULT_QUERY.sorts
  const sortText = `${labelOf(SORTS, sorts[0].by)} ${sorts[0].ascending ? '↑' : '↓'}${sorts.length > 1 ? ` +${sorts.length - 1}` : ''}`
  const fold = (label: string) => {
    const next = folded.includes(label) ? folded.filter((f) => f !== label) : [...folded, label]
    setFolded(next)
    write(() => localStorage, FOLD_KEY, next.join('\n'))
  }

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
    <main class="page">
      <PageHead title="งาน">
          <label class="search">
            <span class="faint">ค้นหา</span>
            <input ref={search} aria-label="ค้นหาชื่องาน" placeholder="ค้นหาชื่องาน" value={text} onInput={(e) => setText(e.currentTarget.value)} />
            <span class="kbd wide-only">/</span>
          </label>
          <button class="primary wide-only" onClick={() => setAdding(true)}>+ เพิ่มงาน</button>
      </PageHead>

      <div class="toolbar">
        <button class={`chip tool${filterCount(query) ? ' on' : ''}`} onClick={() => setOverlay('filter')}>
          กรอง{filterCount(query) ? ` ${filterCount(query)}` : ''}
        </button>
        <button class="chip tool" onClick={() => setOverlay('sort')}>กลุ่ม: {labelOf(GROUPS, query.groupBy)}</button>
        <button class="chip tool" onClick={() => setOverlay('sort')}>เรียง: {sortText}</button>
      </div>
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
            <section class="group empty">{text ? 'ไม่มีงานตรงกับคำค้น' : filterCount(query) ? 'ไม่มีงานตรงกับตัวกรอง' : 'ไม่มีงานค้าง'}</section>
          )}
          {list!.groups.map((g) => {
            const open = !folded.includes(g.label)
            return (
              <section key={g.label} class="group">
                <h2>
                  <button class="group-head" aria-expanded={open} onClick={() => fold(g.label)}>
                    <span class={`caret${open ? ' open' : ''}`}>›</span>
                    <span class={`group-label ${TONE[g.tone]}`}>{g.label}</span>
                    <span class="count">{g.keys.length}</span>
                  </button>
                </h2>
                {open && (
                  <ul class="plain">
                    {g.keys.map((k) => {
                      const t = p.snapshot!.byKey.get(k)!
                      return (
                        <TaskRow key={k} task={t} progress={list!.progress[k]} busy={p.busy} selected={current?.key === k}
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

      <button class="fab narrow-only" aria-label="เพิ่มงาน" onClick={() => setAdding(true)}>+</button>
      {adding && <QuickAdd {...p} onClose={() => setAdding(false)} />}
      {overlay === 'filter' && <FilterPanel query={query} tags={tags} shown={shown} onChange={setQuery} onClose={() => setOverlay(null)} />}
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

/** A sentence in, a TaskForge line out: dates, times, tags and priority are read from the words, as on Android. */
export function QuickAdd(p: PageProps & { onClose: () => void }) {
  // The draft survives the trip to Google when the sign-in runs out while typing.
  const [sentence, setSentenceState] = useState(() => read(() => sessionStorage, DRAFT_KEY))
  const setSentence = (v: string) => { setSentenceState(v); write(() => sessionStorage, DRAFT_KEY, v) }
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])

  // The tags already in use, most used first, to tap instead of typing.
  const tags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([g]) => g)
  }, [p.snapshot])
  const used = (g: string) => sentence.split(/\s+/).includes('#' + g)

  const submit = (e: Event) => {
    e.preventDefault()
    const s = sentence
    if (!s.trim()) return
    p.run(async () => {
      const res = await p.vault.add(s)
      if (res.ok) {
        setSentence('')
        p.onClose()
      }
      return res
    })
  }

  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <form class="dialog quick" role="dialog" aria-modal="true" aria-label="เพิ่มงาน" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>เพิ่มงาน</h2>
        <input
          ref={input}
          class="quick-input"
          aria-label="งานใหม่"
          placeholder="เช่น ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ"
          value={sentence}
          onInput={(e) => setSentence(e.currentTarget.value)}
        />
        {tags.length > 0 && (
          <div class="chips">
            {tags.map((g) => (
              <button
                type="button"
                key={g}
                class={`chip${used(g) ? ' on' : ''}`}
                onClick={() => { if (!used(g)) setSentence(`${sentence.trimEnd()} #${g} `.trimStart()); input.current?.focus() }}
              >#{g}</button>
            ))}
          </div>
        )}
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={p.busy || !sentence.trim()}>เพิ่ม</button>
        </div>
      </form>
    </div>
  )
}
