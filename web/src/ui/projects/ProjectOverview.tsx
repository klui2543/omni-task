import type { OrderOut, ProjectOut, Task } from '../../types'
import { Check } from '../TaskRow'
import { shortDate } from './bits'
import { useSortable } from './Sortable'

const STATE_LABEL: Record<string, string> = { ACTIVE: 'กำลังทำ', TRYING: 'กำลังลอง', CHOSEN: 'เลือกแล้ว', PARKED: 'พับเก็บ' }

/** The line under a task in the order list, in Android's words. */
export const metaText = (o: OrderOut): string => {
  if (o.meta === 'strict') return `รองานข้อ ${o.pos} ก่อน`
  if (o.meta === 'waiting') return `รอ ${o.waitingOn ?? ''}`.trimEnd()
  return [
    o.next ? 'ทำถัดไป' : null,
    o.date ? shortDate(o.date) : null,
    o.subTotal ? `งานย่อย ${o.subDone}/${o.subTotal}` : null,
  ].filter(Boolean).join(', ')
}

/** A project's overview: how it stands, the mind map, "do in order", the tasks in order and what is done. */
export function ProjectOverview(p: {
  project: ProjectOut
  task: (key: string) => Task | undefined
  selected?: string
  busy: boolean
  onTick: (t: Task) => void
  onOpen: (t: Task) => void
  onMap: () => void
  onStrict: (on: boolean) => void
  onOrder: (tasks: Task[]) => void
}) {
  const pr = p.project
  const sort = useSortable(pr.order.map((o) => o.key), (keys) => p.onOrder(keys.map((k) => p.task(k)).filter((t): t is Task => !!t)))
  const rows = new Map(pr.order.map((o) => [o.key, o]))
  const branches = pr.branches.slice(1)
  const mapLine = branches.length === 0
    ? 'ยังไม่มีกิ่ง แตะเพื่อแตกกิ่งแรก'
    : (['ACTIVE', 'TRYING', 'CHOSEN', 'PARKED'] as const)
        .map((s) => [s, branches.filter((b) => b.state === s).length] as const)
        .filter(([, n]) => n > 0)
        .map(([s, n]) => `${STATE_LABEL[s]} ${n}`)
        .join(', ')
  const finished = pr.finished.map(p.task).filter((t): t is Task => !!t)

  return (
    <div class="pj-cols">
      <div class="pj-col a">
        <div class="pj-stats">
          <div class="pj-stat"><div class="pj-num lime">{pr.pct}%</div><div class="small muted">เสร็จแล้ว</div></div>
          <div class="pj-stat"><div class={`pj-num${pr.overdue > 0 ? ' red' : ''}`}>{pr.overdue}</div><div class="small muted">เลยกำหนด</div></div>
          <div class="pj-stat"><div class={`pj-num${pr.blocked.length > 0 ? ' amber' : ''}`}>{pr.blocked.length}</div><div class="small muted">ติดรองานอื่น</div></div>
        </div>

        <button class="pj-card-row" onClick={p.onMap}>
          <span class="grow stack"><span class="strong">Mind map ของโปรเจกต์</span><span class="small muted">{mapLine}</span></span>
          <span class="faint">›</span>
        </button>

        <div class="pj-card-row">
          <span class="grow stack">
            <span>ต้องทำตามลำดับ</span>
            <span class="small muted">
              {pr.strict
                ? 'งานถัดไปรองานก่อนหน้า (เขียนรหัสงานลงไฟล์ให้ปลั๊กอิน Tasks เห็นด้วย)'
                : 'ปิดอยู่: ลำดับเป็นแค่คำแนะนำ ทำข้ามได้'}
            </span>
          </span>
          <button class="pj-switch" role="switch" aria-checked={pr.strict} aria-label="ต้องทำตามลำดับ" disabled={p.busy} onClick={() => p.onStrict(!pr.strict)}><span /></button>
        </div>

        <span class="small muted">Milestone จะมาพร้อมรูปแบบไฟล์ dotpm ส่วนงานที่ต้องรองานอื่นใช้รหัสงานแบบปลั๊กอิน Tasks</span>
      </div>

      <div class="pj-col b">
        {pr.order.length > 0 && (
          <section class="group pj-section">
            <div class="pj-section-head"><span class="strong grow">ลำดับงาน</span><span class="count">{pr.order.length}</span></div>
            <ul class="plain" ref={sort.ref as preact.Ref<HTMLUListElement>}>
              {sort.order.map((k) => {
                const o = rows.get(k)
                const t = p.task(k)
                if (!o || !t) return null
                const meta = metaText(o)
                return (
                  <li key={k} class={`pj-order${p.selected === k ? ' selected' : ''}${sort.lifted === k ? ' lifted' : ''}`} {...sort.row(k)}>
                    <button class="pj-handle" aria-label={`ลากเพื่อเรียง ${t.title}`} {...sort.handle(k)}>⋮⋮</button>
                    <span class={`pj-n${o.next ? ' next' : ''}`}>{sort.order.indexOf(k) + 1}</span>
                    <Check task={t} disabled={p.busy} onToggle={() => p.onTick(t)} />
                    <button class="pj-order-body" onClick={() => p.onOpen(t)}>
                      <span class={`ttitle${o.locked ? ' blocked' : ''}`}>{t.title}</span>
                      {meta && <span class={`small ${o.locked ? 'amber-text' : o.next ? 'accent-text' : 'muted'}`}>{meta}</span>}
                    </button>
                    {o.locked && <span class="pill amber">ล็อก</span>}
                  </li>
                )
              })}
            </ul>
          </section>
        )}
        {finished.length > 0 && (
          <section class="group pj-section">
            <div class="pj-section-head"><span class="strong grow">เสร็จแล้ว</span><span class="count">{finished.length}</span></div>
            <ul class="plain">
              {finished.map((t) => (
                <li key={t.key} class={`pj-order done${p.selected === t.key ? ' selected' : ''}`}>
                  <Check task={t} disabled={p.busy} onToggle={() => p.onTick(t)} />
                  <button class="pj-order-body" onClick={() => p.onOpen(t)}><span class="ttitle">{t.title}</span></button>
                  {t.done && <span class="pill lime">{shortDate(t.done)}</span>}
                </li>
              ))}
            </ul>
          </section>
        )}
        {pr.order.length === 0 && finished.length === 0 && <section class="group empty">ยังไม่มีงานในโปรเจกต์นี้</section>}
      </div>
    </div>
  )
}
