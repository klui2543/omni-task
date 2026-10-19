import type { ComponentChildren } from 'preact'
import { useState } from 'preact/hooks'
import { kindsView } from '../core'
import { kindsStore } from '../kinds'
import {
  BUCKETS, DEFAULT_QUERY, GROUPS, KINDS, ListQuery, PRIORITIES, PRIORITY_DOT, SORTS, STATUSES, SavedFilter, savedFilters, toggleIn,
} from '../query'

/** A panel from the right on wide screens and a sheet from the bottom on narrow ones; a tap outside closes it. */
function Overlay(p: { label: string; side?: boolean; onClose: () => void; children: ComponentChildren }) {
  return (
    <div class={`scrim${p.side ? ' side-scrim' : ' sheet-scrim'}`} onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class={`dialog ${p.side ? 'side-panel' : 'sort-box'}`} role="dialog" aria-modal="true" aria-label={p.label} onClick={(e) => e.stopPropagation()}>
        {p.children}
      </section>
    </div>
  )
}

/** The tags the quick "me" and "Claude" chips stand for: whose turn a task is. */
export const ME_TAG = 'kluido'
export const CLAUDE_TAG = 'claudedo'
const QUICK_TAGS = [ME_TAG, CLAUDE_TAG]
const QUICK_BUCKETS = ['TODAY', 'OVERDUE']

/** Filters chosen beyond the quick chips, for the note on the "more filters" row (as on Android). */
export const moreInUse = (q: ListQuery) =>
  (sameStatuses(q.statuses) ? 0 : 1) + q.kinds.length + q.lists.length + q.priorities.length + q.notes.length +
  q.tags.filter((t) => !QUICK_TAGS.includes(t)).length + q.buckets.filter((b) => !QUICK_BUCKETS.includes(b)).length
const sameStatuses = (a: string[]) => a.length === DEFAULT_QUERY.statuses.length && a.every((x) => (DEFAULT_QUERY.statuses as string[]).includes(x))

/**
 * The filter in two layers, shared by the Tasks and Views pages and Project Ygdrasil: four quick chips (me, Claude,
 * today, overdue) and the saved filters always show; every other filter waits under "more filters". Chips apply as
 * they are tapped. [extra] adds a page's own choices under "more"; [extraInUse] counts them.
 */
