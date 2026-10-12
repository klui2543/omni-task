import type { Task } from '../../types'

export const shortDate = (iso: string) => new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** The tick ring of a task, drawn without a button of its own (for rows that are one button as a whole). */
export function RingMark({ task }: { task: Task }) {
  const state = task.status === 'DONE' ? 'done' : task.status === 'CANCELLED' ? 'cancelled' : task.status === 'IN_PROGRESS' ? 'prog' : ''
  const tint = task.priority === 'HIGHEST' ? 'var(--red)' : task.priority === 'HIGH' ? 'var(--amber)' : task.priority === 'MEDIUM' ? 'var(--blue)' : 'var(--faint)'
  return <span class={`ring ${state}`} style={{ '--tint': tint }} />
}

/**
 * The ring of a task card, a button of its own: a tap on it ticks the task, while a tap anywhere else on the card
 * opens it. Pressing it never starts a drag of the card or the map.
 */
export function RingButton({ task, busy, onTick }: { task: Task; busy: boolean; onTick: () => void }) {
  const done = task.status === 'DONE'
  return (
    <button class="ring-btn" role="checkbox" aria-checked={done} disabled={busy} aria-label={`${done ? 'ยกเลิกการติ๊ก' : 'ติ๊กเสร็จ'} ${task.title}`}
      onPointerDown={(e) => e.stopPropagation()} onClick={(e) => { e.stopPropagation(); onTick() }}>
      <RingMark task={task} />
    </button>
  )
}

