import { useState } from 'preact/hooks'
import type { Task } from '../../types'
import { type ViewCtx, compactMeta } from './ctx'
import { DragGhost, useTaskDrag } from './drag'

type Status = Task['status']
const COLUMNS: [Status, string, string][] = [
  ['TODO', 'ยังไม่เริ่ม', 'var(--v-grey)'],
  ['IN_PROGRESS', 'กำลังทำ', 'var(--accent)'],
  ['DONE', 'เสร็จ', 'var(--lime)'],
]
const ORDER: Status[] = ['TODO', 'IN_PROGRESS', 'DONE']
const labelOf = (s: Status) => COLUMNS.find(([k]) => k === s)![1]

const FOLD_KEY = 'omni.foldedColumns'
const readFolded = (): Status[] => {
  try {
    return (localStorage.getItem(FOLD_KEY) ?? '').split(',').filter((s): s is Status => ORDER.includes(s as Status))
  } catch {
    return []
  }
}

const TINT: Record<Task['priority'], string> = {
  HIGHEST: 'var(--red)', HIGH: 'var(--amber)', MEDIUM: 'var(--blue)', NONE: 'var(--v-grey)', LOW: 'var(--v-grey)', LOWEST: 'var(--v-grey)',
}

/**
 * Three columns by status. A column folds to a thin strip (remembered on this device) and still takes drops;
 * with "hide done" on, Done starts folded. A card moves by dragging it to a column or with its arrow buttons.
 */
export function Kanban(p: ViewCtx & { hideDone: boolean; onShowDone: () => void; onAdd: (status: Status) => void }) {
  const [folded, setFolded] = useState<Status[]>(readFolded)
  const toggle = (s: Status) => {
    const next = folded.includes(s) ? folded.filter((x) => x !== s) : [...folded, s]
    setFolded(next)
    try { localStorage.setItem(FOLD_KEY, next.join(',')) } catch { /* just not kept */ }
  }

  /** Moves a card to a column. Finishing goes through the tick, so repeating tasks and open subtasks behave as there. */
  const move = (t: Task, to: Status) => {
    if (to === t.status) return
    if (to === 'DONE') {
      if (t.open) p.p.tick(t)
      return
    }
    p.p.run(() => p.p.vault.change(p.p.fresh(t), { op: 'status', value: to }))
  }

  const { drag, grip } = useTaskDrag((key, target) => {
    const t = p.task(key)
    if (t && ORDER.includes(target as Status)) move(t, target as Status)
  })

  return (
    <div class="kcols">
      {COLUMNS.map(([status, label, dot]) => {
        const keys = p.out.kanban.find((c) => c.status === status)?.keys ?? []
        const hiddenDone = status === 'DONE' && p.hideDone
        const isFolded = folded.includes(status) || hiddenDone
        const over = drag?.over === status
        if (isFolded) {
          const open = () => (hiddenDone ? p.onShowDone() : toggle(status))
          return (
            <div key={status} class={`kfold${over ? ' over' : ''}`} data-drop={status} style={{ '--dot': dot }}>
              <button class="kfold-dot" aria-hidden="true" tabIndex={-1} onClick={open}><span class="kdot" /></button>
              {status !== 'DONE' && <button class="kplus" aria-label={`เพิ่มงานใน ${label}`} onClick={() => p.onAdd(status)}>+</button>}
              <button class="kfold-main" aria-label={`${label} (พับอยู่ แตะเพื่อแสดง${hiddenDone ? 'งานที่เสร็จ' : ''})`} onClick={open}>
                <span class="kcount">{keys.length}</span>
                <span class="kvert">{label}</span>
              </button>
            </div>
          )
        }
        return (
          <section key={status} class={`kcol${over ? ' over' : ''}`} data-drop={status} aria-label={label} style={{ '--dot': dot }}>
            <button class="khead" aria-expanded="true" aria-label={`พับ ${label}`} onClick={() => toggle(status)}>
              <span class="kdot" />
              <span class="khead-label">{label}</span>
              <span class="kcount">{keys.length}</span>
              <span class="kfoldmark" aria-hidden="true">‹</span>
            </button>
            <div class="kcards">
              {keys.map((key) => {
                const t = p.task(key)
                if (!t) return null
                const i = ORDER.indexOf(t.status)
                const prev = i > 0 ? ORDER[i - 1] : null
                const next = i >= 0 && i < ORDER.length - 1 ? ORDER[i + 1] : null
                const pills = compactMeta(t, p.today, p.out.progress[t.key])
                return (
                  <article
                    key={key}
                    class={`kcard${drag?.key === key ? ' lifted' : ''}${p.selectedKey === key ? ' selected' : ''}${t.open ? '' : ' done'}`}
                    onClick={(e) => { if (!(e.target as Element).closest('button')) p.open(t) }}
                    {...grip(key, t.title)}
                  >
                    <span class="kbar" style={{ background: t.status === 'DONE' ? 'var(--lime)' : TINT[t.priority] }} />
                    <div class="kbody">
                      <button class="kopen" onClick={() => p.open(t)}>{t.title}</button>
                      {t.preview && <span class="kprev">{t.preview}</span>}
                      <div class="kmeta">
                        {pills.map((m, j) => <span key={j} class={`pill ${m.tone}`}>{m.text}</span>)}
                        <span class="grow" />
                        <button class="kmove" data-nodrag disabled={!prev} aria-hidden={prev ? undefined : 'true'} tabIndex={prev ? 0 : -1}
                          aria-label={prev ? `ย้ายไป ${labelOf(prev)}` : undefined} onClick={() => prev && move(t, prev)}>‹</button>
                        <button class="kmove" data-nodrag disabled={!next} aria-hidden={next ? undefined : 'true'} tabIndex={next ? 0 : -1}
                          aria-label={next ? `ย้ายไป ${labelOf(next)}` : undefined} onClick={() => next && move(t, next)}>›</button>
                      </div>
                    </div>
                  </article>
                )
              })}
              {keys.length === 0 && <p class="kempty muted small">ไม่มีงาน</p>}
            </div>
          </section>
        )
      })}
      <DragGhost drag={drag} />
    </div>
  )
}