export function FilterBody(p: {
  query: ListQuery
  onChange: (q: ListQuery) => void
  tags: string[]
  notes?: string[]
  lists?: string[]
  more: boolean
  onMore: (open: boolean) => void
  hideDone?: { on: boolean; set: (on: boolean) => void }
  extra?: ComponentChildren
  extraInUse?: number
}) {
  const q = p.query
  const [saved, setSaved] = useState(savedFilters.get)
  const set = (patch: Partial<ListQuery>) => p.onChange({ ...q, ...patch })
  const keep = (list: SavedFilter[]) => { savedFilters.set(list); setSaved(list) }

  // The owner's own kinds are tags that mark a kind, so they are chips beside the built-in kinds and not in the Tag list.
  const ownKinds = kindsView(kindsStore.get()).custom
  const plainTags = p.tags.filter((g) => !QUICK_TAGS.includes(g) && !ownKinds.some((c) => c.tag.toLowerCase() === g.toLowerCase()))

  const section = <T extends string>(title: string, pairs: [T, string][], on: T[], key: keyof ListQuery, dot?: (k: T) => string, extra?: ComponentChildren) => (
    <div class="filter-section" role="group" aria-label={title}>
      <span class="muted small">{title}</span>
      <div class="chips">
        {pairs.map(([k, label]) => (
          <button key={k} class={`chip${on.includes(k) ? ' on' : ''}`} aria-pressed={on.includes(k)} onClick={() => set({ [key]: toggleIn(on, k) } as Partial<ListQuery>)}>
            {dot && <span class="dot-mark" style={{ background: dot(k) }} />}{label}
          </button>
        ))}
        {extra}
      </div>
    </div>
  )
  const quick = (label: string, on: boolean, flip: () => void, dot?: string) => (
    <button class={`quick-chip${on ? ' on' : ''}`} aria-pressed={on} onClick={flip}>
      {dot && <span class="dot-mark" style={{ background: dot }} />}{label}
    </button>
  )
  const used = moreInUse(q) + (p.extraInUse ?? 0)

  return (
    <div class="filter-body">
      <div class="filter-section">
        <span class="muted small">ด่วน</span>
        <div class="quick-chips" role="group" aria-label="ตัวกรองด่วน">
          {quick('ฉัน', q.tags.includes(ME_TAG), () => set({ tags: toggleIn(q.tags, ME_TAG) }), 'var(--teal)')}
          {quick('Claude', q.tags.includes(CLAUDE_TAG), () => set({ tags: toggleIn(q.tags, CLAUDE_TAG) }), 'var(--accent-text)')}
          {quick('วันนี้', q.buckets.includes('TODAY'), () => set({ buckets: toggleIn(q.buckets, 'TODAY') }))}
          {quick('เลยกำหนด', q.buckets.includes('OVERDUE'), () => set({ buckets: toggleIn(q.buckets, 'OVERDUE') }))}
        </div>
      </div>
      {saved.length > 0 && (
        <div class="filter-section">
          <span class="muted small">ตัวกรองที่บันทึกไว้</span>
          <div class="chips">
            {saved.map((f) => (
              <span key={f.name} class="chip saved">
                {/* A saved filter is only its filters: the grouping and sorting in use stay, as on Android. */}
                <button class="chip-main" onClick={() => p.onChange({ ...DEFAULT_QUERY, ...f.query, groupBy: q.groupBy, sorts: q.sorts })}>★ {f.name}</button>
                <button class="chip-x" aria-label={`ลบตัวกรอง ${f.name}`} onClick={() => keep(saved.filter((s) => s.name !== f.name))}>×</button>
              </span>
            ))}
          </div>
        </div>
      )}
      <button class="more-filters" aria-expanded={p.more} onClick={() => p.onMore(!p.more)}>
        <span class="stack grow left">
          <span>ตัวกรองเพิ่มเติม</span>
          <span class={`small${used ? ' accent-text' : ' muted'}`}>{used ? `ใช้อยู่ ${used} อย่าง` : 'ประเภท วันที่ ความสำคัญ สถานะ Tag'}</span>
        </span>
        <span aria-hidden="true" class={`chev${p.more ? ' up' : ''}`}>⌄</span>
      </button>
      {p.more && (
        <div class="panel-body">
          {section('ประเภทงาน', KINDS, q.kinds, 'kinds', undefined, ownKinds.map((c) => (
            <button key={c.tag} class={`chip${q.tags.includes(c.tag) ? ' on' : ''}`} aria-pressed={q.tags.includes(c.tag)} onClick={() => set({ tags: toggleIn(q.tags, c.tag) })}>{c.label}</button>
          )))}
          {section('วันที่', BUCKETS.filter(([b]) => !QUICK_BUCKETS.includes(b)), q.buckets, 'buckets')}
          {section('ความสำคัญ', PRIORITIES, q.priorities, 'priorities', (k) => PRIORITY_DOT[k])}
          {section('สถานะ', STATUSES, q.statuses, 'statuses')}
          {plainTags.length > 0 && section('Tag', plainTags.map((t): [string, string] => [t, '#' + t]), q.tags, 'tags')}
          {p.lists && p.lists.length > 0 && section('ลิสต์', p.lists.map((l): [string, string] => [l, l]), q.lists, 'lists')}
          {p.notes && p.notes.length > 1 && section('โน้ต', p.notes.map((n): [string, string] => [n, n]), q.notes, 'notes')}
          {p.hideDone && (
            <label class="setting hide-done">
              <span class="stack">
                <span>ซ่อนงานที่เสร็จและยกเลิก</span>
                <span class="muted small">ในมุมมอง Kanban, Matrix, Timeline และปฏิทิน</span>
              </span>
              <input type="checkbox" class="switch" role="switch" aria-label="ซ่อนงานที่เสร็จและยกเลิก" checked={p.hideDone.on} onChange={(e) => p.hideDone!.set(e.currentTarget.checked)} />
            </label>
          )}
          {p.extra}
        </div>
      )}
    </div>
  )
}

/** Saving the filter in use by name: the button, then a name box in its place. */
export function SaveFilter(p: { query: ListQuery; children?: ComponentChildren }) {
  const [naming, setNaming] = useState<string | null>(null)
  if (naming === null) {
    return (
      <div class="panel-foot">
        <button class="ghost grow" onClick={() => setNaming('')}>บันทึกตัวกรองนี้</button>
        {p.children}
      </div>
    )
  }
  const save = () => {
    const name = naming.trim()
    if (!name) return
    savedFilters.set([...savedFilters.get().filter((s) => s.name !== name), { name, query: p.query }])
    setNaming(null)
  }
  return (
    <form class="panel-foot" onSubmit={(e) => { e.preventDefault(); save() }}>
      <input class="field" aria-label="ชื่อตัวกรอง" placeholder="ชื่อตัวกรอง" value={naming} onInput={(e) => setNaming(e.currentTarget.value)} autoFocus />
      <button class="primary" disabled={!naming.trim()}>บันทึก</button>
    </form>
  )
}

