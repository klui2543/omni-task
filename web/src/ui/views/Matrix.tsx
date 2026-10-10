import type { Quadrant, ViewsIn } from '../../types'
import { shortDate } from './dates'
import type { ViewCtx } from './ctx'
import { DragGhost, useTaskDrag } from './drag'

const COLOR: Record<Quadrant, string> = { DO: 'var(--red)', PLAN: 'var(--amber)', QUICK: 'var(--blue)', LATER: 'var(--teal)' }

/**
 * The Eisenhower matrix: open tasks in four quadrants, urgent from the date and important from the priority.
 * Dragging a task up or down changes its importance (High in, Medium out); across the urgent line is refused,
 * since urgency comes from the date. The small arrows do the same by tap.
 */
export function Matrix(p: ViewCtx & { urgent: ViewsIn['urgent']; onNotice: (text: string) => void }) {
  const quads = p.out.matrix

  const move = (key: string, target: string) => {
    const to = quads.find((q) => q.id === target)
    const from = quads.find((q) => q.keys.includes(key))
    const t = p.task(key)
    if (!to || !from || !t || from.id === to.id) return
    if (from.urgent !== to.urgent) return p.onNotice(p.out.sideways)
    p.p.run(() => p.p.vault.change(p.p.fresh(t), { op: 'quadrant', value: to.id, field: p.urgent }))
  }
  const { drag, grip } = useTaskDrag(move)

  return (
    <>
      <div class="mgrid">
        {quads.map((q) => {
          const partner = quads.find((o) => o.urgent === q.urgent && o.important !== q.important)!
          return (
            <section key={q.id} class={`mquad${drag?.over === q.id ? ' over' : ''}`} data-drop={q.id} aria-label={q.label} style={{ '--dot': COLOR[q.id] }}>
              <div class="mhead">
                <span class="kdot" />
                <span class="mlabel">{q.label}</span>
                <span class="mcount">{q.keys.length}</span>
              </div>
              <div class="mitems">
                {q.keys.map((key) => {
                  const t = p.task(key)
                  if (!t) return null
                  const d = t.due ?? t.scheduled
                  return (
                    <div key={key} class={`mitem${drag?.key === key ? ' lifted' : ''}${p.selectedKey === key ? ' selected' : ''}`} {...grip(key, t.title)}>
                      <button class="mopen" onClick={() => p.open(t)}>
                        <span class="mtitle">{t.title}</span>
                        {d && <span class={`mdate${d < p.today ? ' late' : ''}`}>{d === p.today ? 'วันนี้' : shortDate(d)}</span>}
                      </button>
                      <button class="kmove" data-nodrag aria-label={`ย้ายไป ${partner.label}`} onClick={() => move(key, partner.id)}>{q.important ? '↓' : '↑'}</button>
                    </div>
                  )
                })}
              </div>
            </section>
          )
        })}
      </div>
      <p class="mhint muted small">กดค้างแล้วลากขึ้นหรือลงเพื่อเปลี่ยนความสำคัญ</p>
      <DragGhost drag={drag} />
    </>
  )
}
