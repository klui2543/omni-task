import type { JSX } from 'preact'
import { useRef } from 'preact/hooks'
import type { Task } from '../../types'

export const shortDate = (iso: string) => new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** The tick ring of a task, drawn without a button of its own (for rows that are one button as a whole). */
export function RingMark({ task }: { task: Task }) {
  const state = task.status === 'DONE' ? 'done' : task.status === 'CANCELLED' ? 'cancelled' : task.status === 'IN_PROGRESS' ? 'prog' : ''
  const tint = task.priority === 'HIGHEST' ? 'var(--red)' : task.priority === 'HIGH' ? 'var(--amber)' : task.priority === 'MEDIUM' ? 'var(--blue)' : 'var(--faint)'
  return <span class={`ring ${state}`} style={{ '--tint': tint }} />
}

/**
 * A tap that ticks and a long press (or a right click) that opens, as Android's list items and mind map tasks do.
 * A press that turned into a long one does not also tick.
 */
export function useTapOrHold(onTap: () => void, onHold: () => void, ms = 500) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const held = useRef(false)
  const clear = () => {
    if (timer.current) clearTimeout(timer.current)
    timer.current = null
  }
  return {
    onPointerDown: (e: JSX.TargetedPointerEvent<HTMLElement>) => {
      if (e.button) return
      held.current = false
      clear()
      timer.current = setTimeout(() => { held.current = true; timer.current = null; onHold() }, ms)
    },
    onPointerUp: clear,
    onPointerLeave: clear,
    onPointerCancel: clear,
    onContextMenu: (e: Event) => { e.preventDefault(); clear(); held.current = true; onHold() },
    onClick: (e: Event) => {
      if (held.current) { held.current = false; e.preventDefault(); return }
      onTap()
    },
  }
}