/** The Tasks and Views pages' filter: [FilterBody] in a side panel (wide) or a sheet (narrow). */
export function FilterPanel(p: {
  query: ListQuery
  tags: string[]
  shown: number
  onChange: (q: ListQuery) => void
  onClose: () => void
  /** The notes with tasks (by name) and the list notes to choose from, as on Android's filter sheet. */
  notes?: string[]
  lists?: string[]
  /** Android's "hide done" switch, which only the views honour; both the Tasks and Views pages show it (one shared choice). */
  hideDone?: { on: boolean; set: (on: boolean) => void }
  /** Empties the page's search box, which "clear all" clears too, as on Android. */
  onClearText?: () => void
}) {
  const q = p.query
  const [more, setMore] = useState(moreInUse(q) > 0)
  return (
    <Overlay label="กรอง" side onClose={p.onClose}>
      <div class="panel-head">
        <h2>กรอง</h2>
        <button class="link danger" onClick={() => { p.onChange({ ...DEFAULT_QUERY, groupBy: q.groupBy, sorts: q.sorts }); p.onClearText?.() }}>ล้างทั้งหมด</button>
      </div>
      <FilterBody query={q} onChange={p.onChange} tags={p.tags} notes={p.notes} lists={p.lists} more={more} onMore={setMore} hideDone={p.hideDone} />
      <SaveFilter query={q}>
        <button class="primary grow" onClick={p.onClose}>แสดง {p.shown} งาน</button>
      </SaveFilter>
    </Overlay>
  )
}

/** Grouping and up to three sort levels in one box, as on Android. */
export function SortDialog(p: { query: ListQuery; onChange: (q: ListQuery) => void; onClose: () => void; /** Views have no grouping. */ noGroup?: boolean }) {
  const q = p.query
  const sorts = q.sorts.length ? q.sorts : DEFAULT_QUERY.sorts
  const setSorts = (s: ListQuery['sorts']) => p.onChange({ ...q, sorts: s })
  const unused = SORTS.filter(([k]) => !sorts.some((s) => s.by === k))
  return (
    <Overlay label="จัดกลุ่มและเรียงลำดับ" onClose={p.onClose}>
      <div class="sort-cols">
        {!p.noGroup && <div class="sort-groups" role="radiogroup" aria-label="จัดกลุ่มตาม">
          <h2>จัดกลุ่มตาม</h2>
          {GROUPS.map(([k, label]) => (
            <button key={k} class="option" role="radio" aria-checked={q.groupBy === k} onClick={() => p.onChange({ ...q, groupBy: k })}>
              <span>{label}</span><span class="accent-text">{q.groupBy === k ? '✓' : ''}</span>
            </button>
          ))}
        </div>}
        <div class="sort-levels">
          <h2>เรียงลำดับ</h2>
          {sorts.map((s, i) => (
            <div key={i} class="sort-level" role="group" aria-label={i === 0 ? 'เรียงตาม' : 'แล้วตามด้วย'}>
              <div class="sort-level-head">
                <span class="muted small grow">{i === 0 ? 'เรียงตาม' : 'แล้วตามด้วย'}</span>
                <button class="chip" onClick={() => setSorts(sorts.map((x, j) => (j === i ? { ...x, ascending: !x.ascending } : x)))}>
                  {s.ascending ? '↑ น้อยไปมาก' : '↓ มากไปน้อย'}
                </button>
                {sorts.length > 1 && <button class="link quiet" onClick={() => setSorts(sorts.filter((_, j) => j !== i))}>เอาออก</button>}
              </div>
              <div class="chips">
                {SORTS.filter(([k]) => k === s.by || !sorts.some((x) => x.by === k)).map(([k, label]) => (
                  <button key={k} class={`chip${k === s.by ? ' on' : ''}`} aria-pressed={k === s.by} onClick={() => setSorts(sorts.map((x, j) => (j === i ? { ...x, by: k } : x)))}>{label}</button>
                ))}
              </div>
            </div>
          ))}
          {sorts.length < 3 && unused.length > 0 && (
            <button class="chip dashed" onClick={() => setSorts([...sorts, { by: unused[0][0], ascending: true }])}>+ เรียงต่อด้วย</button>
          )}
          <span class="muted small">ถ้ายังเท่ากัน เรียงตามความสำคัญ แล้วชื่องาน</span>
          <div class="dialog-actions"><button class="primary" onClick={p.onClose}>เสร็จ</button></div>
        </div>
      </div>
    </Overlay>
  )
}
