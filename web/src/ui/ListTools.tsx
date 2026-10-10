import type { ComponentChildren } from 'preact'
import { useState } from 'preact/hooks'
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

/**
 * Filters by kind, date, priority, status and tag, applied as they are tapped. The button at the foot says how
 * many tasks are left; filters can be saved by name, as on Android.
 */
export function FilterPanel(p: {
  query: ListQuery
  tags: string[]
  shown: number
  onChange: (q: ListQuery) => void
  onClose: () => void
  /** Android's "hide done" switch, which only the views honour; both the Tasks and Views pages show it (one shared choice). */
  hideDone?: { on: boolean; set: (on: boolean) => void }
}) {
  const q = p.query
  const [saved, setSaved] = useState(savedFilters.get)
  const [naming, setNaming] = useState<string | null>(null)
  const set = (patch: Partial<ListQuery>) => p.onChange({ ...q, ...patch })
  const keep = (list: SavedFilter[]) => { savedFilters.set(list); setSaved(list) }

  const section = <T extends string>(title: string, pairs: [T, string][], on: T[], key: keyof ListQuery, dot?: (k: T) => string) => (
    <div class="filter-section" role="group" aria-label={title}>
      <span class="muted small">{title}</span>
      <div class="chips">
        {pairs.map(([k, label]) => (
          <button key={k} class={`chip${on.includes(k) ? ' on' : ''}`} aria-pressed={on.includes(k)} onClick={() => set({ [key]: toggleIn(on, k) } as Partial<ListQuery>)}>
            {dot && <span class="dot-mark" style={{ background: dot(k) }} />}{label}
          </button>
        ))}
      </div>
    </div>
  )

  return (
    <Overlay label="กรอง" side onClose={p.onClose}>
      <div class="panel-head">
        <h2>กรอง</h2>
        <button class="link danger" onClick={() => p.onChange({ ...DEFAULT_QUERY, groupBy: q.groupBy, sorts: q.sorts })}>ล้างทั้งหมด</button>
      </div>
      {saved.length > 0 && (
        <div class="filter-section">
          <span class="muted small">ตัวกรองที่บันทึกไว้</span>
          <div class="chips">
            {saved.map((f) => (
              <span key={f.name} class="chip saved">
                <button class="chip-main" onClick={() => p.onChange(f.query)}>★ {f.name}</button>
                <button class="chip-x" aria-label={`ลบตัวกรอง ${f.name}`} onClick={() => keep(saved.filter((s) => s.name !== f.name))}>×</button>
              </span>
            ))}
          </div>
        </div>
      )}
      <div class="panel-body">
        {section('ประเภทงาน', KINDS, q.kinds, 'kinds')}
        {section('วันที่', BUCKETS, q.buckets, 'buckets')}
        {section('ความสำคัญ', PRIORITIES, q.priorities, 'priorities', (k) => PRIORITY_DOT[k])}
        {section('สถานะ', STATUSES, q.statuses, 'statuses')}
        {p.tags.length > 0 && section('Tag', p.tags.map((t): [string, string] => [t, '#' + t]), q.tags, 'tags')}
        {p.hideDone && (
          <label class="setting hide-done">
            <span class="stack">
              <span>ซ่อนงานที่เสร็จและยกเลิก</span>
              <span class="muted small">ในมุมมอง Kanban, Matrix, Gantt และปฏิทิน</span>
            </span>
            <input type="checkbox" class="switch" role="switch" aria-label="ซ่อนงานที่เสร็จและยกเลิก" checked={p.hideDone.on} onChange={(e) => p.hideDone!.set(e.currentTarget.checked)} />
          </label>
        )}
      </div>
      {naming !== null ? (
        <form class="panel-foot" onSubmit={(e) => { e.preventDefault(); if (naming.trim()) { keep([...saved.filter((s) => s.name !== naming.trim()), { name: naming.trim(), query: q }]); setNaming(null) } }}>
          <input class="field" aria-label="ชื่อตัวกรอง" placeholder="ชื่อตัวกรอง" value={naming} onInput={(e) => setNaming(e.currentTarget.value)} autoFocus />
          <button class="primary" disabled={!naming.trim()}>บันทึก</button>
        </form>
      ) : (
        <div class="panel-foot">
          <button class="ghost grow" onClick={() => setNaming('')}>บันทึกตัวกรองนี้</button>
          <button class="primary grow" onClick={p.onClose}>แสดง {p.shown} งาน</button>
        </div>
      )}
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
